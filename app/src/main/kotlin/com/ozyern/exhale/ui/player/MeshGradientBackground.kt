/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Only reached when the artwork yields nothing at all — a null url, a failed decode,
 * or a sleeve whose every swatch is filtered out even with the filters cleared.
 */
private val FallbackColors = listOf(
    Color(0xFF141416),
    Color(0xFF1E1E22),
    Color(0xFF16161A),
    Color(0xFF101012),
)

private val meshPaletteCache = android.util.LruCache<String, List<Color>>(64)
private val meshPrewarmScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

/**
 * Starts extracting [url]'s mesh palette now, off composition.
 * Called alongside prewarmHeroTint as navigation begins, so bg paints frame one.
 */
fun prewarmMeshPalette(context: android.content.Context, url: String?) {
    if (url.isNullOrEmpty() || meshPaletteCache.get(url) != null) return
    val appContext = context.applicationContext
    meshPrewarmScope.launch {
        try {
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .size(128)
                .allowHardware(false)
                .build()
            val result = SingletonImageLoader.get(appContext).execute(request)
            val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@launch
            val extracted = withContext(Dispatchers.Default) { paletteOf(bitmap) }
            meshPaletteCache.put(url, extracted)
        } catch (_: Exception) {
            // Palette prewarming is optional; the background can load it later.
        }
    }
}

/** The four mesh colours, wrapped so the backdrop can skip recomposition. */
@Immutable
data class MeshPalette(val colors: List<Color>)

/**
 * Builds a blurred mesh from four colors sampled from album art and crossfades on track changes.
 * Blobs drift during opening and track changes only; continuous drift would blur a full-screen
 * layer every frame. [com.ozyern.exhale.constants.PlayerBackgroundStyle.LIVE_MESH] rotates artwork,
 * while this backdrop uses its colors.
 */
@Composable
fun MeshGradientBackground(
    palette: MeshPalette,
    modifier: Modifier = Modifier,
    trackKey: Any? = null,
    driftMillis: Int = 8_000,
    /** Palette crossfade length; the player passes the ambient (DJ-synced) duration. */
    transitionMillis: Int = 1_400,
) {
    // The dynamic-blur preference also controls motion visible only through this blur.
    val (reduceMotion) = rememberPreference(ReduceDynamicBlurKey, false)

    val tuned = remember(palette.colors) {
        (palette.colors.ifEmpty { FallbackColors } + FallbackColors)
            .take(4)
            .map { it.tuned() }
    }

    // Read animation state in Canvas to redraw the layer without recomposing this tree.
    val colorSpec: AnimationSpec<Color> = remember(reduceMotion, transitionMillis) {
        if (reduceMotion) snap() else tween(transitionMillis, easing = SeamlessEasing)
    }
    val animatedColors = tuned.mapIndexed { index, color ->
        animateColorAsState(color, colorSpec, label = "meshColor$index")
    }
    val baseColor = animateColorAsState(
        tuned.first().dimmed(),
        colorSpec,
        label = "meshBase",
    )

    // Read in the draw lambda, not here: an Animatable read during draw invalidates the
    // draw phase only, leaving composition out of the loop.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(trackKey, reduceMotion) {
        if (reduceMotion) {
            phase.snapTo(0f)
        } else {
            phase.animateTo(
                targetValue = phase.value + DriftRadians,
                animationSpec = tween(driftMillis, easing = FastOutSlowInEasing),
            )
        }
    }

    // Scaled up so the blur's clamped edges never come into view, then the whole layer
    // is blurred (RenderEffect, API 31+; a no-op below, where the radial falloff already
    // reads soft enough on its own).
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.3f
                scaleY = 1.3f
            }
            .blur(64.dp),
    ) {
        val drift = phase.value
        drawRect(baseColor.value)

        animatedColors.forEachIndexed { index, colorState ->
            val color = colorState.value
            val anchor = BlobAnchors[index]
            val speed = BlobSpeeds[index]
            val center = Offset(
                x = (anchor.x + 0.16f * cos(drift * speed + index * 1.7f)) * size.width,
                y = (anchor.y + 0.16f * sin(drift * speed * 0.9f + index * 2.3f)) * size.height,
            )
            val radius = size.maxDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }

        // Gentle scrim so white text stays legible over bright art.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.38f),
                ),
            ),
        )
    }
}

