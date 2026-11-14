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
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Bounded stereo delay with program-specific feedback and transient ducking. */
@UnstableApi
class DelayAudioProcessor(
    initialDelayMs: Long = 220L,
    private val feedback: Float = 0.78f,
) : AudioProcessor {
    private val initialDelayMs = initialDelayMs.coerceIn(1L, MAX_DELAY_MS)
    @Volatile private var delayMs = this.initialDelayMs
    @Volatile var delayType: DelayType = DelayType.AUTO
    @Volatile var wetMix: Float = 0f
    @Volatile var frozen: Boolean = false
    @Volatile var freezeFeedback: Float = 0.72f

    /** Fades the incoming signal into the delay line while leaving what is already ringing to decay. */
    @Volatile var dryLevel: Float = 1f

    private var sampleRate = 0
    private var channelCount = 0
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var ring = FloatArray(0)
    private var ringFrames = 0
    private var writeFrame = 0
    private var delayFrames = 1
    private var previousDelayFrames = 1
    private var retuneRemaining = 0
    private var retuneFrames = 1
    private var active = false
    private var configuredType = DelayType.AUTO
    private var lowpassCoefficient = 0f
    private var highpassCoefficient = 0f
    private var envelopeAttack = 0f
    private var envelopeRelease = 0f
    private var envelope = 0f
    private val lowpassState = FloatArray(2)
    private val highpassInput = FloatArray(2)
    private val highpassState = FloatArray(2)
    private val tailRevision = AtomicLong()
    private var appliedTailRevision = 0L
    private var processingBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false
    private var tailFramesLeft = 0

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        pendingFormat = inputAudioFormat
        return inputAudioFormat
    }

    private fun applyFormat(inputAudioFormat: AudioProcessor.AudioFormat) {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.sampleRate > 0 && inputAudioFormat.channelCount in 1..2
        sampleRate = if (supported) inputAudioFormat.sampleRate else 0
        channelCount = if (supported) inputAudioFormat.channelCount else 0
        if (supported) {
            ringFrames = (sampleRate * MAX_DELAY_MS / 1000L).toInt().coerceAtLeast(2)
            if (ring.size != ringFrames * channelCount) ring = FloatArray(ringFrames * channelCount)
            retuneFrames = (sampleRate * RETUNE_SECONDS).toInt().coerceAtLeast(1)
            envelopeAttack = (1.0 - exp(-1.0 / (sampleRate * 0.012))).toFloat()
            envelopeRelease = (1.0 - exp(-1.0 / (sampleRate * 0.18))).toFloat()
            configureProgram(resolvedType())
        } else {
            ring = FloatArray(0)
            ringFrames = 0
        }
        resetAudioState()
    }

    override fun isActive(): Boolean = pendingFormat.encoding == C.ENCODING_PCM_16BIT &&
        pendingFormat.sampleRate > 0 && pendingFormat.channelCount in 1..2

    fun setDelayMs(ms: Long) {
        delayMs = ms.coerceIn(1L, MAX_DELAY_MS)
    }

    fun clearTail() {
        tailRevision.incrementAndGet()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) {
            if (inputEnded && tailFramesLeft > 0) renderTail()
            return
        }
        val revision = tailRevision.get()
        if (appliedTailRevision != revision) {
            resetAudioState()
            appliedTailRevision = revision
        }
        val mix = wetMix.takeIf(Float::isFinite)?.coerceIn(0f, MAX_WET_MIX) ?: 0f
        val freeze = frozen
        if (sampleRate <= 0 || channelCount <= 0 || (mix == 0f && !freeze)) {
            if (active) resetAudioState()
            outputBuffer = inputBuffer.slice().order(ByteOrder.nativeOrder())
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val out = replaceOutputBuffer(byteCount)
        val program = resolvedType()
        if (configuredType != program) configureProgram(program)
        updateDelay(program)
        active = true
        inputBuffer.order(ByteOrder.nativeOrder())
        val frameCount = byteCount / (channelCount * Short.SIZE_BYTES)
        val normalFeedback = if (program == DelayType.SLAPBACK) 0f else
            feedback.takeIf(Float::isFinite)?.coerceIn(0f, MAX_FEEDBACK) ?: 0f
        val tailFeedback = freezeFeedback.takeIf(Float::isFinite)?.coerceIn(0f, MAX_FEEDBACK) ?: 0f
        val dryGain = 1f - mix * 0.25f
        val level = dryLevel.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 1f

        repeat(frameCount) {
            val left = inputBuffer.short / PCM_SCALE * level
            val right = if (channelCount == 2) inputBuffer.short / PCM_SCALE * level else left
            val peak = max(abs(left), abs(right))
            val envelopeCoefficient = if (peak > envelope) envelopeAttack else envelopeRelease
            envelope += envelopeCoefficient * (peak - envelope)
            val duck = 1f / (1f + 1.8f * envelope)
            val readFrame = wrappedFrame(writeFrame - delayFrames)
            val previousReadFrame = wrappedFrame(writeFrame - previousDelayFrames)
            val tapBlend = if (retuneRemaining > 0) 1f - retuneRemaining.toFloat() / retuneFrames else 1f

            var wetLeft = 0f
            var wetRight = 0f
            repeat(channelCount) { channel ->
                val sourceChannel = if (program == DelayType.PING_PONG && channelCount == 2) 1 - channel else channel
                val delayed = ring[previousReadFrame * channelCount + sourceChannel] * (1f - tapBlend) +
                    ring[readFrame * channelCount + sourceChannel] * tapBlend
                val filtered = filterFeedback(delayed, channel, program)
                val gain = if (freeze) tailFeedback else normalFeedback
                val dry = if (channel == 0) left else right
                var fed = (if (freeze) 0f else dry) + filtered * gain
                if (program == DelayType.TAPE) fed = tapeSaturate(fed)
                ring[writeFrame * channelCount + channel] = fed.coerceIn(-1.5f, 1.5f)
                val wet = if (freeze) filtered * tailFeedback else filtered * mix * duck
                if (channel == 0) wetLeft = wet else wetRight = wet
                if (channelCount == 1) out.putShort(toPcm((if (freeze) 0f else dry * dryGain) + wet))
            }
            if (channelCount == 2) {
                val wetMid = (wetLeft + wetRight) * 0.5f
                val wetSide = (wetLeft - wetRight) * 0.5f * WET_WIDTH
                out.putShort(toPcm((if (freeze) 0f else left * dryGain) + wetMid + wetSide))
                out.putShort(toPcm((if (freeze) 0f else right * dryGain) + wetMid - wetSide))
            }
            if (++writeFrame == ringFrames) writeFrame = 0
            if (retuneRemaining > 0) retuneRemaining--
        }
        out.put(inputBuffer)
        out.flip()
    }

    private fun resolvedType(): DelayType = delayType.let {
        if (it == DelayType.AUTO) DelayType.STEREO else it
    }

    private fun configureProgram(program: DelayType) {
        configuredType = program
        val lowpassHz = when (program) {
            DelayType.TAPE -> 3500.0
            DelayType.FILTERED -> 2400.0
            else -> 8500.0
        }.coerceAtMost(sampleRate * 0.4)
        lowpassCoefficient = (1.0 - exp(-2.0 * PI * lowpassHz / sampleRate)).toFloat()
        highpassCoefficient = exp(-2.0 * PI * 180.0 / sampleRate).toFloat()
        lowpassState.fill(0f)
        highpassInput.fill(0f)
        highpassState.fill(0f)
    }

    private fun updateDelay(program: DelayType) {
        val ms = if (program == DelayType.SLAPBACK) delayMs.coerceIn(80L, 160L) else delayMs
        val target = (sampleRate * ms / 1000L).toInt().coerceIn(1, ringFrames - 1)
        if (target == delayFrames) return
        previousDelayFrames = delayFrames
        delayFrames = target
        retuneRemaining = if (active) retuneFrames else 0
    }

    private fun filterFeedback(value: Float, channel: Int, program: DelayType): Float {
        lowpassState[channel] += lowpassCoefficient * (value - lowpassState[channel])
        val lowpass = lowpassState[channel]
        if (program != DelayType.FILTERED) return lowpass
        val highpass = highpassCoefficient * (highpassState[channel] + lowpass - highpassInput[channel])
        highpassInput[channel] = lowpass
        highpassState[channel] = highpass
        return highpass
    }

    private fun wrappedFrame(frame: Int): Int = if (frame < 0) frame + ringFrames else frame

    private fun tapeSaturate(value: Float): Float {
        val limited = value.coerceIn(-3f, 3f)
        val squared = limited * limited
        return limited * (27f + squared) / (27f + 9f * squared)
    }

    private fun toPcm(value: Float): Short {
        val magnitude = abs(value)
        val shaped = if (magnitude > 0.9f) {
            val excess = (magnitude - 0.9f) / 0.1f
            val limited = 0.9f + 0.1f * excess / (1f + excess)
            if (value < 0f) -limited else limited
        } else value
        return (shaped * PCM_SCALE).toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private fun resetAudioState() {
        ring.fill(0f)
        lowpassState.fill(0f)
        highpassInput.fill(0f)
        highpassState.fill(0f)
        envelope = 0f
        writeFrame = 0
        active = false
        retuneRemaining = 0
        if (ringFrames > 1) {
            val ms = if (resolvedType() == DelayType.SLAPBACK) delayMs.coerceIn(80L, 160L) else delayMs
            delayFrames = (sampleRate * ms / 1000L).toInt().coerceIn(1, ringFrames - 1)
            previousDelayFrames = delayFrames
        }
    }

    override fun queueEndOfStream() {
        inputEnded = true
        tailFramesLeft = if (active && wetMix > 0f && sampleRate > 0) (sampleRate * TAIL_SECONDS).toInt() else 0
    }

    /** Keeps the echoes ringing after the track's last sample by feeding the line silence. */
    private fun renderTail() {
        val frames = minOf(tailFramesLeft, TAIL_CHUNK_FRAMES)
        tailFramesLeft -= frames
        queueInput(ByteBuffer.allocate(frames * channelCount * Short.SIZE_BYTES).order(ByteOrder.nativeOrder()))
    }

    override fun getOutput(): ByteBuffer = outputBuffer.also { outputBuffer = AudioProcessor.EMPTY_BUFFER }

    override fun isEnded(): Boolean = inputEnded && tailFramesLeft == 0 && !outputBuffer.hasRemaining()

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        tailFramesLeft = 0
        applyFormat(pendingFormat)
        appliedTailRevision = tailRevision.get()
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        pendingFormat = AudioProcessor.AudioFormat.NOT_SET
        flush()
        sampleRate = 0
        channelCount = 0
        ring = FloatArray(0)
        ringFrames = 0
        processingBuffer = AudioProcessor.EMPTY_BUFFER
        delayMs = initialDelayMs
        delayType = DelayType.AUTO
        wetMix = 0f
        frozen = false
        freezeFeedback = 0.72f
        dryLevel = 1f
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (processingBuffer.capacity() < size) {
            processingBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            processingBuffer.clear()
        }
        outputBuffer = processingBuffer
        return outputBuffer
    }

    private companion object {
        const val MAX_DELAY_MS = 2000L
        const val MAX_FEEDBACK = 0.92f
        const val MAX_WET_MIX = 0.7f
        const val PCM_SCALE = 32768f
        const val RETUNE_SECONDS = 0.02f
        const val TAIL_SECONDS = 8f
        const val WET_WIDTH = 1.4f
        const val TAIL_CHUNK_FRAMES = 4096
    }
}
