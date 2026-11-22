/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The mesh and the blurred cover follow the reference design's ArtworkMeshBackdrop
 *.
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.ColorUtils
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The cover's own colours, averaged into a coarse grid, flipped so its first row is the cover's
 * bottom edge, and turned sideways so nothing in it lines up with the picture above. Hung from
 * where the hero cover stops, the screen carries on in the colours the cover ended in, roughly in
 * the cover's own proportions, without reading as a reflection of it.
 */
@Immutable
class ArtworkMesh internal constructor(internal val image: ImageBitmap)

/** The mesh for [imageUrl], or the last one shown until it has been read. */
@Composable
fun rememberArtworkMesh(imageUrl: String?): ArtworkMesh? {
    val context = LocalContext.current
    val held = remember { mutableStateOf<ArtworkMesh?>(null) }
    var mesh by remember(imageUrl) { mutableStateOf(imageUrl?.let { meshCache.get(it) } ?: held.value) }
    LaunchedEffect(mesh) { held.value = mesh }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null || meshCache.get(imageUrl) != null) return@LaunchedEffect
        repeat(MESH_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(MESH_RETRY_DELAY_MS)
            val bitmap = decodeSmall(context, imageUrl, MESH_PX) ?: return@repeat
            val found = withContext(Dispatchers.Default) { meshOf(bitmap, imageUrl.hashCode()) }
            if (found != null) {
                meshCache.put(imageUrl, found)
                mesh = found
            }
            return@LaunchedEffect
        }
    }
    return mesh
}

/**
 * The player's ground under the hero: [mesh] drawn from [seam] down, its first row held above it,
 * so the colour right under the cover is the colour the cover ends on. One small texture stretched
 * by the sampler, no render effect; a new cover fades over the old one.
 */
@Composable
fun ArtworkMeshBackdrop(
    mesh: ArtworkMesh?,
    modifier: Modifier = Modifier,
    seam: DrawScope.() -> Float = { 0f },
) {
    var shown by remember { mutableStateOf(mesh) }
    var incoming by remember { mutableStateOf<ArtworkMesh?>(null) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(mesh) {
        val next = mesh ?: return@LaunchedEffect
        incoming?.let { shown = it }
        incoming = null
        val current = shown
        if (next === current) return@LaunchedEffect
        if (current == null) {
            shown = next
            return@LaunchedEffect
        }
        incoming = next
        fade.snapTo(0f)
        fade.animateTo(1f, tween(MESH_FADE_MS, easing = FastOutSlowInEasing))
        shown = next
        incoming = null
    }
    Canvas(modifier.fillMaxSize()) {
        // Nothing until a cover has been read: whatever is beneath stays the ground until then.
        if (shown == null) return@Canvas
        val seamY = seam().coerceIn(0f, size.height)
        shown?.let { drawMesh(it, seamY, 1f) }
        incoming?.let { drawMesh(it, seamY, fade.value) }
        // Enough to keep white text off a bright cover, and no more.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.06f), Color.Black.copy(alpha = 0.30f))))
    }
}

private fun DrawScope.drawMesh(mesh: ArtworkMesh, seamY: Float, alpha: Float) {
    if (alpha <= 0.001f) return
    val image = mesh.image
    val width = size.width.roundToInt()
    if (seamY > 0.5f) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, 1),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(width, seamY.roundToInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(0, seamY.roundToInt()),
        dstSize = IntSize(width, (size.height - seamY).roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
}

/**
 * The whole cover, blurred once off the main thread at thumbnail size and cached: the ground of the
 * lyrics and the queue. Drawing it is one bilinear image sample, so nothing is re-blurred while the
 * player opens, closes or changes page.
 */
@Composable
fun rememberFullArtworkBlur(imageUrl: String?): ImageBitmap? {
    val context = LocalContext.current
    var image by remember { mutableStateOf(imageUrl?.let { fullBlurCache.get(it) }) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        fullBlurCache.get(imageUrl)?.let {
            image = it
            return@LaunchedEffect
        }
        val bitmap = decodeSmall(context, imageUrl, FULL_BLUR_SOURCE_PX) ?: return@LaunchedEffect
        val blurred = withContext(Dispatchers.Default) { boxBlurred(bitmap, FULL_BLUR_PASSES) }
        fullBlurCache.put(imageUrl, blurred)
        image = blurred
    }
    return image
}

/** [image] cropped over the whole surface, under a scrim that deepens toward the controls. */
fun DrawScope.drawFullArtworkBlur(image: ImageBitmap, alpha: Float) {
    if (alpha <= 0.001f || size.width <= 0f || size.height <= 0f) return
    val scale = maxOf(size.width / image.width, size.height / image.height)
    val srcW = (size.width / scale).roundToInt().coerceIn(1, image.width)
    val srcH = (size.height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image = image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.Low,
    )
    drawRect(FullArtworkScrim, topLeft = Offset.Zero, size = size, alpha = alpha)
}

private val FullArtworkScrim = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.34f),
    0.55f to Color.Black.copy(alpha = 0.48f),
    1f to Color.Black.copy(alpha = 0.64f),
)

