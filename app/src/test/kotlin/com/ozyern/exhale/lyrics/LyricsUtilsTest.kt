package com.ozyern.exhale.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun parsesRichSyncTextAndWordTimings() {
        val result = LyricsUtils.parseLyrics(
            "[00:01.00]<00:01.00>Hello <00:02.00>world",
        )

        assertEquals(1, result.size)
        assertEquals(1_000L, result.single().time)
        assertEquals("Hello world", result.single().text)
        assertEquals(
            listOf(
                WordTimestamp("Hello", startTime = 1.0, endTime = 2.0),
                WordTimestamp("world", startTime = 2.0, endTime = 2.5),
            ),
            result.single().words,
        )
    }

    @Test
    fun attachesStandardWordTimingsToThePreviousLyricLine() {
        val result = LyricsUtils.parseLyrics(
            "[00:01.00]Hello\n<Hello:1.0:2.0>",
        )

        assertEquals(1, result.size)
        assertEquals("Hello", result.single().text)
        assertEquals(
            listOf(WordTimestamp("Hello", startTime = 1.0, endTime = 2.0)),
            result.single().words,
        )
    }
}
