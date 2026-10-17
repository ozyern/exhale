/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.ozyern.exhale.desktop.data.LocalSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Moving artwork: four slowly counter-rotating, domain-warped samplings of a 48px
 * thumbnail, averaged, pushed in saturation and vignetted.
 *
 * It used to be a GPU shader evaluated for every pixel of the window, every frame — and because the
 * page's glass records what's behind it, several times a frame. On a 2560×1600 panel that single
 * background was the most expensive thing Exhale drew. The field is nothing but soft gradients, so it
 * is now worked out 30 times a second at 240 pixels wide, off the UI thread, and stretched to the
 * window as one image: the same picture for a small fraction of the work.
 */
private const val FluidSide = 48
private const val FieldWidth = 240

/** The artwork shrunk to 48×48 and softened, as RGB floats for sampling. */
private class FluidTexture(val rgb: FloatArray)

/** Kept for callers from the shader days; the CPU field always works. */
val fluidBackgroundAvailable: Boolean get() = true

private val fluidHttp = OkHttpClient()
private val fluidCache = object : LinkedHashMap<String, FluidTexture>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FluidTexture>) = size > 16
}

private fun loadFluidTexture(url: String): FluidTexture {
    val bytes = fluidHttp.newCall(Request.Builder().url(url).build()).execute().use { it.body.bytes() }
    val source = Image.makeFromEncoded(bytes)
    val bitmap = Bitmap().apply { allocPixels(ImageInfo(FluidSide, FluidSide, ColorType.RGBA_8888, ColorAlphaType.PREMUL)) }
    Canvas(bitmap).apply { drawImageRect(source, Rect.makeWH(FluidSide.toFloat(), FluidSide.toFloat())) }.close()
    val pixels = bitmap.readPixels() ?: error("Unreadable artwork")
    val rgb = FloatArray(FluidSide * FluidSide * 3)
    for (i in 0 until FluidSide * FluidSide) {
        rgb[i * 3] = (pixels[i * 4].toInt() and 0xFF) / 255f
        rgb[i * 3 + 1] = (pixels[i * 4 + 1].toInt() and 0xFF) / 255f
        rgb[i * 3 + 2] = (pixels[i * 4 + 2].toInt() and 0xFF) / 255f
    }
    // No softening on top: the swirls keep the cover's own detail, as the shader's did.
    return FluidTexture(rgb)
}

