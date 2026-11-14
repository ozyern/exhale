package com.ozyern.exhale.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ExportFileNameTest {
    @Test
    fun `artist dash title, unsafe chars replaced`() {
        assertEquals("Shilpa Rao - Barbaad", exportBaseName("Shilpa Rao", "Barbaad"))
        assertEquals("AC_DC - Back_In Black", exportBaseName("AC/DC", "Back:In Black"))
        assertEquals("Song", exportBaseName("", "Song"))
        assertEquals("Untitled", exportBaseName(" ", " "))
    }

    @Test
    fun `extension per container`() {
        assertEquals("m4a", extensionFor("audio/mp4"))
        assertEquals("flac", extensionFor("audio/flac"))
        assertEquals("webm", extensionFor("audio/webm"))
        assertEquals("mp4", extensionFor("video/mp4"))
        assertEquals("webm", extensionFor("video/webm"))
    }

    @Test
    fun `cover url upscaled`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj",
            largeCover("https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"),
        )
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", largeCover("https://i.ytimg.com/vi/x/hqdefault.jpg"))
    }
}
