/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.desktop.data.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val GlowBandFraction = 0.42f
private const val GlowDriftPeriodMs = 34_000f
private const val GlowDriftX = 0.055f
private const val GlowDriftY = 0.10f
private const val GlowBreath = 0.09f
private const val TwoPi = (2.0 * PI).toFloat()

/** How often the slow backgrounds (this glow, the moving artwork) step: 30 times a second. */
internal const val BackgroundTickMs = 33L

/** The same, while Exhale isn't the focused window: 8 times a second, so it barely registers on the CPU. */
internal const val UnfocusedTickMs = 125L

private val artworkHttp = OkHttpClient()
private val artworkColorCache = object : LinkedHashMap<String, List<Color>>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>) = size > 32
}

/** Up to three dominant, saturated colours of an artwork. Cached by URL. */
@Composable
fun rememberArtworkColors(url: String?): State<List<Color>> {
    val colors = remember { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(url) {
        if (url == null) {
            colors.value = emptyList()
            return@LaunchedEffect
        }
        synchronized(artworkColorCache) { artworkColorCache[url] }?.let {
            colors.value = it
            return@LaunchedEffect
        }
        val extracted = withContext(Dispatchers.IO) { runCatching { extractColors(url) }.getOrDefault(emptyList()) }
        if (extracted.isNotEmpty()) synchronized(artworkColorCache) { artworkColorCache[url] = extracted }
        colors.value = extracted
    }
    return colors
}

/**
 * Stand-in for androidx Palette: shrink the cover to 24×24, bucket pixels by hue weighted by
 * saturation × brightness, and average the three heaviest buckets.
 */
private fun extractColors(url: String): List<Color> {
    val bytes = artworkHttp.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
    val image = Image.makeFromEncoded(bytes)
    val side = 24
    val bitmap = Bitmap().apply { allocN32Pixels(side, side) }
    Canvas(bitmap).apply { drawImageRect(image, Rect.makeWH(side.toFloat(), side.toFloat())) }.close()

    // weight, r, g, b
    val buckets = Array(12) { FloatArray(4) }
    val hsb = FloatArray(3)
    for (y in 0 until side) for (x in 0 until side) {
        val argb = bitmap.getColor(x, y)
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        java.awt.Color.RGBtoHSB(r, g, b, hsb)
        if (hsb[1] < 0.18f || hsb[2] < 0.18f) continue
        val weight = hsb[1] * hsb[2]
        val bucket = buckets[(hsb[0] * 12).toInt().coerceIn(0, 11)]
        bucket[0] += weight
        bucket[1] += r * weight
        bucket[2] += g * weight
        bucket[3] += b * weight
    }
    return buckets.filter { it[0] > 0f }
        .sortedByDescending { it[0] }
        .take(3)
        .map { Color(it[1] / it[0] / 255f, it[2] / it[0] / 255f, it[3] / it[0] / 255f) }
}

/**
 * The Android app's AmbientArtworkGlow: three slow radial blobs of the artwork's colour washing
 * down from the top, animated in the draw phase only.
 */
@Composable
fun AmbientGlow(colors: List<Color>, modifier: Modifier = Modifier, intensity: Float = 1f, paused: Boolean = false) {
    val isPaused by androidx.compose.runtime.rememberUpdatedState(paused)
    if (!LocalSettings.current.ambientGlow) return
    val peakAlpha = 0.42f * intensity
    val presence by animateFloatAsState(
        targetValue = if (colors.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "ambient_presence",
    )
    val blobs = remember(colors) { colors.take(3) }

    // A 34-second drift moves less than a pixel between frames, so it steps 30 times a second rather
    // than at the display's rate. On a 240 Hz panel, a per-frame clock here redrew the whole window —
    // and re-blurred every piece of glass over it — 240 times a second, forever, even with nothing
    // else moving.
    val phase = remember { mutableFloatStateOf(0f) }
    val drifting = blobs.isNotEmpty()
    val focused by androidx.compose.runtime.rememberUpdatedState(androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused)
    LaunchedEffect(drifting) {
        if (!drifting) return@LaunchedEffect
        val start = System.nanoTime()
        while (true) {
            if (isPaused) {
                delay(250L)
                continue
            }
            val millis = (System.nanoTime() - start) / 1_000_000L
            phase.floatValue = (millis % GlowDriftPeriodMs.toLong()) / GlowDriftPeriodMs
            delay(if (focused) BackgroundTickMs else UnfocusedTickMs)
        }
    }

    Box(
        modifier = modifier.drawBehind {
            if (presence <= 0.01f || blobs.isEmpty()) return@drawBehind
            val band = size.height * GlowBandFraction
            val radius = size.width * 0.85f
            val turn = phase.floatValue * TwoPi
            blobs.forEachIndexed { index, color ->
                val cx = size.width * (0.24f + 0.26f * index)
                val cy = band * (0.18f + 0.14f * index)
                val angle = turn + index * 2.09f
                val dx = sin(angle) * size.width * GlowDriftX
                val dy = cos(angle * 0.73f) * band * GlowDriftY
                val breath = 1f + GlowBreath * sin(angle * 0.51f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = peakAlpha * presence),
                            color.copy(alpha = peakAlpha * 0.30f * presence),
                            Color.Transparent,
                        ),
                        center = Offset(cx + dx, cy + dy),
                        radius = radius * breath,
                    ),
                )
            }
        },
    )
}
