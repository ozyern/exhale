package com.ozyern.exhale.data.settings

import com.ozyern.exhale.playback.smart.TransitionPlan

/**
 * Automix overlap the planner lined up for the playing track, exposed to the UI so the seek
 * bar can preview the mix window. Bounds are real ms; a prior single 0..1 fraction truncated
 * to 0 in a Long.
 */
data class TransitionWindow(
    val plan: TransitionPlan,
    /** Where the planned overlap opens on the outgoing track, in ms. */
    val startMs: Long = 0,
    /** Where it closes, in ms. */
    val endMs: Long = 0,
    /** The outgoing track's duration, in ms. */
    val totalMs: Long = 0,
) {
    /**
     * The overlap as a span of the track in 0..1, or null when there is nothing sane to draw
     * (unknown duration, or a window that does not actually span anything).
     */
    val fractionRange: ClosedFloatingPointRange<Float>?
        get() {
            if (totalMs <= 0L || endMs <= startMs) return null
            val from = (startMs.toFloat() / totalMs).coerceIn(0f, 1f)
            val to = (endMs.toFloat() / totalMs).coerceIn(0f, 1f)
            return if (to > from) from..to else null
        }
}
