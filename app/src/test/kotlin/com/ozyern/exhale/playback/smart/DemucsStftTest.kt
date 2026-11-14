package com.ozyern.exhale.playback.smart

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemucsStftTest {
    private fun signal(length: Int) =
        FloatArray(length) { (0.5 * sin(it * 0.021) + 0.25 * cos(it * 0.43) + 0.1 * sin(it * 1.3)).toFloat() }

    @Test
    fun `framing matches demucs for a segment length`() {
        assertEquals(336, DemucsStft.frames(343980))
    }

    @Test
    fun `forward then inverse restores the signal away from the edges`() {
        val length = 20_000
        val x = signal(length)
        val frames = DemucsStft.frames(length)
        val re = Array(DemucsStft.BINS) { FloatArray(frames) }
        val im = Array(DemucsStft.BINS) { FloatArray(frames) }
        DemucsStft.forward(x, length, re, im)
        val out = FloatArray(length)
        DemucsStft.inverse(re, im, length, out)

        // Demucs zero-pads the frames it inverts, so the first and last few thousand samples are
        // attenuated by design; inside, every contributing frame is real.
        val from = DemucsStft.N_FFT - 1536 + 100
        val to = (frames - 1) * DemucsStft.HOP - 1536 - 100
        var worst = 0f
        for (i in from until to) worst = maxOf(worst, abs(out[i] - x[i]))
        assertTrue("worst error $worst", to > from && worst < 2e-3f)
    }

    @Test
    fun `a pure tone lands in the expected bin`() {
        val length = 8192
        val bin = 100
        val x = FloatArray(length) { sin(2.0 * Math.PI * bin * it / DemucsStft.N_FFT).toFloat() }
        val frames = DemucsStft.frames(length)
        val re = Array(DemucsStft.BINS) { FloatArray(frames) }
        val im = Array(DemucsStft.BINS) { FloatArray(frames) }
        DemucsStft.forward(x, length, re, im)
        val middle = frames / 2
        val magnitudes = DoubleArray(DemucsStft.BINS) { Math.hypot(re[it][middle].toDouble(), im[it][middle].toDouble()) }
        assertEquals(bin, magnitudes.indices.maxByOrNull { magnitudes[it] })
    }
}
