package com.ozyern.exhale.ui.component.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Manages damped drag animations for sliders and toggles with velocity tracking.
 */
internal class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)

    // Settling onto a tab is iOS's lens spring: a touch under-damped, so the glass arrives with a
    // small overshoot and recoil instead of stopping dead like a cursor.
    private val settleAnimationSpec = spring(0.72f, 420f, visibilityThreshold)
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
    private val velocityTracker = VelocityTracker()

    // One job per animation, re-targeted rather than re-launched.
    //
    // Both of these used to be `animationScope.launch { ... }` with nothing holding the handle,
    // and both were called once per *frame* of a drag. `Animatable.animateTo` takes an internal
    // mutex, so every new launch cancelled the one before it: a 120Hz drag spent its budget
    // starting and tearing down ~240 coroutines a second and the slider visibly stuttered. The
    // animation these produced was identical either way — `animateTo` on a live `Animatable`
    // already re-targets a running spring smoothly — so the churn bought nothing at all.
    private var valueJob: Job? = null
    private var velocityJob: Job? = null

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    /** Live speed of the settle spring, in value units per second — what a tap's travel stretches by. */
    val travelVelocity: Float get() = valueAnimation.velocity

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
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .filter { abs(it - valueAnimation.targetValue) < threshold }
                    .first()
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        // Already heading there. Re-launching would restart the spring from its current velocity
        // for no visible difference.
        if (valueAnimation.targetValue == targetValue) return
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
        }
    }

    /**
     * Puts the value exactly where a gesture left it, with no animation and no spring to fight.
     *
     * Sliders drive their thumb straight from the pointer while a drag is in flight and hand the
     * final position back here on release, so the settled state and the gesture's last frame
     * agree instead of springing apart by whatever the host rounded the value to.
     */
    fun snapToValue(value: Float, onSnapped: () -> Unit = {}) {
        val targetValue = value.coerceIn(valueRange)
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.snapTo(targetValue)
            // Callers hand back control here rather than at the call site: `snapTo` suspends, so
            // a caller that stopped drawing its own gesture position on the line below would
            // show one frame of the pre-snap value.
            onSnapped()
        }
    }

    /**
     * Place the value exactly where a gesture left it, then spring from there to where it belongs.
     *
     * For controls that drive their own position during a drag — writing a plain float per frame
     * rather than re-targeting a spring per frame, which is a coroutine and a mutex acquisition
     * per frame — and only hand the number back on release. Doing it as `snapToValue` followed by
     * `settleToValue` is two coroutines racing over the same job handle, and the snap loses about
     * half the time, so the capsule springs from wherever it was *before* the drag.
     */
    fun settleFrom(from: Float, to: Float, onSnapped: () -> Unit = {}) {
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.snapTo(from.coerceIn(valueRange))
            onSnapped()
            valueAnimation.animateTo(to.coerceIn(valueRange), settleAnimationSpec)
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    /**
     * Re-target the value without touching press state, for a control whose indicator is
     * permanently on screen.
     *
     * [animateToValue] wraps its move in `press()` / `release()`, which is right for a knob that
     * only exists while it is being touched — the pressed swell *is* the acknowledgement of the
     * tap. The nav dock's capsule is always there, so borrowing that call to follow a route change
     * made the pill inflate to its pressed size and deflate again every time the user navigated
     * with something other than the dock, which reads as the bar being poked by a ghost.
     */
    fun settleToValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        if (valueAnimation.targetValue == targetValue && value == valueAnimation.value) return
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.animateTo(targetValue, settleAnimationSpec)
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            System.currentTimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        if (abs(targetVelocity - velocityAnimation.targetValue) < visibilityThreshold) return
        velocityJob?.cancel()
        velocityJob = animationScope.launch {
            velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec)
        }
    }
}
