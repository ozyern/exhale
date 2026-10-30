/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * A PCM audio processor that fades in and out inside ExoPlayer's pipeline.
 * It works alongside CrossfadeAudio rather than with it: this one fades within
 * a single audio stream (the start and end of a track), while CrossfadeAudio
 * mixes two players at once across the change between songs.
 *
 * Buffers:
 *   - tailBuffer (a ring buffer): holds the last N bytes of the incoming stream,
 *     crossfadeDurationMs worth, so they can be faded out
 *     once the end of the stream is seen.
 *   - outBuffer  (a ring buffer): processed bytes waiting
 *     to be handed out through getOutput().
 *
 * Data flow:
 *   queueInput() → appendInputToTail() + drainTailToArray() → applyFadeIn/Out → enqueueOutput()
 *   getOutput()  → dequeueOutputToByteBuffer()
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

@UnstableApi
class CrossfadeAudioProcessor : AudioProcessor {

    // ── Audio format ──────────────────────────────────────────────────────────

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET

    // ── Crossfade length ──────────────────────────────────────────────────────

    /** Set from outside; takes effect on the next flush(). */
    @Volatile
    var crossfadeDurationMs: Int = 0
        set(value) {
            field = value
            pendingCrossfadeDurationMs = value
        }

    @Volatile
    private var pendingCrossfadeDurationMs: Int = 0

    private var appliedCrossfadeDurationMs: Int = 0
    private var crossfadeFrames: Int = 0
    private var bytesPerFrame: Int = 0

    // ── Fade state ────────────────────────────────────────────────────────────

    private var isEnding: Boolean = false
    private var shouldFadeInThisStream: Boolean = false
    private var shouldFadeInNextStream: Boolean = false
    private var framesOutputInStream: Long = 0L

    // ── Ring buffer: the tail ─────────────────────────────────────────────────

    private var tailBufferBytes: ByteArray = ByteArray(0)
    private var tailCapacityBytes: Int = 0
    private var tailStartIndex: Int = 0
    private var tailSizeBytes: Int = 0

    // ── Ring buffer: the output ───────────────────────────────────────────────

    private var outBufferBytes: ByteArray = ByteArray(0)
    private var outCapacityBytes: Int = 0
    private var outStartIndex: Int = 0
    private var outSizeBytes: Int = 0

    // ── Scratch space and the output ByteBuffer ───────────────────────────────

    private var scratch: ByteArray = ByteArray(0)
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER

