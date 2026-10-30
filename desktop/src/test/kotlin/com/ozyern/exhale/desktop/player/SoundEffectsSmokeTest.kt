/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.Settings
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 8D and a Sound preset on the real libmpv: the filter graph is accepted, the audio keeps playing
 * through it, and a preset's speed is what the clock shows. EXHALE_SMOKE=1 to run (plays muted).
 */
class SoundEffectsSmokeTest {
    /** The equalizer's linear-phase FIR: this libmpv accepts it, and the music plays on through it. */
    @Test
    fun linearPhaseEqualizerPlays() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        val stream = StreamResolver.resolve(song.id).getOrThrow()

        val settings = Settings(equalizerEnabled = true, equalizerBands = listOf(4, 3, 1, 0, -1, 0, 1, 2, 3, 2))
        val chain = AudioEffects.filterChain(settings)
        println("af: $chain")
        assertTrue("not the FIR equalizer", "firequalizer" in chain)

        val loaded = CountDownLatch(1)
        val position = AtomicReference(0.0)
        Mpv(object : Mpv.Listener {
            override fun onFileLoaded() = loaded.countDown()
            override fun onPositionChanged(seconds: Double) = position.set(seconds)
            override fun onError(message: String) = loaded.countDown()
        }).use { mpv ->
            mpv.setVolume(0)
            assertTrue("mpv refused the FIR equalizer", mpv.setProperty("af", chain))
            mpv.load(stream.url, stream.headers)
            assertTrue("file never loaded", loaded.await(30, TimeUnit.SECONDS))
            Thread.sleep(1_500)
            val start = position.get()
            Thread.sleep(5_000)
            println("af after load: ${mpv.getProperty("af")}")
            println("stream: ${stream.mimeType} ${stream.bitrate / 1000} kbps")
            assertTrue("playback stalled under the FIR equalizer", position.get() - start > 3.5)
        }
    }

    @Test
    fun eightDAndNightcorePlay() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val song = YouTube.search("Sabrina Carpenter Espresso", YouTube.SearchFilter.FILTER_SONG)
            .getOrThrow().items.filterIsInstance<SongItem>().first()
        val stream = StreamResolver.resolve(song.id).getOrThrow()

        val settings = Settings(eightD = true, soundPreset = "NIGHTCORE", equalizerEnabled = true, equalizerBands = listOf(3, 2, 0, 0, 0, 0, 0, 0, 1, 2))
        val chain = AudioEffects.filterChain(settings)
        println("af: $chain")

        val loaded = CountDownLatch(1)
        val position = AtomicReference(0.0)
        val error = AtomicReference<String?>(null)
        Mpv(object : Mpv.Listener {
            override fun onFileLoaded() = loaded.countDown()
            override fun onPositionChanged(seconds: Double) = position.set(seconds)
            override fun onError(message: String) {
                error.set(message)
                loaded.countDown()
            }
        }).use { mpv ->
            mpv.setVolume(0)
            assertTrue("mpv refused the filter chain", mpv.setProperty("af", chain))
            mpv.setProperty("audio-pitch-correction", if (AudioEffects.pitchCorrection(settings)) "yes" else "no")
            mpv.setProperty("speed", AudioEffects.effectiveSpeed(settings).toString())
            mpv.load(stream.url, stream.headers)
            assertTrue("file never loaded", loaded.await(30, TimeUnit.SECONDS))
            println("error: ${error.get()}")
            Thread.sleep(1_500)
            val start = position.get()
            val wallStart = System.nanoTime()
            Thread.sleep(6_000)
            val advanced = position.get() - start
            val wall = (System.nanoTime() - wallStart) / 1e9
            println("af after load: ${mpv.getProperty("af")}")
            println("audio-params: ${mpv.getProperty("audio-params")}")
            println("audio-out-params: ${mpv.getProperty("audio-out-params")}")
            println("advanced ${"%.2f".format(advanced)} s in ${"%.2f".format(wall)} s = ${"%.2f".format(advanced / wall)}x")
            assertTrue("playback stalled under 8D", advanced > 4.0)
            assertTrue("preset speed not applied", advanced / wall > 1.1)
        }
    }
}
