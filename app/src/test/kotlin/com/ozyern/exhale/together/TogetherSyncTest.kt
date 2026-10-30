/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherSyncTest {
    /** Host up for 5 hours, guest for 1: their clocks are 4 hours apart. */
    private val hostAhead = 4 * 3_600_000L

    private fun measuredClock(offsetMs: Long, rttMs: Long = 40L): TogetherClockSnapshot {
        val clock = TogetherClock()
        val sent = 1_000_000L
        return clock.onPong(sentAtElapsedMs = sent, receivedAtElapsedMs = sent + rttMs, serverElapsedMs = sent + rttMs / 2 + offsetMs)
    }

    @Test
    fun clock_has_no_sample_until_the_first_pong() {
        assertFalse(TogetherClock().snapshot().hasSample)
        assertTrue(measuredClock(0L).hasSample)
    }

    @Test
    fun host_stamp_converts_to_guest_time_in_either_direction() {
        // Guest up for 6 hours, so the host's stamp is positive either way.
        val now = 6 * 3_600_000L
        for (offset in listOf(hostAhead, -hostAhead)) {
            val clock = measuredClock(offset)
            // Stamped 300ms ago, on the host's clock.
            val sentAtHost = now - 300L + offset
            val age = TogetherSync.ageOfStateMs(sentAtHost, now, clock, online = false)
            assertEquals("offset $offset", 300.0, age.toDouble(), 2.0)
        }
    }

    @Test
    fun unmeasured_clock_never_jumps_hours_into_the_song() {
        val age = TogetherSync.ageOfStateMs(sentAtServerMs = 5 * 3_600_000L, nowLocalMs = 3_600_000L, clock = TogetherClock().snapshot(), online = false)
        assertTrue(age < 1_000L)
        val ageOnline = TogetherSync.ageOfStateMs(sentAtServerMs = 1L, nowLocalMs = 9 * 3_600_000L, clock = null, online = true)
        assertTrue(ageOnline < 1_000L)
    }

    @Test
    fun implausible_age_falls_back_to_one_trip() {
        val clock = measuredClock(0L, rttMs = 100L)
        val age = TogetherSync.ageOfStateMs(sentAtServerMs = 1_000L, nowLocalMs = 60_000L, clock = clock, online = false)
        assertEquals(50L, age)
    }

    @Test
    fun online_adds_the_unmeasured_host_to_relay_leg() {
        val now = 2_000_000L
        val clock = measuredClock(offsetMs = 10_000L, rttMs = 120L)
        val age = TogetherSync.ageOfStateMs(now - 200L + 10_000L, now, clock, online = true)
        assertEquals(260.0, age.toDouble(), 2.0)
    }

    @Test
    fun paused_target_does_not_advance() {
        assertEquals(5_000L, TogetherSync.targetPositionMs(5_000L, isPlaying = false, ageMs = 900L))
        assertEquals(5_900L, TogetherSync.targetPositionMs(5_000L, isPlaying = true, ageMs = 900L))
    }

    @Test
    fun seek_lead_learns_and_stays_bounded() {
        assertEquals(300L, TogetherSync.nextSeekLeadMs(0L, residualBehindMs = 500L))
        // Landing ahead pulls the lead back down, never below zero.
        assertEquals(0L, TogetherSync.nextSeekLeadMs(100L, residualBehindMs = -1_000L))
        assertEquals(TogetherSync.MaxSeekLeadMs, TogetherSync.nextSeekLeadMs(1_400L, residualBehindMs = 2_000L))
    }
}
