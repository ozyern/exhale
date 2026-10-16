/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A slider whose played part is a moving wave, like Android 13's media seek bar.
 *
 * Replaces me.saket.squigglyslider, whose last release (1.0.0) calls a material3 Slider overload
 * that material3 1.5 removed - the "Circular" seek bar crashed with NoSuchMethodError. Same call
 * shape as the library, so call sites only change their import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    squigglesSpec: SquigglySlider.SquigglesSpec = SquigglySlider.SquigglesSpec(),
) {
    Slider(
        state = rememberSliderStateOf(value.coerceIn(valueRange), valueRange),
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = {
            SliderDefaults.Thumb(interactionSource = interactionSource, colors = colors, enabled = enabled)
        },
        track = { state ->
            val span = (state.trackRange.endInclusive - state.trackRange.start).takeIf { it > 0f } ?: 1f
            val fraction = ((state.value - state.trackRange.start) / span).coerceIn(0f, 1f)
            SquigglyTrack(fraction, colors.activeTrackColor, colors.inactiveTrackColor, squigglesSpec)
        },
    )
}

object SquigglySlider {
    /** How the wave looks: its height, stroke, length of one wave, and how fast it travels. */
    data class SquigglesSpec(
        val amplitude: Dp = 3.dp,
        val strokeWidth: Dp = 4.dp,
        val wavelength: Dp = 16.dp,
        val periodMillis: Int = 1600,
    )
}

@Composable
private fun SquigglyTrack(
    fraction: Float,
    active: androidx.compose.ui.graphics.Color,
    inactive: androidx.compose.ui.graphics.Color,
    spec: SquigglySlider.SquigglesSpec,
) {
    val phase by rememberInfiniteTransition(label = "squiggle").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(spec.periodMillis, easing = LinearEasing)),
        label = "squigglePhase",
    )
    Canvas(Modifier.fillMaxWidth().height(spec.strokeWidth + spec.amplitude * 2 + 4.dp)) {
        val mid = size.height / 2
        val stroke = spec.strokeWidth.toPx()
        val split = size.width * fraction
        drawLine(inactive, Offset(split, mid), Offset(size.width, mid), stroke, StrokeCap.Round)
        val amp = spec.amplitude.toPx()
        if (split <= 0f) return@Canvas
        if (amp <= 0f) {
            drawLine(active, Offset(0f, mid), Offset(split, mid), stroke, StrokeCap.Round)
            return@Canvas
        }
        val wave = spec.wavelength.toPx().coerceAtLeast(1f)
        val path = Path()
        var x = 0f
        path.moveTo(0f, mid + amp * sin((phase * 2 * PI).toFloat()))
        while (x < split) {
            x = (x + 2f).coerceAtMost(split)
            path.lineTo(x, mid + amp * sin(((x / wave + phase) * 2 * PI).toFloat()))
        }
        drawPath(path, active, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}
