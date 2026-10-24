/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.SettingsDividerStartIndent
import com.ozyern.exhale.ui.component.SettingsDividerThickness
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.settingsDividerColor
import com.ozyern.exhale.ui.component.settingsGroupBorderBrush
import com.ozyern.exhale.ui.component.settingsGroupSurfaceColor
import com.ozyern.exhale.ui.component.settingsPageBackgroundColor

object SettingsDimensions {
    val GroupCardCornerRadius = SettingsGroupCornerRadius
    val QuickActionCardCornerRadius = 20.dp
    val IntegrationPillCornerRadius = 14.dp
    val BannerCardCornerRadius = 20.dp
    val HeroCardCornerRadius = 24.dp
    val RowIconCornerRadius = 12.dp

    val ScreenHorizontalPadding = 16.dp
    val CardInternalPadding = 16.dp
    val SectionSpacing = 14.dp
    val RowVerticalPadding = 14.dp
    val RowHorizontalPadding = 16.dp

    val RowIconSize = 36.dp
    val RowIconInnerSize = 20.dp
    val QuickActionIconSize = 40.dp
    val QuickActionIconInnerSize = 22.dp
    val HeroIconSize = 56.dp
    val HeroIconInnerSize = 30.dp
    val IntegrationIconSize = 28.dp
    val IntegrationIconInnerSize = 16.dp
    val BannerIconSize = 44.dp
    val BannerIconInnerSize = 22.dp
    val ChevronSize = 18.dp

    val DividerThickness = SettingsDividerThickness
    val DividerStartIndent = SettingsDividerStartIndent

    val SectionHeaderBottomPadding = 6.dp

    /**
     * Header indent *inside* the screen gutter. The groups are already inset by
     * [ScreenHorizontalPadding], so 4dp here puts the header 20dp from the screen edge — a hair
     * proud of the card, which is how iOS sets a grouped-table header. The previous 20dp pushed it
     * to 36dp, past even the row text, so no header lined up with anything.
     */
    val SectionHeaderHorizontalPadding = 4.dp

    val QuickActionTileAspectRatio = 1.4f

    val CompactColumns = 2
    val MediumColumns = 4
    val ExpandedColumns = 4

    val MediumPaneLeftWeight = 0.42f
    val MediumPaneRightWeight = 0.58f
    val ExpandedListPaneWidth = 380.dp

    /** The widest a settings page grows; wider windows centre it. iPad's form-sheet measure. */
    val ReadableMaxWidth = 680.dp

    // The grouped-table palette itself lives in `ui.component` so the shared preference widgets
    // can reach it too; these are the settings-screen names for the same three colours.

    /** The ground the inset group cards float on. */
    @Composable
    fun screenBackgroundColor(): Color = settingsPageBackgroundColor()

    /** The card colour — one solid step off [screenBackgroundColor], never elevated. */
    @Composable
    fun groupSurfaceColor(): Color = settingsGroupSurfaceColor()

    /** Hairline used only *between* rows of a group, never at a card's top or bottom edge. */
    @Composable
    fun dividerColor(): Color = settingsDividerColor()

    /** Rim around a group card, so the plate has an edge on any palette. */
    @Composable
    fun groupBorderBrush(): Brush = settingsGroupBorderBrush()
}

object SettingsAnimations {
    val PressScale = 0.97f
    val TilePressScale = 0.94f
    val PillPressScale = 0.95f
    val IconPressRotation = 5f
    val PillPressLift = (-2).dp

    val EntranceFadeDuration = 300
    val EntranceSlideDuration = 350
    val StaggerDelayPerItem = 80
    val ExitFadeDuration = 200

    fun <T> pressSpring() = spring<T>(stiffness = Spring.StiffnessHigh)
    fun <T> entranceSpring() = spring<T>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = 0.85f,
    )
}
