/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component.floatingtabbar

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.ui.geometry.Rect

/**
 * An underdamped spring's step response squeezed into a fixed-duration [Easing].
 *
 * The bar needs every clock (container size, fades, shared bounds) to end on one frame, so it
 * can't use a real `spring()`. The cubic beziers it had peaked barely 1% past the target,
 * which reads as no overshoot at all. This keeps the spring's own shape: crosses the target
 * early, overshoots by ~exp(-zeta*pi/sqrt(1-zeta^2)) (zeta 0.66 ~6%, 0.74 ~3%) and settles.
 * [omegaT] = natural frequency x duration; 7.8 leaves <0.6% residual, removed linearly so
 * transform(1) is exactly 1.
 */
internal class SpringEasing(private val zeta: Float, private val omegaT: Float = 7.8f) : Easing {
    private val root = kotlin.math.sqrt(1f - zeta * zeta)
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wd = omegaT * root * t
        return 1f - kotlin.math.exp(-zeta * omegaT * t) *
            (kotlin.math.cos(wd) + zeta / root * kotlin.math.sin(wd))
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

/**
 * Critically damped step response on the same fixed-duration frame as [SpringEasing]:
 * spring-shaped (front-loaded, asymptotic landing), never passes the target.
 */
internal class CriticalEasing(private val omegaT: Float = 7.8f) : Easing {
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wt = omegaT * t
        return 1f - (1f + wt) * kotlin.math.exp(-wt)
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

/**
 * One axis of a bounds animation: how long it waits, how long it runs, and its curves.
 *
 * [easing] moves the axis CENTRE, [sizeEasing] its EXTENT. Surfaces pass a critically
 * damped centre with a springy extent, so the overshoot is the body swelling past its
 * size and settling -- while everything centred inside it (icons, the mini's artwork and
 * buttons) travels on a curve that never overshoots. User: "overshoot of the surface or
 * body only, not the icons or items".
 */
internal class AxisCurve(
    val delayMs: Int,
    val durationMs: Int,
    val easing: Easing,
    val sizeEasing: Easing = easing,
) {
    val endMs: Int get() = delayMs + durationMs

    private fun fraction(playMs: Float) = ((playMs - delayMs) / durationMs).coerceIn(0f, 1f)

    fun progress(playMs: Float): Float = easing.transform(fraction(playMs))

    fun sizeProgress(playMs: Float): Float = sizeEasing.transform(fraction(playMs))

    /** (start, end) edges of this axis at [playMs]: centre on [easing], extent on [sizeEasing]. */
    fun edges(playMs: Float, fromStart: Float, fromEnd: Float, toStart: Float, toEnd: Float): Pair<Float, Float> {
        val p = progress(playMs)
        val sp = sizeProgress(playMs)
        val fromCenter = (fromStart + fromEnd) / 2f
        val center = fromCenter + ((toStart + toEnd) / 2f - fromCenter) * p
        val fromSize = fromEnd - fromStart
        val half = (fromSize + ((toEnd - toStart) - fromSize) * sp) / 2f
        return (center - half) to (center + half)
    }
}

/**
 * A [Rect] animation whose horizontal edges (left/right) and vertical edges (top/bottom)
 * run on independent delays, durations and curves.
 *
 * Apple's mini player does not travel in a straight line between its expanded and inline
 * slots: measured off a screen recording, on collapse it slides sideways first and drops
 * about 67ms later, on expand it rises first and widens 33ms later. A single easing on the
 * whole rect (what `tween<Rect>` gives) moves both axes on one clock and reads as a
 * different motion.
 */
internal class AxisStaggerSpec(
    private val x: AxisCurve,
    private val y: AxisCurve,
) : FiniteAnimationSpec<Rect> {

    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Rect, V>,
    ): VectorizedFiniteAnimationSpec<V> = Vectorized(x, y)

    /** Total run time: the later of the two axes. */
    val durationMs: Int get() = maxOf(x.endMs, y.endMs)

    fun rectAt(playMs: Float, from: Rect, to: Rect): Rect {
        val (l, r) = x.edges(playMs, from.left, from.right, to.left, to.right)
        val (t, b) = y.edges(playMs, from.top, from.bottom, to.top, to.bottom)
        return Rect(l, t, r, b)
    }