private suspend fun decodeSmall(context: android.content.Context, url: String, px: Int): android.graphics.Bitmap? =
    withContext(Dispatchers.IO) {
        val request = coil3.request.ImageRequest.Builder(context)
            .data(url.resize(544, 544))
            .size(px, px)
            .allowHardware(false)
            .build()
        runCatching {
            (coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult)?.image?.toBitmap()
        }.getOrNull()
    }

private val meshCache = android.util.LruCache<String, ArtworkMesh>(64)
private val fullBlurCache = android.util.LruCache<String, ImageBitmap>(8)

private const val MESH_PX = 120
private const val MESH_ATTEMPTS = 4
private const val MESH_RETRY_DELAY_MS = 1_500L
private const val MESH_GRID = 6
private const val MESH_TEX = 32
private const val MESH_BLUR_RADIUS = 1
private const val MESH_BLUR_PASSES = 3
private const val MESH_FADE_MS = 900
private const val FULL_BLUR_SOURCE_PX = 128
private const val FULL_BLUR_PASSES = 3
private const val MESH_VIBRANCE = 1.12f
private const val MESH_FLOOR = 0.045f

private fun meshOf(source: android.graphics.Bitmap, seed: Int): ArtworkMesh? {
    val width = source.width
    val height = source.height
    if (width < 1 || height < 1) return null
    val cols = MESH_GRID.coerceAtMost(width)
    val rows = MESH_GRID.coerceAtMost(height)
    val cells = rows * cols
    val red = LongArray(cells)
    val green = LongArray(cells)
    val blue = LongArray(cells)
    val count = IntArray(cells)
    val line = IntArray(width)
    for (y in 0 until height) {
        source.getPixels(line, 0, width, 0, y, width, 1)
        // Flipped as it is read: row 0 of the grid is the cover's bottom edge.
        val rowBase = ((height - 1 - y) * rows / height) * cols
        for (x in 0 until width) {
            val cell = rowBase + x * cols / width
            val pixel = line[x]
            red[cell] += (pixel shr 16) and 0xFF
            green[cell] += (pixel shr 8) and 0xFF
            blue[cell] += pixel and 0xFF
            count[cell]++
        }
    }
    val grid = IntArray(cells) { cell ->
        val n = count[cell].coerceAtLeast(1)
        argb((red[cell] / n).toInt(), (green[cell] / n).toInt(), (blue[cell] / n).toInt()).lifted()
    }
    val texels = grid.rotatedBelowSeam(cols, rows, seed).resampled(cols, rows, MESH_TEX)
    val scratch = IntArray(texels.size)
    repeat(MESH_BLUR_PASSES) {
        boxBlurPass(texels, scratch, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = true)
        boxBlurPass(scratch, texels, MESH_TEX, MESH_TEX, MESH_BLUR_RADIUS, horizontal = false)
    }
    return ArtworkMesh(bitmapOf(texels, MESH_TEX, MESH_TEX))
}

