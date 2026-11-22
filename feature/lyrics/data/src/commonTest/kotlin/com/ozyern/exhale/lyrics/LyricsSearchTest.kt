package com.ozyern.exhale.lyrics

import com.ozyern.exhale.lyrics.providers.LyricLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LyricsSearchTest {
    @Test fun barSeparatedUploadsSearchTheSongAndItsSinger() {
        assertEquals(
            "Re Bawri" to "Alia Bhatt",
            LyricsSearch.query("Re Bawri (Song) | Alia Bhatt | Don't Be Shy | Sona Mohapatra | T-Series", "T-Series"),
        )
    }

    @Test fun channelBeforeTheDashIsTheArtist() {
        assertEquals("Without Me" to "Eminem", LyricsSearch.query("Eminem - Without Me (Official Music Video)", "EminemVEVO"))
    }

    @Test fun uploadPackagingIsDropped() {
        assertEquals("Kesariya", LyricsSearch.query("Kesariya – Full Video", "Arijit Singh").first)
        assertEquals("Yesterday", LyricsSearch.query("Yesterday - 2009 Remaster", "The Beatles").first)
        assertEquals("Espresso", LyricsSearch.query("Espresso [Explicit]", "Sabrina Carpenter").first)
        assertEquals("Shakira", LyricsSearch.query("Hips Don't Lie", "Shakira - Topic").second)
    }

    @Test fun otherRecordingsKeepTheirName() {
        assertEquals("Levitating (Remix)", LyricsSearch.query("Levitating (Remix)", "Dua Lipa").first)
    }

    @Test fun anOrdinaryTitleIsLeftAlone() {
        assertEquals("Espresso" to "Sabrina Carpenter", LyricsSearch.query("Espresso", "Sabrina Carpenter"))
    }

    @Test fun phrasingsTryTheSingerThenTheTitleAsWritten() {
        val upload = "Re Bawri (Song) | Alia Bhatt | Sona Mohapatra | T-Series"
        assertEquals(
            listOf("Re Bawri" to "Alia Bhatt", upload to "T-Series"),
            LyricsSearch.phrasings(upload, "T-Series"),
        )
    }

    @Test fun aPlainTitleIsAskedOnce() {
        assertEquals(listOf("Espresso" to "Sabrina Carpenter"), LyricsSearch.phrasings("Espresso", "Sabrina Carpenter"))
    }

    private fun timed(vararg seconds: Int) = seconds.map { LyricLine(it * 1000L, "line $it") }

    @Test fun timingsFromAnotherCutAreTurnedAway() {
        val song = 200_000L
        assertTrue(LyricsSearch.fits(timed(10, 50, 100, 180), song))
        assertFalse(LyricsSearch.fits(timed(10, 50, 100, 260), song), "runs past the end")
        assertFalse(LyricsSearch.fits(timed(150, 160, 170, 180), song), "starts most of the way in")
        assertFalse(LyricsSearch.fits(timed(5, 20, 40, 60), song), "stops far short")
        assertFalse(LyricsSearch.fits(timed(10, 50, 100, 130), song, title = "Espresso (Live)"), "live takes need more coverage")
    }

    @Test fun untimedOrUnknownAlwaysPass() {
        assertTrue(LyricsSearch.fits(listOf("a", "b", "c", "d").map { LyricLine(0, it) }, 200_000L))
        assertTrue(LyricsSearch.fits(timed(10, 50, 100, 400), 0L))
    }
}
