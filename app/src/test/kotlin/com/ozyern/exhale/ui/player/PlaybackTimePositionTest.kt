package com.ozyern.exhale.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimePositionTest {
    @Test
    fun unknownDurationDoesNotOverflowIntoADaysLongCounter() {
        assertEquals(0L, remainingPlaybackSeconds(Long.MIN_VALUE + 1, 60_000))
        assertEquals(0L, remainingPlaybackSeconds(-1, 60_000))
        assertEquals(0L, remainingPlaybackSeconds(0, 60_000))
    }

    @Test
    fun scrubbingBothDirectionsChangesElapsedAndRemainingImmediately() {
        assertEquals(30L, elapsedPlaybackSeconds(30_500))
        assertEquals(89L, remainingPlaybackSeconds(120_000, 30_500))
        assertEquals(10L, elapsedPlaybackSeconds(10_000))
        assertEquals(110L, remainingPlaybackSeconds(120_000, 10_000))
    }

    @Test
    fun clampsPositionsOutsideTheTimeline() {
        assertEquals(0L, elapsedPlaybackSeconds(-1))
        assertEquals(120L, remainingPlaybackSeconds(120_000, -1))
        assertEquals(0L, remainingPlaybackSeconds(120_000, Long.MAX_VALUE))
        assertEquals(Long.MAX_VALUE / 1000, remainingPlaybackSeconds(Long.MAX_VALUE, 0))
    }
}
