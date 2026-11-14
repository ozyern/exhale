/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal/vertical rhythm of a page, overridable for one subtree. Shared components
 * (section headers, chip rows) read this instead of [AppleTokens.Gutter] directly, so a
 * screen that follows a different reference can retune them without forking them.
 *
 * [chevronHeaders]: a tappable section header shows Apple Music's inline chevron after
 * its title instead of a "See All" link.
 */
@Immutable
data class PageMetrics(
    val gutter: Dp,
    val headerTop: Dp,
    val headerBottom: Dp,
    val chevronHeaders: Boolean = false,
)

/** Default: the Apple rhythm every screen used before per-screen metrics existed. */
val LocalPageMetrics = staticCompositionLocalOf { PageMetrics(AppleTokens.Gutter, 12.dp, 12.dp) }

/**
 * Home's page rhythm: 20dp gutter, 16dp between a header and its row, 24dp between a
 * row and the next header, and 14dp between shelf cards.
 *
 * Home's list spaces every item by [ItemSpacing], so the header adds the rest:
 * header -> row = 10 + 6 = 16dp, row -> next header = 10 + 14 = 24dp.
 */
object AppPage {
    val Gutter = 20.dp
    /** Spacing between every item of a page's list. */
    val ItemSpacing = 10.dp
    /** Extra space above a section header, on top of [ItemSpacing]. */
    val SectionGap = 14.dp
    val HeroCardGap = 14.dp
    val Metrics = PageMetrics(gutter = Gutter, headerTop = SectionGap, headerBottom = 6.dp, chevronHeaders = true)

    private const val HeroCardFraction = 0.70f
    private val HeroCardMaxWidth = 320.dp

    fun heroCardWidth(available: Dp): Dp = minOf(available * HeroCardFraction, HeroCardMaxWidth)
}
