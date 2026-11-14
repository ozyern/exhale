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
import kotlin.math.exp
import kotlin.math.roundToInt

/** Transition reverb for 16-bit PCM; other formats bypass it. */
@UnstableApi
class DjAmbienceAudioProcessor : BaseAudioProcessor() {
    @Volatile var wetMix = 0f
    @Volatile var reverbType = ReverbType.ROOM
    @Volatile private var clearRequested = false

    private var channels = 0
    private var sampleRate = 0
    private var currentMix = 0f
    private var mixStep = 0f
    private var damping = 0f
    private var highPassStep = 0f
    private var attack = 0f
    private var release = 0f
    private var envelope = 0f
    private var appliedType: ReverbType? = null
    private var program = ReverbProgram.forType(ReverbType.ROOM)
    private var combs = emptyArray<Array<FloatArray>>()
    private var diffusion = emptyArray<Array<FloatArray>>()
    private var early = emptyArray<FloatArray>()
    private var combPositions = emptyArray<IntArray>()
    private var diffusionPositions = emptyArray<IntArray>()
    private var combDamping = emptyArray<FloatArray>()
    private var highPassState = FloatArray(0)
    private val combLengths = IntArray(COMB_COUNT)
    private val diffusionLengths = IntArray(DIFFUSION_COUNT)
    private val feedback = FloatArray(COMB_COUNT)
    private val earlyOffsets = IntArray(EARLY_TAP_COUNT)
    private var earlyPosition = 0
    private var preDelayFrames = 0
    private var stereoSpreadFrames = 0
    private var hasTail = false
    private var streamEnded = false
    private var frameDry = FloatArray(0)
    private var frameWet = FloatArray(0)
    private var tailFramesLeft = 0

    fun stop() {
        wetMix = 0f
        clearRequested = true
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..8 || inputAudioFormat.sampleRate <= 0
        ) return AudioProcessor.AudioFormat.NOT_SET

