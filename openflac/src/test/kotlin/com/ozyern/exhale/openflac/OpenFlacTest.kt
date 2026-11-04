/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.openflac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFlacTest {
    @Test
    fun licenceNames() {
        assertEquals("CC BY-NC-SA 3.0", OpenFlac.licenceName("https://creativecommons.org/licenses/by-nc-sa/3.0/"))
        assertEquals("Public Domain", OpenFlac.licenceName("https://creativecommons.org/publicdomain/mark/1.0/"))
        assertNull(OpenFlac.licenceName(null))
    }

    private fun track(title: String, artist: String, duration: Int?) = OpenFlac.Track(
        title = title,
        artist = artist,
        album = null,
        durationSeconds = duration,
        url = "https://example.com/a.flac",
        artworkUrl = null,
        credit = OpenFlac.Credit(null, null, "https://example.com"),
    )

    /** A concert tape of the same song must never replace the studio recording. */
    @Test
    fun onlyTheSameRecordingCounts() {
        fun same(track: OpenFlac.Track) = OpenFlac.isSameRecording("Ripple", "Grateful Dead", 260, track)
        assertTrue(same(track("Ripple", "Grateful Dead", 261)))
        assertTrue(same(track("Ripple (Remastered)", "The Grateful Dead", 259)))
        assertFalse(same(track("Ripple", "Grateful Dead", 520)))
        assertFalse(same(track("Ripple", "Grateful Dead", null)))
        assertFalse(same(track("Ripple", "Someone Else", 260)))
        assertFalse(same(track("Ripple Effect", "Grateful Dead", 260)))
        assertTrue(OpenFlac.isSameRecording("Tití Me Preguntó", "Bad Bunny", 243, track("Titi Me Pregunto", "Bad Bunny", 243)))
    }

    @Test
    fun archiveFilesGoStraightToTheirServerOnceItIsKnown() {
        val link = "https://archive.org/download/some-item/disc%201/track.flac"
        assertEquals(link, OpenFlac.fastUrl(link))
        OpenFlac.noteArchiveServer("some-item", "ia800000.us.archive.org", "/17/items/some-item")
        assertEquals("https://ia800000.us.archive.org/17/items/some-item/disc%201/track.flac", OpenFlac.fastUrl(link))
        // Any other address is left alone.
        assertEquals("https://example.com/a.flac", OpenFlac.fastUrl("https://example.com/a.flac"))
        // A server that failed goes back to the ordinary link.
        OpenFlac.forgetServerFor(link)
        assertEquals(link, OpenFlac.fastUrl(link))
    }

    @Test
    fun lengths() {
        assertEquals(274, OpenFlac.parseLength("274.45"))
        assertEquals(274, OpenFlac.parseLength("4:34"))
        assertNull(OpenFlac.parseLength(""))
    }
}
