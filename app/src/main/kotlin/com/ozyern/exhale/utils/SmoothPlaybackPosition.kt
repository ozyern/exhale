/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.os.SystemClock
import kotlin.math.abs

/**
 * The playback position for something that draws every frame — the lyric sweep.
 *
 * Since Media3 1.11 the player schedules its own work dynamically: while the audio buffer is full
 * its thread sleeps, and `currentPosition` only moves when it wakes — about once a second, in one
 * 1000ms jump. That is good for the battery and fatal for a highlight that is meant to run through
 * a word letter by letter: it sat still, then leapt a second. (1.0.405 was on Media3 1.10, where the
 * position moved every few milliseconds, which is why its lyrics flowed.)
 *
 * So the position is carried forward on the clock between readings and steered by them. A reading
 * a little ahead of or behind the estimate is absorbed rather than shown, so the sweep never steps
 * back; one far off — a seek, a skip — is taken as is.
 */
class SmoothPlaybackPosition {
    private var anchorPos = 0L
    private var anchorAt = 0L
    private var lastRaw = Long.MIN_VALUE
    private var shown = 0L

    fun read(raw: Long, playing: Boolean, speed: Float = 1f): Long {
        val now = SystemClock.uptimeMillis()
        if (!playing) {
            reset(raw, now)
            return raw
        }
        if (raw != lastRaw) {
            val first = lastRaw == Long.MIN_VALUE
            val estimate = estimateAt(now, speed)
            lastRaw = raw
            if (first || abs(raw - estimate) > JUMP_MS) {
                // A seek, a skip, or the first reading: the song really is somewhere else.
                reset(raw, now)
                return raw
            }
            // On course: re-anchor between the estimate and the reading, so the gap closes over
            // the next frames instead of in one.
            anchorPos = estimate + (raw - estimate) / 2
            anchorAt = now
        }
        val next = estimateAt(now, speed)
        // Forward only, and never more than a little ahead of what the player has confirmed.
        shown = next.coerceAtLeast(shown).coerceAtMost(lastRaw + MAX_AHEAD_MS)
        return shown
    }

    private fun estimateAt(now: Long, speed: Float): Long =
        anchorPos + ((now - anchorAt) * speed).toLong()

    private fun reset(raw: Long, now: Long) {
        anchorPos = raw
        anchorAt = now
        lastRaw = raw
        shown = raw
    }

    private companion object {
        /** Farther than this from the estimate is a jump, not a late reading. */
        const val JUMP_MS = 1_500L

        /** How far the estimate may run past the last confirmed reading before it waits for one. */
        const val MAX_AHEAD_MS = 1_400L
    }
}
