/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx (github.com/cosmictaserdev-creator/Convx), GPL-3.0: BarMotion, SpringEasing,
 * CriticalEasing, AxisCurve and AxisStaggerSpec from its floating tab bar (2.0.4-nightly), the
 * bouncy fold between the open bar (state A) and the folded row (state B).
 */

package com.ozyern.exhale.ui.component.foldingdock

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * An underdamped spring's step response squeezed into a fixed-duration [Easing].
 *
 * Every piece of the bar has to end on the same frame, which a real `spring()` can't promise.
 * This keeps a spring's shape — crosses the target early, overshoots by about
 * exp(-zeta*pi/sqrt(1-zeta^2)) and settles — on a clock. [omegaT] is natural frequency x duration;
 * at 7.8 the residual is under 0.6%, removed linearly so transform(1) is exactly 1.
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

/** Critically damped on the same clock: spring-shaped, front-loaded, never passes the target. */
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
 * One axis of a bounds animation: its delay, its length, and two curves. [easing] moves the
 * axis's centre and [sizeEasing] its extent, so the body can swell past its size and settle while
 * everything centred in it travels without overshooting.
 */
internal class AxisCurve(
    val delayMs: Int,
    val durationMs: Int,
    val easing: Easing,
    val sizeEasing: Easing = easing,
) {
    val endMs: Int get() = delayMs + durationMs

    private fun fraction(playMs: Float) = ((playMs - delayMs) / durationMs).coerceIn(0f, 1f)

    /** The (start, end) edges at [playMs]: centre on [easing], extent on [sizeEasing]. */
    fun edges(playMs: Float, fromStart: Float, fromEnd: Float, toStart: Float, toEnd: Float): Pair<Float, Float> {
        val f = fraction(playMs)
        val p = easing.transform(f)
        val sp = sizeEasing.transform(f)
        val fromCenter = (fromStart + fromEnd) / 2f
        val center = fromCenter + ((toStart + toEnd) / 2f - fromCenter) * p
        val fromSize = fromEnd - fromStart
        val half = (fromSize + ((toEnd - toStart) - fromSize) * sp) / 2f
        return (center - half) to (center + half)
    }
}

/** A [Rect] animated with its horizontal and vertical edges on separate delays and curves. */
internal class AxisStaggerSpec(
    private val x: AxisCurve,
    private val y: AxisCurve,
) : FiniteAnimationSpec<Rect> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Rect, V>): VectorizedFiniteAnimationSpec<V> =
        Vectorized4(x, y)

    // Rect.VectorConverter is AnimationVector4D(left, top, right, bottom).
    @Suppress("UNCHECKED_CAST")
    private class Vectorized4<V : AnimationVector>(private val x: AxisCurve, private val y: AxisCurve) : CurveVectorized<V>(maxOf(x.endMs, y.endMs)) {
        override fun valueAt(ms: Float, from: V, to: V): V {
            val a = from as AnimationVector4D
            val b = to as AnimationVector4D
            val (l, r) = x.edges(ms, a.v1, a.v3, b.v1, b.v3)
            val (t, btm) = y.edges(ms, a.v2, a.v4, b.v2, b.v4)
            return AnimationVector4D(l, t, r, btm) as V
        }

        override fun scaledDiff(after: V, before: V, k: Float): V {
            val a = after as AnimationVector4D
            val b = before as AnimationVector4D
            return AnimationVector4D((a.v1 - b.v1) * k, (a.v2 - b.v2) * k, (a.v3 - b.v3) * k, (a.v4 - b.v4) * k) as V
        }
    }
}

/** One axis packed as an [Offset] of (start edge, end edge), for a player that moves its axes separately. */
internal class AxisEdgesSpec(private val curve: AxisCurve) : FiniteAnimationSpec<Offset> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Offset, V>): VectorizedFiniteAnimationSpec<V> =
        Vectorized2(curve)

    @Suppress("UNCHECKED_CAST")
    private class Vectorized2<V : AnimationVector>(private val curve: AxisCurve) : CurveVectorized<V>(curve.endMs) {
        override fun valueAt(ms: Float, from: V, to: V): V {
            val a = from as AnimationVector2D
            val b = to as AnimationVector2D
            val (s, e) = curve.edges(ms, a.v1, a.v2, b.v1, b.v2)
            return AnimationVector2D(s, e) as V
        }

        override fun scaledDiff(after: V, before: V, k: Float): V {
            val a = after as AnimationVector2D
            val b = before as AnimationVector2D
            return AnimationVector2D((a.v1 - b.v1) * k, (a.v2 - b.v2) * k) as V
        }
    }
}

/** A fixed-length curve as a vectorized spec, its velocity read off by central difference. */
private abstract class CurveVectorized<V : AnimationVector>(private val endMs: Int) : VectorizedFiniteAnimationSpec<V> {
    abstract fun valueAt(ms: Float, from: V, to: V): V

