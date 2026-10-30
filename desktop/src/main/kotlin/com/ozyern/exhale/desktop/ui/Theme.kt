/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font as FontFace
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.m3color.dynamiccolor.DynamicScheme
import com.kyant.m3color.hct.Hct
import com.kyant.m3color.scheme.SchemeMonochrome
import com.kyant.m3color.scheme.SchemeNeutral
import com.kyant.m3color.scheme.SchemeTonalSpot
import com.ozyern.exhale.desktop.data.AccentPalette
import com.ozyern.exhale.desktop.data.AccentPalettes
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow

/** Same key colour as the Android app's DefaultThemeColor. */
val ExhaleAccent = Color(0xFFED5564)

/** The current song's artwork colour, for "Colour from artwork". Set by the app shell. */
object ThemeSeed {
    val artwork = MutableStateFlow<Color?>(null)
}

private fun m3Scheme(seed: Color): DynamicScheme =
    Hct.fromInt(seed.toArgb()).let { hct ->
        when {
            hct.chroma < 4.0 -> SchemeMonochrome(hct, true, 0.0)
            hct.chroma < 12.0 -> SchemeNeutral(hct, true, 0.0)
            else -> SchemeTonalSpot(hct, true, 0.0)
        }
    }

private fun Int.c(): Color = Color(this.toLong() and 0xFFFFFFFFL)

/**
 * The Android app's four-seed Material 3 scheme (m3color, the same library): primary, secondary
 * and tertiary from their own seeds, every surface tier from the neutral seed. Dark only.
 */
fun exhaleColorScheme(palette: AccentPalette, pureBlack: Boolean): ColorScheme {
    val p = m3Scheme(palette.primary)
    val s = m3Scheme(palette.secondary)
    val t = m3Scheme(palette.tertiary)
    val n = m3Scheme(palette.neutral)
    val scheme = darkColorScheme(
        primary = p.primary.c(),
        onPrimary = p.onPrimary.c(),
        primaryContainer = p.primaryContainer.c(),
        onPrimaryContainer = p.onPrimaryContainer.c(),
        inversePrimary = p.inversePrimary.c(),
        secondary = s.primary.c(),
        onSecondary = s.onPrimary.c(),
        secondaryContainer = s.primaryContainer.c(),
        onSecondaryContainer = s.onPrimaryContainer.c(),
        tertiary = t.primary.c(),
        onTertiary = t.onPrimary.c(),
        tertiaryContainer = t.primaryContainer.c(),
        onTertiaryContainer = t.onPrimaryContainer.c(),
        background = n.background.c(),
        onBackground = n.onBackground.c(),
        surface = n.surface.c(),
        onSurface = n.onSurface.c(),
        surfaceVariant = n.surfaceVariant.c(),
        onSurfaceVariant = n.onSurfaceVariant.c(),
        surfaceTint = p.surfaceTint.c(),
        inverseSurface = n.inverseSurface.c(),
        inverseOnSurface = n.inverseOnSurface.c(),
        error = p.error.c(),
        onError = p.onError.c(),
        errorContainer = p.errorContainer.c(),
        onErrorContainer = p.onErrorContainer.c(),
        outline = n.outline.c(),
        outlineVariant = n.outlineVariant.c(),
        scrim = n.scrim.c(),
        surfaceBright = n.surfaceBright.c(),
        surfaceContainer = n.surfaceContainer.c(),
        surfaceContainerHigh = n.surfaceContainerHigh.c(),
        surfaceContainerHighest = n.surfaceContainerHighest.c(),
        surfaceContainerLow = n.surfaceContainerLow.c(),
        surfaceContainerLowest = n.surfaceContainerLowest.c(),
        surfaceDim = n.surfaceDim.c(),
    )
    return if (pureBlack) scheme.copy(background = Color.Black, surface = Color.Black) else scheme
}

/** Accent changes glide instead of snapping — the colours the UI actually paints with. */
@Composable
private fun animate(target: ColorScheme): ColorScheme {
    val spec = spring<Color>(stiffness = Spring.StiffnessLow)
    @Composable
    fun a(color: Color) = animateColorAsState(color, spec).value
    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        secondary = a(target.secondary),
        tertiary = a(target.tertiary),
        background = a(target.background),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant),
        onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
    )
}

/**
 * Inter, in four real weights: the open face drawn after Apple's SF, which the Music app is set in
 * (SF itself is only licensed for Apple's platforms). Real weights, so a regular label is regular —
 * the Linotte file this replaced came in Bold only, and every line of the app was set bold with it.
 */
private val Inter: FontFamily by lazy {
    fun face(file: String, weight: FontWeight): FontFace {
        val bytes = object {}.javaClass.getResourceAsStream("/font/$file.ttf")!!.use { it.readAllBytes() }
        return Font(identity = file, data = bytes, weight = weight)
    }
    FontFamily(
        face("inter_regular", FontWeight.Normal),
        face("inter_medium", FontWeight.Medium),
        face("inter_semibold", FontWeight.SemiBold),
        face("inter_bold", FontWeight.Bold),
    )
}

/**
 * The Android app's type scale: Material roles, but with the iOS large-title convention on the
 * display/headline/title band — bold, tracked tighter as the size grows.
 */
private fun exhaleTypography(font: FontFamily): Typography {
    val base = Typography()
    // Material spaces its small text out (up to 0.5sp); SF sits slightly tight at these sizes.
    fun TextStyle.f() = copy(fontFamily = font, letterSpacing = (-0.08).sp)
    return Typography(
        displayLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1.0).sp),
        displayMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.8).sp),
        displaySmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.6).sp),
        headlineLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}

private val ExhaleShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Theme + settings for everything under it: accent (or artwork colour), pure black, and the
 * interface size, applied as a density override so every dp and sp scales together.
 */
@Composable
fun ExhaleTheme(settings: Settings, artworkSeed: Color?, content: @Composable () -> Unit) {
    val palette = if (settings.dynamicTheme && artworkSeed != null) {
        AccentPalette("artwork", "Artwork", artworkSeed)
    } else {
        AccentPalettes.byId(settings.accent)
    }
    val target = remember(palette, settings.pureBlack) {
        val scheme = exhaleColorScheme(palette, settings.pureBlack)
        // Left on the default accent, the app wears the Music app's own: macOS 27's system blue for what's
        // playing, links and active controls. A chosen accent, or colour from artwork, still wins.
        if (palette.id == "default" && !(settings.dynamicTheme && artworkSeed != null)) {
            scheme.copy(primary = AppleColors.Accent, onPrimary = Color.White)
        } else {
            scheme
        }
    }
    val scheme = animate(target)
    val typography = remember { exhaleTypography(Inter) }
    val density = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(density.density * settings.uiScale, density.fontScale),
        LocalSettings provides settings,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = ExhaleShapes, content = content)
    }
}
