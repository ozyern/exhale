/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.together

/**
 * Where a guest should be in the song, and whether it is far enough off to seek.
 *
 * A room state carries the host's position and the moment it was true, stamped on the clock the
 * guest's pongs come from: the host phone's on a nearby session, the relay's online (the relay
 * re-stamps each state as it arrives). [TogetherClock] turns that moment into the guest's own time,
 * and the song has moved on by however long ago that was.
 */
internal object TogetherSync {
    /** Older than this and the stamp isn't trusted: the clocks haven't been measured right, or the relay doesn't re-stamp. */
    private const val MaxPlausibleAgeMs = 8_000L

    /** Behind by more than this while playing and the guest seeks; under it, a seek's own hiccup would be worse. */
    const val LanSeekThresholdMs = 350L
    const val OnlineSeekThresholdMs = 700L
    const val PausedSeekThresholdMs = 200L

    /** After a correcting seek, how long to let the player settle before judging it again. */
    const val SeekCooldownMs = 2_500L

    /** The most a learned seek lead may grow to; past it the stream is the problem, not the timing. */
    const val MaxSeekLeadMs = 1_500L

    /** How long ago, on this phone's clock, the host's position in [state] was true. */
    fun ageOfStateMs(
        sentAtServerMs: Long,
        nowLocalMs: Long,
        clock: TogetherClockSnapshot?,
        online: Boolean,
    ): Long {
        val oneWay = (clock?.estimatedRttMs ?: 0L) / 2L
        if (clock == null || !clock.hasSample || sentAtServerMs <= 0L) {
            // No measurement yet: assume the state is as old as one trip.
            return if (online) 250L else oneWay
        }
        val age = nowLocalMs - clock.toLocal(sentAtServerMs)
        if (age < -500L || age > MaxPlausibleAgeMs) return if (online) 250L + oneWay else oneWay
        // Online, the relay stamped the state when it arrived from the host; the host-to-relay leg
        // isn't measured, so this phone's own one-way time stands in for it.
        return age.coerceAtLeast(0L) + if (online) oneWay else 0L
    }

    fun targetPositionMs(positionMs: Long, isPlaying: Boolean, ageMs: Long): Long =
        if (isPlaying) (positionMs + ageMs).coerceAtLeast(0L) else positionMs.coerceAtLeast(0L)

    fun seekThresholdMs(isPlaying: Boolean, online: Boolean): Long = when {
        !isPlaying -> PausedSeekThresholdMs
        online -> OnlineSeekThresholdMs
        else -> LanSeekThresholdMs
    }

    /**
     * A seek lands late by however long the player takes to buffer again, so a guest that seeks to
     * the exact spot is still behind. The lead is learned: whatever is left after a seek settles is
     * added to the next one.
     */
    fun nextSeekLeadMs(currentLeadMs: Long, residualBehindMs: Long): Long =
        (currentLeadMs + residualBehindMs * 6 / 10).coerceIn(0L, MaxSeekLeadMs)
}
