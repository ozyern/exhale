/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins how picked lines become the stretches of song a share video plays. */
class ShareTimelineTest {

    @Test
    fun `lines that run on from each other play as one take`() {
        val timeline = shareTimeline(
            listOf(ShareLine("a", 10_000, 12_000), ShareLine("b", 12_000, 15_000)),
        )!!
        assertEquals(listOf(10_000L to 15_000L), timeline.segments)
        assertEquals(5_000L, timeline.durationMs)
        assertEquals(0, timeline.lineAt(1_000_000))
        assertEquals(1, timeline.lineAt(3_000_000))
    }

    @Test
    fun `a gap between picked lines cuts straight on`() {
        val timeline = shareTimeline(
            listOf(ShareLine("a", 10_000, 12_000), ShareLine("b", 40_000, 43_000)),
        )!!
        assertEquals(listOf(10_000L to 12_000L, 40_000L to 43_000L), timeline.segments)
        assertEquals(1, timeline.lineAt(2_500_000))
    }

    @Test
    fun `unsynced lines have no timeline`() {
        assertNull(shareTimeline(listOf(ShareLine("a", null, null))))
    }

    @Test
    fun `a video stops at a minute`() {
        val timeline = shareTimeline(listOf(ShareLine("long", 0, 120_000)))!!
        assertEquals(60_000L, timeline.durationMs)
    }
}
