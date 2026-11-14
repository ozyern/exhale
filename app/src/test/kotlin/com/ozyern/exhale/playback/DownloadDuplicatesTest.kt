package com.ozyern.exhale.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadDuplicatesTest {
    @After
    fun clear() {
        listOf("single", "album", "other").forEach(DownloadDuplicates::unregister)
    }

    @Test
    fun `same recording under another id finds the finished download`() {
        DownloadDuplicates.register("single", "Blinding Lights", "The Weeknd", 200)
        assertEquals("single", DownloadDuplicates.ownerOf("album", "Blinding Lights (feat. Nobody)", "the weeknd", 201))
    }

    @Test
    fun `a different version or length is not merged`() {
        DownloadDuplicates.register("single", "Blinding Lights", "The Weeknd", 200)
        assertNull(DownloadDuplicates.ownerOf("album", "Blinding Lights (Live)", "The Weeknd", 200))
        assertNull(DownloadDuplicates.ownerOf("album", "Blinding Lights", "The Weeknd", 215))
    }

    @Test
    fun `a removed download no longer owns anything and a song never owns itself`() {
        DownloadDuplicates.register("single", "Song", "Artist", 180)
        assertNull(DownloadDuplicates.ownerOf("single", "Song", "Artist", 180))
        DownloadDuplicates.unregister("single")
        assertNull(DownloadDuplicates.ownerOf("album", "Song", "Artist", 180))
    }

    @Test
    fun `missing artist or duration never matches`() {
        DownloadDuplicates.register("single", "Song", null, 180)
        assertNull(DownloadDuplicates.ownerOf("album", "Song", null, 180))
        DownloadDuplicates.register("other", "Song", "Artist", 180)
        assertNull(DownloadDuplicates.ownerOf("album", "Song", "Artist", -1))
    }
}