/**
 * Every row below the seam row carried round by one seeded amount, and mirrored half the time: each
 * cell keeps its neighbours, so the gradients survive, but no column sits under the one it came from.
 */
private fun IntArray.rotatedBelowSeam(cols: Int, rows: Int, seed: Int): IntArray {
    if (rows <= 1) return this
    val random = Random(seed)
    val mirror = random.nextBoolean()
    val shift = random.nextInt(cols)
    val out = copyOf()
    for (row in 1 until rows) {
        val base = row * cols
        for (x in 0 until cols) {
            val src = if (mirror) cols - 1 - x else x
            out[base + x] = this[base + (src + shift) % cols]
        }
    }
    return out
}

private fun IntArray.resampled(cols: Int, rows: Int, size: Int): IntArray {
    val out = IntArray(size * size)
    for (ty in 0 until size) {
        val fy = (ty + 0.5f) / size * rows - 0.5f
        val y0 = floor(fy).toInt().coerceIn(0, rows - 1)
        val y1 = (y0 + 1).coerceAtMost(rows - 1)
        val wy = smoothstep(fy - y0)
        for (tx in 0 until size) {
            val fx = (tx + 0.5f) / size * cols - 0.5f
            val x0 = floor(fx).toInt().coerceIn(0, cols - 1)
            val x1 = (x0 + 1).coerceAtMost(cols - 1)
            val wx = smoothstep(fx - x0)
            val top = lerpArgb(this[y0 * cols + x0], this[y0 * cols + x1], wx)
            val bottom = lerpArgb(this[y1 * cols + x0], this[y1 * cols + x1], wx)
            out[ty * size + tx] = lerpArgb(top, bottom, wy)
        }
    }
    return out
}

private fun boxBlurred(bitmap: android.graphics.Bitmap, passes: Int): ImageBitmap {
    val width = bitmap.width
    val height = bitmap.height
    if (width < 2 || height < 2) return bitmap.asImageBitmap()
    val source = IntArray(width * height)
    bitmap.getPixels(source, 0, width, 0, 0, width, height)
    val target = IntArray(source.size)
    val radius = (minOf(width, height) / 12).coerceAtLeast(2)
    repeat(passes) {
        boxBlurPass(source, target, width, height, radius, horizontal = true)
        boxBlurPass(target, source, width, height, radius, horizontal = false)
    }
    return bitmapOf(source, width, height)
}

private fun boxBlurPass(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val major = if (horizontal) width else height
    val minor = if (horizontal) height else width
    val window = radius * 2 + 1
    for (fixed in 0 until minor) {
        fun pixel(at: Int): Int {
            val position = at.coerceIn(0, major - 1)
            return if (horizontal) source[fixed * width + position] else source[position * width + fixed]
        }
        var red = 0
        var green = 0
        var blue = 0
        for (offset in -radius..radius) {
            val color = pixel(offset)
            red += color shr 16 and 0xFF
            green += color shr 8 and 0xFF
            blue += color and 0xFF
        }
        for (moving in 0 until major) {
            val index = if (horizontal) fixed * width + moving else moving * width + fixed
            target[index] = argb(
                (red + window / 2) / window,
                (green + window / 2) / window,
                (blue + window / 2) / window,
            )
            val leaving = pixel(moving - radius)
            val entering = pixel(moving + radius + 1)
            red += (entering shr 16 and 0xFF) - (leaving shr 16 and 0xFF)
            green += (entering shr 8 and 0xFF) - (leaving shr 8 and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}

private fun bitmapOf(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpArgb(from: Int, to: Int, t: Float): Int {
    if (t <= 0f) return from
    if (t >= 1f) return to
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + ((b - a) * t)).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

private fun argb(red: Int, green: Int, blue: Int): Int =
    (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

/** Saturation put back where averaging greyed it, and lightness floored off pure black. */
private fun Int.lifted(): Int {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(this, it) }
    hsl[1] = (hsl[1] * MESH_VIBRANCE).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceAtLeast(MESH_FLOOR)
    return ColorUtils.HSLToColor(hsl)
}
