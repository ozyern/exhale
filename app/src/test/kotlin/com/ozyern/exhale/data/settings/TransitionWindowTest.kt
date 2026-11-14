package com.ozyern.exhale.data.settings

import com.ozyern.exhale.playback.smart.TransitionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The seek bar's Automix marker is drawn straight from [TransitionWindow.fractionRange], and
 * the field it replaced (`positionMs = transitionStart * 1000 / duration`) truncated a 0..1
 * fraction into a Long, so it was 0 for every track. Nothing read it, so nothing caught it.
 */
class TransitionWindowTest {

    private fun window(startMs: Long, endMs: Long, totalMs: Long) =
        TransitionWindow(plan = TransitionPlan(), startMs = startMs, endMs = endMs, totalMs = totalMs)

    @Test
    fun `the overlap maps onto the bar as a real span`() {
        val range = window(startMs = 150_000, endMs = 165_000, totalMs = 200_000).fractionRange
        requireNotNull(range)
        assertEquals(0.75f, range.start, 0.0001f)
        assertEquals(0.825f, range.endInclusive, 0.0001f)
    }

    @Test
    fun `an unknown duration draws nothing`() {
        assertNull(window(startMs = 150_000, endMs = 165_000, totalMs = 0).fractionRange)
    }

    @Test
    fun `an empty or inverted window draws nothing`() {
        assertNull(window(startMs = 150_000, endMs = 150_000, totalMs = 200_000).fractionRange)
        assertNull(window(startMs = 165_000, endMs = 150_000, totalMs = 200_000).fractionRange)
    }

    @Test
    fun `a window running past the end is clamped to the bar`() {
        val range = window(startMs = 190_000, endMs = 260_000, totalMs = 200_000).fractionRange
        requireNotNull(range)
        assertEquals(0.95f, range.start, 0.0001f)
        assertEquals(1f, range.endInclusive, 0.0001f)
    }
}
