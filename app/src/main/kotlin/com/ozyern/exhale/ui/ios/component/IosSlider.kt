/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import kotlin.math.roundToInt

/** SwiftUI's SliderTick — a mark drawn on the track at [value] in the slider's range. */
@Immutable
data class IosSliderTick(val value: Float, val label: String? = null)

/**
 * SwiftUI's Slider, including the iOS 26 tick marks.
 *
 * @param step snaps the value, and — as on iOS — implies a tick per step when
 *   [ticks] is left empty.
 */
@Composable
fun IosSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float? = null,
    ticks: List<IosSliderTick> = emptyList(),
    tint: Color = Color.Unspecified,
    minimumValueLabel: (@Composable () -> Unit)? = null,
    maximumValueLabel: (@Composable () -> Unit)? = null,
    onEditingChanged: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val colors = IosTheme.colors
    val activeColor = if (tint.isSpecified) tint else IosTheme.tint
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f

    fun report(rawFraction: Float) {
        val clamped = rawFraction.coerceIn(0f, 1f)
        val raw = valueRange.start + clamped * span
        val snapped = if (step != null && step > 0f) {
            valueRange.start + ((raw - valueRange.start) / step).roundToInt() * step
        } else {
            raw
        }
        onValueChange(snapped.coerceIn(valueRange.start, valueRange.endInclusive))
    }

    val resolvedTicks = when {
        ticks.isNotEmpty() -> ticks
        step != null && step > 0f -> {
            val count = (span / step).roundToInt()
            // Ticks only read as ticks while they stay countable; iOS stops drawing
            // them once the steps get dense, and so do we.
            if (count in 1..20) {
                (0..count).map { IosSliderTick(valueRange.start + it * step) }
            } else {
                emptyList()
            }
        }

        else -> emptyList()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        minimumValueLabel?.invoke()

        var dragging by remember { mutableStateOf(false) }
        // The knob swells while it is held. It is the only feedback an iOS slider gives,
        // and without it a drag reads as if nothing has been grabbed.
        val knobScale by animateFloatAsState(
            targetValue = if (dragging) 1.12f else 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 900f),
            label = "IosSliderKnob",
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .pointerInput(enabled, valueRange, step) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset -> report(offset.x / size.width) }
                }
                .pointerInput(enabled, valueRange, step) {
                    if (!enabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = {
                            dragging = true
                            onEditingChanged?.invoke(true)
                        },
                        onDragEnd = {
                            dragging = false
                            onEditingChanged?.invoke(false)
                        },
                        onDragCancel = {
                            dragging = false
                            onEditingChanged?.invoke(false)
                        },
                    ) { change, _ ->
                        change.consume()
                        report(change.position.x / size.width)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)

            Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                val trackHeight = 4.dp.toPx()
                val knobRadius = 13.5f.dp.toPx() * knobScale
                val usable = size.width - knobRadius * 2f
                val knobX = knobRadius + usable * fraction
                val centerY = size.height / 2f

                drawLine(
                    color = colors.systemGray5,
                    start = androidx.compose.ui.geometry.Offset(knobRadius, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width - knobRadius, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = if (enabled) activeColor else activeColor.copy(alpha = 0.4f),
                    start = androidx.compose.ui.geometry.Offset(knobRadius, centerY),
                    end = androidx.compose.ui.geometry.Offset(knobX, centerY),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Round,
                )

                resolvedTicks.forEach { tick ->
                    val tickFraction = ((tick.value - valueRange.start) / span).coerceIn(0f, 1f)
                    val x = knobRadius + usable * tickFraction
                    drawLine(
                        color = colors.tertiaryLabel,
                        start = androidx.compose.ui.geometry.Offset(x, centerY - 5.dp.toPx()),
                        end = androidx.compose.ui.geometry.Offset(x, centerY + 5.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                drawCircle(
                    color = Color.Black.copy(alpha = 0.12f),
                    radius = knobRadius,
                    center = androidx.compose.ui.geometry.Offset(knobX, centerY + 1.dp.toPx()),
                )
                drawCircle(
                    color = Color.White,
                    radius = knobRadius,
                    center = androidx.compose.ui.geometry.Offset(knobX, centerY),
                )
            }
        }

        maximumValueLabel?.invoke()
    }
}
