package com.ozyern.exhale.ui.component

import com.ozyern.exhale.lyrics.LyricsEntry
import com.ozyern.exhale.lyrics.WordTimestamp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the conversion from Exhale's own lyrics model into the karaoke renderer's.
 * Everything the Apple-style view can show that the plain styles cannot — duet sides,
 * background vocals, per-syllable lighting — is decided here rather than in the library,
 * so this is where those decisions have to be pinned.
 */
class LyricsKaraokeViewTest {

    private fun word(text: String, start: Double, end: Double) = WordTimestamp(text, start, end)

    private fun sung(
        time: Long,
        text: String,
        words: List<WordTimestamp>?,
        agent: String? = null,
        background: Boolean = false,
    ) = LyricsEntry(
        time = time,
        text = text,
        words = words,
        agent = agent,
        isBackground = background,
    )

    @Test
    fun `the second singer is thrown to the other side`() {
        val lines = listOf(
            sung(0, "one", listOf(word("one", 0.0, 1.0)), agent = "v1"),
            sung(1000, "two", listOf(word("two", 1.0, 2.0)), agent = "v2"),
            sung(2000, "three", listOf(word("three", 2.0, 3.0)), agent = null),
        ).toSyncedLyrics().lines.filterIsInstance<KaraokeLine.MainKaraokeLine>()

        assertEquals(3, lines.size)
        assertEquals(KaraokeAlignment.Start, lines[0].alignment)
        assertEquals(KaraokeAlignment.End, lines[1].alignment)
        // No agent at all is one singer, which is the left-hand side.
        assertEquals(KaraokeAlignment.Start, lines[2].alignment)
    }

    @Test
    fun `background vocals ride on the line above instead of taking a row`() {
        val lines = listOf(
            sung(0, "main", listOf(word("main", 0.0, 2.0))),
            sung(500, "ooh", listOf(word("ooh", 0.5, 1.5)), background = true),
        ).toSyncedLyrics().lines

        assertEquals(1, lines.size)
        val main = lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(1, main.accompanimentLines?.size)
        assertEquals(500, main.accompanimentLines?.first()?.start)
    }

    @Test
    fun `a background line with nothing above it still gets shown`() {
        // Dropping it silently is the failure this guards: the listener sees a gap where
        // the record is clearly singing.
        val lines = listOf(
            sung(0, "ooh", listOf(word("ooh", 0.0, 1.0)), background = true),
        ).toSyncedLyrics().lines

        assertEquals(1, lines.size)
    }

    @Test
    fun `an overrunning syllable is cut at the next one`() {
        // Providers routinely publish an end past the next word's start. Left alone the
        // line lights in two places at once.
        val main = listOf(
            sung(0, "a b", listOf(word("a", 0.0, 5.0), word("b", 1.0, 2.0))),
        ).toSyncedLyrics().lines.single() as KaraokeLine.MainKaraokeLine

        assertEquals(1000, main.syllables[0].end)
        assertEquals(1000, main.syllables[1].start)
    }

    @Test
    fun `a word-less line is capped instead of holding through an instrumental`() {
        val lines = listOf(
            sung(0, "last line before the break", words = null),
            sung(90_000, "after the break", words = null),
        ).toSyncedLyrics().lines

        val first = lines.first() as SyncedLine
        assertEquals(4_000, first.end)
    }

    @Test
    fun `a word-less line otherwise runs up to the next one`() {
        val first = listOf(
            sung(0, "one", words = null),
            sung(1_500, "two", words = null),
        ).toSyncedLyrics().lines.first() as SyncedLine

        assertEquals(1_499, first.end)
    }

    @Test
    fun `blank and negative entries are dropped`() {
        val lines = listOf(
            sung(-1, "before zero", listOf(word("x", 0.0, 1.0))),
            sung(0, "   ", words = null),
            sung(1000, "real", listOf(word("real", 1.0, 2.0))),
        ).toSyncedLyrics().lines

        assertEquals(1, lines.size)
        assertTrue(lines.single() is KaraokeLine.MainKaraokeLine)
    }

    @Test
    fun `word timings decide when to use this view at all`() {
        assertTrue(listOf(sung(0, "a", listOf(word("a", 0.0, 1.0)))).hasKaraokeTimings())
        assertTrue(!listOf(sung(0, "a", words = null)).hasKaraokeTimings())
    }

    @Test
    fun `an empty set converts to an empty view rather than throwing`() {
        assertEquals(0, emptyList<LyricsEntry>().toSyncedLyrics().lines.size)
        assertNull(emptyList<LyricsEntry>().toSyncedLyrics().lines.firstOrNull())
    }
}
