/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ozyern.exhale.playback.dj.Biquad
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** 16-bit PCM; stereo narrowing applies only to two-channel audio. */
@UnstableApi
class DjFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var lowPassHz: Float = OPEN_LOW_PASS_HZ

    @Volatile
    var highPassHz: Float = OPEN_HIGH_PASS_HZ

    @Volatile
    var stereoWidth: Float = 1f

    /** -1 hard left to 1 hard right; stereo only. */
    @Volatile
    var pan: Float = 0f

    @Volatile
    private var snapRequested = false

    private var channels = 0
    private var sampleRate = 0

    private var currentLowPass = OPEN_LOW_PASS_HZ
    private var currentHighPass = OPEN_HIGH_PASS_HZ
    private var currentWidth = 1f
    private var currentPan = 0f

    private var appliedLowPass = Float.NaN
    private var appliedHighPass = Float.NaN

    private lateinit var lowPass: Array<Array<Biquad>>
    private lateinit var highPass: Array<Array<Biquad>>

    fun snapToTargets() {
        snapRequested = true
    }

    fun open() {
        lowPassHz = OPEN_LOW_PASS_HZ
        highPassHz = OPEN_HIGH_PASS_HZ
        stereoWidth = 1f
        pan = 0f
        snapToTargets()
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
        lowPass = Array(channels) { arrayOf(Biquad(), Biquad()) }
        highPass = Array(channels) { arrayOf(Biquad(), Biquad()) }
        currentLowPass = lowPassHz
        currentHighPass = highPassHz
        currentWidth = stereoWidth.coerceIn(0f, 1f)
        currentPan = pan
        appliedLowPass = Float.NaN
        appliedHighPass = Float.NaN
        snapRequested = false
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 || channels == 0) return
        if (snapRequested) {
            snapRequested = false
            currentLowPass = lowPassHz
            currentHighPass = highPassHz
            currentWidth = stereoWidth.coerceIn(0f, 1f)
            currentPan = pan
        }
        val output = replaceOutputBuffer(remaining)

        if (isOpen()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        val frameCount = remaining / 2 / channels
        var framesDone = 0
        while (framesDone < frameCount) {
            val chunk = minOf(COEFFICIENT_CHUNK_FRAMES, frameCount - framesDone)
            advanceCutoffs(chunk)
            val lowOn = currentLowPass < OPEN_LOW_PASS_HZ
            val highOn = currentHighPass > OPEN_HIGH_PASS_HZ
            if (!nearlyEqual(currentLowPass, appliedLowPass)) {
                val reference = lowPass[0][0]
                reference.setLowPass(sampleRate, currentLowPass)
                for (channel in 0 until channels) {
                    for (stage in lowPass[channel]) if (stage !== reference) stage.copyCoefficientsFrom(reference)
                }
                appliedLowPass = currentLowPass
            }
            if (!nearlyEqual(currentHighPass, appliedHighPass)) {
                val reference = highPass[0][0]
                reference.setHighPass(sampleRate, currentHighPass)
                for (channel in 0 until channels) {
                    for (stage in highPass[channel]) if (stage !== reference) stage.copyCoefficientsFrom(reference)
                }
                appliedHighPass = currentHighPass
            }
            repeat(chunk) {
                if (channels == 2 && (currentWidth < 0.999f || abs(currentPan) > PAN_EPSILON)) {
                    val left = filter(inputBuffer.short.toDouble(), 0, lowOn, highOn)
                    val right = filter(inputBuffer.short.toDouble(), 1, lowOn, highOn)
                    val mid = (left + right) * 0.5
                    val side = (left - right) * 0.5 * currentWidth
                    val wideLeft = mid + side
                    val wideRight = mid - side
                    val toLeft = (-currentPan).coerceAtLeast(0f)
                    val toRight = currentPan.coerceAtLeast(0f)
                    // The far side folds into the near one so panning moves the sound instead of dropping half of it.
                    output.putShort(clamp(wideLeft * (1.0 - toRight) + wideRight * toLeft * FOLD_GAIN))
                    output.putShort(clamp(wideRight * (1.0 - toLeft) + wideLeft * toRight * FOLD_GAIN))
                } else {
                    for (channel in 0 until channels) {
                        output.putShort(clamp(filter(inputBuffer.short.toDouble(), channel, lowOn, highOn)))
                    }
                }
            }
            framesDone += chunk
        }
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }

    private fun filter(input: Double, channel: Int, lowOn: Boolean, highOn: Boolean): Double {
        var sample = input
        if (lowOn) for (stage in lowPass[channel]) sample = stage.process(sample)
        if (highOn) for (stage in highPass[channel]) sample = stage.process(sample)
        return sample
    }

    private fun clamp(sample: Double): Short = sample.coerceIn(MIN_SAMPLE, MAX_SAMPLE).toInt().toShort()

    private fun advanceCutoffs(frames: Int) {
        val targetLow = lowPassHz
        val targetHigh = highPassHz
        val targetWidth = stereoWidth.coerceIn(0f, 1f)
        currentPan += ((pan - currentPan) * (1.0 - exp(-frames.toDouble() / (sampleRate * PAN_TIME_CONSTANT_S)))).toFloat()
        if (abs(currentPan - pan) < PAN_EPSILON) currentPan = pan
        if (currentLowPass == targetLow && currentHighPass == targetHigh && currentWidth == targetWidth) return
        val alpha = 1.0 - exp(-frames.toDouble() / (sampleRate * GLIDE_TIME_CONSTANT_S))
        currentLowPass = glide(currentLowPass, targetLow, alpha)
        currentHighPass = glide(currentHighPass, targetHigh, alpha)
        currentWidth += ((targetWidth - currentWidth) * alpha).toFloat()
        if (abs(currentWidth - targetWidth) < 0.001f) currentWidth = targetWidth
    }

    private fun glide(current: Float, target: Float, alpha: Double): Float {
        if (current == target) return target
        val next = exp(ln(current.toDouble()) + (ln(target.toDouble()) - ln(current.toDouble())) * alpha)
        return if (abs(next - target) < target * 0.001) target else next.toFloat()
    }

    private fun nearlyEqual(a: Float, b: Float): Boolean = abs(a - b) < b * 0.001f

    private fun isOpen(): Boolean =
        currentLowPass >= OPEN_LOW_PASS_HZ && currentHighPass <= OPEN_HIGH_PASS_HZ &&
            lowPassHz >= OPEN_LOW_PASS_HZ && highPassHz <= OPEN_HIGH_PASS_HZ &&
            (channels != 2 || currentWidth >= 0.999f && stereoWidth >= 0.999f &&
                abs(currentPan) <= PAN_EPSILON && abs(pan) <= PAN_EPSILON)

    override fun onFlush() {
        if (::lowPass.isInitialized) lowPass.forEach { channel -> channel.forEach { it.reset() } }
        if (::highPass.isInitialized) highPass.forEach { channel -> channel.forEach { it.reset() } }
        currentLowPass = lowPassHz
        currentHighPass = highPassHz
        currentWidth = stereoWidth.coerceIn(0f, 1f)
        currentPan = pan
    }

    override fun onReset() {
        channels = 0
        sampleRate = 0
        appliedLowPass = Float.NaN
        appliedHighPass = Float.NaN
        lowPassHz = OPEN_LOW_PASS_HZ
        highPassHz = OPEN_HIGH_PASS_HZ
        stereoWidth = 1f
        pan = 0f
        snapToTargets()
    }

    companion object {
        const val OPEN_LOW_PASS_HZ = 20_000f
        const val OPEN_HIGH_PASS_HZ = 20f

        const val BASS_KILL_LOW_PASS_HZ = 250f
        const val BASS_KILL_HIGH_PASS_HZ = 250f

        private const val COEFFICIENT_CHUNK_FRAMES = 128
        private const val GLIDE_TIME_CONSTANT_S = 0.35
        private const val PAN_TIME_CONSTANT_S = 0.03
        private const val PAN_EPSILON = 0.002f
        private const val FOLD_GAIN = 0.7071
        private const val MIN_SAMPLE = -32768.0
        private const val MAX_SAMPLE = 32767.0
    }
}
