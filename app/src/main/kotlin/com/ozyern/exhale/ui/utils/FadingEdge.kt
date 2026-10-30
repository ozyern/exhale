/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp


fun Modifier.fadingEdge(
    left: Dp? = null,
    top: Dp? = null,
    right: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            drawRect(
                brush =
                Brush.verticalGradient(
                    colors =
                    listOf(
                        Color.Transparent,
                        Color.Black,
                    ),
                    startY = 0f,
                    endY = top.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            drawRect(
                brush =
                Brush.verticalGradient(
                    colors =
                    listOf(
                        Color.Black,
                        Color.Transparent,
                    ),
                    startY = size.height - bottom.toPx(),
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (left != null) {
            drawRect(
                brush =
                Brush.horizontalGradient(
                    colors =
                    listOf(
                        Color.Black,
                        Color.Transparent,
                    ),
                    startX = 0f,
                    endX = left.toPx(),
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right != null) {
            drawRect(
                brush =
                Brush.horizontalGradient(
                    colors =
                    listOf(
                        Color.Transparent,
                        Color.Black,
                    ),
                    startX = size.width - right.toPx(),
                    endX = size.width,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.fadingEdge(
    horizontal: Dp? = null,
    vertical: Dp? = null,
) = fadingEdge(
    left = horizontal,
    right = horizontal,
    top = vertical,
    bottom = vertical,
)

fun Modifier.smoothFadingEdge(
    top: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            val topPx = top.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.3f to Color.Black.copy(alpha = 0.15f),
                        0.5f to Color.Black.copy(alpha = 0.4f),
                        0.7f to Color.Black.copy(alpha = 0.7f),
                        0.85f to Color.Black.copy(alpha = 0.9f),
                        1.0f to Color.Black,
                    ),
                    startY = 0f,
                    endY = topPx,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            val bottomPx = bottom.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Black,
                        0.15f to Color.Black.copy(alpha = 0.9f),
                        0.3f to Color.Black.copy(alpha = 0.7f),
                        0.5f to Color.Black.copy(alpha = 0.4f),
                        0.7f to Color.Black.copy(alpha = 0.15f),
                        1.0f to Color.Transparent,
                    ),
                    startY = size.height - bottomPx,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.smoothFadingEdge(
    vertical: Dp,
) = smoothFadingEdge(
    top = vertical,
    bottom = vertical,
)

/**
 * `basicMarquee` with Apple's soft ends: a title too long for its box scrolls through a fade at
 * each edge instead of being sliced in half at the margin. A title that fits is left untouched,
 * with no mask and no offscreen layer, so its first and last letters never dim.
 */
fun Modifier.fadingMarquee(edge: Dp = 14.dp): Modifier = composed {
    // Written in layout and read in draw, which follows it in the same frame: no state, so a
    // title that starts or stops overflowing costs no recomposition.
    val overflowing = remember { booleanArrayOf(false) }
    this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            overflowing[0] = constraints.hasBoundedWidth && placeable.width >= constraints.maxWidth
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            compositingStrategy = if (overflowing[0]) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            drawContent()
            if (!overflowing[0]) return@drawWithContent
            val fade = (edge.toPx() / size.width).coerceAtMost(0.3f)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    fade to Color.Black,
                    1f - fade to Color.Black,
                    1f to Color.Transparent,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        .basicMarquee()
}
