/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

@UnstableApi
class VocalSuppressionAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var amount: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }

    private val reducer = CenteredVocalReducer()
    private var samples = ShortArray(0)
    private var borrowedOutput = AudioProcessor.EMPTY_BUFFER

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        reducer.bypass()
        borrowedOutput = AudioProcessor.EMPTY_BUFFER
    }

    override fun onReset() {
        reducer.bypass()
        samples = ShortArray(0)
        borrowedOutput = AudioProcessor.EMPTY_BUFFER
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return
        borrowedOutput = AudioProcessor.EMPTY_BUFFER
        val strength = amount
        if (strength <= 0f || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate <= 0
        ) {
            reducer.bypass()
            borrowedOutput = inputBuffer.slice().order(ByteOrder.nativeOrder())
            inputBuffer.position(inputBuffer.limit())
            return
        }
        val outputBuffer = replaceOutputBuffer(byteCount).order(ByteOrder.nativeOrder())
        val sampleCount = byteCount / (BYTES_PER_SAMPLE * CHANNELS) * CHANNELS

        if (samples.size < sampleCount) samples = ShortArray(sampleCount)
        inputBuffer.order(ByteOrder.nativeOrder())
        repeat(sampleCount) { samples[it] = inputBuffer.short }

        reducer.process(samples, sampleCount / CHANNELS, inputAudioFormat.sampleRate, strength)

        repeat(sampleCount) { outputBuffer.putShort(samples[it]) }
        outputBuffer.put(inputBuffer)
        outputBuffer.flip()
    }

    override fun getOutput(): ByteBuffer {
        val processed = super.getOutput()
        val borrowed = borrowedOutput
        borrowedOutput = AudioProcessor.EMPTY_BUFFER
        return if (borrowed.hasRemaining()) borrowed else processed
    }

    override fun isEnded(): Boolean = super.isEnded() && !borrowedOutput.hasRemaining()

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val CHANNELS = 2
    }
}

internal class CenteredVocalReducer {
    private val window = FloatArray(WINDOW) { index ->
        (0.5 - 0.5 * cos(2.0 * PI * index / WINDOW)).toFloat()
    }
    private val twiddleCos = FloatArray(WINDOW / 2) { index ->
        cos(2.0 * PI * index / WINDOW).toFloat()
    }
    private val twiddleSin = FloatArray(WINDOW / 2) { index ->
        sin(2.0 * PI * index / WINDOW).toFloat()
    }
    private val leftReal = FloatArray(WINDOW)
    private val leftImaginary = FloatArray(WINDOW)
    private val rightReal = FloatArray(WINDOW)
    private val rightImaginary = FloatArray(WINDOW)
    private var input = FloatArray(WINDOW_SAMPLES * 2)
    private var overlap = FloatArray(WINDOW_SAMPLES * 2)
    private var output = FloatArray(WINDOW_SAMPLES * 2)
    private var pendingSamples = 0
    private var readySamples = 0
    private var sampleRate = 0
    private var active = false

    fun bypass() {
        if (active) reset()
    }

    fun reset() {
        input.fill(0f)
        overlap.fill(0f)
        output.fill(0f)
        pendingSamples = 0
        readySamples = 0
        sampleRate = 0
        active = false
    }

    fun process(pcm: ShortArray, frames: Int, rate: Int, strength: Float) {
        if (frames <= 0) return
        if (strength <= 0f) {
            if (active) reset()
            return
        }
        if (rate != sampleRate) {
            reset()
            sampleRate = rate
        }
        active = true

        val sampleCount = frames * CHANNELS
        ensureCapacity(sampleCount)
        for (index in 0 until sampleCount) {
            input[pendingSamples + index] = pcm[index] / PCM_SCALE
        }
        pendingSamples += sampleCount

        var consumedSamples = 0
        while (pendingSamples - consumedSamples >= WINDOW_SAMPLES) {
            transform(consumedSamples, rate, strength)
            consumedSamples += HOP_SAMPLES
        }

        if (consumedSamples > 0) {
            ensureOutputCapacity(readySamples + consumedSamples)
            System.arraycopy(overlap, 0, output, readySamples, consumedSamples)
            readySamples += consumedSamples
            System.arraycopy(overlap, consumedSamples, overlap, 0, overlap.size - consumedSamples)
            overlap.fill(0f, overlap.size - consumedSamples, overlap.size)
            System.arraycopy(input, consumedSamples, input, 0, pendingSamples - consumedSamples)
            pendingSamples -= consumedSamples
        }

        if (readySamples >= sampleCount) {
            for (index in 0 until sampleCount) {
                pcm[index] = toPcm(output[index])
            }
            System.arraycopy(output, sampleCount, output, 0, readySamples - sampleCount)
            readySamples -= sampleCount
        } else {
            pcm.fill(0, 0, sampleCount)
        }
    }

    private fun ensureCapacity(additionalSamples: Int) {
        val required = pendingSamples + additionalSamples + WINDOW_SAMPLES * 2
        if (input.size < required) input = input.copyOf(required)
        if (overlap.size < required) overlap = overlap.copyOf(required)
    }

