/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActiveLyricRowsTest {
    private fun line(startMs: Long, endMs: Long, text: String = "la") =
        LyricLine(timeMs = startMs, text = text, words = listOf(LyricWord(startMs, endMs, text)))

    // A held line running under the next two, a gap, and a line with no stated end.
    private val lines = listOf(
        line(1_000, 6_000),
        line(2_000, 3_000),
        line(4_000, 5_000),
        LyricLine(timeMs = 7_000, text = ""),
        LyricLine(timeMs = 9_000, text = "no end"),
        line(12_000, 13_000),
    )

    /** Frame by frame it says what the plain function says. */
    @Test
    fun agreesWithTheFunctionItReplaces() {
        val rows = ActiveLyricRows(lines)
        for (positionMs in 0L..14_000L step 16L) {
            assertEquals("at $positionMs", activeLyricRows(lines, positionMs), rows.at(positionMs))
        }
        // And asked out of order, as after a seek back.
        for (positionMs in longArrayOf(12_500, 2_500, 0, 4_500, 9_500, 2_500)) {
            assertEquals("at $positionMs", activeLyricRows(lines, positionMs), rows.at(positionMs))
        }
    }

    /** The same answer is the same list, so nothing reading it is told it changed. */
    @Test
    fun handsBackTheSameListUntilItChanges() {
        val rows = ActiveLyricRows(lines)
        val first = rows.at(2_100)
        assertSame(first, rows.at(2_500))
        assertEquals(listOf(0, 1), first)
        assertEquals(listOf(0, 2), rows.at(4_500))
    }

    @Test
    fun latestRow() {
        assertEquals(-1, latestLyricRow(lines, 500))
        assertEquals(0, latestLyricRow(lines, 1_000))
        assertEquals(3, latestLyricRow(lines, 8_000))
        assertEquals(5, latestLyricRow(lines, 60_000))
        assertEquals(-1, latestLyricRow(emptyList(), 60_000))
    }
}
