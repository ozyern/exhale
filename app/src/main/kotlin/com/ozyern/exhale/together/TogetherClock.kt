/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.together

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

/**
 * [estimatedOffsetMs] is the server's clock minus this phone's: a server time `t` is `t - offset`
 * here. Until the first pong, [hasSample] is false and the offset means nothing — two phones'
 * uptimes are hours apart, so treating it as 0 puts the guest hours into the song.
 */
@Immutable
data class TogetherClockSnapshot(
    val estimatedOffsetMs: Long = 0L,
    val estimatedRttMs: Long = 0L,
    val hasSample: Boolean = false,
) {
    /** A time on the server's clock, on this phone's. */
    fun toLocal(serverElapsedMs: Long): Long = serverElapsedMs - estimatedOffsetMs
}

class TogetherClock {
    private var offsetMs: Double = 0.0
    private var rttMs: Double = 0.0
    private var samples = 0

    fun onPong(
        sentAtElapsedMs: Long,
        receivedAtElapsedMs: Long,
        serverElapsedMs: Long,
    ): TogetherClockSnapshot {
        val rtt = (receivedAtElapsedMs - sentAtElapsedMs).coerceAtLeast(0L)
        val mid = sentAtElapsedMs + rtt / 2L
        val newOffset = (serverElapsedMs - mid).toDouble()

        val rttAlpha = 0.15
        val offsetAlpha = if (newOffset.absoluteValue > 1500.0) 0.6 else 0.2

        rttMs = if (samples == 0) rtt.toDouble() else rttMs + (rtt - rttMs) * rttAlpha
        offsetMs = if (samples == 0) newOffset else offsetMs + (newOffset - offsetMs) * offsetAlpha
        samples++

        return snapshot()
    }

    fun snapshot(): TogetherClockSnapshot =
        TogetherClockSnapshot(
            estimatedOffsetMs = offsetMs.toLong(),
            estimatedRttMs = rttMs.toLong(),
            hasSample = samples > 0,
        )
}
