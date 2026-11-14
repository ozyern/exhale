/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.utils

import kotlin.math.abs

/**
 * Apple's momentum projection from *Designing Fluid Interfaces* (WWDC 2018).
 *
 * Don't snap from the release point — project where the gesture is *going*
 * (exponential decay, like scroll deceleration), then pick the snap point
 * nearest that projected endpoint. This is what makes a flick throw.
 *
 * Physics-textbook `v²/(2·decel)` is NOT what Apple ships — use this form.
 *
 * @param velocityPxPerSec release velocity, same units as [current] per second.
 * @param decelerationRate 0.998 normal scroll feel, 0.99 snappier.
 */
fun projectMomentum(velocityPxPerSec: Float, decelerationRate: Float = 0.998f): Float {
    if (velocityPxPerSec == 0f) return 0f
    return (velocityPxPerSec / 1000f) * decelerationRate / (1f - decelerationRate)
}

/** Projected resting position: current + decay tail of the release velocity. */
fun projectedEndpoint(
    current: Float,
    velocityPxPerSec: Float,
    decelerationRate: Float = 0.998f,
): Float = current + projectMomentum(velocityPxPerSec, decelerationRate)

/**
 * Snap target nearest the *projected* endpoint, not the release point.
 * Returns null for empty [snapPoints]. Ties resolve toward velocity direction.
 */
fun nearestProjectedSnap(
    current: Float,
    velocityPxPerSec: Float,
    snapPoints: List<Float>,
    decelerationRate: Float = 0.998f,
): Float? {
    if (snapPoints.isEmpty()) return null
    val projected = projectedEndpoint(current, velocityPxPerSec, decelerationRate)
    var best = snapPoints[0]
    var bestDist = abs(best - projected)
    for (i in 1..snapPoints.lastIndex) {
        val d = abs(snapPoints[i] - projected)
        // Strictly-less keeps the first (lower) on ties; nudge ties toward fling dir.
        if (d < bestDist || (d == bestDist && (snapPoints[i] - current) * velocityPxPerSec > (best - current) * velocityPxPerSec)) {
            best = snapPoints[i]
            bestDist = d
        }
    }
    return best
}
