/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.foundation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.designsystem.ExhaleDesignTokens

/** SwiftUI's `ControlSize`, read from the environment the way iOS controls read it. */
enum class IosControlSize { Mini, Small, Regular, Large, ExtraLarge }

/** Layout metrics shared by Exhale controls. */
object IosMetrics {
    val ListRowHeight: Dp = ExhaleDesignTokens.Metrics.SidebarRowHeight
    val ListRowMinHeightWithSubtitle: Dp = 58.dp
    val ListRowInset: Dp = ExhaleDesignTokens.Metrics.ListRowInset
    val SectionSpacing: Dp = 35.dp
    val SeparatorThickness: Dp = 0.33.dp
    val GroupedCornerRadius: Dp = 26.dp
    val SheetCornerRadius: Dp = 38.dp
    val TabBarHeight: Dp = 56.dp
    val TabBarMargin: Dp = 12.dp
    val MenuCornerRadius: Dp = 14.dp
    val ButtonCornerRadius: Dp = 12.dp
    val IconTileSize: Dp = 29.dp
    val IconTileRadius: Dp = 7.dp
}
