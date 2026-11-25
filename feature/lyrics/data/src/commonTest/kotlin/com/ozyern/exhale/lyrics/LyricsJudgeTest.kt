package com.ozyern.exhale.lyrics

import com.ozyern.exhale.lyrics.providers.LyricLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LyricsJudgeTest {
    private fun timed(vararg text: String) = text.mapIndexed { i, line -> LyricLine((i + 1) * 4_000L, line) }

    private val taste = timed(
        "I leave my lipstick on your coffee cup",
        "You got a fake smile but I got the real stuff",
        "Every time you kiss him you'll get a taste of me",
        "Taste, taste, you'll get a taste of me",
    )
    private val onMyWay = timed(
        "I'm on my way, I'm on my way",
        "Driving through the night and the city lights",
        "Nothing gonna stop me now",
        "On my way back home to you",
    )

    @Test fun aLooseSourceMustSingTheTitle() {
        assertNotNull(LyricsSearch.judge(taste, "Taste", loose = true))
        assertNull(LyricsSearch.judge(onMyWay, "Taste", loose = true))
        // Asked by name and artist, a source is trusted with a song that never says its name.
        assertNotNull(LyricsSearch.judge(onMyWay, "Taste", loose = false))
    }

    @Test fun creditsAndTheTitleHeadingAreNotSungLines() {
        val withPaperwork = timed("Taste - Sabrina Carpenter", "Composer: Amy Allen") + taste
            .map { it.copy(timeMs = it.timeMs + 8_000L) }
        assertEquals(taste.map { it.text }, LyricsSearch.judge(withPaperwork, "Taste", loose = false)?.map { it.text })
    }

    @Test fun paperworkAloneIsNotLyrics() {
        assertNull(LyricsSearch.judge(timed("Publisher: Warner Chappell", "Songwriter: Amy Allen", "Sabrina Carpenter："), "Taste", loose = false))
    }
}
