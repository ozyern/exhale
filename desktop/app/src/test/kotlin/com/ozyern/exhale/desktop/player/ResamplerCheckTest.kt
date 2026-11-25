/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * The high-quality resampler really resamples: a 44.1 kHz tone played into mpv's null output, forced to
 * 48 kHz, comes out at 48 kHz and keeps playing. Silent — nothing reaches a speaker.
 */
class ResamplerCheckTest {
    @Test
    fun hqResamplerConverts44100To48000() {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val options = AudioEffects.resamplerOptions(Settings(hqResampler = true))
        val (rate, advanced) = playThrough(options)
        println("resampler check: '$options' -> output $rate Hz, playing=$advanced")
        assertEquals("48000", rate)
        assertTrue("playback didn't advance through the resampler", advanced)
    }

    /** The check has to notice a resampler that fails, or the pass above proves nothing. */
    @Test
    fun brokenResamplerIsCaught() {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val (rate, advanced) = playThrough("resampler=not_a_resampler")
        println("resampler check: broken -> output $rate Hz, playing=$advanced")
        assertFalse("a broken resampler looked fine", rate == "48000" && advanced)
    }

    private fun playThrough(resampler: String): Pair<String?, Boolean> {
        val wav = sineWav()
        val loaded = CountDownLatch(1)
        val mpv = Mpv(
            object : Mpv.Listener {
                override fun onFileLoaded() = loaded.countDown()

                override fun onError(message: String) {
                    println("resampler check: mpv error: $message")
                    loaded.countDown()
                }
            },
        )
        try {
            mpv.setProperty("ao", "null")
            mpv.setProperty("volume", "0")
            mpv.setProperty("audio-samplerate", "48000")
            mpv.setProperty("audio-swresample-o", resampler)
            mpv.load(wav.absolutePath, StreamHeaders("Exhale resampler check", null, null))
            loaded.await(5, TimeUnit.SECONDS)
            Thread.sleep(1_500)
            val rate = mpv.getProperty("audio-out-params/samplerate")
            val position = mpv.position() ?: 0.0
            return rate to (position > 0.5)
        } finally {
            mpv.close()
            wav.delete()
        }
    }

    /** Four seconds of a quiet 440 Hz tone, 16-bit mono at 44.1 kHz. */
    private fun sineWav(): File {
        val rate = 44_100
        val samples = rate * 4
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(36 + samples * 2)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(1)
            putInt(rate)
            putInt(rate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray())
            putInt(samples * 2)
            for (i in 0 until samples) putShort((sin(2 * PI * 440 * i / rate) * 6_000).toInt().toShort())
        }.array()
        return File.createTempFile("exhale-resampler", ".wav").apply { writeBytes(bytes) }
    }
}
