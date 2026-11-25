/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matching a YouTube song to a copy in someone's library. Getting this wrong plays the wrong
 * recording, so the cases that must fail matter as much as the ones that must pass.
 */
class TrackMatchTest {
    private fun song(title: String, artist: String, duration: Int?) = SongItem(
        id = "test",
        title = title,
        artists = listOf(Artist(artist, null)),
        duration = duration,
        thumbnail = "",
    )

    private fun copy(title: String, artist: String?, duration: Double?, format: String = "flac") = LosslessTrack(
        title = title,
        artist = artist,
        album = null,
        durationSeconds = duration,
        url = "C:\\Music\\track.flac",
        format = format,
        sourceName = "Library",
    )

    @Test
    fun matchesThroughYouTubeTitleNoise() {
        val playing = song("Blinding Lights (Official Video)", "The Weeknd", 200)
        assertTrue(TrackMatch.matches(playing, copy("Blinding Lights", "The Weeknd", 200.0)))
        assertTrue(TrackMatch.matches(song("Levitating (feat. DaBaby)", "Dua Lipa", 203), copy("Levitating", "Dua Lipa", 204.0)))
        assertTrue(TrackMatch.matches(song("Café del Mar", "Energy 52", 400), copy("Cafe del Mar", "Energy 52", 401.0)))
    }

    @Test
    fun rejectsADifferentRecordingOfTheSameSong() {
        // A seven-minute live take is not the three-minute single.
        assertFalse(TrackMatch.matches(song("Creep", "Radiohead", 239), copy("Creep", "Radiohead", 430.0)))
    }

    @Test
    fun rejectsADifferentSongAndADifferentArtist() {
        assertFalse(TrackMatch.matches(song("Espresso", "Sabrina Carpenter", 176), copy("Please Please Please", "Sabrina Carpenter", 186.0)))
        assertFalse(TrackMatch.matches(song("Hello", "Adele", 295), copy("Hello", "Martin Solveig", 295.0)))
    }

    /** A file with nothing but a title still counts, as long as the runtime agrees. */
    @Test
    fun untaggedFileNeedsTheRuntimeToAgree() {
        assertTrue(TrackMatch.matches(song("Redbone", "Childish Gambino", 327), copy("Redbone", null, 327.0)))
        assertFalse(TrackMatch.matches(song("Redbone", "Childish Gambino", 327), copy("Redbone", null, null)))
    }

    @Test
    fun lossyCopiesAreNotLossless() {
        assertFalse(copy("Redbone", "Childish Gambino", 327.0, format = "mp3").lossless)
        assertTrue(copy("Redbone", "Childish Gambino", 327.0, format = "flac").lossless)
        assertTrue(copy("Redbone", "Childish Gambino", 327.0, format = "alac").lossless)
    }

    /** Hi-res beats CD quality, and any lossless beats a high-bitrate lossy file. */
    @Test
    fun betterCopiesRankHigher() {
        val hiRes = copy("x", "y", 100.0).copy(sampleRate = 96_000, bitDepth = 24)
        val cd = copy("x", "y", 100.0).copy(sampleRate = 44_100, bitDepth = 16)
        val lossy = copy("x", "y", 100.0, format = "mp3").copy(bitrate = 320_000)
        assertTrue(hiRes.rank > cd.rank)
        assertTrue(cd.rank > lossy.rank)
    }

    /** A file at hand never stutters, so it beats a better one across the internet; FLAC beats other lossless. */
    @Test
    fun nearbyCopiesAndFlacComeFirst() {
        val onDisk = copy("x", "y", 100.0).copy(sampleRate = 44_100, bitDepth = 16)
        val onLan = onDisk.copy(url = "http://192.168.1.5:8200/a.flac")
        val remoteHiRes = onDisk.copy(url = "https://archive.org/download/a.flac", sampleRate = 96_000, bitDepth = 24)
        val wav = onDisk.copy(format = "wav")
        assertTrue(onDisk.rank > remoteHiRes.rank)
        assertTrue(onLan.rank > remoteHiRes.rank)
        assertTrue(onDisk.rank > wav.rank)
    }

