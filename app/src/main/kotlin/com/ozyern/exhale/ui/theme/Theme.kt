/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.darkIosColors
import com.ozyern.exhale.ui.ios.foundation.iosColorScheme
import com.ozyern.exhale.ui.ios.foundation.iosMaterialTypography
import com.ozyern.exhale.ui.ios.foundation.lightIosColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.palette.graphics.Palette
import com.ozyern.exhale.constants.AppFont
import com.ozyern.exhale.constants.AppTextColorKey
import com.ozyern.exhale.constants.SelectedFontKey
import com.ozyern.exhale.utils.rememberPreference
import com.materialkolor.score.Score
import com.ozyern.exhale.ui.theme.colorengine.ColorEngine

val DefaultThemeColor = AppleTokens.AccentRed

/**
 * The user's chosen accent color, provided app-wide. ONLY the nav bar uses this
 * as an accent; the rest of the UI is contrast-based (white/black by luminance).
 * Kept separate from [ColorScheme.primary], which is neutralized to white in the
 * dark Apple theme so generic M3 controls don't render red.
 */
val LocalAccentColor = androidx.compose.runtime.compositionLocalOf { DefaultThemeColor }

/**
 * The accent tuned for readable body text: same hue, lightened on dark
 * backgrounds and darkened on light ones, but never pushed to near-white or
 * near-black — so it reads as "coloured text," legible on either theme.
 * Consume via [LocalAccentTextColor].
 */
fun accentTextColor(accent: Color, dark: Boolean): Color {
    val blended = if (dark) {
        androidx.compose.ui.graphics.lerp(accent, Color.White, 0.35f)
    } else {
        androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.45f)
    }
    // A very dark or very pale accent can survive the blend and still be
    // unreadable against the surface, so clamp lightness rather than trust it.
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(blended.toArgb(), hsl)
    hsl[2] = if (dark) hsl[2].coerceAtLeast(0.72f) else hsl[2].coerceAtMost(0.32f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Accent-contrast text color, provided app-wide from the current accent + theme. */
val LocalAccentTextColor = androidx.compose.runtime.compositionLocalOf { DefaultThemeColor }

@Composable
fun ExhaleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    // Keep iOS-style neutrals fixed; the user's theme color controls the accent only.
    val iosColors = remember(darkTheme, themeColor) {
        if (darkTheme) darkIosColors(themeColor) else lightIosColors(themeColor)
    }
    val baseColorScheme = remember(iosColors) { iosColorScheme(iosColors) }

    val (appTextColorInt) = rememberPreference(AppTextColorKey, defaultValue = 0)

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, themeColor, appTextColorInt) {
        val withBlack = if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
        // Keep text roles neutral; the theme accent is for interactive elements. Hero screens
        // derive their tint from artwork, and the explicit text-color setting still wins.
        if (appTextColorInt != 0) withBlack.flatText(Color(appTextColorInt)) else withBlack
    }

    val (selectedFont) = rememberPreference(SelectedFontKey, defaultValue = AppFont.PLUS_JAKARTA_SANS.value)
    val customFont = rememberCustomFontFamily()

    val appFont = remember(selectedFont, customFont) {
        when (AppFont.fromValue(selectedFont)) {
            AppFont.SYSTEM -> customFont ?: SfProFontFamily
            AppFont.GOOGLE_SANS -> GoogleSansFontFamily
            AppFont.SANS_FLEX -> SansFlexFontFamily
            AppFont.OUTFIT -> OutfitFontFamily
            AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalAccentColor provides themeColor,
        // Vibrator-backed: fires even with the system touch-vibration switch off.
        androidx.compose.ui.platform.LocalHapticFeedback provides rememberAppHaptics(),
        // The shared top bar sits outside screen themes, so expose the user's text override here.
        LocalAccentTextColor provides (
            if (appTextColorInt != 0) Color(appTextColorInt) else accentTextColor(themeColor, darkTheme)
        ),
    ) {
        IosTheme(
            darkTheme = darkTheme,
            accent = themeColor,
            typography = remember(appFont) { com.ozyern.exhale.ui.ios.foundation.iosTypography(appFont) },
        ) {
            // Keeps unported screens on the app palette while they still use Material3.
            MaterialTheme(
                colorScheme = colorScheme,
                typography = remember(appFont) { iosMaterialTypography(appFont) },
                shapes = AppShapes,
                content = content
            )
        }
    }
}