    // ── AudioProcessor API ────────────────────────────────────────────────────

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        bytesPerFrame = inputAudioFormat.channelCount * 2
        applyCrossfadeDurationIfNeeded(force = true)
        return outputAudioFormat
    }

    override fun isActive(): Boolean =
        pendingCrossfadeDurationMs > 0 && inputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputAudioFormat == AudioFormat.NOT_SET) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        applyCrossfadeDurationIfNeeded(force = false)

        val incomingBytes = inputBuffer.remaining()
        if (incomingBytes <= 0) return

        // Sin crossfade configurado: pass-through directo
        if (crossfadeFrames <= 0 || bytesPerFrame <= 0 || tailCapacityBytes <= 0) {
            ensureScratchCapacity(incomingBytes)
            inputBuffer.get(scratch, 0, incomingBytes)
            enqueueOutput(scratch, 0, incomingBytes)
            return
        }

        if (shouldFadeInNextStream) {
            shouldFadeInThisStream = true
            shouldFadeInNextStream = false
        }

        val bytesToOutput = computeBytesToOutput(incomingBytes)
        val alignedBytesToOutput = bytesToOutput - (bytesToOutput % bytesPerFrame)

        if (alignedBytesToOutput > 0) {
            ensureScratchCapacity(alignedBytesToOutput)
            drainTailToArray(scratch, 0, alignedBytesToOutput)

            if (shouldFadeInThisStream) {
                applyFadeInPcm16(
                    data = scratch,
                    offset = 0,
                    length = alignedBytesToOutput,
                    startFrameIndex = framesOutputInStream,
                )
            }

            framesOutputInStream += (alignedBytesToOutput / bytesPerFrame).toLong()

            if (shouldFadeInThisStream && framesOutputInStream >= crossfadeFrames.toLong()) {
                shouldFadeInThisStream = false
            }

            enqueueOutput(scratch, 0, alignedBytesToOutput)
        }

        appendInputToTail(inputBuffer)
    }

    override fun getOutput(): ByteBuffer {
        applyCrossfadeDurationIfNeeded(force = false)

        if (outSizeBytes > 0) {
            return dequeueOutputToByteBuffer(outSizeBytes)
        }

        if (!isEnding) return AudioProcessor.EMPTY_BUFFER

        // End of the stream: fade out what is in the tail
        if (crossfadeFrames <= 0 || bytesPerFrame <= 0 || tailSizeBytes <= 0) {
            shouldFadeInThisStream = false
            shouldFadeInNextStream = false
            tailStartIndex = 0
            tailSizeBytes = 0
            return AudioProcessor.EMPTY_BUFFER
        }

        val alignedTailBytes = tailSizeBytes - (tailSizeBytes % bytesPerFrame)
        if (alignedTailBytes <= 0) {
            shouldFadeInThisStream = false
            shouldFadeInNextStream = false
            tailStartIndex = 0
            tailSizeBytes = 0
            return AudioProcessor.EMPTY_BUFFER
        }

        ensureScratchCapacity(alignedTailBytes)
        drainTailToArray(scratch, 0, alignedTailBytes)

        val tailFrames = alignedTailBytes / bytesPerFrame
        applyFadeOutPcm16(
            data = scratch,
            offset = 0,
            frameCount = tailFrames,
            startFrameIndex = framesOutputInStream,
        )
        framesOutputInStream += tailFrames.toLong()

        // Flag the next stream to fade in
        shouldFadeInNextStream = true
        enqueueOutput(scratch, 0, alignedTailBytes)

        return dequeueOutputToByteBuffer(outSizeBytes)
    }

    override fun isEnded(): Boolean =
        isEnding && outSizeBytes == 0 && tailSizeBytes == 0

    override fun flush() {
        val preserveFadeIn = isEnding && shouldFadeInNextStream
        framesOutputInStream = 0L
        isEnding = false
        shouldFadeInThisStream = false
        shouldFadeInNextStream = preserveFadeIn
        tailStartIndex = 0
        tailSizeBytes = 0
        outStartIndex = 0
        outSizeBytes = 0
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
        pendingCrossfadeDurationMs = 0
        appliedCrossfadeDurationMs = 0
        crossfadeDurationMs = 0
        crossfadeFrames = 0
        bytesPerFrame = 0
        shouldFadeInNextStream = false
        tailBufferBytes = ByteArray(0)
        tailCapacityBytes = 0
        tailStartIndex = 0
        tailSizeBytes = 0
        outBufferBytes = ByteArray(0)
        outCapacityBytes = 0
        outStartIndex = 0
        outSizeBytes = 0
        scratch = ByteArray(0)
        outputBuffer = AudioProcessor.EMPTY_BUFFER
    }

    override fun queueEndOfStream() {
        isEnding = true
    }

    // ── Changing the length ───────────────────────────────────────────────────

    private fun applyCrossfadeDurationIfNeeded(force: Boolean) {
        val targetMs = pendingCrossfadeDurationMs
        if (!force && targetMs == appliedCrossfadeDurationMs) return

        appliedCrossfadeDurationMs = targetMs

        val newCrossfadeFrames =
            if (inputAudioFormat != AudioFormat.NOT_SET && bytesPerFrame > 0 && targetMs > 0) {
                (inputAudioFormat.sampleRate * targetMs) / 1000
            } else {
                0
            }
        crossfadeFrames = newCrossfadeFrames

        val newCapacityBytes =
            if (newCrossfadeFrames > 0 && bytesPerFrame > 0) newCrossfadeFrames * bytesPerFrame else 0

        if (newCapacityBytes == tailCapacityBytes) return

        if (newCapacityBytes <= 0) {
            tailBufferBytes = ByteArray(0)
            tailCapacityBytes = 0
            tailStartIndex = 0
            tailSizeBytes = 0
            shouldFadeInThisStream = false
            shouldFadeInNextStream = false
            return
        }

        // Resize the tail, keeping the most recent bytes
        val newBuffer = ByteArray(newCapacityBytes)
        val bytesToCopy = min(tailSizeBytes, newCapacityBytes)

        if (bytesToCopy > 0 && tailCapacityBytes > 0) {
            val oldCapacity = tailCapacityBytes
            val tailEndExclusive = (tailStartIndex + tailSizeBytes) % oldCapacity
            val copyStartIndex =
                ((tailEndExclusive - bytesToCopy) % oldCapacity + oldCapacity) % oldCapacity

            val firstChunk = min(bytesToCopy, oldCapacity - copyStartIndex)
            System.arraycopy(tailBufferBytes, copyStartIndex, newBuffer, 0, firstChunk)
            val remaining = bytesToCopy - firstChunk
            if (remaining > 0) System.arraycopy(
                tailBufferBytes,
                0,
                newBuffer,
                firstChunk,
                remaining
            )
        }

        tailBufferBytes = newBuffer
        tailCapacityBytes = newCapacityBytes
        tailStartIndex = 0
        tailSizeBytes = bytesToCopy
    }

    // ── Ring buffer: tail ─────────────────────────────────────────────────────

    private fun computeBytesToOutput(incomingBytes: Int): Int {
        val total = tailSizeBytes + incomingBytes
        return (total - tailCapacityBytes).coerceAtLeast(0)
    }

    private fun drainTailToArray(target: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        val capacity = tailCapacityBytes
        val firstChunk = min(length, capacity - tailStartIndex)
        System.arraycopy(tailBufferBytes, tailStartIndex, target, offset, firstChunk)
        val remaining = length - firstChunk
        if (remaining > 0) System.arraycopy(
            tailBufferBytes,
            0,
            target,
            offset + firstChunk,
            remaining
        )
        tailStartIndex = (tailStartIndex + length) % capacity
        tailSizeBytes -= length
    }

    private fun appendInputToTail(inputBuffer: ByteBuffer) {
        val capacity = tailCapacityBytes
        if (capacity <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val bytesToAppend = inputBuffer.remaining()
        if (bytesToAppend <= 0) return

        var endIndex = (tailStartIndex + tailSizeBytes) % capacity
        var remaining = bytesToAppend
        while (remaining > 0) {
            val chunk = min(remaining, capacity - endIndex)
            inputBuffer.get(tailBufferBytes, endIndex, chunk)
            tailSizeBytes += chunk
            remaining -= chunk
            endIndex = (endIndex + chunk) % capacity
        }
    }

    // ── Ring buffer: salida ───────────────────────────────────────────────────

    private fun enqueueOutput(source: ByteArray, offset: Int, length: Int) {
        if (length <= 0) return
        ensureOutputCapacity(length)
        val capacity = outCapacityBytes
        var endIndex = (outStartIndex + outSizeBytes) % capacity
        var remaining = length
        var srcOffset = offset
        while (remaining > 0) {
            val chunk = min(remaining, capacity - endIndex)
            System.arraycopy(source, srcOffset, outBufferBytes, endIndex, chunk)
            srcOffset += chunk
            remaining -= chunk
            endIndex = (endIndex + chunk) % capacity
        }
        outSizeBytes += length
    }

    private fun dequeueOutputToByteBuffer(maxBytes: Int): ByteBuffer {
        val bytesToRead = min(outSizeBytes, maxBytes).coerceAtLeast(0)
        if (bytesToRead <= 0) return AudioProcessor.EMPTY_BUFFER

        val out = replaceOutputBuffer(bytesToRead)
        val capacity = outCapacityBytes
        val firstChunk = min(bytesToRead, capacity - outStartIndex)
        out.put(outBufferBytes, outStartIndex, firstChunk)
        val remaining = bytesToRead - firstChunk
        if (remaining > 0) out.put(outBufferBytes, 0, remaining)
        out.flip()

        outStartIndex = (outStartIndex + bytesToRead) % capacity
        outSizeBytes -= bytesToRead
        if (outSizeBytes == 0) outStartIndex = 0

        return out
    }

    private fun ensureOutputCapacity(additionalBytes: Int) {
        val required = outSizeBytes + additionalBytes
        if (outCapacityBytes >= required) return

        val newCapacity = when {
            outCapacityBytes <= 0 -> maxOf(16_384, required)
            else -> maxOf(outCapacityBytes * 2, required)
        }

        val newBuffer = ByteArray(newCapacity)
        if (outSizeBytes > 0) {
            val firstChunk = min(outSizeBytes, outCapacityBytes - outStartIndex)
            System.arraycopy(outBufferBytes, outStartIndex, newBuffer, 0, firstChunk)
            val remaining = outSizeBytes - firstChunk
            if (remaining > 0) System.arraycopy(outBufferBytes, 0, newBuffer, firstChunk, remaining)
        }

        outBufferBytes = newBuffer
        outCapacityBytes = newCapacity
        outStartIndex = 0
    }

    // ── DSP: fade-in / fade-out sobre PCM 16-bit ──────────────────────────────

    /**
     * Fades in [length] bytes of [data] linearly, starting at [offset].
     * The gain runs 0 → 1 across [crossfadeFrames] frames in all,
     * contando desde [startFrameIndex].
     */
    private fun applyFadeInPcm16(
        data: ByteArray,
        offset: Int,
        length: Int,
        startFrameIndex: Long,
    ) {
        if (crossfadeFrames <= 0 || bytesPerFrame <= 0) return
        val frames = length / bytesPerFrame
        var i = 0
        while (i < frames) {
            val globalFrame = startFrameIndex + i
            val gain = if (globalFrame < crossfadeFrames.toLong()) {
                globalFrame.toFloat() / crossfadeFrames.toFloat()
            } else {
                1f
            }
            scaleFramePcm16(data, offset + i * bytesPerFrame, gain)
            i++
        }
    }

    /**
     * Fades out [frameCount] frames of [data] linearly, starting at [offset].
     * With [shouldFadeInThisStream] set, it fades the incoming stream in at the same
     * time (a PCM-level crossfade within one buffer).
     */
    private fun applyFadeOutPcm16(
        data: ByteArray,
        offset: Int,
        frameCount: Int,
        startFrameIndex: Long,
    ) {
        if (frameCount <= 0 || bytesPerFrame <= 0) return

        val denom = (frameCount - 1).coerceAtLeast(1).toFloat()
        var i = 0
        while (i < frameCount) {
            val fadeOutGain = if (frameCount <= 1) 0f else 1f - (i.toFloat() / denom)

            val globalFrame = startFrameIndex + i
            val fadeInGain = if (shouldFadeInThisStream && crossfadeFrames > 0 &&
                globalFrame < crossfadeFrames.toLong()
            ) {
                globalFrame.toFloat() / crossfadeFrames.toFloat()
            } else {
                1f
            }

            scaleFramePcm16(data, offset + i * bytesPerFrame, fadeInGain * fadeOutGain)
            i++
        }
    }

    /**
     * Scales one 16-bit little-endian PCM frame by [gain].
     * Covers every channel in the frame ([bytesPerFrame] bytes).
     */
    private fun scaleFramePcm16(data: ByteArray, frameOffset: Int, gain: Float) {
        if (gain == 1f) return
        var byteIndex = frameOffset
        val frameEnd = frameOffset + bytesPerFrame
        while (byteIndex < frameEnd) {
            val lo = data[byteIndex].toInt() and 0xFF
            val hi = data[byteIndex + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort().toInt()
            val scaled = (sample * gain).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            data[byteIndex] = (scaled and 0xFF).toByte()
            data[byteIndex + 1] = ((scaled shr 8) and 0xFF).toByte()
            byteIndex += 2
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun ensureScratchCapacity(size: Int) {
        if (scratch.size < size) scratch = ByteArray(size)
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        return if (outputBuffer.capacity() < size) {
            ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
                .also { outputBuffer = it }
        } else {
            outputBuffer.clear()
            outputBuffer
        }
    }
}