    private fun ensureOutputCapacity(required: Int) {
        if (output.size < required) output = output.copyOf(required + WINDOW_SAMPLES * 2)
    }

    private fun transform(offset: Int, rate: Int, strength: Float) {
        for (index in 0 until WINDOW) {
            val weight = window[index]
            leftReal[index] = input[offset + index * CHANNELS] * weight
            leftImaginary[index] = 0f
            rightReal[index] = input[offset + index * CHANNELS + 1] * weight
            rightImaginary[index] = 0f
        }

        fft(leftReal, leftImaginary, inverse = false)
        fft(rightReal, rightImaginary, inverse = false)

        val firstBin = (KEEP_LOW_HZ * WINDOW / rate).toInt()
        val lastBin = (KEEP_HIGH_HZ * WINDOW / rate).toInt()
        for (bin in 0..WINDOW / 2) {
            val gain = if (bin < firstBin || bin > lastBin) {
                1f
            } else {
                centeredGain(bin, strength)
            }
            leftReal[bin] *= gain
            leftImaginary[bin] *= gain
            rightReal[bin] *= gain
            rightImaginary[bin] *= gain

            if (bin in 1 until WINDOW / 2) {
                val mirror = WINDOW - bin
                leftReal[mirror] = leftReal[bin]
                leftImaginary[mirror] = -leftImaginary[bin]
                rightReal[mirror] = rightReal[bin]
                rightImaginary[mirror] = -rightImaginary[bin]
            }
        }

        fft(leftReal, leftImaginary, inverse = true)
        fft(rightReal, rightImaginary, inverse = true)

        for (index in 0 until WINDOW) {
            val weight = window[index] * OVERLAP_GAIN
            overlap[offset + index * CHANNELS] += leftReal[index] * weight
            overlap[offset + index * CHANNELS + 1] += rightReal[index] * weight
        }
    }

    private fun centeredGain(bin: Int, strength: Float): Float {
        val leftMagnitude = hypot(leftReal[bin], leftImaginary[bin])
        val rightMagnitude = hypot(rightReal[bin], rightImaginary[bin])
        if (leftMagnitude < MIN_MAGNITUDE && rightMagnitude < MIN_MAGNITUDE) return 1f

        val balance = abs(leftMagnitude - rightMagnitude) / (leftMagnitude + rightMagnitude)
        val crossReal = leftReal[bin] * rightReal[bin] + leftImaginary[bin] * rightImaginary[bin]
        val crossImaginary = leftImaginary[bin] * rightReal[bin] - leftReal[bin] * rightImaginary[bin]
        val phase = abs(atan2(crossImaginary, crossReal))
        val centered = exp(
            -(balance * balance) / (BALANCE_WIDTH * BALANCE_WIDTH) -
                (phase * phase) / (PHASE_WIDTH * PHASE_WIDTH),
        )
        return 1f - strength * centered
    }

    private fun fft(real: FloatArray, imaginary: FloatArray, inverse: Boolean) {
        var reversed = 0
        for (index in 1 until WINDOW) {
            var bit = WINDOW shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed or bit
            if (index < reversed) {
                var temporary = real[index]
                real[index] = real[reversed]
                real[reversed] = temporary
                temporary = imaginary[index]
                imaginary[index] = imaginary[reversed]
                imaginary[reversed] = temporary
            }
        }

        var length = 2
        while (length <= WINDOW) {
            val tableStep = WINDOW / length
            var start = 0
            while (start < WINDOW) {
                for (offset in 0 until length / 2) {
                    val tableIndex = offset * tableStep
                    val wr = twiddleCos[tableIndex]
                    val wi = twiddleSin[tableIndex] * if (inverse) 1f else -1f
                    val even = start + offset
                    val odd = even + length / 2
                    val oddReal = real[odd] * wr - imaginary[odd] * wi
                    val oddImaginary = real[odd] * wi + imaginary[odd] * wr
                    val evenReal = real[even]
                    val evenImaginary = imaginary[even]
                    real[even] = evenReal + oddReal
                    imaginary[even] = evenImaginary + oddImaginary
                    real[odd] = evenReal - oddReal
                    imaginary[odd] = evenImaginary - oddImaginary
                }
                start += length
            }
            length = length shl 1
        }

        if (inverse) {
            for (index in 0 until WINDOW) {
                real[index] /= WINDOW
                imaginary[index] /= WINDOW
            }
        }
    }

    private fun toPcm(sample: Float): Short =
        (sample * PCM_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

    private companion object {
        const val WINDOW = 4096
        const val HOP = WINDOW / 4
        const val CHANNELS = 2
        const val WINDOW_SAMPLES = WINDOW * CHANNELS
        const val HOP_SAMPLES = HOP * CHANNELS
        const val PCM_SCALE = 32768f
        const val KEEP_LOW_HZ = 120f
        const val KEEP_HIGH_HZ = 12_000f
        const val BALANCE_WIDTH = 0.28f
        const val PHASE_WIDTH = 0.55f
        const val MIN_MAGNITUDE = 1e-7f
        const val OVERLAP_GAIN = 2f / 3f
    }
}