/**
 * The three raw colours a hero screen background paints itself from. These favour
 * the artwork's own most vivid colours — a screen background should look like the
 * cover, not like a safe seed for the OS to build widgets around.
 */
data class HeroPalette(
    /** The screen's flat tint, from the artwork's dominant/most-vivid swatch. */
    val background: Color,
    /** A second, distinct swatch — the mesh wash's highlight blobs. */
    val accent: Color,
    /** Mean colour of the artwork's own bottom edge, for a wash that reads as
     *  a continuation of the artwork rather than a second surface starting. */
    val wash: Color,
    /** Titles on [background], from the colour engine's own WCAG-enforced text ladder
     *  rather than a fixed white. Defaults to the token pair for callers (fallbacks,
     *  pure-black override) that have no artwork to derive from. */
    val primaryText: Color = AppleTokens.onColor(background),
    /** Descriptions, counts and metadata on [background] — the tier that carries the
     *  artwork's hue without losing legibility. */
    val secondaryText: Color = AppleTokens.onColorSecondary(background),
)

/**
 * [cacheKey] is the artwork URL — the colour engine caches its analysis by it, so a
 * second screen showing the same cover pays a map lookup instead of a second run.
 *
 * No Material Palette fallback: when the engine declines (an image it cannot
 * sample, or one that clusters to nothing) this returns null, and
 * [rememberHeroPalette] already falls back to the user's own theme accent for
 * that case rather than a second, different extraction method.
 */
suspend fun Bitmap.extractHeroPalette(cacheKey: String): HeroPalette? = engineHeroPalette(cacheKey)

/** The colour engine's answer for this bitmap, in [HeroPalette] shape. */
private suspend fun Bitmap.engineHeroPalette(cacheKey: String): HeroPalette? {
    val result = ColorEngine.analyze(this, cacheKey) ?: return null
    return HeroPalette(
        background = result.palette.dominant,
        accent = result.palette.accent,
        wash = bottomEdgeMean(),
    )
}

/** Flat mean of the artwork's bottom band. */
private fun Bitmap.bottomEdgeMean(): Color {
    val band = (height * 0.18f).toInt().coerceIn(1, height)
    val startY = height - band
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, startY, width, band)

    var red = 0L
    var green = 0L
    var blue = 0L
    for (pixel in pixels) {
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val n = pixels.size.coerceAtLeast(1)
    return Color(red = (red / n).toInt(), green = (green / n).toInt(), blue = (blue / n).toInt())
}

/** True when the artwork carries no usable hue. Shares the tested rule in
 *  [com.ozyern.exhale.ui.theme.PlayerColorExtractor.isAchromatic]. */
private fun List<Palette.Swatch>.isAchromatic(): Boolean {
    if (isEmpty()) return true
    val hsv = FloatArray(3)
    val saturations = FloatArray(size)
    val values = FloatArray(size)
    val populations = IntArray(size)
    forEachIndexed { i, swatch ->
        android.graphics.Color.colorToHSV(swatch.rgb, hsv)
        saturations[i] = hsv[1]
        values[i] = hsv[2]
        populations[i] = swatch.population
    }
    return PlayerColorExtractor.isAchromatic(saturations, values, populations)
}

