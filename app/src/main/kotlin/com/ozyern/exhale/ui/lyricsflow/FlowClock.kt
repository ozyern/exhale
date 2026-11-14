/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The playhead and the per-frame lyric clock, taken from Exhale master's FlowLyrics.kt so the lyric
 * styles ported from master (Melody, BitChord) run off the same clock they do there.
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import kotlin.math.abs

private const val LYRIC_VISUAL_LEAD_MS = 120L

internal fun adjustedLyricsPosition(positionMs: Long, offsetMs: Int): Long =
    (positionMs - offsetMs.toLong()).coerceAtLeast(0L)

internal fun adjustedLyricsSeekTarget(lineTimeMs: Long, offsetMs: Int): Long =
    (lineTimeMs + offsetMs.toLong()).coerceAtLeast(0L)

/**
 * What the lyrics read the playhead through: the player's latest reading,
 * shifted by the lyrics offset, together with when it was taken and whether the
 * player has jumped since — see [LyricClock] for why all three are needed.
 *
 * Read only from the frame loop and from effects, never in composition. Read in
 * composition, every reading recomposed the whole lyric panel twice a second;
 * and a value captured there and a stamp read later in the frame could belong
 * to two different readings, which is the very mismatch this exists to stop.
 */
@Stable
internal class LyricPlayhead(
    private val position: PlaybackPosition,
    private val offsetMs: State<Int>,
) {
    val positionMs: Long get() = adjustedLyricsPosition(position.positionMs, offsetMs.value) + LYRIC_VISUAL_LEAD_MS

    val sampledAtNanos: Long get() = position.sampledAtNanos

    /**
     * Changes whenever the lyrics are meant to jump: the player seeking, or the
     * reader moving the lyrics offset — which shifts every reading at once, and
     * without saying so would look like a reading gone wrong.
     */
    val discontinuity: Long
        get() = (position.seeks.toLong() shl 32) or (offsetMs.value.toLong() and 0xFFFF_FFFFL)
}

@Composable
internal fun rememberLyricPlayhead(position: PlaybackPosition): LyricPlayhead {
    val offset = PlayerSettings.lyricsOffsetMs.collectAsState()
    return remember(position, offset) { LyricPlayhead(position, offset) }
}

private fun nanosToMs(nanos: Long): Double = nanos / 1_000_000.0

/**
 * The song position, ticking every frame.
 *
 * The player reports where it is about twice a second, which is fine for a
 * scrubber and far too coarse for a highlight that has to keep up with a
 * singer. This runs a clock on the frames in between and lets the readings
 * steer it — see [LyricClock] for why steering, rather than setting, is the
 * difference between a sweep that glides and one that stutters or stalls.
 *
 * Returned as state rather than a plain value on purpose: read inside a draw
 * lambda, only the draw phase re-runs each frame. Read in composition, the
 * whole line would recompose sixty times a second.
 */
@Composable
internal fun rememberLyricClock(
    trackKey: Any,
    playhead: LyricPlayhead,
    isPlaying: Boolean,
): MutableLongState {
    val clock = remember(trackKey) {
        mutableLongStateOf(Snapshot.withoutReadObservation { playhead.positionMs })
    }
    val engine = remember(trackKey) { LyricClock(clock.longValue) }

    // Not moving: each reading is the truth, including a seek made while paused.
    // Followed off the snapshot rather than read here, so a reading recomposes
    // nothing.
    if (!isPlaying) {
        LaunchedEffect(engine, playhead) {
            snapshotFlow { playhead.positionMs }.collect { positionMs ->
                engine.hold(positionMs, nanosToMs(System.nanoTime()))
                clock.longValue = positionMs
            }
        }
    }

    // Gated on the app being on screen. The loop asks for a frame, writes a
    // value that invalidates a drawing, and is handed the next frame for it —
    // which is a request to render continuously for as long as it runs. That is
    // the right trade for a lyric being read and the wrong one for a phone in a
    // pocket, and the composition alone cannot tell the two apart.
    //
    // Every start is a fresh run from the latest reading, so neither a pause
    // nor a spell in the background is mistaken for one very long frame.
    //
    // One loop for the whole run, reading the playhead itself. Keyed on the
    // reading instead, every reading restarted it, and each restart spent a
    // frame waiting for its first frame time — a hitch twice a second.
    val foreground = rememberIsForeground()
    LaunchedEffect(engine, playhead, isPlaying, foreground) {
        if (!isPlaying || !foreground) return@LaunchedEffect
        engine.restart()
        while (true) {
            withFrameNanos { frameNanos ->
                // Readings are stamped on the System.nanoTime clock. The frame
                // time is that clock too wherever it is a vsync stamp, and is
                // preferred for being evenly spaced; anywhere it is not, the
                // two are seconds apart and the real clock is used instead.
                val system = System.nanoTime()
                val now = if (abs(system - frameNanos) < SAME_CLOCK_NANOS) frameNanos else system
                val sampledAt = playhead.sampledAtNanos
                clock.longValue = engine.frame(
                    nowMs = nanosToMs(now),
                    reportedMs = playhead.positionMs,
                    sampledAtMs = if (sampledAt > 0L) nanosToMs(sampledAt) else Double.NaN,
                    discontinuity = playhead.discontinuity,
                )
            }
        }
    }
    return clock
}

/** A frame time this close to [System.nanoTime] is on the same clock. */
private const val SAME_CLOCK_NANOS = 250_000_000L
