/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp

/** Extends a carousel under the left-pinned side bar while reporting its original slot width. Pair with [plusStart] to keep the first card clear. */
fun Modifier.bleedStart(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = inset.roundToPx().coerceAtLeast(0)
    val widened = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = constraints.maxWidth + extra,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(widened)
    // Report the ORIGINAL width: the parent must not learn it got wider, or the
    // list starts scrolling horizontally.
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) {
        placeable.place(-extra, 0)
    }
}

/** Width the floating side bar covers at the start edge; zero when there is none. */
@Composable
fun sideBarInset(): Dp = com.ozyern.exhale.LocalPlayerAwareWindowInsets.current
    .asPaddingValues()
    .calculateStartPadding(LocalLayoutDirection.current)
    .coerceAtLeast(0.dp)

/** [PaddingValues] with [extra] added to the start edge. */
@Composable
fun PaddingValues.plusStart(extra: Dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection) + extra,
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding(),
    )
}