        channels = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        val scale = sampleRate / REFERENCE_SAMPLE_RATE
        stereoSpreadFrames = (37 * scale).roundToInt().coerceAtLeast(1)
        frameDry = FloatArray(channels)
        frameWet = FloatArray(channels)
        val spreadCapacity = (channels - 1) * stereoSpreadFrames
        combs = Array(channels) { Array(COMB_COUNT) { FloatArray((MAX_COMB_FRAMES * scale).roundToInt() + spreadCapacity + 1) } }
        diffusion = Array(channels) { Array(DIFFUSION_COUNT) { FloatArray((MAX_DIFFUSION_FRAMES * scale).roundToInt() + spreadCapacity + 1) } }
        early = Array(channels) { FloatArray((sampleRate * MAX_EARLY_SECONDS).roundToInt().coerceAtLeast(1)) }
        combPositions = Array(channels) { IntArray(COMB_COUNT) }
        diffusionPositions = Array(channels) { IntArray(DIFFUSION_COUNT) }
        combDamping = Array(channels) { FloatArray(COMB_COUNT) }
        highPassState = FloatArray(channels)
        mixStep = smoothing(MIX_GLIDE_SECONDS)
        highPassStep = cutoffStep(140f)
        attack = smoothing(0.012)
        release = smoothing(0.24)
        appliedType = null
        currentMix = 0f
        applyProgram()
        return inputAudioFormat
    }

    override fun onQueueEndOfStream() {
        streamEnded = true
        tailFramesLeft = if (hasTail && channels > 0 && wetMix > 0f) (sampleRate * TAIL_SECONDS).roundToInt() else 0
    }

    override fun isEnded(): Boolean = super.isEnded() && tailFramesLeft == 0

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0 && streamEnded && tailFramesLeft > 0) {
            // Keeps the reverb ringing after the last sample by feeding the tank silence.
            val frames = minOf(tailFramesLeft, TAIL_CHUNK_FRAMES)
            tailFramesLeft -= frames
            queueInput(ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder()))
            return
        }
        if (remaining == 0 || channels == 0) return
        if (clearRequested) {
            clearRequested = false
            clearTail()
            currentMix = 0f
        }
        val output = replaceOutputBuffer(remaining)
        val targetMix = wetMix.coerceIn(0f, MAX_WET_MIX)
        if (targetMix == 0f && currentMix < SETTLED_MIX) {
            if (hasTail) clearTail()
            currentMix = 0f
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (appliedType != reverbType) applyProgram()
        inputBuffer.order(ByteOrder.nativeOrder())
        output.order(ByteOrder.nativeOrder())
        hasTail = true
        repeat(remaining / (channels * 2)) {
            currentMix += (targetMix - currentMix) * mixStep
            if (abs(currentMix - targetMix) < SETTLED_MIX) currentMix = targetMix
            var peak = 0f
            val base = inputBuffer.position()
            var sum = 0f
            for (channel in 0 until channels) {
                frameDry[channel] = inputBuffer.getShort(base + channel * 2).toFloat()
                sum += frameDry[channel]
            }
            val mid = sum / channels
            for (channel in 0 until channels) {
                peak = maxOf(peak, abs(frameDry[channel]))
                // Both sides feed one tank, so the tails come out wide and decorrelated instead of
                // two separate mono reverbs.
                frameWet[channel] = reverb(channel, frameDry[channel] + (mid - frameDry[channel]) * CROSS_FEED)
            }
            inputBuffer.position(base + channels * 2)
            if (channels == 2) {
                val wetMid = (frameWet[0] + frameWet[1]) * 0.5f
                val wetSide = (frameWet[0] - frameWet[1]) * 0.5f * WET_WIDTH
                frameWet[0] = wetMid + wetSide
                frameWet[1] = wetMid - wetSide
            }
            val duckedMix = currentMix / (1f + envelope * DUCK_AMOUNT / 32768f)
            for (channel in 0 until channels) {
                val limitedWet = frameWet[channel] / (1f + abs(frameWet[channel]) / 49152f)
                val mixed = frameDry[channel] * (1f - duckedMix) + limitedWet * duckedMix
                output.putShort(mixed.coerceIn(-32768f, 32767f).toInt().toShort())
            }
            envelope += (peak - envelope) * if (peak > envelope) attack else release
            earlyPosition = if (earlyPosition + 1 == early[0].size) 0 else earlyPosition + 1
        }
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        output.flip()
    }

    private fun reverb(channel: Int, input: Float): Float {
        highPassState[channel] += (input - highPassState[channel]) * highPassStep
        val earlyLine = early[channel]
        earlyLine[earlyPosition] = input - highPassState[channel]
        var earlySignal = 0f
        for (offset in earlyOffsets) {
            var index = earlyPosition - offset
            if (index < 0) index += earlyLine.size
            earlySignal += earlyLine[index]
        }
        earlySignal /= EARLY_TAP_COUNT
        var preDelayIndex = earlyPosition - preDelayFrames
        if (preDelayIndex < 0) preDelayIndex += earlyLine.size
        var diffused = earlyLine[preDelayIndex]
        for (index in program.inputDiffusionFrames.indices) diffused = diffuse(channel, index, diffused)

        var wet = 0f
        val channelCombs = combs[channel]
        val positions = combPositions[channel]
        val damped = combDamping[channel]
        for (index in channelCombs.indices) {
            val position = positions[index]
            val line = channelCombs[index]
            val delayed = line[position]
            damped[index] += (delayed - damped[index]) * damping
            line[position] = diffused * (1f - feedback[index]) + damped[index] * feedback[index]
            val length = combLengths[index] + channel * stereoSpreadFrames
            positions[index] = if (position + 1 == length) 0 else position + 1
            wet += delayed
        }
        wet /= COMB_COUNT
        for (index in program.outputDiffusionFrames.indices) wet = diffuse(channel, INPUT_DIFFUSION_COUNT + index, wet)
        return earlySignal * program.earlyMix + wet * (1f - program.earlyMix) * program.lateGain
    }

    private fun diffuse(channel: Int, index: Int, input: Float): Float {
        val line = diffusion[channel][index]
        val position = diffusionPositions[channel][index]
        val delayed = line[position]
        val output = delayed - program.diffusion * input
        line[position] = input + program.diffusion * output
        val length = diffusionLengths[index] + channel * stereoSpreadFrames
        diffusionPositions[channel][index] = if (position + 1 == length) 0 else position + 1
        return output
    }

    private fun applyProgram() {
        clearTail()
        val type = reverbType
        appliedType = type
        program = ReverbProgram.forType(type)
        val scale = sampleRate / REFERENCE_SAMPLE_RATE
        for (index in combLengths.indices) {
            combLengths[index] = (program.combFrames[index] * scale).roundToInt().coerceAtLeast(1)
            feedback[index] = exp(-6.907755 * combLengths[index] / sampleRate / program.decaySeconds)
                .toFloat().coerceAtMost(0.985f)
        }
        for (index in program.inputDiffusionFrames.indices) {
            diffusionLengths[index] = (program.inputDiffusionFrames[index] * scale).roundToInt().coerceAtLeast(1)
        }
        for (index in program.outputDiffusionFrames.indices) {
            diffusionLengths[INPUT_DIFFUSION_COUNT + index] =
                (program.outputDiffusionFrames[index] * scale).roundToInt().coerceAtLeast(1)
        }
        preDelayFrames = sampleRate * program.preDelayMs / 1000
        for (index in earlyOffsets.indices) earlyOffsets[index] =
            sampleRate * (program.preDelayMs + program.earlyTapsMs[index]) / 1000
        damping = cutoffStep(program.highCutHz)
    }

    private fun cutoffStep(hz: Float) = (1.0 - exp(-2.0 * PI * hz / sampleRate)).toFloat()
    private fun smoothing(seconds: Double) = (1.0 - exp(-1.0 / (sampleRate * seconds))).toFloat()

    private fun clearTail() {
        for (channel in combs.indices) {
            for (line in combs[channel]) line.fill(0f)
            for (line in diffusion[channel]) line.fill(0f)
            early[channel].fill(0f)
            combPositions[channel].fill(0)
            diffusionPositions[channel].fill(0)
            combDamping[channel].fill(0f)
        }
        highPassState.fill(0f)
        earlyPosition = 0
        envelope = 0f
        hasTail = false
    }

    override fun onFlush() {
        clearTail()
        currentMix = 0f
        streamEnded = false
        tailFramesLeft = 0
    }

    override fun onReset() {
        wetMix = 0f
        channels = 0
        sampleRate = 0
        currentMix = 0f
        combs = emptyArray()
        diffusion = emptyArray()
        early = emptyArray()
        combPositions = emptyArray()
        diffusionPositions = emptyArray()
        combDamping = emptyArray()
        highPassState = FloatArray(0)
        hasTail = false
        clearRequested = false
        appliedType = null
    }

    private companion object {
        const val REFERENCE_SAMPLE_RATE = 44_100f
        const val COMB_COUNT = 4
        const val INPUT_DIFFUSION_COUNT = 2
        const val DIFFUSION_COUNT = 5
        const val EARLY_TAP_COUNT = 4
        const val MAX_COMB_FRAMES = 2_953
        const val MAX_DIFFUSION_FRAMES = 809
        const val MAX_EARLY_SECONDS = 0.1f
        const val MIX_GLIDE_SECONDS = 0.045
        const val MAX_WET_MIX = 0.55f
        const val SETTLED_MIX = 0.0001f
        const val DUCK_AMOUNT = 1.5f
        const val TAIL_SECONDS = 8f
        const val CROSS_FEED = 0.6f
        const val WET_WIDTH = 1.5f
        const val TAIL_CHUNK_FRAMES = 4096
    }
}
