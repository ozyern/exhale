/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

data class SettingsQuickAction(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val accentColor: Color,
    /** The glyph carries its own colours: drawn untinted. */
    val coloredIcon: Boolean = false,
)

data class SettingsGroup(
    val title: String,
    val items: List<SettingsItem>,
)

data class SettingsItem(
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val showUpdateIndicator: Boolean = false,
    val accentColor: Color = Color.Unspecified,
    val keywords: List<String> = emptyList(),
    /** The glyph carries its own colours, as ColorOS draws its Settings: drawn untinted. */
    val coloredIcon: Boolean = false,
    val onClick: () -> Unit,
)

data class SettingsIntegrationAction(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val accentColor: Color,
    /** The glyph carries its own colours: drawn untinted. */
    val coloredIcon: Boolean = false,
)
