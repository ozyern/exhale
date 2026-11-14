/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.constants.DynamicThemeKey
import com.ozyern.exhale.constants.PureBlackHeroBackgroundKey
import com.ozyern.exhale.constants.SelectedThemeColorKey
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.DefaultThemeColor
import com.ozyern.exhale.ui.theme.HeroPalette
import com.ozyern.exhale.ui.theme.colorengine.textHierarchyOn
import com.ozyern.exhale.ui.theme.extractHeroPalette
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clamps an extracted artwork color into a pleasant deep background tint:
 * caps lightness (pale/skin/whitish colors become rich, legible) and floors
 * saturation (avoids washed-out grey). Keeps the hue.
 */
fun Color.asDeepTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.28f)   // floor saturation
    hsl[2] = hsl[2].coerceIn(0.14f, 0.34f) // cap lightness → deep, not pale
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Same clamp family as [asDeepTint], tuned for a mesh-wash blob instead of a
 *  flat page background: lighter and more saturated so it reads as a glow. */
fun Color.asVividAccent(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.45f)
    hsl[2] = hsl[2].coerceIn(0.45f, 0.68f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Creates a subtle glass tint from the ambient color and current surface brightness. */
fun Color.asGlassSurfaceTint(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.12f, 0.22f) // low saturation: greyish with ambient color whisper
    hsl[2] = if (isDark) {
        hsl[2].coerceIn(0.20f, 0.28f) // deep glass slate
    } else {
        hsl[2].coerceIn(0.88f, 0.94f) // frosted light glass
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Specular highlight / rim light ("pluck") tint: the ambient hue, muted enough
 *  to read as lit glass rather than neon — saturated enough to see the color,
 *  never colorful. Shared by every glass surface on the screen (see
 *  [Modifier.liquidGlass]'s default rim), so all rims match. */
fun Color.asRimPop(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.40f, 0.55f) // visible hue, not neon
    hsl[2] = if (isDark) hsl[2].coerceIn(0.62f, 0.74f) else hsl[2].coerceIn(0.75f, 0.90f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Muted icon tint for deselected nav items and floating chrome (back, share,
 *  menu) on ambient screens: same hue as the mesh background, just enough
 *  saturation to see the color, drawn under additiveChrome (BlendMode.Plus)
 *  for its glow. Replaces [asIconPop]'s vivid floor (0.80) which turned
 *  Library blue. */
fun Color.asSoftIconTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.32f, 0.45f) // hue visible, never colorful
    hsl[2] = hsl[2].coerceIn(0.66f, 0.76f) // bright enough to glow through Plus
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Glass surface fill for ambient screens: the neutral frost grey carried
 *  20% toward the mesh hue, so the surface feels tinted without going
 *  colorful. Painted at ~0.36 opacity by [Modifier.liquidGlass]. */
fun Color.asAmbientSurfaceTint(isDark: Boolean = true): Color {
    val frosted = asGlassSurfaceTint(isDark)
    return lerpColor(frosted, this, 0.20f)
}

/** Selection-pill wash: lighter than the ambient surface with a hint more of
 *  the mesh hue. Alpha is picked at the call site by contrast (0.70 on light
 *  ambient, 0.80 on dark). */
fun Color.asPuckTint(isDark: Boolean = true): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceIn(0.15f, 0.25f) // a hint of the hue
    hsl[2] = if (isDark) {
        hsl[2].coerceIn(0.32f, 0.42f) // lighter than the 0.20..0.28 surface
    } else {
        hsl[2].coerceIn(0.80f, 0.88f)
    }
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Bright and saturated icon tint for additive chrome (BlendMode.Plus) over glass:
 *  vibrant hue with high saturation and sufficient brightness so it glows vividly. */
fun Color.asIconPop(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.80f) // bright and saturated
    hsl[2] = hsl[2].coerceIn(0.58f, 0.76f) // light and vivid for additive chroma glow
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Same clamp family as [asDeepTint], a shade lighter — the wash a real blur
 *  of the artwork would leave behind, not the page's own deep tint. */
fun Color.asWashTint(): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = hsl[1].coerceAtLeast(0.22f)
    hsl[2] = hsl[2].coerceIn(0.20f, 0.42f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/**
 * The artwork palette for [url], from cache when it is there and by extraction when it is not.
 *
 * Cheap on a repeat call even when the cache misses: the 100x100 decode Coil does here
 * lands in its own memory cache, so a second caller pays a lookup rather than a fetch.
 */
suspend fun heroPaletteOf(context: Context, url: String): HeroPalette? {
    heroPaletteCache.get(url)?.let { return it }
    return withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(200, 200)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).image?.toBitmap()
                ?.extractHeroPalette(cacheKey = url)
                ?.let { raw ->
                    val background = raw.background.asDeepTint()
                    val accent = raw.accent.asVividAccent()
                    // Calculate text contrast from the final colors that the screen paints.
                    val text = textHierarchyOn(background = background, tint = accent)
                    HeroPalette(
                        background = background,
                        accent = accent,
                        wash = raw.wash.asWashTint(),
                        primaryText = text[0],
                        secondaryText = text[1],
                    )
                }
                // Do not cache fallback palettes; a later visit can retry extraction.
                ?.also { heroPaletteCache.put(url, it) }
        } catch (_: Exception) {
            null
        }
    }
}

/** The flat background half of [heroPaletteOf] — every existing caller only wants this. */
suspend fun heroTintOf(context: Context, url: String): Color? = heroPaletteOf(context, url)?.background

/**
 * Starts extracting [url]'s palette now, off any composition.
 *
 * Called as navigation into a hero screen begins, so the colours are already in the cache by
 * the time that screen composes -- otherwise the extraction starts on arrival and the
 * background visibly washes in a beat after the artwork.
 */
fun prewarmHeroTint(context: Context, url: String?) {
    if (url.isNullOrEmpty() || heroPaletteCache.get(url) != null) return
    val appContext = context.applicationContext
    heroTintScope.launch { heroPaletteOf(appContext, url) }
}

internal val heroTintScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The full [HeroPalette] a screen builds its background from — flat tint,
 * mesh-wash accent and edge wash together, so all three come from the one
 * cached extraction instead of three separate ones drifting out of sync.
 */
@Composable
fun rememberHeroPalette(url: String?): HeroPalette {
    val context = LocalContext.current

    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val fallbackTint = com.ozyern.exhale.ui.theme.GlassTintTokens.baseSurfaceColor(isDark)
    val fallbackPalette = remember(fallbackTint) {
        HeroPalette(
            background = fallbackTint,
            accent = fallbackTint,
            wash = fallbackTint,
            primaryText = if (isDark) Color.White else Color.Black,
            secondaryText = if (isDark) Color(0xFFEBEBF5) else Color(0xFF3C3C43),
        )
    }

    // Show a cached palette immediately; a black fallback flashes during navigation.
    var palette by remember(url) { mutableStateOf(url?.let { heroPaletteCache.get(it) } ?: fallbackPalette) }

    LaunchedEffect(url) {
        // Always await extraction; a prewarm may still be in flight.
        palette = (url?.let { heroPaletteOf(context, it) }) ?: fallbackPalette
    }

    // Apply the setting here so every screen using this palette gets the same override.
    val (pureBlack) = rememberPreference(PureBlackHeroBackgroundKey, false)
    return if (pureBlack) {
        HeroPalette(background = Color.Black, accent = Color.Black, wash = Color.Black)
    } else {
        palette
    }
}

@Composable
fun rememberHeroTint(url: String?): Color = rememberHeroPalette(url).background

/**
 * The screen surface below a hero image: an eased wash down to [screenBackground]
 * with two soft colour blobs breaking up what would otherwise be a flat vertical
 * ramp — draws the same idea a blurred-artwork backdrop reaches for, but from the
 * extracted [palette] alone, so it costs the same regardless of API level and
 * never re-shows a picture that is already on screen above it.
 *
 * Sized entirely by [modifier]; callers place it as the first child behind their
 * hero content, same as any other background layer.
 */
@Composable
fun HeroMeshWash(
    palette: HeroPalette,
    screenBackground: Color,
    modifier: Modifier = Modifier,
    /** Fraction of the surface's height the wash is still at full strength. */
    washFraction: Float = 0.5f,
) {
    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                0f to palette.wash,
                washFraction to palette.wash,
                washFraction + (1f - washFraction) * 0.45f to lerpColor(palette.wash, screenBackground, 0.20f),
                washFraction + (1f - washFraction) * 0.80f to lerpColor(palette.wash, screenBackground, 0.65f),
                1f to screenBackground,
            ),
        )
        heroWashBlob(palette.accent.copy(alpha = 0.16f), Offset(0.16f, washFraction + 0.10f), 0.80f)
        heroWashBlob(palette.wash.copy(alpha = 0.26f), Offset(0.90f, washFraction + 0.32f), 0.95f)
    }
}

/** One soft radial blob: full colour at [at], gone by [radiusFraction] of the width. */
internal fun DrawScope.heroWashBlob(color: Color, at: Offset, radiusFraction: Float) {
    val center = Offset(at.x * size.width, at.y * size.height)
    val radius = size.width * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The screen background tint: always the screen's own [contentTint]. The accent colour chosen in
 * Theme settings recolours accents (buttons, selection, links), not what screens paint behind
 * their content, so it never reaches the page background.
 */
@Composable
fun rememberAppBackgroundTint(contentTint: Color): Color = contentTint
