/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * What the Espresso player, ported from Exhale master, takes from the rest of master's Now Playing:
 * the cover's colours, the bled ground under the cover, the slow ambient clock and the one-line
 * lyric. Kept small and to themselves so the port touches nothing else in this player.
 */

package com.ozyern.exhale.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.ui.lyricsflow.LyricLine
import com.ozyern.exhale.ui.lyricsflow.latestLyricRow
import com.ozyern.exhale.ui.lyricsflow.rememberFlowPosition
import com.ozyern.exhale.ui.lyricsflow.rememberLyricClock
import com.ozyern.exhale.ui.lyricsflow.rememberLyricPlayhead
import com.ozyern.exhale.ui.lyricsflow.toFlowLines
import com.ozyern.exhale.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The cover's colours, as Espresso dresses itself in them: a few for the ground, one accent. */
@Immutable
internal data class EspressoPalette(val colors: List<Color>, val accent: Color)

private val EspressoFallback = EspressoPalette(
    colors = listOf(Color(0xFF3B2418), Color(0xFF6B3A22), Color(0xFF1E120C), Color(0xFF8C5A3C)),
    accent = Color(0xFFE8C9A0),
)

private val espressoPalettes = object : LinkedHashMap<String, EspressoPalette>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EspressoPalette>?) = size > 48
}

/** The cover's palette, worked out once per cover and kept, so reopening never fades into it. */
@Composable
internal fun rememberArtworkPalette(imageUrl: String?): EspressoPalette {
    val context = LocalContext.current
    var palette by remember {
        mutableStateOf(imageUrl?.let { synchronized(espressoPalettes) { espressoPalettes[it] } } ?: EspressoFallback)
    }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        synchronized(espressoPalettes) { espressoPalettes[imageUrl] }?.let {
            palette = it
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context).data(imageUrl).size(128, 128).allowHardware(false).build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@LaunchedEffect
        palette = withContext(Dispatchers.Default) { paletteOf(bitmap) }
            .also { synchronized(espressoPalettes) { espressoPalettes[imageUrl] = it } }
    }
    return palette
}

private fun paletteOf(bitmap: Bitmap): EspressoPalette {
    val generated = Palette.from(bitmap).maximumColorCount(24).generate()
    val colors = generated.swatches.sortedByDescending { it.population }.map { Color(it.rgb) }.take(4)
    val swatch = generated.lightVibrantSwatch ?: generated.vibrantSwatch ?: generated.lightMutedSwatch
        ?: generated.dominantSwatch
    val accent = swatch?.let {
        val hsl = FloatArray(3).also { out -> ColorUtils.colorToHSL(it.rgb, out) }
        hsl[1] = if (hsl[1] < 0.12f) hsl[1] else hsl[1].coerceIn(0.45f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.70f, 0.82f)
        Color(ColorUtils.HSLToColor(hsl))
    } ?: EspressoFallback.accent
    return EspressoPalette(colors.ifEmpty { EspressoFallback.colors }, accent)
}

/**
 * The ground under the cover: the cover itself, bled down the screen the way the lock screen does
 * it — drawn where the full-bleed cover sits, its last rows stretched to the foot, blurred hard and
 * settling into the colour its foot averages to — darkened only as far as the controls need.
 */
@Composable
internal fun LiveArtworkBackdrop(artUrl: String?, palette: EspressoPalette, heroHeight: Dp? = null) {
    Box(Modifier.fillMaxSize().background(palette.colors.firstOrNull()?.copy(alpha = 1f) ?: Color.Black)) {
        // Exhale's live background: a slow field of the cover's colours, for the little movement
        // that shows through the bleed.
        val playing = LocalPlayerConnection.current?.isPlaying?.collectAsState()?.value ?: false
        val (liveSetting) = com.ozyern.exhale.utils.rememberPreference(com.ozyern.exhale.constants.PlayerLiveArtworkKey, true)
        if (liveSetting) {
            com.ozyern.exhale.ui.component.FluidArtworkBackground(
                url = artUrl,
                brightness = 0.7f,
                paused = !playing,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (artUrl != null && heroHeight != null && heroHeight > 0.dp) EspressoBleed(artUrl, heroHeight)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.06f),
                        0.45f to Color.Black.copy(alpha = 0.16f),
                        0.75f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.46f),
                    ),
                ),
        )
    }
}

