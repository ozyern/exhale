package com.ozyern.exhale.playback.lossless

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMatchTest {
    private fun track(title: String, artist: String?, seconds: Double?, format: String = "flac") = ServerTrack(
        title = title,
        artist = artist,
        album = null,
        durationSeconds = seconds,
        url = "http://192.168.1.20/rest/stream.view",
        format = format,
        serverName = "Home",
    )

    @Test
    fun theSameRecordingMatchesThroughYouTubeLabels() {
        assertTrue(TrackMatch.matches("Espresso (Official Video)", listOf("Sabrina Carpenter"), 175, track("Espresso", "Sabrina Carpenter", 175.4)))
        assertTrue(TrackMatch.matches("Yeah! (feat. Lil Jon & Ludacris)", listOf("Usher"), 250, track("Yeah!", "Usher", 250.0)))
    }

    @Test
    fun anotherRecordingDoesNot() {
        // A live take of the same song, half a minute longer.
        assertFalse(TrackMatch.matches("Espresso", listOf("Sabrina Carpenter"), 175, track("Espresso", "Sabrina Carpenter", 205.0)))
        // Another artist's song of the same name.
        assertFalse(TrackMatch.matches("Espresso", listOf("Sabrina Carpenter"), 175, track("Espresso", "Someone Else", 175.0)))
        // Another song altogether.
        assertFalse(TrackMatch.matches("Espresso", listOf("Sabrina Carpenter"), 175, track("Please Please Please", "Sabrina Carpenter", 186.0)))
    }

    @Test
    fun anUntaggedFileCountsOnlyWhenTheRuntimeAgrees() {
        assertTrue(TrackMatch.matches("Espresso", listOf("Sabrina Carpenter"), 175, track("Espresso", null, 175.0)))
        assertFalse(TrackMatch.matches("Espresso", listOf("Sabrina Carpenter"), 175, track("Espresso", null, null)))
    }

    @Test
    fun losslessAndNearbyRankFirst() {
        val lossy = track("Espresso", "Sabrina Carpenter", 175.0, format = "mp3")
        val flac = track("Espresso", "Sabrina Carpenter", 175.0, format = "flac")
        assertFalse(lossy.lossless)
        assertTrue(flac.lossless)
        assertTrue(flac.rank > lossy.rank)
    }
}
