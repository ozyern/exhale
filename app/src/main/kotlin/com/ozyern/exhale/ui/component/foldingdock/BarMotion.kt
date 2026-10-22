/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx (github.com/cosmictaserdev-creator/Convx), GPL-3.0: BarMotion from its
 * floating tab bar (2.0.4), the fixed-duration springs and the staggered rect spec the mini
 * player, the tab pill and the search circle all move on.
 */

package com.ozyern.exhale.ui.component.foldingdock

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * An underdamped spring's step response squeezed into a fixed-duration easing, so every clock of
 * the morph ends on one frame while still overshooting by exp(-zeta*pi/sqrt(1-zeta^2)) and
 * settling. [omegaT] is natural frequency x duration; the residual is removed linearly so
 * transform(1) is exactly 1.
 */
internal class SpringEasing(private val zeta: Float, private val omegaT: Float = 7.8f) : Easing {
    private val root = sqrt(1f - zeta * zeta)
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wd = omegaT * root * t
        return 1f - exp(-zeta * omegaT * t) * (cos(wd) + zeta / root * sin(wd))
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

/** Critically damped step response on the same fixed-duration frame: never passes the target. */
internal class CriticalEasing(private val omegaT: Float = 7.8f) : Easing {
    private val end = raw(1f)

    private fun raw(t: Float): Float {
        val wt = omegaT * t
        return 1f - (1f + wt) * exp(-wt)
    }

