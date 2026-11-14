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

/** Tape stop preserves the output frame count; other PCM formats bypass it. */
@UnstableApi
class DjTailAudioProcessor : BaseAudioProcessor() {

    private enum class Mode { OFF, TAPE_STOP }

    @Volatile
    private var mode = Mode.OFF

    private var channels = 0
    private var sampleRate = 0

    private var ring = ShortArray(0)
    private var ringFrames = 0
    private var writeFrame = 0

    private var readPosition = 0.0
    private var tapeRate = 1.0
    private var tapeDecayPerFrame = 0.0

    fun startTapeStop(durationMs: Long) {
        if (sampleRate <= 0 || channels <= 0) return
        val frames = ((durationMs * sampleRate) / 1000L).coerceAtLeast(1L)
        tapeRate = 1.0
        tapeDecayPerFrame = 1.0 / frames
        readPosition = writeFrame.toDouble()
        mode = Mode.TAPE_STOP
    }

    fun stop() {
        mode = Mode.OFF
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..8 ||
            inputAudioFormat.sampleRate <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        ringFrames = sampleRate * RING_SECONDS
        ring = ShortArray(ringFrames * channels)
        writeFrame = 0
        mode = Mode.OFF
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val frameCount = remaining / 2 / channels

        when (mode) {
            Mode.OFF -> {
                output.put(inputBuffer)
                output.flip()
                return
            }
            Mode.TAPE_STOP -> brake(inputBuffer, output, frameCount)
        }
        output.flip()
    }

    private fun brake(input: ByteBuffer, output: ByteBuffer, frameCount: Int) {
        repeat(frameCount) {
            for (channel in 0 until channels) {
                ring[writeFrame * channels + channel] = input.short
            }
            writeFrame = (writeFrame + 1) % ringFrames

            if (tapeRate <= 0.0) {
                repeat(channels) { output.putShort(0) }
                return@repeat
            }

            val base = Math.floor(readPosition).toInt()
            val fraction = readPosition - base
            val frameA = Math.floorMod(base, ringFrames)
            val frameB = (frameA + 1) % ringFrames
            for (channel in 0 until channels) {
                val a = ring[frameA * channels + channel].toDouble()
                val b = ring[frameB * channels + channel].toDouble()
                val interpolated = a + (b - a) * fraction
                output.putShort(interpolated.coerceIn(MIN_SAMPLE, MAX_SAMPLE).toInt().toShort())
            }
            readPosition += tapeRate
            tapeRate = (tapeRate - tapeDecayPerFrame).coerceAtLeast(0.0)
        }
    }

    override fun onFlush() {
        mode = Mode.OFF
        writeFrame = 0
        if (ring.isNotEmpty()) ring.fill(0)
    }

    override fun onReset() {
        mode = Mode.OFF
        channels = 0
        sampleRate = 0
        ring = ShortArray(0)
        ringFrames = 0
    }

    companion object {
        private const val RING_SECONDS = 4

        private const val MIN_SAMPLE = -32768.0
        private const val MAX_SAMPLE = 32767.0
    }
}