@Composable
private fun EspressoBleed(url: String, heroHeight: Dp) {
    val context = LocalContext.current
    val decoded by produceState<Pair<ImageBitmap, Color>?>(null, url) {
        value = withContext(Dispatchers.IO) {
            val request = ImageRequest.Builder(context)
                .data(url.resize(384, 384))
                .size(384, 384)
                .allowHardware(false)
                .build()
            runCatching {
                context.imageLoader.execute(request).image?.toBitmap()?.let { it.asImageBitmap() to footColour(it) }
            }.getOrNull()
        }
    }
    val (image, foot) = decoded ?: return
    val saturate = remember {
        androidx.compose.ui.graphics.ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.35f) })
    }
    val appear = remember(url) { Animatable(0f) }
    LaunchedEffect(url) { appear.animateTo(1f, tween(500)) }
    Canvas(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value * 0.92f
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val radius = 56.dp.toPx()
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        radius, radius, androidx.compose.ui.graphics.TileMode.Clamp,
                    )
                }
                clip = true
            },
    ) {
        val bannerH = heroHeight.toPx().coerceAtMost(size.height)
        val scale = maxOf(size.width / image.width, bannerH / image.height)
        val srcW = (size.width / scale).coerceAtMost(image.width.toFloat())
        val srcH = (bannerH / scale).coerceAtMost(image.height.toFloat())
        val srcX = ((image.width - srcW) / 2f).roundToInt()
        val srcY = ((image.height - srcH) / 2f).roundToInt()
        drawImage(
            image = image,
            srcOffset = IntOffset(srcX, srcY),
            srcSize = IntSize(srcW.roundToInt(), srcH.roundToInt()),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), bannerH.roundToInt()),
            colorFilter = saturate,
        )
        val stripH = (srcH * 0.06f).roundToInt().coerceAtLeast(1)
        val rest = (size.height - bannerH).roundToInt()
        if (rest > 0) {
            drawImage(
                image = image,
                srcOffset = IntOffset(srcX, srcY + srcH.roundToInt() - stripH),
                srcSize = IntSize(srcW.roundToInt(), stripH),
                dstOffset = IntOffset(0, bannerH.roundToInt()),
                dstSize = IntSize(size.width.roundToInt(), rest),
                colorFilter = saturate,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to foot.copy(alpha = 0f),
                    0.55f to foot.copy(alpha = 0.62f),
                    1f to foot.copy(alpha = 0.86f),
                    startY = bannerH,
                    endY = size.height,
                ),
                topLeft = Offset(0f, bannerH),
                size = Size(size.width, size.height - bannerH),
            )
        }
    }
}