    override fun transform(fraction: Float): Float =
        if (fraction >= 1f) 1f else raw(fraction) + (1f - end) * fraction
}

/**
 * One axis of a rect animation: its delay, duration and curves. [easing] moves the axis centre,
 * [sizeEasing] its extent, so a surface's body can swell past its size and settle while whatever
 * is centred inside it travels on a curve that never overshoots.
 */
internal class AxisCurve(
    val delayMs: Int,
    val durationMs: Int,
    val easing: Easing,
    val sizeEasing: Easing = easing,
) {
    val endMs: Int get() = delayMs + durationMs

    private fun fraction(playMs: Float) = ((playMs - delayMs) / durationMs).coerceIn(0f, 1f)

    fun edges(playMs: Float, fromStart: Float, fromEnd: Float, toStart: Float, toEnd: Float): Pair<Float, Float> {
        val p = easing.transform(fraction(playMs))
        val sp = sizeEasing.transform(fraction(playMs))
        val fromCenter = (fromStart + fromEnd) / 2f
        val center = fromCenter + ((toStart + toEnd) / 2f - fromCenter) * p
        val fromSize = fromEnd - fromStart
        val half = (fromSize + ((toEnd - toStart) - fromSize) * sp) / 2f
        return (center - half) to (center + half)
    }
}

/**
 * A [Rect] animation whose horizontal and vertical edges run on their own delays, durations and
 * curves: folding, the mini player slides sideways first and drops a beat later; unfolding, it
 * rises first and widens a beat later. One easing on the whole rect moves both on one clock and
 * reads as a different motion.
 */
internal class AxisStaggerSpec(
    private val x: AxisCurve,
    private val y: AxisCurve,
) : FiniteAnimationSpec<Rect> {

    override fun <V : AnimationVector> vectorize(
        converter: TwoWayConverter<Rect, V>,
    ): VectorizedFiniteAnimationSpec<V> = Vectorized(x, y)

    // Rect.VectorConverter is AnimationVector4D(left, top, right, bottom).
    @Suppress("UNCHECKED_CAST")
    private class Vectorized<V : AnimationVector>(
        private val x: AxisCurve,
        private val y: AxisCurve,
    ) : VectorizedFiniteAnimationSpec<V> {

        override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
            val from = initialValue as AnimationVector4D
            val to = targetValue as AnimationVector4D
            val ms = playTimeNanos / 1_000_000f
            val (l, r) = x.edges(ms, from.v1, from.v3, to.v1, to.v3)
            val (t, b) = y.edges(ms, from.v2, from.v4, to.v2, to.v4)
            return AnimationVector4D(l, t, r, b) as V
        }

        // Central difference: only read when a running animation is retargeted.
        override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
            val window = 2_000_000L
            val start = (playTimeNanos - window).coerceAtLeast(0L)
            val before = getValueFromNanos(start, initialValue, targetValue, initialVelocity) as AnimationVector4D
            val after = getValueFromNanos(playTimeNanos + window, initialValue, targetValue, initialVelocity) as AnimationVector4D
            val perSecond = 1_000_000_000f / (playTimeNanos + window - start)
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
 * The reference bar's choreography, number for number: the mini player's slide was fitted to an
 * iPhone recording, the tab pill and search circle bodies ride gentler springs, and everything is
 * centred on a curve that never overshoots, so only the bodies bounce, never the icons.
 */
internal object BarMotion {
    /** How long the fades and the gooey pulse take. */
    const val CollapseMs = 317
    const val ExpandMs = 251

    /** How long the surfaces take: they outlast the fades so their overshoot visibly settles. */
    const val CollapseMorphMs = 560
    const val ExpandMorphMs = 470

    /** Folding, the mini player holds its height this long before it drops into the row. */
    const val CollapseYDelayMs = 67

    fun barMs(collapsing: Boolean) = if (collapsing) CollapseMs else ExpandMs
    fun morphMs(collapsing: Boolean) = if (collapsing) CollapseMorphMs else ExpandMorphMs

    /** Centre travel for everything: spring-shaped, never overshoots. */
    val Travel = CriticalEasing()

    // Sizes ride the same curve as the centres. Bouncier size springs (zeta 0.34-0.62) grew each
    // body to its new size, and 25-30% past it, while its centre had barely left, so the search
    // circle and the player swelled fat in place before they moved. Grown on one clock, a body
    // changes shape and place together and lands without a bulge.
    private val CollapseSpring = Travel
    private val ExpandSpring = Travel
    private val BarSurfaceCollapseSpring = Travel
    private val BarSurfaceExpandSpring = Travel
    private val BarSurfaceExpandWidthSpring = Travel
    private val AccessoryCollapseXSpring = Travel
    private val AccessoryExpandXSpring = Travel

    val CollapseAccessory = AxisStaggerSpec(
        x = AxisCurve(0, 560, Travel, sizeEasing = AccessoryCollapseXSpring),
        y = AxisCurve(CollapseYDelayMs, 560, Travel, sizeEasing = CollapseSpring),
    )
    val ExpandAccessory = AxisStaggerSpec(
        x = AxisCurve(33, 450, Travel, sizeEasing = AccessoryExpandXSpring),
        y = AxisCurve(0, 470, Travel, sizeEasing = ExpandSpring),
    )

    private fun surface(durationMs: Int, sizeSpring: Easing, widthSpring: Easing = sizeSpring) = AxisStaggerSpec(
        x = AxisCurve(0, durationMs, Travel, sizeEasing = widthSpring),
        y = AxisCurve(0, durationMs, Travel, sizeEasing = sizeSpring),
    )

    val CollapseSurface = surface(CollapseMorphMs, BarSurfaceCollapseSpring)
    val ExpandSurface = surface(ExpandMorphMs, BarSurfaceExpandSpring, BarSurfaceExpandWidthSpring)

    /**
     * Folding with nothing playing: the whole bar gathers into two circles in the middle, a far
     * bigger change of shape than stepping aside for the player, so it is given longer and a
     * softer start — at the player fold's 560 ms it read as a snap.
     */
    const val CollapseEmptyMs = 760
    private val EmptyTravel = CriticalEasing(omegaT = 6.2f)
    val CollapseEmptySurface = AxisStaggerSpec(
        x = AxisCurve(0, CollapseEmptyMs, EmptyTravel),
        y = AxisCurve(0, CollapseEmptyMs, EmptyTravel),
    )
    val CollapseEmptyItem = tween<Rect>(CollapseEmptyMs, easing = EmptyTravel)

    /** Items riding the morph (the travelling page glyph): no overshoot at all. */
    val CollapseItem = tween<Rect>(CollapseMorphMs, easing = Travel)
    val ExpandItem = tween<Rect>(ExpandMorphMs, easing = Travel)
}
