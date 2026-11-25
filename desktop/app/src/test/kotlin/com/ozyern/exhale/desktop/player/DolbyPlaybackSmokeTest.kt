/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A real Dolby file through the bundled libmpv: a 5.1 Dolby Digital Plus (E-AC-3) track is encoded
 * from a test tone, then played back the way the player plays it, and must be read as Dolby Digital
 * Plus with six channels. EXHALE_SMOKE=1 to run.
 */
class DolbyPlaybackSmokeTest {
    private class Events : Mpv.Listener {
        val loaded = CountDownLatch(1)
        val ended = CountDownLatch(1)
        @Volatile var error: String? = null

        override fun onFileLoaded() = loaded.countDown()
        override fun onEndOfFile() = ended.countDown()
        override fun onError(message: String) {
            error = message
            loaded.countDown()
            ended.countDown()
        }
    }

    @Test
    fun eac3SurroundPlaysAndIsDetected() {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val file = File.createTempFile("exhale-dolby", ".mka").apply { deleteOnExit() }

        val encode = Events()
        Mpv(encode, extraOptions = mapOf("o" to file.absolutePath, "of" to "matroska", "oac" to "eac3", "audio-channels" to "5.1", "ao" to "null")).use { mpv ->
            mpv.load("av://lavfi:sine=frequency=440:duration=3", StreamHeaders(userAgent = "Exhale", origin = null, referer = null))
            assertTrue("encoding didn't finish", encode.ended.await(30, TimeUnit.SECONDS))
        }
        assertEquals(null, encode.error)
        assertTrue("nothing was encoded", file.length() > 1_000)

        val play = Events()
        Mpv(play, extraOptions = mapOf("ao" to "null")).use { mpv ->
            mpv.load(file.absolutePath, StreamHeaders(userAgent = "Exhale", origin = null, referer = null))
            assertTrue("never loaded", play.loaded.await(15, TimeUnit.SECONDS))
            assertEquals(null, play.error)
            val source = ImmersiveAudio.fromTrackList(mpv.getProperty("track-list"))
            println("Dolby smoke: $source")
            assertEquals(ImmersiveFormat.DolbyDigitalPlus, source?.format)
            assertEquals(6, source?.channels)
            assertTrue("playback ended in an error", play.ended.await(15, TimeUnit.SECONDS) && play.error == null)
        }
    }
}
