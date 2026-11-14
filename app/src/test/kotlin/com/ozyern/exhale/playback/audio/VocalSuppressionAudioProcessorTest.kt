package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class VocalSuppressionAudioProcessorTest {

    @Test
    fun `mono and float formats bypass the processor`() {
        val mono = VocalSuppressionAudioProcessor()
        mono.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        assertTrue(!mono.isActive)

        val float = VocalSuppressionAudioProcessor()
        float.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        assertTrue(!float.isActive)
    }

    @Test
    fun `zero amount preserves samples exactly`() {
        val input = tone(leftGain = 0.6, rightGain = 0.2, frames = 4096)
        val output = render(input, amount = 0f)
        assertArrayEquals(input, output)
    }

    @Test
    fun `centered tone is reduced while a panned tone remains`() {
        val centered = tone(leftGain = 0.5, rightGain = 0.5, frames = SAMPLE_RATE)
        val panned = tone(leftGain = 0.5, rightGain = 0.1, frames = SAMPLE_RATE)

        val centeredOutput = render(centered, amount = 1f)
        val pannedOutput = render(panned, amount = 1f)

        assertTrue(rms(centeredOutput) < rms(centered) * 0.15)
        assertTrue("panned tone ratio=${rms(pannedOutput) / rms(panned)}", rms(pannedOutput) > rms(panned) * 0.8)
    }

    private fun render(input: ShortArray, amount: Float): ShortArray {
        val processor = VocalSuppressionAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(SAMPLE_RATE, 2, C.ENCODING_PCM_16BIT))
            flush()
            this.amount = amount
        }
        val output = ShortArray(input.size)
        val chunkFrames = 1024
        var frameOffset = 0
        while (frameOffset < input.size / 2) {
            val frames = minOf(chunkFrames, input.size / 2 - frameOffset)
            val chunk = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
            val firstSample = frameOffset * 2
            repeat(frames * 2) { chunk.putShort(input[firstSample + it]) }
            chunk.flip()
            processor.queueInput(chunk)

            val result = processor.output.order(ByteOrder.nativeOrder()).asShortBuffer()
            val resultSamples = result.remaining()
            result.get(output, firstSample, resultSamples)
            frameOffset += frames
        }
        return output
    }

    private fun tone(leftGain: Double, rightGain: Double, frames: Int): ShortArray =
        ShortArray(frames * 2).also { samples ->
            for (frame in 0 until frames) {
                val value = sin(2.0 * PI * TONE_HZ * frame / SAMPLE_RATE)
                samples[frame * 2] = (value * leftGain * Short.MAX_VALUE).toInt().toShort()
                samples[frame * 2 + 1] = (value * rightGain * Short.MAX_VALUE).toInt().toShort()
            }
        }

    private fun rms(samples: ShortArray): Double {
        val firstFrame = WINDOW_FRAMES
        val sum = (firstFrame * 2 until samples.size).sumOf { samples[it].toDouble() * samples[it] }
        val count = samples.size - firstFrame * 2
        return sqrt(sum / count)
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val TONE_HZ = 1_000
        const val WINDOW_FRAMES = 4096
    }
}
