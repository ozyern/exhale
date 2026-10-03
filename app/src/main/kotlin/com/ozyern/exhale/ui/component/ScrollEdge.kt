/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The ground a transparent top bar gets once something scrolls beneath it, and only then.
 *
 * At rest the bar has no colour of its own: the page runs straight up behind the title and the
 * status bar. When content slides under, the page's colour rises behind the bar — nearly solid
 * where the title sits, fading to nothing a little below the bar's edge — so the title stays
 * readable without a band across the top of the screen. [visibility] is read while drawing, so
 * following the scroll costs no recomposition.
 */
fun Modifier.scrollEdgeScrim(color: Color, visibility: () -> Float): Modifier = drawBehind {
    val shown = visibility().coerceIn(0f, 1f)
    if (shown <= 0.001f || color.alpha == 0f) return@drawBehind
    val tail = ScrollEdgeTail.toPx()
    val total = size.height + tail
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0.92f * shown),
            (size.height * 0.6f / total) to color.copy(alpha = 0.82f * shown),
            (size.height / total) to color.copy(alpha = 0.45f * shown),
            1f to color.copy(alpha = 0f),
            endY = total,
        ),
        size = Size(size.width, total),
    )
}

/** How far below a bar its scroll edge fades out. */
val ScrollEdgeTail = 28.dp

/**
 * How much of the page is under the bar right now, 0..1: content scrolled beneath a pinned bar,
 * or a large bar collapsing onto it. Zero with no scroll behaviour, where nothing is known.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun TopAppBarScrollBehavior?.scrollEdgeVisibility(): Float {
    val state = this?.state ?: return 0f
    return maxOf(state.overlappedFraction, state.collapsedFraction).coerceIn(0f, 1f)
}

/** [scrollEdgeScrim]'s visibility for pages that only know "is something under the bar", eased. */
@Composable
fun rememberScrollEdge(shown: Boolean): State<Float> =
    animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "scrollEdge")
