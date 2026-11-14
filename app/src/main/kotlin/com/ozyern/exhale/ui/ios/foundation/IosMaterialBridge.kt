/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.foundation

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Migration bridge: an M3 [ColorScheme] and [Typography] built entirely out of the iOS
 * palette and text styles.
 *
 * No screen reads this any more — they all go through `AppTheme` / `IosTheme`. It is
 * still needed because the iOS compat layer delegates to real Material machinery for
 * things that carry no Material styling of their own but do read `MaterialTheme` for
 * internal defaults: `Scaffold`'s layout, `ModalBottomSheet`'s drag/anchor behaviour,
 * `SnackbarHost`'s queueing, `TooltipBox`, `SwipeToDismissBox`, and the wavy slider that
 * backs the user-selectable `SliderStyle.WAVY`.
 *
 * Without this, those internals fall back to Material's stock purple. It is invisible on
 * screen; its whole job is keeping the parts we did not reimplement on the iOS palette.
 *
 * Keep this bridge until every delegated Material component has a native implementation.
 * Removing it while they exist does not remove Material — it just makes it ugly.
 */
@Stable
fun iosColorScheme(colors: IosColors): ColorScheme {
    val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accent.copy(alpha = 0.18f),
        onPrimaryContainer = colors.accent,
        inversePrimary = colors.accent,

        secondary = colors.accent,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.secondarySystemFill,
        onSecondaryContainer = colors.label,

        tertiary = colors.systemIndigo,
        onTertiary = colors.onAccent,
        tertiaryContainer = colors.tertiarySystemFill,
        onTertiaryContainer = colors.label,

        background = colors.systemGroupedBackground,
        onBackground = colors.label,

        // iOS has no elevation tint ladder: a "surface" is a card, and every level of
        // it is one of the three flat system greys.
        surface = colors.systemBackground,
        onSurface = colors.label,
        surfaceVariant = colors.secondarySystemGroupedBackground,
        onSurfaceVariant = colors.secondaryLabel,
        surfaceTint = colors.accent,
        inverseSurface = colors.label,
        inverseOnSurface = colors.systemBackground,

        surfaceDim = colors.systemGroupedBackground,
        surfaceBright = colors.systemBackground,
        surfaceContainerLowest = colors.systemBackground,
        surfaceContainerLow = colors.secondarySystemGroupedBackground,
        surfaceContainer = colors.secondarySystemGroupedBackground,
        surfaceContainerHigh = colors.tertiarySystemGroupedBackground,
        surfaceContainerHighest = colors.tertiarySystemGroupedBackground,

        error = colors.systemRed,
        onError = colors.onAccent,
        errorContainer = colors.systemRed.copy(alpha = 0.18f),
        onErrorContainer = colors.systemRed,

        outline = colors.separator,
        outlineVariant = colors.opaqueSeparator,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f),
    )
}

/**
 * Material typography adapted to the app's iOS palette. Display titles use the
 * heavier weights and tighter tracking used by Apple Music; other iOS typography
 * remains unchanged.
 */
@Stable
fun iosMaterialTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    val t = iosTypography(fontFamily)

    // Line height is set at ~1.2x rather than left to the font: a two-line album title
    // at -0.8 tracking and W800 sets very tight by default, and the second line ends up
    // touching the first.
    fun display(size: Int, weight: FontWeight, tracking: Float) = TextStyle(
        fontFamily = fontFamily,
        fontSize = size.sp,
        lineHeight = (size * 1.2f).sp,
        fontWeight = weight,
        letterSpacing = tracking.sp,
    )

    return Typography(
        displayLarge = display(34, FontWeight.W800, -0.8f),
        displayMedium = display(30, FontWeight.W800, -0.7f),
        displaySmall = display(26, FontWeight.W700, -0.5f),
        headlineLarge = display(30, FontWeight.W800, -0.7f),
        headlineMedium = display(22, FontWeight.W700, -0.4f),
        headlineSmall = display(20, FontWeight.W700, -0.3f),
        titleLarge = display(20, FontWeight.W700, -0.3f),
        titleMedium = display(16, FontWeight.W600, -0.2f),
        titleSmall = t.subheadline,
        bodyLarge = display(16, FontWeight.W400, 0f),
        bodyMedium = display(14, FontWeight.W400, 0f),
        bodySmall = t.footnote,
        labelLarge = t.headline,
        labelMedium = display(12, FontWeight.W600, 0f),
        labelSmall = display(11, FontWeight.W600, 0f),
    )
}
