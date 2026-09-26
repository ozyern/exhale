/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Exhale's "working on it": a comet of light running round a ring, its tail stretching as it
 * gathers pace and drawing in as it settles — a song arriving rather than a spinner ticking over.
 *
 * This is the desktop build's loader, brought over so both halves of the app wait the same way.
 * It replaces Material 3's expressive indicators everywhere in the phone app: the wavy ring and the
 * pull-to-refresh blob are unmistakably Material, and an app whose every other surface is built to
 * iOS proportions cannot announce Android's design language every time it fetches something.
 *
 * Only the drawing changes as it turns; nothing is measured or composed again for it. Give it a
 * size through [modifier] — it fills whatever it is handed, like the indicators it replaces.
 */
@Composable
fun LoadingRing(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    stroke: Dp = 2.5.dp,
) {
    val transition = rememberInfiniteTransition(label = "loading_ring")
    val turn = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_150, easing = LinearEasing)),
        label = "loading_ring_turn",
    )
    val tail = transition.animateFloat(
        initialValue = 30f,
        targetValue = 275f,
        animationSpec = infiniteRepeatable(
            tween(950, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "loading_ring_tail",
    )
    Canvas(modifier) {
        val width = stroke.toPx()
        val inset = width / 2f
        // A sweep from nothing to the full colour: the head is bright, the tail fades out behind it.
        val comet = Brush.sweepGradient(
            0f to color.copy(alpha = 0f),
            0.35f to color.copy(alpha = 0.28f),
            0.85f to color,
            1f to color,
            center = center,
        )
        rotate(turn.value) {
            drawArc(
                brush = comet,
                startAngle = 0f,
                sweepAngle = tail.value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
        }
    }
}
