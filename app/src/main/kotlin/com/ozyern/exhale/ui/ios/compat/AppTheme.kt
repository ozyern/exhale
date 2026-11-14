/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosColors
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.IosTypography

/**
 * The app's theme accessor, carrying Material's *role names* over the iOS palette.
 *
 * Screens were written against `MaterialTheme.colorScheme.onSurfaceVariant` and
 * `MaterialTheme.typography.bodyMedium` in ~1900 places. Re-deciding each of those by
 * hand is a different (and much riskier) change than moving them off Material, so this
 * keeps the role vocabulary and swaps what the roles resolve to: every colour below is
 * an iOS system colour, every text style an iOS one, every shape a continuous corner.
 *
 * New code should read [IosTheme] directly — `label`, `secondaryLabel`,
 * `secondarySystemGroupedBackground` say what they are. This exists so the existing
 * screens could stop being Material without being rewritten first.
 */
object AppTheme {
    val colorScheme: AppColorRoles
        @Composable get() {
            val colors = IosTheme.colors
            val tint = IosTheme.tint
            return remember(colors, tint) { AppColorRoles(colors, tint) }
        }

    val typography: AppTextRoles
        @Composable get() {
            val type = IosTheme.typography
            return remember(type) { AppTextRoles(type) }
        }

    val shapes: AppShapeRoles get() = AppShapeRoles

    val motionScheme: AppMotion get() = AppMotion
}

/**
 * Material's motion scheme, answered with iOS springs.
 *
 * iOS has one motion idea — a critically damped spring — and varies only its stiffness,
 * so the "effects" (small, non-moving changes) and "spatial" (things that travel) specs
 * differ by speed rather than by curve.
 */
object AppMotion {
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1400f)
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 800f)
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 400f)
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 800f)
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 500f)
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = 260f)
}

/**
 * Material's colour roles resolved to iOS system colours.
 *
 * The surface ladder is the part that matters: Material tints each level a little more
 * purple as it "rises", and iOS does not stack elevation at all — it has exactly three
 * flat greys. So every container level collapses onto one of those three, and
 * [surfaceColorAtElevation] ignores its argument rather than pretending depth exists.
 */
@Immutable
class AppColorRoles(private val c: IosColors, private val tint: Color) {
    val primary: Color get() = tint
    val onPrimary: Color get() = c.onAccent
    val primaryContainer: Color get() = tint.copy(alpha = 0.15f)
    val onPrimaryContainer: Color get() = tint
    val inversePrimary: Color get() = tint

    // The subtitle role — the artist name under a song title reads through this.
    val secondary: Color get() = c.secondaryLabel
    val onSecondary: Color get() = c.onAccent
    val secondaryContainer: Color get() = c.secondarySystemFill
    val onSecondaryContainer: Color get() = c.label

    val tertiary: Color get() = c.systemIndigo
    val onTertiary: Color get() = c.onAccent
    val tertiaryContainer: Color get() = c.tertiarySystemFill
    val onTertiaryContainer: Color get() = c.label

    val background: Color get() = c.systemGroupedBackground
    val onBackground: Color get() = c.label

    val surface: Color get() = c.systemBackground
    val onSurface: Color get() = c.label
    val surfaceVariant: Color get() = c.secondarySystemGroupedBackground
    val onSurfaceVariant: Color get() = c.secondaryLabel
    val surfaceTint: Color get() = c.accent
    val inverseSurface: Color get() = c.label
    val inverseOnSurface: Color get() = c.systemBackground

    val surfaceDim: Color get() = c.systemGroupedBackground
    val surfaceBright: Color get() = c.systemBackground
    val surfaceContainerLowest: Color get() = c.systemBackground
    val surfaceContainerLow: Color get() = c.secondarySystemGroupedBackground
    val surfaceContainer: Color get() = c.secondarySystemGroupedBackground
    val surfaceContainerHigh: Color get() = c.tertiarySystemGroupedBackground
    val surfaceContainerHighest: Color get() = c.tertiarySystemGroupedBackground

    val error: Color get() = c.systemRed
    val onError: Color get() = c.onAccent
    val errorContainer: Color get() = c.systemRed.copy(alpha = 0.15f)
    val onErrorContainer: Color get() = c.systemRed

    val outline: Color get() = c.separator
    val outlineVariant: Color get() = c.opaqueSeparator
    val scrim: Color get() = Color.Black.copy(alpha = 0.32f)

    /** iOS has no elevation tint ladder, so the elevation is discarded. */
    @Suppress("UNUSED_PARAMETER")
    fun surfaceColorAtElevation(elevation: Dp): Color = c.secondarySystemGroupedBackground
}

/** Material's type roles resolved to the iOS text styles. */
@Immutable
class AppTextRoles(private val t: IosTypography) {
    val displayLarge: TextStyle get() = t.largeTitle
    val displayMedium: TextStyle get() = t.largeTitle
    val displaySmall: TextStyle get() = t.title
    val headlineLarge: TextStyle get() = t.title
    val headlineMedium: TextStyle get() = t.title2
    val headlineSmall: TextStyle get() = t.title3
    val titleLarge: TextStyle get() = t.title3
    val titleMedium: TextStyle get() = t.headline
    val titleSmall: TextStyle get() = t.subheadline
    val bodyLarge: TextStyle get() = t.body
    val bodyMedium: TextStyle get() = t.callout
    val bodySmall: TextStyle get() = t.footnote
    val labelLarge: TextStyle get() = t.headline
    val labelMedium: TextStyle get() = t.footnote
    val labelSmall: TextStyle get() = t.caption2
}

/** Material's shape scale, drawn with continuous (superellipse) corners. */
object AppShapeRoles {
    val extraSmall: Shape = ContinuousRoundedRectangle(6.dp)
    val small: Shape = ContinuousRoundedRectangle(10.dp)
    val medium: Shape = ContinuousRoundedRectangle(14.dp)
    val large: Shape = ContinuousRoundedRectangle(20.dp)
    val extraLarge: Shape = ContinuousRoundedRectangle(30.dp)
}