/**
 * Loads the artwork with Coil (software bitmap, thumbnail-sized) and pulls a 4-colour
 * palette out of it. Recomputes when [imageUrl] changes.
 *
 * A track's motion artwork is often lit nothing like its still sleeve — different
 * shot, different grade — so [canvasFrame], a frame captured off the playing clip once
 * one exists, is quantised the same way and takes over from there, crossfading in
 * exactly like a track skip.
 */
@Composable
fun rememberArtworkMeshPalette(imageUrl: String?, canvasFrame: Bitmap? = null): MeshPalette {
    val context = LocalContext.current
    val currentCanvasFrame by androidx.compose.runtime.rememberUpdatedState(canvasFrame)
    val cached = remember(imageUrl) { imageUrl?.let(meshPaletteCache::get) }
    var palette by remember(imageUrl) { 
        mutableStateOf(MeshPalette(cached ?: FallbackColors)) 
    }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        if (cached != null) {
            palette = MeshPalette(cached)
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(128) // Palette quality is fine at thumbnail size, and it is fast.
            .allowHardware(false) // Palette needs pixel access.
            .build()
        val result = SingletonImageLoader.get(context).execute(request)
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) { paletteOf(bitmap) }
        meshPaletteCache.put(imageUrl, extracted)
        // A video palette that landed first wins: the still's slower decode must not
        // overwrite the colours of the clip actually on screen.
        if (currentCanvasFrame == null) palette = MeshPalette(extracted)
    }

    LaunchedEffect(canvasFrame) {
        val frame = canvasFrame ?: return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) { paletteOf(frame) }
        palette = MeshPalette(extracted)
    }
    return palette
}

/**
 * How far the blobs travel in one settle. A shade under half a turn: far enough that
 * the backdrop visibly reacts to a track change, short of a full orbit that would land
 * them back where they started.
 */
private const val DriftRadians = (PI * 0.45f).toFloat()

private val BlobAnchors = listOf(
    Offset(0.20f, 0.25f),
    Offset(0.80f, 0.20f),
    Offset(0.75f, 0.80f),
    Offset(0.25f, 0.75f),
)

private val BlobSpeeds = listOf(1f, -0.7f, 0.85f, -1.15f)

/**
 * Four mesh colours drawn from the artwork.
 *
 * Palette's named swatches — vibrant, muted and friends — are a convenience over the
 * full set, and on a dark or desaturated sleeve every vibrant slot comes back null.
 * Topping the shortfall up from [FallbackColors] is what leaves those covers sitting
 * under a stock purple that has nothing to do with the record. So the whole swatch
 * list is read instead, ordered by population, and any shortfall is derived from the
 * art's own colours rather than borrowed.
 */
private fun paletteOf(bitmap: Bitmap): List<Color> {
    fun swatchesOf(builder: Palette.Builder): List<Color> =
        builder.maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { Color(it.rgb) }

    val found = swatchesOf(Palette.from(bitmap)).ifEmpty {
        // The default filter discards near-black and near-white, which on a monochrome
        // sleeve can be everything there is.
        swatchesOf(Palette.from(bitmap).clearFilters())
    }

    val distinct = found.distinctEnough()
    return when {
        distinct.isEmpty() -> FallbackColors
        distinct.size >= 4 -> distinct.take(4)
        else -> distinct.expandedToFour()
    }
}

/** Drop near-duplicates, so the four blobs don't collapse into one wash. */
private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

/** Fill the empty slots off the art itself, fanning hue and lightness out. */
private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

/** Boost saturation and clamp lightness so any artwork yields a rich, non-muddy mesh. */
internal fun Color.tuned(): Color {
    val hsl = hsl()
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/** The near-black the blobs are laid over, kept in the artwork's own hue. */
internal fun Color.dimmed(): Color {
    val hsl = hsl()
    hsl[2] = 0.12f
    return Color(ColorUtils.HSLToColor(hsl))
}
