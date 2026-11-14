/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.ozyern.exhale.ui.utils.Motion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.data.settings.AppSettings
import androidx.compose.ui.graphics.lerp as lerpColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 10.dp,
    /**
     * Draws the Automix overlap the planner has lined up as a brighter stretch of the unplayed
     * bar, and sweeps a sheen along the bar while a mix is actually running. The planner has
     * published both facts since Automix landed; nothing read them, so the one part of the
     * feature the listener could have seen was the part that was missing.
     */
    showAutomix: Boolean = true,
) {
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor
    val valueRange = sliderState.valueRange
    val mixing = showAutomix && AppSettings.smartMixInProgress.value

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            drawTrack(
                stepsToTickFractions(sliderState.steps),
                0f,
                calcFraction(
                    valueRange.start,
                    valueRange.endInclusive,
                    sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive)
                ),
                inactiveTrackColor,
                activeTrackColor,
                inactiveTickColor,
                activeTickColor,
                trackHeight,
                // Read inside the draw lambda on purpose: the planner republishes this every
                // 250 ms tick, and reading it here invalidates the draw phase only rather than
                // recomposing the player chrome around it.
                automixWindow = if (showAutomix) {
                    AppSettings.smartTransitionWindow.value?.fractionRange
                } else {
                    null
                },
            )
        }
        // Composed only while a mix runs, not drawn conditionally: an infinite transition keeps
        // asking for frames for as long as it exists, so the cheap way to stop it costing
        // anything is for it not to exist. AnimatedVisibility keeps it alive through the exit
        // fade, so the sheen dies away with the mix instead of vanishing on its last frame.
        AnimatedVisibility(
            visible = mixing,
            enter = fadeIn(Motion.appear()),
            exit = fadeOut(Motion.appearExit()),
        ) {
            MixSheen(trackHeight = trackHeight)
        }
    }
}

/**
 * One soft highlight travelling the length of the bar, over and over, while two tracks are
 * being mixed.
 *
 * A travelling gradient rather than an opacity pulse: a pulse is what every loading shimmer in
 * every app means, and this is the opposite claim — not that the app is waiting, but that it is
 * doing something. It sweeps the whole bar rather than the played portion, because a mix
 * happens in the opening seconds of the incoming track, where the played portion is a few
 * percent of the width and a highlight crossing it is a flicker at the far left.
 */
@Composable
private fun MixSheen(trackHeight: Dp) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Long enough to read as a sweep rather than a flicker, slow enough not to compete
            // with the music for attention.
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        val band = size.width * SheenBandFraction
        // Enters fully off the left edge and leaves fully off the right, so the highlight
        // arrives and departs instead of materialising mid-bar.
        val centre = -band + (size.width + band * 2f) * phase
        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                ),
                start = Offset(centre - band / 2f, 0f),
                end = Offset(centre + band / 2f, 0f),
            ),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

/** Width of the travelling highlight, as a fraction of the whole bar. */
private const val SheenBandFraction = 0.7f

private fun DrawScope.drawTrack(
    tickFractions: FloatArray,
    activeRangeStart: Float,
    activeRangeEnd: Float,
    inactiveTrackColor: Color,
    activeTrackColor: Color,
    inactiveTickColor: Color,
    activeTickColor: Color,
    trackHeight: Dp = 2.dp,
    automixWindow: ClosedFloatingPointRange<Float>? = null,
) {
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tickSize = 2.0.dp.toPx()
    val trackStrokeWidth = trackHeight.toPx()
    // Round caps bulge half the stroke width past each endpoint. Inset the line so the caps
    // land inside [0, size.width] instead of overshooting it — additiveChrome's saveLayer
    // clips to exactly that rect, which was cutting the caps off at both ends.
    val capInset = trackStrokeWidth / 2f
    val sliderLeft = Offset(capInset, center.y)
    val sliderRight = Offset(size.width - capInset, center.y)
    val sliderStart = if (isRtl) sliderRight else sliderLeft
    val sliderEnd = if (isRtl) sliderLeft else sliderRight
    drawLine(
        inactiveTrackColor,
        sliderStart,
        sliderEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    val sliderValueEnd = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeEnd,
        center.y
    )
    val sliderValueStart = Offset(
        sliderStart.x +
                (sliderEnd.x - sliderStart.x) * activeRangeStart,
        center.y
    )
    // Under the played fill, and halfway between the two track colours: once the playhead
    // reaches the window the mix is no longer upcoming, and the ordinary progress colour
    // taking it over is what says so.
    automixWindow?.let { window ->
        val from = lerp(sliderStart, sliderEnd, window.start.coerceIn(0f, 1f))
        val to = lerp(sliderStart, sliderEnd, window.endInclusive.coerceIn(0f, 1f))
        if (to.x > from.x) {
            drawLine(
                lerpColor(inactiveTrackColor, activeTrackColor, 0.55f),
                Offset(from.x, center.y),
                Offset(to.x, center.y),
                trackStrokeWidth,
                StrokeCap.Round
            )
        }
    }
    drawLine(
        activeTrackColor,
        sliderValueStart,
        sliderValueEnd,
        trackStrokeWidth,
        StrokeCap.Round
    )
    for (tick in tickFractions) {
        val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
        drawCircle(
            color = if (outsideFraction) inactiveTickColor else activeTickColor,
            center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
            radius = tickSize / 2f
        )
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)
