/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How far the page travels down to reveal the spinner, and stays while it spins. */
val IosRefreshTravel = 64.dp

private const val Spokes = 12

/**
 * iOS's pull to refresh, in two halves: [iosRefreshPull] moves the page itself down with the
 * finger, and [IosRefreshSpinner] is the activity indicator the page uncovers above it.
 *
 * Material's version is a disc that drops over the content, which is the most Android thing a
 * feed can do. On iOS nothing is laid over the page: the page is pulled away from the top, the
 * spokes of the indicator fill in one by one as it goes, and past the threshold they spin until the
 * refresh lands and the page settles back up.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun Modifier.iosRefreshPull(state: PullToRefreshState, travel: Dp = IosRefreshTravel): Modifier =
    graphicsLayer {
        // The page follows the pull, and past the threshold gives more and more grudgingly — this
        // is the page's rubber-band at the top, so the list under it carries none of its own (two
        // stacked sent the page twice as far as the finger, and the generic one ate the drag before
        // the refresh could see it). The state holds at the threshold while refreshing and eases
        // back to zero after, so the gap stays open and then closes on its own.
        val f = state.distanceFraction.coerceAtLeast(0f)
        val eased = if (f <= 1f) f else 1f + (1f - 1f / (1f + (f - 1f) * 1.5f)) * 0.35f
        translationY = travel.toPx() * eased
    }

/**
 * The spokes. Drawn as the page reveals them: at a quarter of the pull the first spoke appears and
 * by the threshold all twelve are in, each a step greyer than the last, as iOS's are. Refreshing,
 * the bright spoke steps round the clock — stepping, not gliding, because that is what the real
 * one does and what makes it read as an activity indicator rather than a generic loader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosRefreshSpinner(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 26.dp,
) {
    val spin = rememberInfiniteTransition(label = "iosRefreshSpin")
    val turn by spin.animateFloat(
        initialValue = 0f,
        targetValue = Spokes.toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "iosRefreshTurn",
    )
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                val f = state.distanceFraction.coerceAtLeast(0f)
                alpha = if (isRefreshing) 1f else ((f - 0.15f) / 0.3f).coerceIn(0f, 1f)
                // It grows in from a little smaller, so it arrives rather than blinks on.
                val s = if (isRefreshing) 1f else 0.8f + 0.2f * f.coerceAtMost(1f)
                scaleX = s
                scaleY = s
            },
    ) {
        val f = state.distanceFraction.coerceAtLeast(0f)
        // How many spokes the pull has uncovered so far: all of them while refreshing.
        val shown = if (isRefreshing) Spokes else (((f - 0.25f) / 0.75f) * Spokes).toInt().coerceIn(0, Spokes)
        val lead = if (isRefreshing) floor(turn).toInt() % Spokes else 0
        val stroke = this.size.minDimension * 0.09f
        val outer = this.size.minDimension / 2f
        val inner = outer * 0.48f
        rotate(-90f) {
            for (i in 0 until shown) {
                val angle = 2 * PI * i / Spokes
                // Brightest at the lead spoke, fading back round the circle behind it.
                val behind = ((lead - i) % Spokes + Spokes) % Spokes
                val a = if (isRefreshing) 1f - behind * (0.75f / Spokes) else 0.35f + 0.65f * (i + 1) / Spokes
                drawLine(
                    color = color.copy(alpha = color.alpha * a),
                    start = Offset(center.x + inner * cos(angle).toFloat(), center.y + inner * sin(angle).toFloat()),
                    end = Offset(center.x + outer * cos(angle).toFloat(), center.y + outer * sin(angle).toFloat()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