/** Bilinear sample of [tex] at uv (0..1, clamped like the shader was), added into [acc] times [w]. */
private fun sample(tex: FluidTexture, u: Float, v: Float, w: Float, acc: FloatArray) {
    val cx = u.coerceIn(0.02f, 0.98f) * FluidSide - 0.5f
    val cy = v.coerceIn(0.02f, 0.98f) * FluidSide - 0.5f
    val x0 = cx.toInt().coerceIn(0, FluidSide - 1)
    val y0 = cy.toInt().coerceIn(0, FluidSide - 1)
    val x1 = (x0 + 1).coerceAtMost(FluidSide - 1)
    val y1 = (y0 + 1).coerceAtMost(FluidSide - 1)
    val fx = (cx - x0).coerceIn(0f, 1f)
    val fy = (cy - y0).coerceIn(0f, 1f)
    val rgb = tex.rgb
    for (c in 0..2) {
        val a = rgb[(y0 * FluidSide + x0) * 3 + c]
        val b = rgb[(y0 * FluidSide + x1) * 3 + c]
        val d = rgb[(y1 * FluidSide + x0) * 3 + c]
        val e = rgb[(y1 * FluidSide + x1) * 3 + c]
        val top = a + (b - a) * fx
        val bottom = d + (e - d) * fx
        acc[c] += (top + (bottom - top) * fy) * w
    }
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun renderField(current: FluidTexture, previous: FluidTexture, blend: Float, time: Float, width: Int, height: Int, aspect: Float, brightness: Float): ImageBitmap {
    val out = ByteArray(width * height * 4)
    val cosA = FloatArray(4)
    val sinA = FloatArray(4)
    val warpA = FloatArray(4)
    val warpB = FloatArray(4)
    val driftX = FloatArray(4)
    val driftY = FloatArray(4)
    for (i in 0..3) {
        val fi = i.toFloat()
        val dir = if (i == 1 || i == 3) -1f else 1f
        val angle = time * (0.045f + 0.02f * fi) * dir + fi * 1.9f
        cosA[i] = cos(angle)
        sinA[i] = sin(angle)
        warpA[i] = time * 0.17f + fi * 2.3f
        warpB[i] = time * 0.13f + fi * 1.1f
        driftX[i] = sin(time * 0.05f + fi) * 0.18f
        driftY[i] = cos(time * 0.04f + fi * 1.7f) * 0.18f
    }
    val now = FloatArray(3)
    val before = FloatArray(3)
    val mixing = blend < 0.999f
    var o = 0
    for (y in 0 until height) {
        val py = (y + 0.5f) / height - 0.5f
        for (x in 0 until width) {
            val px = ((x + 0.5f) / width - 0.5f) * aspect
            now.fill(0f)
            before.fill(0f)
            for (i in 0..3) {
                val scale = 1f / (1.35f + 0.45f * i)
                val qx = (cosA[i] * px - sinA[i] * py) * scale
                val qy = (sinA[i] * px + cosA[i] * py) * scale
                val u = qx + 0.10f * sin(warpA[i] + qy * 4f) + 0.5f + driftX[i]
                val v = qy + 0.10f * cos(warpB[i] + qx * 4f) + 0.5f + driftY[i]
                val w = if (i == 0) 1.6f else 1f
                sample(current, u, v, w, now)
                if (mixing) sample(previous, u, v, w, before)
            }
            var r = now[0] / 4.6f
            var g = now[1] / 4.6f
            var b = now[2] / 4.6f
            if (mixing) {
                r = before[0] / 4.6f + (r - before[0] / 4.6f) * blend
                g = before[1] / 4.6f + (g - before[1] / 4.6f) * blend
                b = before[2] / 4.6f + (b - before[2] / 4.6f) * blend
            }
            val luma = r * 0.299f + g * 0.587f + b * 0.114f
            r = (luma + (r - luma) * 1.45f).coerceAtLeast(0f)
            g = (luma + (g - luma) * 1.45f).coerceAtLeast(0f)
            b = (luma + (b - luma) * 1.45f).coerceAtLeast(0f)
            val vignette = 1f - smoothstep(0.2f, 1.3f, sqrt(px * px + py * py))
            val k = brightness * (0.62f + 0.38f * vignette)
            out[o] = (r * k * 255f).coerceIn(0f, 255f).toInt().toByte()
            out[o + 1] = (g * k * 255f).coerceIn(0f, 255f).toInt().toByte()
            out[o + 2] = (b * k * 255f).coerceIn(0f, 255f).toInt().toByte()
            out[o + 3] = 0xFF.toByte()
            o += 4
        }
    }
    val bitmap = Bitmap().apply {
        allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
        installPixels(out)
        setImmutable()
    }
    return Image.makeFromBitmap(bitmap).toComposeImageBitmap()
}

/**
 * The artwork at [url] as a slowly moving field of its own colours. A new cover cross-fades in over
 * 1.4 s rather than cutting. Only drawing is invalidated as it moves, never composition.
 */
@Composable
fun FluidArtwork(url: String?, modifier: Modifier = Modifier, brightness: Float = 0.9f, paused: Boolean = false) {
    val isPaused by rememberUpdatedState(paused)
    var current by remember { mutableStateOf<FluidTexture?>(null) }
    var previous by remember { mutableStateOf<FluidTexture?>(null) }
    val blend = remember { Animatable(1f) }

    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val texture = synchronized(fluidCache) { fluidCache[url] }
            ?: withContext(Dispatchers.IO) { runCatching { loadFluidTexture(url) }.getOrNull() }
                ?.also { synchronized(fluidCache) { fluidCache[url] = it } }
            ?: return@LaunchedEffect
        if (texture === current) return@LaunchedEffect
        previous = current ?: texture
        current = texture
        blend.snapTo(0f)
        blend.animateTo(1f, tween(1_400, easing = LinearOutSlowInEasing))
    }

    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    val aspect = remember { mutableFloatStateOf(16f / 10f) }
    val animate = !LocalSettings.current.reducedMotion
    // Behind another window the field keeps drifting, just at a fraction of the work.
    val focused by rememberUpdatedState(LocalWindowInfo.current.isWindowFocused)

    LaunchedEffect(animate, brightness) {
        val start = System.nanoTime()
        var renderedFor: FluidTexture? = null
        // How long the last field took to work out, so the next one can wait its turn.
        var spent = 0L
        while (true) {
            // Covered or hidden: don't work out frames nobody will see — except one for a new cover, so
            // it's already there the moment the field is shown again.
            if (isPaused && current === renderedFor) {
                delay(250L)
                continue
            }
            val texture = current
            if (texture != null) {
                val time = if (animate) (System.nanoTime() - start) / 1_000_000_000f else 0f
                val ratio = aspect.floatValue.coerceIn(0.4f, 4f)
                var width = FieldWidth
                var height = (FieldWidth / ratio).roundToInt().coerceIn(40, 400)
                // A very tall or very wide cover would otherwise make the field several times the work
                // of a square one; hold the pixel count to about what a square 240 costs.
                val budget = FieldWidth * FieldWidth
                if (width * height > budget) {
                    val scale = sqrt(budget.toFloat() / (width * height))
                    width = (width * scale).roundToInt().coerceAtLeast(120)
                    height = (height * scale).roundToInt().coerceAtLeast(60)
                }
                val mix = if (isPaused) 1f else blend.value
                val before = previous ?: texture
                val startedAt = System.nanoTime()
                frame = withContext(Dispatchers.Default) { renderField(texture, before, mix, time, width, height, ratio, brightness) }
                // However long a frame took, leave twice that idle before the next one. A cover that's
                // expensive to draw slows its own drift instead of stealing the app's frames.
                spent = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtMost(400L)
                renderedFor = texture
            }
            // Still (reduced motion) only needs redrawing for a cover change.
            delay(
                when {
                    !animate && !blend.isRunning -> 250L
                    focused -> maxOf(BackgroundTickMs, spent * 2)
                    else -> maxOf(UnfocusedTickMs, spent * 3)
                },
            )
        }
    }

    Box(
        modifier
            .onSizeChanged { if (it.height > 0) aspect.floatValue = it.width.toFloat() / it.height }
            .drawBehind {
                val image = frame ?: return@drawBehind
                // Bicubic, so stretching the field keeps its swirls crisp-edged rather than smeared.
                drawImage(image, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.High)
            },
    )
}
