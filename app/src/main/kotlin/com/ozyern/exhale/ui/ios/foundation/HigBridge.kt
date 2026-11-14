/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import zone.ien.hig.theme.ColorScheme
import zone.ien.hig.theme.CupertinoTheme as HigTheme
import zone.ien.hig.theme.Typography as HigTypography
import zone.ien.hig.theme.darkColorScheme
import zone.ien.hig.theme.lightColorScheme

/**
 * Hands the vendored compose-hig components this app's palette and type instead of their
 * own defaults.
 *
 * Field names match [IosColors]/[IosTypography] one-to-one against compose-hig's
 * [ColorScheme]/[HigTypography], since both were modelled on the same iOS system palette.
 */
@Composable
internal fun ProvideHigTheme(
    colors: IosColors,
    typography: IosTypography,
    content: @Composable () -> Unit,
) {
    val scheme = remember(colors) { colors.toHigColorScheme() }
    val type = remember(typography) { typography.toHigTypography() }

    // HigTheme (zone.ien.hig.theme.CupertinoTheme) provides the theme's composition
    // locals directly, so we don't need a separate CompositionLocalProvider wrapper.
    HigTheme(
        colorScheme = scheme,
        typography = type,
    ) {
        content()
    }
}

private fun IosColors.toHigColorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        accent = accent,
        label = label,
        secondaryLabel = secondaryLabel,
        tertiaryLabel = tertiaryLabel,
        quaternaryLabel = quaternaryLabel,
        systemFill = systemFill,
        secondarySystemFill = secondarySystemFill,
        tertiarySystemFill = tertiarySystemFill,
        quaternarySystemFill = quaternarySystemFill,
        placeholderText = placeholderText,
        separator = separator,
        opaqueSeparator = opaqueSeparator,
        // compose-hig's only role with no counterpart of ours — iOS draws links in the
        // system blue rather than in the app's accent.
        link = systemBlue,
        systemGroupedBackground = systemGroupedBackground,
        secondarySystemGroupedBackground = secondarySystemGroupedBackground,
        tertiarySystemGroupedBackground = tertiarySystemGroupedBackground,
        systemBackground = systemBackground,
        secondarySystemBackground = secondarySystemBackground,
        tertiarySystemBackground = tertiarySystemBackground,
    )
} else {
    lightColorScheme(
        accent = accent,
        label = label,
        secondaryLabel = secondaryLabel,
        tertiaryLabel = tertiaryLabel,
        quaternaryLabel = quaternaryLabel,
        systemFill = systemFill,
        secondarySystemFill = secondarySystemFill,
        tertiarySystemFill = tertiarySystemFill,
        quaternarySystemFill = quaternarySystemFill,
        placeholderText = placeholderText,
        separator = separator,
        opaqueSeparator = opaqueSeparator,
        link = systemBlue,
        systemGroupedBackground = systemGroupedBackground,
        secondarySystemGroupedBackground = secondarySystemGroupedBackground,
        tertiarySystemGroupedBackground = tertiarySystemGroupedBackground,
        systemBackground = systemBackground,
        secondarySystemBackground = secondarySystemBackground,
        tertiarySystemBackground = tertiarySystemBackground,
    )
}

private fun IosTypography.toHigTypography() = HigTypography(
    largeTitle = largeTitle,
    // Ours predates the numbered names: `title` is Title 1.
    title1 = title,
    title2 = title2,
    title3 = title3,
    headline = headline,
    body = body,
    callout = callout,
    subhead = subheadline,
    footnote = footnote,
    caption1 = caption,
    caption2 = caption2,
)