    @Test
    fun youTubeFlacIsRecognised() {
        assertTrue(StreamResolver.isLosslessMime("audio/flac"))
        assertTrue(StreamResolver.isLosslessMime("audio/mp4; codecs=\"fLaC\""))
        assertFalse(StreamResolver.isLosslessMime("audio/webm; codecs=\"opus\""))
        assertFalse(StreamResolver.isLosslessMime("audio/mp4; codecs=\"mp4a.40.2\""))
    }

    /**
     * Strict matching, for a public catalogue full of other performances of the same song: the runtime
     * has to be there and has to agree, or a concert tape would quietly replace the studio single.
     */
    @Test
    fun strictMatchingNeedsTheRuntimeToAgreeClosely() {
        val playing = song("Ripple", "Grateful Dead", 260)
        assertTrue(TrackMatch.matches(playing, copy("Ripple", "Grateful Dead", 261.0), strict = true))
        // Four seconds out passes the everyday check but not this one.
        assertTrue(TrackMatch.matches(playing, copy("Ripple", "Grateful Dead", 264.0)))
        assertFalse(TrackMatch.matches(playing, copy("Ripple", "Grateful Dead", 264.0), strict = true))
        // A live take twice the length, and a copy that never says how long it is.
        assertFalse(TrackMatch.matches(playing, copy("Ripple", "Grateful Dead", 520.0), strict = true))
        assertFalse(TrackMatch.matches(playing, copy("Ripple", "Grateful Dead", null), strict = true))
        assertFalse(TrackMatch.matches(playing, copy("Ripple", null, 261.0), strict = true))
    }

    /** Lengths arrive as seconds, as mm:ss, or as a DLNA clock with milliseconds. */
    @Test
    fun lengthsParse() {
        assertEquals(274.45, LosslessLibrary.parseLength("274.45")!!, 0.01)
        assertEquals(274.0, LosslessLibrary.parseLength("4:34")!!, 0.01)
        assertEquals(3723.0, LosslessLibrary.parseLength("1:02:03")!!, 0.01)
        assertEquals(273.0, LosslessLibrary.parseLength("0:04:33.000")!!, 0.01)
        assertEquals(null, LosslessLibrary.parseLength(""))
        assertEquals(null, LosslessLibrary.parseLength("unknown"))
    }

    /**
     * What a media server on the network sends back. The lossless resource is taken with its sample
     * rate and bit depth; an item offering only MP3 is no better than YouTube and is left alone.
     */
    @Test
    fun didlLiteYieldsOnlyLosslessTracks() {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                       xmlns:dc="http://purl.org/dc/elements/1.1/"
                       xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
              <item id="1" parentID="0" restricted="1">
                <dc:title>Ripple</dc:title>
                <upnp:artist>Grateful Dead</upnp:artist>
                <upnp:album>American Beauty</upnp:album>
                <res protocolInfo="http-get:*:audio/flac:*" duration="0:04:33.000" sampleFrequency="44100" bitsPerSample="16">http://192.168.1.5:8200/MediaItems/42.flac</res>
              </item>
              <item id="2" parentID="0" restricted="1">
                <dc:title>Only A Copy</dc:title>
                <upnp:artist>Someone</upnp:artist>
                <res protocolInfo="http-get:*:audio/mpeg:*" duration="0:03:00.000">http://192.168.1.5:8200/MediaItems/43.mp3</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val tracks = LosslessLibrary.didlTracks(didl, "NAS")
        assertEquals(1, tracks.size)
        val track = tracks.first()
        assertEquals("Ripple", track.title)
        assertEquals("Grateful Dead", track.artist)
        assertEquals("flac", track.format)
        assertEquals(273.0, track.durationSeconds!!, 0.01)
        assertEquals(44_100, track.sampleRate)
        assertEquals(16, track.bitDepth)
        assertTrue(track.lossless)
    }
}
