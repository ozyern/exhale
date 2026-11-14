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
import kotlin.math.roundToInt
import kotlin.math.exp
import kotlin.math.PI

/**
 * Stereo widening with a short, low-passed crossfeed between channels.
 * Applies attenuation based on the combined coefficients to retain headroom.
 * Runs inside ExoPlayer's PCM processor pipeline.
 *
 * Deliberately alters the signal, so [enabled] must be forced false while Bit
 * Perfect is on — same reasoning as the EQ bypass in
 * [com.ozyern.exhale.eq.EqualizerService.setBitPerfectBypass].
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false

    /** How much wider the stereo image gets. 1.0 = untouched. */
    private val widthGain = 1.25f

    /** Makeup attenuation after widening, so the wider side energy doesn't clip. */
    // Bound the sum of the widening and crossfeed coefficients to unity.
    // Even full-scale anti-phase stereo must not hit the hard PCM clamp.
    private val outputGain get() = 1f / (widthGain + crossfeedGain)

    /** How much of the delayed, low-passed opposite channel gets mixed back in. */
    private val crossfeedGain = 0.2f

    /** One-pole lowpass factor applied to the cross-fed signal — dulls it, like a far ear would. */
    private var lowpassCoeff = 0f

    private var delayLeft = ShortArray(0)
    private var delayRight = ShortArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f
    private var processingActive = false
    private var borrowedOutput = AudioProcessor.EMPTY_BUFFER

    /** Unsupported formats bypass the effect without failing playback. */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount != 2 || inputAudioFormat.sampleRate <= 0
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        if (inputAudioFormat.sampleRate > 0 && inputAudioFormat.channelCount == 2 &&
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        ) {
            val delaySamples = (inputAudioFormat.sampleRate * DELAY_MS / 1000f)
                .roundToInt().coerceAtLeast(1)
            if (delayLeft.size != delaySamples) {
                delayLeft = ShortArray(delaySamples)
                delayRight = ShortArray(delaySamples)
            }
            lowpassCoeff = (1.0 - exp(-2.0 * PI * 700.0 / inputAudioFormat.sampleRate)).toFloat()
        } else {
            delayLeft = ShortArray(0)
            delayRight = ShortArray(0)
        }
        delayLeft.fill(0)
        delayRight.fill(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        processingActive = false
        borrowedOutput = AudioProcessor.EMPTY_BUFFER
    }

    override fun onReset() {
        onFlush()
        delayLeft = ShortArray(0)
        delayRight = ShortArray(0)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return
        val frameCount = byteCount / BYTES_PER_FRAME
        borrowedOutput = AudioProcessor.EMPTY_BUFFER

        if (!enabled || delayLeft.isEmpty()) {
            // Do not replay crossfeed from a previous enabled interval.
            if (processingActive) onFlush()
            borrowedOutput = inputBuffer.slice().order(ByteOrder.nativeOrder())
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val outputBuffer = replaceOutputBuffer(byteCount)
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())
        processingActive = true
        val gain = outputGain

        val delaySize = delayLeft.size
        repeat(frameCount) {
            val left = inputBuffer.short.toInt()
            val right = inputBuffer.short.toInt()

            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f * widthGain
            var widenedLeft = mid + side
            var widenedRight = mid - side

            val delayedRight = delayRight[delayIndex].toFloat()
            val delayedLeft = delayLeft[delayIndex].toFloat()
            lowpassLeft += lowpassCoeff * (delayedRight - lowpassLeft)
            lowpassRight += lowpassCoeff * (delayedLeft - lowpassRight)
            widenedLeft += lowpassLeft * crossfeedGain
            widenedRight += lowpassRight * crossfeedGain

            delayLeft[delayIndex] = left.toShort()
            delayRight[delayIndex] = right.toShort()
            if (++delayIndex == delaySize) delayIndex = 0

            outputBuffer.putShort(clampToShort(widenedLeft * gain))
            outputBuffer.putShort(clampToShort(widenedRight * gain))
        }
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

    private fun clampToShort(value: Float): Short =
        value.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

    private companion object {
        const val BYTES_PER_FRAME = 4 // stereo, 16-bit
        const val DELAY_MS = 0.35f
    }
}