    // Rect.VectorConverter is AnimationVector4D(left, top, right, bottom); V is only generic
    // because the interface is, so the casts below are total.
    @Suppress("UNCHECKED_CAST")
    private class Vectorized<V : AnimationVector>(
        private val x: AxisCurve,
        private val y: AxisCurve,
    ) : VectorizedFiniteAnimationSpec<V> {

        override fun getValueFromNanos(
            playTimeNanos: Long,
            initialValue: V,
            targetValue: V,
            initialVelocity: V,
        ): V {
            val from = initialValue as AnimationVector4D
            val to = targetValue as AnimationVector4D
            val ms = playTimeNanos / 1_000_000f
            val (l, r) = x.edges(ms, from.v1, from.v3, to.v1, to.v3)
            val (t, b) = y.edges(ms, from.v2, from.v4, to.v2, to.v4)
            return AnimationVector4D(l, t, r, b) as V
        }

        // Central difference: the curves are cheap and this is only read when a running
        // animation is retargeted, where a 2ms window is far below a frame.
        override fun getVelocityFromNanos(
            playTimeNanos: Long,
            initialValue: V,
            targetValue: V,
            initialVelocity: V,
        ): V {
            val window = 2_000_000L
            val before = getValueFromNanos((playTimeNanos - window).coerceAtLeast(0L), initialValue, targetValue, initialVelocity) as AnimationVector4D
            val after = getValueFromNanos(playTimeNanos + window, initialValue, targetValue, initialVelocity) as AnimationVector4D
            val perSecond = 1_000_000_000f / (playTimeNanos + window - (playTimeNanos - window).coerceAtLeast(0L))
            return AnimationVector4D(
                (after.v1 - before.v1) * perSecond,
                (after.v2 - before.v2) * perSecond,
                (after.v3 - before.v3) * perSecond,
                (after.v4 - before.v4) * perSecond,
            ) as V
        }

        override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long =
            maxOf(x.endMs, y.endMs) * 1_000_000L
    }
}

/**
 * The mini player's slide between its expanded slot (above the tab pill, full width) and
 * its inline slot (between the Home circle and Search). Fitted to `m2-res_854p.mp4`, an
 * iPhone screen recording at 30fps, by tracking the artwork thumbnail per frame
 * (RMSE 0.013 to 0.029 of the travel). Both overshoot ~3%: bezier y2 above 1.
 */
internal object BarMotion {
    /** How long the mini player holds its height before dropping on collapse. The bar's own
     *  SizeTransform reads this too -- that container resize, not the accessory bounds, is
     *  what actually moves the mini vertically. */
    const val CollapseYDelayMs = 67

    /** Liquid-glass overshoot, per user: clearly visible, smooth, a little slow (~11% expand,
     *  ~9% collapse of the travel; the mini width bounces more, ~15%/~12%). */
    val CollapseSpring = SpringEasing(zeta = 0.62f)
    val ExpandSpring = SpringEasing(zeta = 0.58f)

    /** Tab pill / search circle body: gentler than the mini (user: less overshoot on the
     *  bar, the mini's bounce is fine) -- ~3% expand, ~2% collapse. */
    val BarSurfaceCollapseSpring = CriticalEasing() // inline pill never undershoots its icon
    val BarSurfaceExpandSpring = SpringEasing(zeta = 0.74f)

    /** Tab pill WIDTH on expand: bouncier than its height (user: more width overshoot when the nav expands). */
    val BarSurfaceExpandWidthSpring = SpringEasing(zeta = 0.40f)

    /** Mini pill WIDTH (x axis) gets its own, bouncier spring: the user asked for a
     *  bigger width overshoot than the bar's. */
    val AccessoryCollapseXSpring = SpringEasing(zeta = 0.56f, omegaT = 9f)
    val AccessoryExpandXSpring = SpringEasing(zeta = 0.34f, omegaT = 9f)

    /** Centre travel for everything: spring-shaped, never overshoots. */
    val Travel = CriticalEasing()

    val CollapseAccessory = AxisStaggerSpec(
        // Slides sideways first, then drops ~67ms later. Centre on [Travel]; only the
        // pill's extent bounces.
        x = AxisCurve(0, 560, Travel, sizeEasing = AccessoryCollapseXSpring),
        y = AxisCurve(CollapseYDelayMs, 560, Travel, sizeEasing = CollapseSpring),
    )

    val ExpandAccessory = AxisStaggerSpec(
        // Rises first, widens ~33ms later.
        y = AxisCurve(0, 470, Travel, sizeEasing = ExpandSpring),
        x = AxisCurve(33, 450, Travel, sizeEasing = AccessoryExpandXSpring),
    )

    /** A surface (tab pill, search circle): centre on [Travel], size on [sizeSpring]. */
    fun surface(durationMs: Int, sizeSpring: Easing, widthSpring: Easing = sizeSpring) = AxisStaggerSpec(
        x = AxisCurve(0, durationMs, Travel, sizeEasing = widthSpring),
        y = AxisCurve(0, durationMs, Travel, sizeEasing = sizeSpring),
    )
}
