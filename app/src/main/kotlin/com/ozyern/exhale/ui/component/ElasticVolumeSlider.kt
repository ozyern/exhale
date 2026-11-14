/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Port of React Bits' ElasticSlider (github.com/DavidHDev/react-bits): drag past
 * either end and the track stretches/squashes like rubber instead of just
 * clamping, with the edge icon nudging outward and pulsing, then springs back
 * on release. The web version drives this off Framer Motion's clientX/overflow
 * motion values (`.jump()` while dragging, `animate()` only on release); here
 * that same shape comes from a plain float while dragging and an [Animatable]
 * only for the release bounce, since Compose's [Animatable.snapTo] is suspend
 * and drag callbacks below aren't.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import kotlinx.coroutines.launch
import kotlin.math.exp

/** How far the track can visually stretch past its own bounds, no matter how
 *  far past the edge the finger actually goes — [decay] softly caps toward
 *  this without ever quite reaching it. */
private val MaxOverflow = 28.dp

private enum class ElasticRegion { LEFT, MIDDLE, RIGHT }

/**
 * Volume-style slider (no thumb, a filling capsule track) with the React Bits
 * ElasticSlider drag-past-the-edge feel. Icons are supplied by the caller so
 * this stays agnostic to which glyphs a volume/brightness/whatever control uses.
 */
@Composable
fun ElasticVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color,
    inactiveColor: Color,
    restingTrackHeight: Dp = 6.dp,
    /** Inset of the speaker glyphs from the row edges. */
    horizontalPadding: Dp = 16.dp,
    activeTrackHeight: Dp = 18.dp,
    leftIcon: @Composable () -> Unit,
    rightIcon: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxOverflowPx = with(density) { MaxOverflow.toPx() }

    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var region by remember { mutableStateOf(ElasticRegion.MIDDLE) }

    // Plain state (not Animatable): drag callbacks aren't suspend, so the write must be synchronous; only release springs.
    var liveOverflowPx by remember { mutableFloatStateOf(0f) }
    var releasing by remember { mutableStateOf(false) }
    val releaseOverflow = remember { Animatable(0f) }
    val overflowPx = if (releasing) releaseOverflow.value else liveOverflowPx

    val scale by animateFloatAsState(
        targetValue = if (dragging) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "elasticVolumeScale",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) activeTrackHeight else restingTrackHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "elasticVolumeTrackHeight",
    )

    // One-shot nudge on entry into an overflow region, not a held bulge.
    val leftPulse = remember { Animatable(1f) }
    val rightPulse = remember { Animatable(1f) }
    LaunchedEffect(region) {
        when (region) {
            ElasticRegion.LEFT -> {
                leftPulse.snapTo(1f)
                leftPulse.animateTo(1.4f, tween(120))
                leftPulse.animateTo(1f, tween(130))
            }
            ElasticRegion.RIGHT -> {
                rightPulse.snapTo(1f)
                rightPulse.animateTo(1.4f, tween(120))
                rightPulse.animateTo(1f, tween(130))
            }
            ElasticRegion.MIDDLE -> Unit
        }
    }

    fun onMove(rawX: Float) {
        releasing = false
        region = regionFor(rawX, trackWidthPx)
        liveOverflowPx = decay(overflowPast(rawX, trackWidthPx), maxOverflowPx)
        if (trackWidthPx > 0f) onValueChange((rawX / trackWidthPx).coerceIn(0f, 1f))
    }

    fun onRelease() {
        dragging = false
        region = ElasticRegion.MIDDLE
        val startFrom = liveOverflowPx
        liveOverflowPx = 0f
        releasing = true
        scope.launch {
            releaseOverflow.snapTo(startFrom)
            releaseOverflow.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
            releasing = false
        }
        onValueChangeFinished?.invoke()
    }

    Row(
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElasticIcon(pulse = leftPulse, pullPx = if (region == ElasticRegion.LEFT)
            -overflowPx.coerceAtMost(with(density) { 8.dp.toPx() }) else 0f) {
            leftIcon()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    // One gesture owns both press and drag: the former tap
                    // detector cancelled and shrank the track when dragging began.
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        dragging = true
                        onMove(down.position.x)
                        down.consume()
                        try {
                            do {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                    ?: break
                                if (change.pressed) onMove(change.position.x)
                                change.consume()
                            } while (change.pressed)
                        } finally {
                            onRelease()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Squash/stretch anchored at the pulled end so the other end stays put.
            val stretchOrigin = if (region == ElasticRegion.RIGHT) 0f else 1f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .graphicsLayer {
                        if (size.width > 0f) {
                            scaleX = scale * (1f + overflowPx / size.width)
                        }
                        scaleY = scale * (1f - (overflowPx / maxOverflowPx) * 0.2f)
                        transformOrigin = TransformOrigin(stretchOrigin, 0.5f)
                    }
                    .clip(ContinuousCapsule())
                    .background(inactiveColor)
            ) {
                val filled = trackWidthPx * value.coerceIn(0f, 1f)
                if (filled > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(trackHeight)
                            .drawBehind {
                                drawRoundRect(activeColor, size = Size(filled, size.height),
                                    cornerRadius = CornerRadius(size.height / 2f))
                            }
                    )
                }
            }
        }

        ElasticIcon(pulse = rightPulse, pullPx = if (region == ElasticRegion.RIGHT)
            overflowPx.coerceAtMost(with(density) { 8.dp.toPx() }) else 0f) {
            rightIcon()
        }
    }
}

@Composable
private fun ElasticIcon(pulse: Animatable<Float, AnimationVector1D>, pullPx: Float, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
            translationX = pullPx
        }
    ) {
        content()
    }
}

private fun regionFor(rawX: Float, widthPx: Float): ElasticRegion = when {
    rawX < 0f -> ElasticRegion.LEFT
    rawX > widthPx -> ElasticRegion.RIGHT
    else -> ElasticRegion.MIDDLE
}

private fun overflowPast(rawX: Float, widthPx: Float): Float = when {
    rawX < 0f -> -rawX
    rawX > widthPx -> rawX - widthPx
    else -> 0f
}

/** Same soft-cap the reference uses: a sigmoid that approaches [max] but never
 *  reaches it, so the elastic pull always has somewhere left to give. */
private fun decay(value: Float, max: Float): Float {
    if (max == 0f) return 0f
    val entry = value / max
    val sigmoid = 2 * (1 / (1 + exp(-entry)) - 0.5f)
    return sigmoid * max
}
