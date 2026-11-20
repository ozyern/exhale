package com.ozyern.exhale.playback

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OplusLiveLyricsTest {
    @Test
    fun lyricLaneDropsSingerTagsAndBackgroundRows() {
        val lyrics = "[00:01.00]{agent:v1}Hello\n[00:02.00]{bg}(ooh)\n[00:03.00]Bye"

        assertEquals("[00:01.00]Hello\n[00:03.00]Bye\n", OplusLiveLyrics.toLrc(lyrics))
    }

    @Test
    fun unsyncedTextPublishesNothing() {
        assertNull(OplusLiveLyrics.toLrc("Hello\nworld"))
        assertNull(OplusLiveLyrics.toEnhancedLrc("Hello\nworld"))
    }

    @Test
    fun karaokeLaneKeepsTheSpacingBetweenWordsAndClosesOnTheLastWord() {
        val lyrics = "[00:01.00]Hello world\n<Hello:1.0:1.4|world:1.5:2.0>"

        assertEquals(
            "[00:01.000]<00:01.000>Hello <00:01.500>world<00:02.000>\n",
            OplusLiveLyrics.toEnhancedLrc(lyrics),
        )
    }

    @Test
    fun karaokeWordTimesNeverMoveBackward() {
        val lyrics = "[00:02.00]a b\n<a:2.0:2.2|b:1.5:2.5>"

        assertEquals(
            "[00:02.000]<00:02.000>a <00:02.000>b<00:02.500>\n",
            OplusLiveLyrics.toEnhancedLrc(lyrics),
        )
    }

    @Test
    fun karaokeLaneIsAllOrNothing() {
        val lyrics = "[00:01.00]Hello\n<Hello:1.0:1.4>\n[00:02.00]Bye"

        assertNull(OplusLiveLyrics.toEnhancedLrc(lyrics))
    }

    @Test
    fun karaokeLaneLeavesBackgroundVocalsOut() {
        val lyrics = "[00:01.00]Hi\n<Hi:1.0:1.4>\n[00:01.20]{bg}ooh"

        assertEquals("[00:01.000]<00:01.000>Hi<00:01.400>\n", OplusLiveLyrics.toEnhancedLrc(lyrics))
    }

    @Test
    fun timestampsStayAsciiUnderLocalesWithTheirOwnDigits() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val lyrics = "[01:02.50]Hi\n<Hi:62.5:63.0>"

            assertEquals("[01:02.500]<01:02.500>Hi<01:03.000>\n", OplusLiveLyrics.toEnhancedLrc(lyrics))
        } finally {
            Locale.setDefault(original)
        }
    }
}