/** The colour the last rows of [bitmap] average to, a little richer and dark enough for white type. */
private fun footColour(bitmap: Bitmap): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return Color.Black
    val rows = (h * 0.08f).toInt().coerceAtLeast(1)
    val pixels = IntArray(w * rows)
    bitmap.getPixels(pixels, 0, w, 0, h - rows, w, rows)
    var r = 0L
    var g = 0L
    var b = 0L
    for (pixel in pixels) {
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
    }
    val n = pixels.size.toFloat() * 255f
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (r / n * 255f).toInt().coerceIn(0, 255),
        (g / n * 255f).toInt().coerceIn(0, 255),
        (b / n * 255f).toInt().coerceIn(0, 255),
        hsv,
    )
    hsv[1] = (hsv[1] * 1.35f).coerceAtMost(0.85f)
    hsv[2] = hsv[2].coerceIn(0.16f, 0.55f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Seconds of slow drift, for ambient motion; stopped while [running] is false. */
@Composable
internal fun rememberEspressoAmbientSeconds(running: Boolean = true): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val isRunning by rememberUpdatedState(running)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            if (!isRunning) {
                snapshotFlow { isRunning }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                if (last != 0L) seconds.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    return seconds
}

/** How the one-line lyric is set: the type, and the ink for what is to come and what is sung. */
@Immutable
internal data class StripLook(
    val fontFamily: FontFamily? = null,
    val fontSize: TextUnit = 17.sp,
    val fontWeight: FontWeight = FontWeight.SemiBold,
    val dim: Color = Color.White.copy(alpha = 0.42f),
    val lit: Color = Color.White,
    /** Light behind the sung words, or none. */
    val glow: Color? = null,
    val chevron: Color = Color.White.copy(alpha = 0.5f),
)

internal val LocalStripLook = staticCompositionLocalOf { StripLook() }

/**
 * The line being sung, one line, the sung part lit: Espresso writes it across the label in script.
 * A tap opens the lyrics.
 */
@Composable
internal fun CurrentLyricStrip(trackKey: String, isPlaying: Boolean, onClick: () -> Unit) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsState(initial = null)
    val raw = lyricsEntity?.lyrics
    val lines = remember(raw) {
        if (raw == null || raw == LYRICS_NOT_FOUND) emptyList<LyricLine>() else toFlowLines(raw).filter { !it.isGap }
    }
    val look = LocalStripLook.current
    if (lines.isEmpty() || lines.none { it.timeMs > 0L }) return
    val position = rememberFlowPosition(playerConnection.player) { null }
    val clock = rememberLyricClock(trackKey, rememberLyricPlayhead(position), isPlaying)
    val index by remember(lines) { derivedStateOf { latestLyricRow(lines, clock.longValue) } }
    val line = lines.getOrNull(index) ?: return
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Text(
            text = line.text,
            color = look.dim,
            fontFamily = look.fontFamily,
            fontSize = look.fontSize,
            fontWeight = look.fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The sung part, the same text lit and wiped in from the left as the line goes.
        Text(
            text = line.text,
            color = look.lit,
            fontFamily = look.fontFamily,
            fontSize = look.fontSize,
            fontWeight = look.fontWeight,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer {
                val words = line.words
                val through = if (words.isEmpty()) 1f else {
                    val start = words.first().startMs
                    val end = words.last().endMs.coerceAtLeast(start + 1)
                    ((clock.longValue - start).toFloat() / (end - start)).coerceIn(0f, 1f)
                }
                // Wipe: a clip growing from the left edge.
                alpha = if (through <= 0f) 0f else 1f
                clipFraction(through)
            },
        )
    }
}

/** Clips the layer to the left [fraction] of its width. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.clipFraction(fraction: Float) {
    val f = fraction.coerceIn(0f, 1f)
    shape = object : androidx.compose.ui.graphics.Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: androidx.compose.ui.unit.LayoutDirection,
            density: androidx.compose.ui.unit.Density,
        ) = androidx.compose.ui.graphics.Outline.Rectangle(
            androidx.compose.ui.geometry.Rect(0f, 0f, size.width * f, size.height),
        )
    }
    clip = true
}

/**
 * The full-bleed cover's dissolve as an eased curve rather than a straight ramp: smootherstep
 * starts and ends flat, so the picture lets go gradually and arrives in the ground below without a
 * seam. [floor] is the alpha left at the foot.
 */
internal fun heroDissolveStops(floor: Float): Array<Pair<Float, Color>> =
    Array(9) { i ->
        val t = i / 8f
        val eased = t * t * t * (t * (t * 6f - 15f) + 10f)
        t to Color.Black.copy(alpha = 1f - (1f - floor) * eased)
    }
