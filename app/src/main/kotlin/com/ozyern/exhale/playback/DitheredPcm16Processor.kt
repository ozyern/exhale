/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * The one place float becomes 16-bit, when the output has to be 16-bit (the phone's speaker, or
 * float switched off).
 *
 * Before this, a speaker track was decoded to 16-bit and every stage after it — equaliser, filter
 * sweeps, spatialiser, 8D — worked on 16-bit samples and truncated its result back to 16-bit:
 * one rounding per stage, each a little undithered distortion stacked on the last, and the quiet
 * tails of a fade reduced to steps. Now the decoder hands over float, the whole chain runs in
 * float, and the signal is quantised exactly once, here, with TPDF dither — triangular noise of
 * one least significant bit, which turns quantisation error into a constant, inaudibly low hiss
 * instead of distortion that follows the music.
 *
 * Inactive (the stream passes through untouched) whenever the input is already 16-bit or
 * [active] says float may go to the track. With [dither] off (a bit-perfect 16-bit DAC) it only
 * rounds, at decoders' own 1/32768 scale, so an untouched 16-bit source comes out sample for sample.
 */
@UnstableApi
class DitheredPcm16Processor(
    private val active: () -> Boolean,
    private val dither: () -> Boolean = { true },
) : BaseAudioProcessor() {

    // xorshift32: fast, allocation-free and plenty random for dither.
    private var seed = 0x2545F491

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT || !active()) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            inputAudioFormat.channelCount,
            C.ENCODING_PCM_16BIT,
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val samples = inputBuffer.remaining() / 4
        if (samples == 0) return
        val out = replaceOutputBuffer(samples * 2)
        val dithering = dither()
        repeat(samples) {
            // Two uniform values in [0, 1) subtracted: triangular over (-1, 1) LSB.
            val noise = if (dithering) nextUniform() - nextUniform() else 0f
            // 32768, the scale decoders turn 16-bit into float by; +1.0 is clamped below.
            val scaled = inputBuffer.float * 32768f + noise
            out.putShort(scaled.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        out.flip()
    }

    private fun nextUniform(): Float {
        var x = seed
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        seed = x
        return (x ushr 8) * (1f / (1 shl 24))
    }
}