/** Strips hue from [argb] and pins its brightness, for greyscale gradients. */
private fun desaturate(argb: Int, value: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[1] = 0f
    hsv[2] = value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun Bitmap.extractGradientColors(): List<Color> {
    val swatches = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches

    // Score's Google-Blue fallback trap: on greyscale artwork
    // Score returns 0xff4285f4 and the gradient comes out blue. Hand back greys
    // drawn from the image instead.
    if (swatches.isAchromatic()) {
        val ordered = swatches.sortedByDescending { Color(it.rgb).luminance() }
        val light = ordered.firstOrNull()?.rgb?.let { desaturate(it, 0.62f) } ?: Color(0xFF595959)
        val dark = ordered.lastOrNull()?.rgb?.let { desaturate(it, 0.16f) } ?: Color(0xFF0D0D0D)
        return listOf(light, dark)
    }

    val extractedColors = swatches.associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

/**
 * Apple-dark surface overrides applied unconditionally in dark mode.
 * pureBlack is applied AFTER this and wins for surface/background when enabled.
 */
fun ColorScheme.appleSurfaces() = copy(
    background = AppleTokens.Bg,
    surface = AppleTokens.Bg,
    surfaceContainerLowest = AppleTokens.Bg,
    surfaceContainerLow = AppleTokens.BgElevated,
    surfaceContainer = AppleTokens.Card,
    surfaceContainerHigh = AppleTokens.Card,
    surfaceContainerHighest = AppleTokens.CardSecondary,
    surfaceVariant = AppleTokens.CardSecondary,
    // primary/onPrimary set here are only a base — accentText runs afterwards and
    // gives them the contrast-clamped accent, so controls and link text carry the
    // theme colour rather than rendering flat white.
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = AppleTokens.CardSecondary,
    onPrimaryContainer = Color.White,
)

/** Pulls this text color toward white ([light]) or black until it reaches 4.5:1 on [background]; no-op when unspecified. */
private fun Color.passingOn(background: Color, light: Boolean): Color {
    if (!background.isSpecified) return this
    val extreme = if (light) Color.White else Color.Black
    var result = this
    var step = 0
    while (AppleTokens.contrastRatio(result, background) < 4.5f && step++ < 10) {
        result = androidx.compose.ui.graphics.lerp(result, extreme, 0.2f)
    }
    return result
}

/**
 * Swaps Material's neutral text roles for accent-contrast ones, so every screen
 * picks it up from `MaterialTheme.colorScheme` without being edited.
 *
 * Body text keeps only a whisper of the accent — it has to stay readable over
 * long lists — while secondary text carries it plainly, which is what replaces
 * M3's washed-out grey. Tune the two lerp amounts to taste; they are the whole
 * calibration.
 */
fun ColorScheme.accentText(accent: Color, dark: Boolean, background: Color = Color.Unspecified): ColorScheme {
    val body = if (dark) {
        androidx.compose.ui.graphics.lerp(accent, Color.White, 0.86f)
    } else {
        androidx.compose.ui.graphics.lerp(accent, Color.Black, 0.86f)
    }.passingOn(background, dark)
    val secondary = accentTextColor(accent, dark).passingOn(background, dark)
    return copy(
        onSurface = body,
        onBackground = body,
        onSurfaceVariant = secondary,
        onSecondaryContainer = secondary,
        onTertiaryContainer = secondary,
        onPrimaryContainer = secondary,
        onSecondary = secondary,
        // The subtitle role: the artist name under a song or album title reads
        // through this everywhere in the app.
        secondary = secondary,
        // Use the secondary color for links and selections; keep filled controls legible with onPrimary.
        primary = secondary,
        onPrimary = if (dark) Color.Black else Color.White,
    )
}

/**
 * Flattens every text role [accentText] tints to one explicit user-chosen color —
 * the Theme screen's "Text Color" override.
 *
 * `primary` is included, and it has to be: [accentText] deliberately routes the app's
 * second-largest text role through it (links, "See All" actions, selected states,
 * every settings screen's highlighted label), so leaving it accent-derived meant a
 * user who picked a text color still saw red text across Settings, About and section
 * actions. It doubles as a button/slider fill, so `onPrimary` is derived from the
 * picked color's own luminance rather than the theme's — a dark text color picked in
 * dark mode would otherwise leave black-on-dark button labels.
 */
fun ColorScheme.flatText(color: Color) = copy(
    onSurface = color,
    onBackground = color,
    onSurfaceVariant = color,
    onSecondaryContainer = color,
    onTertiaryContainer = color,
    onPrimaryContainer = color,
    onSecondary = color,
    secondary = color,
    primary = color,
    onPrimary = if (color.luminance() > 0.5f) Color.Black else Color.White,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
