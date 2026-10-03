/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * The colours a release page paints itself in, all derived from its artwork: the page tint, the
 * colour the sleeve's bottom edge blurs down to, a fill for the circles, an accent legible on the
 * tint, and the text and hairline colours that go with them.
 */
@Immutable
data class ArtworkPalette(
    val background: Color,
    val wash: Color,
    val elevated: Color,
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

@Composable
fun rememberReleasePalette(imageUrl: String?): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = ColorUtils.calculateLuminance(scheme.background.toArgb()) < 0.5
    val context = LocalContext.current
    var seed by remember(imageUrl) { mutableStateOf(imageUrl?.let(seedCache::get)) }
    val knownUpFront = remember(imageUrl) { seed != null }

    LaunchedEffect(imageUrl) {
        if (imageUrl == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(PALETTE_PX)
            .allowHardware(false)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
        val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) { runCatching { seedOf(bitmap) }.getOrNull() }
            ?: return@LaunchedEffect
        seedCache[imageUrl] = found
        seed = found
    }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
    )
    val spec: AnimationSpec<Color> = if (knownUpFront) snap() else tween(260)
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(target.onBackgroundVariant, spec, label = "tintOnVariant").value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

/** The palette of the page being drawn, for components that dress themselves in it when present. */
val LocalReleasePalette = androidx.compose.runtime.staticCompositionLocalOf<ArtworkPalette?> { null }

/**
 * A palette for a collection with no picture of its own — Liked, Downloaded, Top — from the two
 * colours of its gradient cover, read the same way a sleeve's would be.
 */
@Composable
fun rememberReleasePalette(colors: List<Color>): ArtworkPalette {
    val dark = ColorUtils.calculateLuminance(MaterialTheme.colorScheme.background.toArgb()) < 0.5
    return remember(colors, dark) {
        val first = colors.firstOrNull() ?: Color.Gray
        val last = colors.lastOrNull() ?: first
        Seed(dominant = first, vibrant = first, edge = last).toPalette(dark)
    }
}

/**
 * The page under a release header: the wash at full strength where the artwork ends, settling into
 * the page tint further down, with two soft lobes of the sleeve's colours so it is never one flat fill.
 */
@Composable
fun ArtworkWash(palette: ArtworkPalette, modifier: Modifier = Modifier, washFraction: Float = 0.62f) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                androidx.compose.ui.util.lerp(washFraction, 1f, 0.35f) to lerp(palette.wash, palette.background, 0.12f),
                androidx.compose.ui.util.lerp(washFraction, 1f, 0.70f) to lerp(palette.wash, palette.background, 0.55f),
                1f to palette.background,
            ),
        )
        blob(palette.accent.copy(alpha = 0.13f), Offset(0.12f, washFraction + 0.08f), 0.80f)
        blob(palette.elevated.copy(alpha = 0.30f), Offset(0.96f, washFraction + 0.30f), 0.95f)
    }
}

private fun DrawScope.blob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

private data class Seed(val dominant: Color, val vibrant: Color, val edge: Color)

private val seedCache = object : LinkedHashMap<String, Seed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, Seed>) = size > 128
}

private const val PALETTE_PX = 128
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f

private fun seedOf(bitmap: Bitmap): Seed? {
    fun swatches(builder: Palette.Builder) = builder.maximumColorCount(24).generate().swatches
    // Unfiltered for "what is this page mostly made of": the default filter drops near-black and
    // near-white, which turns a dark photograph into whatever small warm detail survived it.
    val all = swatches(Palette.from(bitmap).clearFilters())
    if (all.isEmpty()) return null
    val accentCandidates = swatches(Palette.from(bitmap)).ifEmpty { all }
    val dominant = all.maxBy { it.population }
    val vibrant = accentCandidates.maxBy { swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    }
    return Seed(Color(dominant.rgb), Color(vibrant.rgb), bitmap.bottomEdgeColor())
}

/** The flat mean of the artwork's bottom band — what a wide blur of that edge leaves behind. */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * 0.18f).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)
    var r = 0L
    var g = 0L
    var b = 0L
    pixels.forEach { p ->
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1)
    return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
}

private fun Seed.toPalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        background = dominant.withHsl({ sat(it, 0.20f, 0.62f) }, { 0.13f }),
        wash = edge.withHsl({ sat(it, 0.18f, 0.58f) }, { it.coerceIn(0.14f, 0.24f) }),
        elevated = dominant.withHsl({ sat(it, 0.20f, 0.62f) }, { 0.22f }),
        accent = vibrant.withHsl({ sat(it, 0.55f, 1f) }, { it.coerceIn(0.62f, 0.78f) }),
        onBackground = Color.White,
        onBackgroundVariant = Color.White.copy(alpha = 0.80f),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl({ sat(it, 0.14f, 0.50f) }, { 0.91f }),
        wash = edge.withHsl({ sat(it, 0.12f, 0.46f) }, { it.coerceIn(0.78f, 0.90f) }),
        elevated = dominant.withHsl({ sat(it, 0.14f, 0.50f) }, { 0.83f }),
        accent = vibrant.withHsl({ sat(it, 0.55f, 1f) }, { it.coerceIn(0.30f, 0.44f) }),
        onBackground = Color.Black,
        onBackgroundVariant = Color.Black.copy(alpha = 0.70f),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

/** Keeps a grey sleeve grey: lifting hue-less grey to a saturation floor invents a maroon. */
private fun sat(source: Float, minimum: Float, maximum: Float): Float {
    val s = source.coerceIn(0f, 1f)
    return if (s < CHROMATIC_SATURATION_THRESHOLD) s else s.coerceIn(minimum, maximum)
}

private fun Color.withHsl(saturation: (Float) -> Float, lightness: (Float) -> Float): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}
