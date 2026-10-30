/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Windows app's lyric motion, for a lazy list.
 *
 * On Windows every line has its own spring and walks to its new place on its own, the line above
 * the sung one first and each line below it 34ms later, so a line change runs down the page like a
 * waterfall. A lazy list can only scroll as one sheet, so here the list jumps to where it is going
 * and every line is drawn back where it was, then let go along that same spring and stagger.
 *
 * The old attempt at this caught the jump a frame late (the per-line offsets were set from an
 * effect), and that frame was a visible flicker. Here the jump and the offsets are written in the
 * same frame, and each line's offset is worked out in the draw phase from the frame clock, so there
 * is no frame where the list has moved and the lines have not.
 *
 * Shifts add up: a line change that lands while the last one is still settling starts from wherever
 * every line is, at the speed it is going, exactly as retargeted springs do on Windows.
 */
@Stable
class LyricsWaterfall(private val scope: CoroutineScope) {
    private class Shift(val delta: Float, val startNanos: Long, val focus: Int)

    private val shifts = ArrayList<Shift>()
    private var ticker: Job? = null

    /** The frame being drawn. Read only in the draw phase, so a tick redraws the lines and nothing else. */
    private var frameNanos by mutableLongStateOf(0L)

    /** The list was just scrolled by [delta] px to bring line [focus] to the anchor, on frame [now]. */
    fun push(delta: Float, focus: Int, now: Long) {
        if (delta == 0f) return
        shifts += Shift(delta, now, focus)
        frameNanos = now
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (shifts.isNotEmpty()) {
                    withFrameNanos { time ->
                        frameNanos = time
                        shifts.removeAll { time - it.startNanos > SettledNanos }
                    }
                }
            }
        }
    }

    /** How far line [index] still trails its place, in px. */
    fun offset(index: Int): Float {
        val now = frameNanos
        var trail = 0f
        for (shift in shifts) {
            val lagMs = (index - shift.focus + 1).coerceIn(0, MaxLagLines) * LagMs
            val seconds = (now - shift.startNanos) / 1_000_000_000f - lagMs / 1000f
            trail += shift.delta * (1f - stepResponse(seconds))
        }
        return trail
    }

    private companion object {
        const val LagMs = 34
        const val MaxLagLines = 12

        // Windows' line spring: spring(dampingRatio = 0.86f, stiffness = 120f).
        const val Damping = 0.86f
        val Omega = sqrt(120f)
        val OmegaD = Omega * sqrt(1f - Damping * Damping)
        val SineWeight = Damping / sqrt(1f - Damping * Damping)

        // The longest stagger plus the spring's settle, after which a shift has nothing left to move.
        const val SettledNanos = (MaxLagLines * LagMs + 900) * 1_000_000L

        /** How far a unit spring released at rest has travelled after [t] seconds. */
        fun stepResponse(t: Float): Float {
            if (t <= 0f) return 0f
            val decay = exp(-Damping * Omega * t)
            return 1f - decay * (cos(OmegaD * t) + SineWeight * sin(OmegaD * t))
        }
    }
}
