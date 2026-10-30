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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The ground a transparent top bar used to get once something scrolled beneath it.
 *
 * Off: however it was faded, the page's colour rising behind the bar read as a dark band across
 * the top of the screen, on the tabs and on every page with a list. Bars sit straight on the page
 * now; the pages' callers are left as they are so the ground can come back in one place.
 */
fun Modifier.scrollEdgeScrim(color: Color, visibility: () -> Float): Modifier =
    if (!ScrollEdgeGround) this else drawBehind {
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

private const val ScrollEdgeGround = false

/**
 * Content that scrolls up into the top bar fades away instead of running under the logo — a
 * fade of the content itself, not a band of colour laid over it, so what shows there is whatever
 * is behind the page (the moving artwork), exactly as at rest. [height] is the bar's bottom edge.
 */
fun Modifier.topEdgeFade(active: () -> Boolean = { true }, height: () -> Float): Modifier = this
    .graphicsLayer {
        // The fade needs the page drawn offscreen to cut into, which costs a full-screen buffer
        // every frame — so only while something has actually scrolled under the bar.
        compositingStrategy = if (active()) {
            androidx.compose.ui.graphics.CompositingStrategy.Offscreen
        } else {
            androidx.compose.ui.graphics.CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        if (!active()) return@drawWithContent
        val h = height()
        if (h <= 0f) return@drawWithContent
        // Nothing at all behind the bar itself, then a short fade just under its edge, as iOS's
        // scroll edge does. The fade used to start halfway up the bar, so a heading scrolled up
        // behind the logo was still there at a fifth of its strength, a ghost beside the mark.
        val end = h + 26.dp.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                (h / end) to Color.Transparent,
                1f to Color.Black,
                endY = end,
            ),
            size = Size(size.width, end),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
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
