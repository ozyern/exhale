/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Detects silent lossless/Atmos output stalls when armed, independently of SkipSilence. */
@UnstableApi
class LosslessStallWatchdogAudioProcessor(
    private val minStallDurationUs: Long = 1_500_000L,
    private val silenceThreshold: Int = 128,
    private val onStallDetected: () -> Unit,
) : AudioProcessor {

    @Volatile
    var armed: Boolean = false

    private var sampleRate = 0
    private var channelCount = 0
    private var bytesPerSample = 0
    private var isFloat = false
    private var byteOrder = ByteOrder.LITTLE_ENDIAN
    private var stallFrameThreshold = 0L

    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    private var consecutiveSilentFrames: Long = 0
    private var notifiedThisStall = false

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        val (bytes, float) = bytesPerSampleFor(inputAudioFormat.encoding)
        bytesPerSample = bytes
        isFloat = float
        byteOrder = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT_BIG_ENDIAN, C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT_BIG_ENDIAN -> ByteOrder.BIG_ENDIAN
            else -> ByteOrder.LITTLE_ENDIAN
        }
        stallFrameThreshold = ((sampleRate.toLong() * minStallDurationUs + 999_999L) / 1_000_000L)
            .coerceAtLeast(1L)
        if (bytesPerSample == 0) {
            sampleRate = 0
            channelCount = 0
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = bytesPerSample != 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        if (!armed || sampleRate <= 0 || channelCount <= 0) {
            resetTracking()
            outputBuffer = inputBuffer.slice().order(inputBuffer.order())
            inputBuffer.position(inputBuffer.limit())
            return
        }

        detectStall(inputBuffer)
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun detectStall(inputBuffer: ByteBuffer) {
        inputBuffer.order(byteOrder)

        val frameSize = bytesPerSample * channelCount
        if (frameSize <= 0) return
        val frameCount = inputBuffer.remaining() / frameSize
        val basePosition = inputBuffer.position()

        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            for (channelIndex in 0 until channelCount) {
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * bytesPerSample
                val magnitude = sampleMagnitude(inputBuffer, sampleIndex)
                if (magnitude > framePeak) framePeak = magnitude
                if (framePeak >= silenceThreshold) break
            }

            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                if (consecutiveSilentFrames >= stallFrameThreshold && !notifiedThisStall) {
                    notifiedThisStall = true
                    onStallDetected()
                }
            } else {
                resetTracking()
            }
        }
    }

    /** Returns a value in the same [0, 32767]-ish range regardless of source bit depth. */
    private fun sampleMagnitude(buffer: ByteBuffer, index: Int): Int {
        return if (isFloat) {
            (abs(buffer.getFloat(index)) * 32767f).toInt()
        } else {
            when (bytesPerSample) {
                2 -> abs(buffer.getShort(index).toInt())
                3 -> {
                    val leastSignificant = if (byteOrder == ByteOrder.LITTLE_ENDIAN) index else index + 2
                    val mostSignificant = if (byteOrder == ByteOrder.LITTLE_ENDIAN) index + 2 else index
                    val b0 = buffer.get(leastSignificant).toInt() and 0xFF
                    val b1 = buffer.get(index + 1).toInt() and 0xFF
                    val b2 = buffer.get(mostSignificant).toInt()
                    val value = (b2 shl 16) or (b1 shl 8) or b0
                    abs(value) shr 8
                }
                4 -> (abs(buffer.getInt(index).toLong()) shr 16).toInt()
                else -> 0
            }
        }
    }

    private fun bytesPerSampleFor(encoding: Int): Pair<Int, Boolean> = when (encoding) {
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2 to false
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3 to false
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 4 to false
        C.ENCODING_PCM_FLOAT -> 4 to true
        else -> 0 to false
    }

    private fun resetTracking() {
        consecutiveSilentFrames = 0
        notifiedThisStall = false
    }

    fun resetForNewTrack() {
        resetTracking()
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        resetTracking()
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        bytesPerSample = 0
        buffer = EMPTY_BUFFER
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        outputBuffer = buffer
        return outputBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