    /** (after - before) * k, per component. */
    abstract fun scaledDiff(after: V, before: V, k: Float): V

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
        valueAt(playTimeNanos / 1_000_000f, initialValue, targetValue)

    // Only read when a running animation is retargeted, where a 2ms window is far below a frame.
    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
        val window = 2_000_000L
        val start = (playTimeNanos - window).coerceAtLeast(0L)
        val before = valueAt(start / 1_000_000f, initialValue, targetValue)
        val after = valueAt((playTimeNanos + window) / 1_000_000f, initialValue, targetValue)
        return scaledDiff(after, before, 1_000_000_000f / (playTimeNanos + window - start))
    }

    override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V): Long = endMs * 1_000_000L
}

/**
 * The bar's choreography, as Convx 2.0.4 tunes it: a clearly visible, slightly slow liquid-glass
 * overshoot. Folding the mini player slides sideways first and drops ~67ms later; unfolding it rises
 * first and widens ~33ms later. Only bodies overshoot — glyphs and centres travel on [Travel], which
 * never does — so the glass swells past its size and settles while the icons glide in.
 */
internal object BarMotion {
    const val CollapseMorphMs = 560
    const val ExpandMorphMs = 470

    /** FoldingTabBar's single fold: collapse lands with a deeper overshoot than the expand. */
    val FoldCollapseSpring = SpringEasing(zeta = 0.55f)
    val FoldExpandSpring = SpringEasing(zeta = 0.74f)

    /** The mini player's height: ~9% overshoot folding, ~11% unfolding. */
    private val CollapseSpring = SpringEasing(zeta = 0.62f)
    private val ExpandSpring = SpringEasing(zeta = 0.58f)

    /** The tab pill and search circle: gentler than the player. Folding it never undershoots its icon. */
    private val BarSurfaceCollapseSpring = CriticalEasing()
    private val BarSurfaceExpandSpring = SpringEasing(zeta = 0.74f)

    /** The pill's width when the bar opens: bouncier than its height. */
    private val BarSurfaceExpandWidthSpring = SpringEasing(zeta = 0.40f)

    /** The player's width gets its own, bouncier spring than the bar's. */
    private val AccessoryCollapseXSpring = SpringEasing(zeta = 0.56f, omegaT = 9f)
    private val AccessoryExpandXSpring = SpringEasing(zeta = 0.34f, omegaT = 9f)

    /** Centre travel for everything: spring-shaped, never overshoots. */
    private val Travel = CriticalEasing()

    private const val CollapseYDelayMs = 67
    private const val ExpandXDelayMs = 33

    fun surface(collapsing: Boolean): AxisStaggerSpec =
        if (collapsing) {
            AxisStaggerSpec(
                x = AxisCurve(0, CollapseMorphMs, Travel, sizeEasing = BarSurfaceCollapseSpring),
                y = AxisCurve(0, CollapseMorphMs, Travel, sizeEasing = BarSurfaceCollapseSpring),
            )
        } else {
            AxisStaggerSpec(
                x = AxisCurve(0, ExpandMorphMs, Travel, sizeEasing = BarSurfaceExpandWidthSpring),
                y = AxisCurve(0, ExpandMorphMs, Travel, sizeEasing = BarSurfaceExpandSpring),
            )
        }

    /** The travelling page glyph: centre and size on [Travel], so it never wobbles in its circle. */
    fun item(collapsing: Boolean): AxisStaggerSpec {
        val ms = if (collapsing) CollapseMorphMs else ExpandMorphMs
        return AxisStaggerSpec(AxisCurve(0, ms, Travel), AxisCurve(0, ms, Travel))
    }

    /** The player's left/right edges. */
    fun playerX(collapsing: Boolean): AxisEdgesSpec = AxisEdgesSpec(
        if (collapsing) AxisCurve(0, 560, Travel, sizeEasing = AccessoryCollapseXSpring)
        else AxisCurve(ExpandXDelayMs, 450, Travel, sizeEasing = AccessoryExpandXSpring),
    )

    /** The player's top/bottom edges. */
    fun playerY(collapsing: Boolean): AxisEdgesSpec = AxisEdgesSpec(
        if (collapsing) AxisCurve(CollapseYDelayMs, 560, Travel, sizeEasing = CollapseSpring)
        else AxisCurve(0, 470, Travel, sizeEasing = ExpandSpring),
    )

    /**
     * The contents. Leaving, they clear while their plate is still nearly full width; arriving,
     * they wait until it has opened far enough to hold them.
     */
    val contentOut: TweenSpec<Float> = tween(durationMillis = 130, easing = FastOutSlowInEasing)
    val contentIn: TweenSpec<Float> = tween(durationMillis = 220, delayMillis = 110, easing = LinearOutSlowInEasing)
}
