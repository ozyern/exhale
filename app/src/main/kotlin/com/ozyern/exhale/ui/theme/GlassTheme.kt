/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the "liquid glass" (glassmorphic) aesthetic.
 *
 * This is a presentation-only layer that sits *alongside* the existing Material 3
 * [ColorScheme]; it does not replace any colors. Values are derived from the active
 * color scheme so glass surfaces tint themselves to the current (album-art-reactive)
 * theme automatically.
 *
 * Consumed via [LocalGlass]. See `ui/component/Glass.kt` for the modifiers/composables
 * that turn these tokens into actual surfaces.
 */
@Immutable
data class GlassTokens(
    /** Translucent fill layered over blurred content (the "frosted" tint). */
    val tint: Color,
    /** Opaque-ish base fed to Haze so text stays legible over moving gradients. */
    val blurBackground: Color,
    /** Near-opaque fill used on devices/paths without live backdrop blur (API < 31). */
    val fallbackScrim: Color,
    /** Backdrop blur radius for live-blur surfaces. */
    val blurRadius: Dp,
    /** Subtle film grain to break up banding in the blur. */
    val noiseFactor: Float,
    /** Hairline "light catching the edge of glass" border. */
    val borderWidth: Dp,
    val borderBrush: Brush,
    /** Soft top-down sheen drawn inside the panel. */
    val highlightBrush: Brush,
    /** Soft, expansive, low-opacity elevation shadow. */
    val shadowColor: Color,
    val shadowElevation: Dp,
    val cornerRadius: Dp,
    val isDark: Boolean,
)

/**
 * Builds [GlassTokens] from the active [ColorScheme]. Pure function (no composition),
 * so it can be memoized against the (animated) color scheme.
 */
fun glassTokensOf(colorScheme: ColorScheme, isDark: Boolean): GlassTokens {
    val surface = colorScheme.surface

    // Frosted tint: lighter alpha in dark mode so blurred content shows through.
    val tintAlpha = if (isDark) 0.36f else 0.50f
    val fallbackAlpha = if (isDark) 0.80f else 0.85f

    // Edge highlight: brighter top-left fading to near-invisible, simulating a
    // light source catching the rim of the glass.
    val edge = if (isDark) 0.22f else 0.55f
    val borderBrush = Brush.linearGradient(
        0.0f to Color.White.copy(alpha = edge),
        0.5f to Color.White.copy(alpha = edge * 0.28f),
        1.0f to Color.White.copy(alpha = 0.03f),
    )

    val highlightBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (isDark) 0.06f else 0.18f),
            Color.Transparent,
        )
    )

    return GlassTokens(
        // True iOS UIBlurEffect: thin dark tint over a transparent blur base so the
        // background clearly shines through (not the old opaque surface fill).
        tint = Color.Black.copy(alpha = if (isDark) 0.20f else 0.14f),
        blurBackground = Color.Transparent,
        fallbackScrim = surface.copy(alpha = fallbackAlpha),
        blurRadius = 56.dp,
        noiseFactor = 0.06f,
        borderWidth = 1.dp,
        borderBrush = borderBrush,
        highlightBrush = highlightBrush,
        shadowColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.18f),
        shadowElevation = 12.dp,
        cornerRadius = 28.dp,
        isDark = isDark,
    )
}

/** Provided by [ExhaleTheme]. Reading it outside the theme is a programmer error. */
val LocalGlass = staticCompositionLocalOf<GlassTokens> {
    error("LocalGlass not provided. Wrap content in ExhaleTheme { }.")
}

/**
 * Motion tokens for the "the iOS music app-level smoothness" pass: physical spring specs that
 * replace rigid tweens. Prefer these over ad-hoc `tween()` so momentum feels consistent
 * app-wide. All are float specs (drive `graphicsLayer` scale/alpha/translation on the GPU).
 */
object MotionTokens {
    /** Scale a pressed element shrinks to. Subtle, premium. */
    const val PressedScale = 0.96f

    /** Tap physics: quick, with a faint bounce back on release. */
    val PressSpring: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 520f)

    /** General emphasized motion for content entering/settling. */
    val EmphasizedSpring: SpringSpec<Float> =
        spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)

    /** Expanding sheets/dialogs: momentum-based, no overshoot. */
    val SheetSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
}
