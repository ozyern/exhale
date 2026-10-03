package com.ozyern.exhale.ui.component.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

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
    /**
     * Damping of the tracked velocity that callers turn into stretch. Under-damped (the default)
     * rings after every change of speed; 1f tracks the drag and stops when it stops.
     */
    val velocityDampingRatio: Float = 0.5f,
) {
    private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)

    private val velocityAnimationSpec = spring(velocityDampingRatio, 300f, visibilityThreshold * 10f)
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

    // Finger-following state. While a drag is in flight the value is driven by one loop per frame
    // (see [followTo]) rather than by a fresh spring per pointer event: touch is sampled at up to
    // 240Hz, and restarting an `Animatable` for every sample is a coroutine launch, a cancellation
    // and a mutex acquisition each time, which is what made dragging stutter.
    private var followTarget = Float.NaN
    private var followJob: Job? = null
    private val followVelocity = mutableFloatStateOf(0f)
    private val following get() = followJob?.isActive == true

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    /** Where the value is heading: the finger's position while dragging, else the spring's target. */
    val targetValue: Float get() = if (!followTarget.isNaN()) followTarget else valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = if (following) followVelocity.floatValue else velocityAnimation.value

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

    /**
     * Follow a moving target — the finger — on the same stiff, critically damped spring
     * [updateValue] uses, integrated once per frame in a single coroutine for the whole gesture.
     * Velocity comes out of the same integration, smoothed, so the stretch tracks the motion and
     * stops when it stops. Any other move ([updateValue], [animateToValue]) ends the follow.
     */
    fun followTo(value: Float) {
        followTarget = value.coerceIn(valueRange)
        if (following) return
        valueJob?.cancel()
        velocityJob?.cancel()
        followVelocity.floatValue = 0f
        followJob = animationScope.launch {
            val stiffness = 1000f
            val damping = 2f * sqrt(stiffness)
            val range = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
            var x = valueAnimation.value
            var v = 0f
            var last = 0L
            while (isActive) {
                val dt = withFrameNanos { now ->
                    val step = if (last == 0L) 1f / 120f else ((now - last) / 1e9f).coerceIn(0f, 1f / 30f)
                    last = now
                    step
                }
                val target = followTarget
                if (target.isNaN()) break
                // Semi-implicit Euler in a few sub-steps: stable at this stiffness on a 30Hz frame.
                val steps = 4
                val h = dt / steps
                repeat(steps) {
                    val a = stiffness * (target - x) - damping * v
                    v += a * h
                    x += v * h
                }
                valueAnimation.snapTo(x.coerceIn(valueRange))
                // Same scale as the tracker-based velocity: value units per second over the range,
                // eased towards the raw figure at the rate a 300-stiffness spring would.
                val raw = v / range
                followVelocity.floatValue += (raw - followVelocity.floatValue) * (1f - exp(-dt * 17f))
            }
        }
    }

    private fun stopFollowing() {
        if (followTarget.isNaN() && !following) return
        followTarget = Float.NaN
        followJob?.cancel()
        followJob = null
        // Hand the stretch over to the ordinary velocity spring so it eases out rather than snapping.
        val from = followVelocity.floatValue
        followVelocity.floatValue = 0f
        velocityJob?.cancel()
        velocityJob = animationScope.launch {
            velocityAnimation.snapTo(from)
            velocityAnimation.animateTo(0f, velocityAnimationSpec)
        }
    }

    fun updateValue(value: Float) {
        stopFollowing()
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
        stopFollowing()
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

    fun animateToValue(value: Float) {
        stopFollowing()
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
