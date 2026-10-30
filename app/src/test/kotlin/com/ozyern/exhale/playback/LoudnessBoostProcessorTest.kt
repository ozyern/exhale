/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class LoudnessBoostProcessorTest {
    private val rate = 48_000

    private fun tone(amplitude: Float, seconds: Float): FloatArray {
        val frames = (rate * seconds).toInt()
        return FloatArray(frames * 2) { i -> amplitude * sin(2 * PI * 440 * (i / 2) / rate).toFloat() }
    }

    private fun run(processor: LoudnessBoostProcessor, samples: FloatArray): FloatArray {
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val out = ArrayList<Float>(samples.size)
        var i = 0
        while (i < samples.size) {
            val n = minOf(2048, samples.size - i)
            val buf = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())
            for (k in 0 until n) buf.putFloat(samples[i + k])
            buf.flip()
            processor.queueInput(buf)
            val o = processor.output
            while (o.hasRemaining()) out.add(o.float)
            i += n
        }
        return out.toFloatArray()
    }

    @Test
    fun unityIsBitExactEvenForALoudMaster() {
        val input = tone(0.999f, 0.25f)
        assertArrayEquals(input, run(LoudnessBoostProcessor { 1f }, input), 0f)
    }

    @Test
    fun aQuietSongIsLiftedByTheWholeGain() {
        val input = tone(0.1f, 0.5f)
        val out = run(LoudnessBoostProcessor { 2f }, input)
        // Past the first buffer's ramp, the lift is exact: far under the ceiling, nothing is limited.
        val i = input.size - 1000
        assertEquals(input[i] * 2f, out[i], 1e-5f)
    }

    @Test
    fun aBoostedLoudSongNeverClips() {
        val out = run(LoudnessBoostProcessor { LoudnessBoostProcessor.MAX_BOOST }, tone(0.9f, 0.5f))
        assertTrue(out.all { abs(it) <= 0.9661f })
    }

    @Test
    fun aChangeOfGainIsRampedNotStepped() {
        var gain = 1f
        val processor = LoudnessBoostProcessor { gain }
        val input = FloatArray(4096) { 0.1f }
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush()
        gain = 2f
        val buf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        input.forEach { buf.putFloat(it) }
        buf.flip()
        processor.queueInput(buf)
        val o = processor.output
        val out = FloatArray(o.remaining() / 4) { o.float }
        // No sample jumps more than a sliver from the one before it.
        for (k in 2 until out.size) assertTrue(abs(out[k] - out[k - 2]) < 0.001f)
        assertEquals(0.2f, out.last(), 1e-4f)
    }
}
