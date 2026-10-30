/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx (github.com/cosmictaserdev-creator/Convx), GPL-3.0: BarMotion from its
 * floating tab bar (2.0.4), the order the mini player, the tab pill and the search circle move in.
 */

package com.ozyern.exhale.ui.component.foldingdock.classic

import com.ozyern.exhale.ui.component.foldingdock.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * The bar's choreography, on real springs.
 *
 * It used to run every piece on a fixed-duration, critically damped curve. That curve covered
 * nearly half its distance in the first 90 ms and spent the rest creeping in, so on screen the dock
 * snapped into its new shape and then sat there settling — and because a fixed-duration curve has
 * no velocity to hand on, a fold reversed halfway stopped dead and set off again from rest.
 *
 * Springs keep their speed when they are retargeted, so a fold caught by an unfold turns round in
 * one smooth motion, and these are tuned to iOS's tab bar: about half a second of response, damped
 * just short of critical so a body lands with a breath of settle rather than a bounce.
 *
 * The order matters as much as the curves. Folding, the tab pill starts retracting at once and the
 * player only drops a beat later, into room the pill has already left; unfolding, the player lifts
 * clear first and the pill widens under it a beat later. Started together, the two pieces of glass
 * slid over each other for most of the motion.
 */
internal object BarMotion {
    /** Each body's response: ~0.5 s, settling with no visible overshoot. */
    private const val BodyDamping = 0.86f
    private const val BodyStiffness = 150f

    /** Nothing playing, the whole bar gathers into two circles: a bigger change, given longer. */
    private const val EmptyStiffness = 105f

    /** Folding, the player waits this long before it drops into the row. */
    const val CollapsePlayerDropDelayMs = 90L

    /** Unfolding, the bar waits this long for the player to lift before it widens. */
    const val ExpandBarDelayMs = 50L

    /** Unfolding, the player starts sliding out to full width this long after it starts rising. */
    const val ExpandPlayerWidenDelayMs = 40L

    fun surface(collapsing: Boolean, hasPlayer: Boolean): SpringSpec<Rect> =
        spring(
            dampingRatio = BodyDamping,
            stiffness = if (collapsing && !hasPlayer) EmptyStiffness else BodyStiffness,
            visibilityThreshold = Rect.VisibilityThreshold,
        )

    /** The travelling page glyph: no overshoot at all, so the icon never wobbles in its circle. */
    fun item(collapsing: Boolean, hasPlayer: Boolean): SpringSpec<Rect> =
        spring(
            dampingRatio = 1f,
            stiffness = if (collapsing && !hasPlayer) EmptyStiffness else BodyStiffness,
            visibilityThreshold = Rect.VisibilityThreshold,
        )

    /** One axis of the player (left/right or top/bottom, packed in an [Offset]). */
    val player: SpringSpec<Offset> =
        spring(dampingRatio = BodyDamping, stiffness = BodyStiffness, visibilityThreshold = Offset.VisibilityThreshold)

    /**
     * The contents. Leaving, they clear in the first quarter of the fold, while their plate is still
     * nearly full width — lingering, they were cut off by the retracting edge. Arriving, they wait
     * until the plate has opened far enough to hold them.
     */
    val contentOut: TweenSpec<Float> = tween(durationMillis = 130, easing = FastOutSlowInEasing)
    val contentIn: TweenSpec<Float> = tween(durationMillis = 220, delayMillis = 110, easing = LinearOutSlowInEasing)
}
