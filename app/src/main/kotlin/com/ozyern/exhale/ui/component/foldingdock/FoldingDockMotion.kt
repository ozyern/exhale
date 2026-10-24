/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx (github.com/cosmictaserdev-creator/Convx), GPL-3.0: the gooey merge from its
 * 1.5.2 tab bar, and the press stretch from its glass buttons. InteractiveHighlight,
 * DampedDragAnimation and inspectDragGestures come from compose-miuix-ui / Kyant0's
 * AndroidLiquidGlass catalog (Apache License 2.0), by way of Convx.
 */

package com.ozyern.exhale.ui.component.foldingdock

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.time.TimeSource

/**
 * How far the folding dock is folded, published for the host: while it is anywhere but fully open
 * the dock draws the mini player itself, so the player sheet's own pill has to stand aside.
 */
object FoldingDockMotion {
    var fold by mutableFloatStateOf(0f)
}

/* ------------------------------------------------------------------ */
/* Gooey merge                                                         */
/* ------------------------------------------------------------------ */

// Alpha snaps from 0 to 1 across a narrow band centred on 0.5: a crisp edge, not more blur.
private const val ThresholdSlope = 10f
private const val ThresholdMidpoint255 = 0.5f * 255f

private val ThresholdMatrix = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, ThresholdSlope, -ThresholdSlope * ThresholdMidpoint255,
)

// Integer radii only: the threshold hides sub-pixel blur changes, and the effects can be reused.
private val gooeyEffectCache = HashMap<Int, androidx.compose.ui.graphics.RenderEffect>()

/**
 * Blur, then a steep alpha threshold: overlapping pieces of the bar bleed into each other and are
 * snapped back to one crisp edge, so mid-fold they join by a neck that thins and pinches off
 * instead of crossfading. Free at rest: no offscreen layer while [blurRadiusPx] is 0.
 */
internal fun Modifier.gooey(blurRadiusPx: () -> Float): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    return graphicsLayer {
        val radius = blurRadiusPx().toInt()
        if (radius > 0) {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = gooeyEffectCache.getOrPut(radius) {
                RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(ThresholdMatrix),
                    RenderEffect.createBlurEffect(radius.toFloat(), radius.toFloat(), Shader.TileMode.DECAL),
                ).asComposeRenderEffect()
            }
        } else {
            compositingStrategy = CompositingStrategy.Auto
            renderEffect = null
        }
    }
}

/* ------------------------------------------------------------------ */
/* Press sheen and stretch                                             */
/* ------------------------------------------------------------------ */

/**
 * A press-driven additive sheen that follows the finger. Drawing and gesture are separate
 * modifiers so the sheen can sit on the glass while the touch target is somewhere else.
 */
internal class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    private val radiusScale: Float = 1.5f,
    private val sheenAlpha: Float = 0.08f,
    private val spotAlpha: Float = 0.15f,
) {
    private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressProgressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            // Plus lifts what is behind without replacing its detail.
            drawRect(color = Color.White.copy(alpha = sheenAlpha * progress), blendMode = BlendMode.Plus)
            val pos = position(size, positionAnimation.value)
            val radius = (size.minDimension * radiusScale).coerceAtLeast(1f)
            val spot = Color.White.copy(alpha = spotAlpha * progress)
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0.0f to spot, 0.5f to spot, 1.0f to Color.White.copy(alpha = 0f)),
                    center = Offset(pos.x.coerceIn(0f, size.width), pos.y.coerceIn(0f, size.height)),
                    radius = radius,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        inspectDragGestures(
            onDragStart = { down ->
                startPosition = down.position
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = { release() },
            onDragCancel = { release() },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }

    private fun release() {
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
        }
    }
}

/** The glass swells a few dp under the finger and leans after it as it drags. */
internal fun elasticStretchLayerBlock(
    interactiveHighlight: InteractiveHighlight,
    magnitude: Float = 1f,
): GraphicsLayerScope.() -> Unit = {
    val width = size.width
    val height = size.height
    if (width > 0f && height > 0f) {
        val progress = interactiveHighlight.pressProgress
        val scale = lerp(1f, 1f + magnitude * 4f.dp.toPx() / height, progress)
        val maxOffset = size.minDimension
        val initialDerivative = 0.05f
        val offset = interactiveHighlight.offset
        translationX = magnitude * maxOffset * tanh(initialDerivative * offset.x / maxOffset)
        translationY = magnitude * maxOffset * tanh(initialDerivative * offset.y / maxOffset)
        val maxDragScale = magnitude * 4f.dp.toPx() / height
        val offsetAngle = atan2(offset.y, offset.x)
        scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
            (width / height).fastCoerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
            (height / width).fastCoerceAtMost(1f)
    }
}

/* ------------------------------------------------------------------ */
/* The selector's drag physics                                         */
/* ------------------------------------------------------------------ */

/**
 * Keeps the selector's logical position, its pressed swell and its normalised drag velocity in
 * sync. Callers turn the dimensionless [value] into pixels themselves.
 */
internal class DockDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DockDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DockDragAnimation.() -> Unit,
    val onDrag: DockDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private var pressJob: Job? = null
    private var releaseJob: Job? = null
    private var settleJob: Job? = null
    private val velocityTracker = VelocityTracker()
    private val startMark = TimeSource.Monotonic.markNow()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    fun press() {
        releaseJob?.cancel()
        pressJob?.cancel()
        velocityTracker.resetTracking()
        pressJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        releaseJob?.cancel()
        pressJob?.cancel()
        releaseJob = animationScope.launch {
            withFrameMillis { }
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                withTimeoutOrNull(400) {
                    snapshotFlow { valueAnimation.value }
                        .first { abs(it - valueAnimation.targetValue) < threshold }
                }
            }
            launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    /** Drag path: the finger owns the position, so it snaps rather than queueing springs. */
    fun updateValue(value: Float) {
        val coerced = if (value.isFinite()) value.coerceIn(valueRange) else valueAnimation.value
        animationScope.launch {
            valueAnimation.snapTo(coerced)
            snapVelocity()
        }
    }

    /** Tap path: single-flight, and the swell always deflates even when a newer tap wins. */
    fun animateToValue(value: Float) {
        settleJob?.cancel()
        settleJob = animationScope.launch {
            mutatorMutex.mutate {
                var released = false
                try {
                    press()
                    val target = if (value.isFinite()) value.coerceIn(valueRange) else valueAnimation.value
                    valueAnimation.animateTo(target, valueAnimationSpec)
                    if (velocityAnimation.value != 0f) velocityAnimation.animateTo(0f, velocityAnimationSpec)
                    release()
                    released = true
                } finally {
                    if (!released &&
                        (pressProgressAnimation.value != 0f ||
                            scaleXAnimation.value != initialScale ||
                            scaleYAnimation.value != initialScale)
                    ) {
                        launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
                        launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
                        launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
                    }
                }
            }
        }
    }

    private suspend fun snapVelocity() {
        velocityTracker.addPosition(startMark.elapsedNow().inWholeMilliseconds, Offset(valueAnimation.value, 0f))
        val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-6f)
        val raw = velocityTracker.calculateVelocity().x / span
        velocityAnimation.snapTo(if (raw.isFinite()) raw else 0f)
    }
}

/** Reports every gesture as a drag, taps included, without consuming it. */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val upEvent = trackDrag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
    }
}

private suspend inline fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) return dragEvent else pointer = otherDown.id
        } else if (dragEvent.previousPosition != dragEvent.position) {
            return dragEvent
        }
    }
}
