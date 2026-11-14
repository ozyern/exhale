/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ozyern.exhale.playback.dj.BeatGrid
import com.ozyern.exhale.playback.dj.BeatTracker
import com.ozyern.exhale.playback.dj.KeyDetector
import com.ozyern.exhale.playback.dj.MusicalKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** PCM tap with track-scoped analysis snapshots; estimation runs off the audio thread. */
@UnstableApi
class TrackAnalyzerAudioProcessor(
    private val onAnalysisReady: () -> Unit,
    private val analysisEnabled: () -> Boolean = { true },
) : AudioProcessor {
    private val lock = Any()
    private var format = AudioProcessor.AudioFormat.NOT_SET
    private var pendingFormat = AudioProcessor.AudioFormat.NOT_SET
    private var generation = 0L
    @Volatile private var run: AnalysisRun? = null
    private var mediaId: String? = null
    private var startPositionMs = 0L
    private var processedFrames = 0L
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    class AnalysisInput internal constructor(
        val generation: Long,
        val mediaId: String?,
        private val tracker: BeatTracker,
        private val keyDetector: KeyDetector,
        private val energyProfile: ByteArray,
    ) {
        private val result by lazy {
            AnalysisSnapshot(generation, mediaId, tracker.estimate(), keyDetector.estimate(), energyProfile)
        }
        internal fun compute(): AnalysisSnapshot = result
    }

    data class AnalysisSnapshot(
        val generation: Long,
        val mediaId: String?,
        val grid: BeatGrid,
        val key: MusicalKey,
        val energyProfile: ByteArray,
    )

    data class EnergySnapshot(
        val generation: Long,
        val mediaId: String?,
        val energyProfile: ByteArray,
    )

    private class AnalysisRun(
        val generation: Long,
        val mediaId: String?,
        val format: AudioProcessor.AudioFormat,
        startPositionMs: Long,
    ) {
        val tracker = BeatTracker(format.sampleRate).also { it.beginAt(startPositionMs) }
        val keyDetector = KeyDetector(format.sampleRate)
        val bytesPerSample = when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            else -> 4
        }
        val bigEndian = format.encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            format.encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            format.encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN
        var analysisInput: AnalysisInput? = null
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        synchronized(lock) {
            pendingFormat = inputAudioFormat
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = supports(pendingFormat)

    val energyProfile: ByteArray get() = energySnapshot()?.energyProfile ?: EMPTY_PROFILE

    fun captureAnalysisInput(): AnalysisInput? = synchronized(lock) { run?.analysisInput }

    fun energySnapshot(): EnergySnapshot? = synchronized(lock) {
        run?.let { EnergySnapshot(it.generation, it.mediaId, it.tracker.energyProfile()) }
    }

    fun isCurrentAnalysis(generation: Long): Boolean = run?.generation == generation

    fun computeAnalysis(input: AnalysisInput): AnalysisSnapshot = input.compute()

    fun computeAnalysis(): Pair<BeatGrid, MusicalKey> {
        val result = captureAnalysisInput()?.compute()
        return (result?.grid ?: BeatGrid.NONE) to (result?.key ?: MusicalKey.NONE)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount == 0) return
        val enabled = analysisEnabled()
        val ready = synchronized(lock) {
            if (enabled && run == null && supports(format)) {
                generation++
                val position = startPositionMs + processedFrames * 1000L / format.sampleRate
                run = AnalysisRun(generation, mediaId, format, position)
            } else if (!enabled && run != null) {
                generation++
                run = null
            }
            val analysisReady = if (enabled) analyze(inputBuffer) else false
            if (format.bytesPerFrame > 0) processedFrames += byteCount / format.bytesPerFrame
            analysisReady
        }
        outputBuffer = inputBuffer.slice().order(ByteOrder.nativeOrder())
        inputBuffer.position(inputBuffer.limit())
        if (ready) onAnalysisReady()
    }

    private fun analyze(buffer: ByteBuffer): Boolean {
        val current = run ?: return false
        val channels = current.format.channelCount
        val sampleSize = current.bytesPerSample
        val frameCount = buffer.remaining() / (sampleSize * channels)
        val basePosition = buffer.position()
        buffer.order(if (current.bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        repeat(frameCount) { frame ->
            var sum = 0f
            repeat(channels) { channel ->
                val index = basePosition + (frame * channels + channel) * sampleSize
                sum += readSample(buffer, index, current)
            }
            val mixed = sum / channels
            val mono = if (mixed.isFinite()) mixed else 0f
            current.tracker.accept(mono)
            if (current.analysisInput == null) current.keyDetector.accept(mono)
        }
        if (current.analysisInput != null || !current.tracker.isAnalysisComplete) return false

        // Completed envelopes are never reset or written again; only energy keeps accumulating.
        current.analysisInput = AnalysisInput(
            current.generation,
            current.mediaId,
            current.tracker,
            current.keyDetector,
            current.tracker.energyProfile(),
        )
        return true
    }

    private fun readSample(buffer: ByteBuffer, index: Int, current: AnalysisRun): Float = when {
        current.format.encoding == C.ENCODING_PCM_FLOAT -> buffer.getFloat(index)
        current.bytesPerSample == 2 -> buffer.getShort(index) / 32768f
        current.bytesPerSample == 3 -> {
            val lowIndex = if (current.bigEndian) index + 2 else index
            val highIndex = if (current.bigEndian) index else index + 2
            val low = buffer.get(lowIndex).toInt() and 0xFF
            val middle = buffer.get(index + 1).toInt() and 0xFF
            val high = buffer.get(highIndex).toInt()
            ((high shl 16) or (middle shl 8) or low) / 8388608f
        }
        else -> buffer.getInt(index) / 2147483648f
    }

    fun resetForTrack(startPositionMs: Long = 0L, mediaId: String? = null) {
        synchronized(lock) {
            this.startPositionMs = startPositionMs.coerceAtLeast(0L)
            this.mediaId = mediaId
            beginAnalysis()
        }
    }

    private fun beginAnalysis() {
        generation++
        processedFrames = 0L
        run = if (supports(format) && analysisEnabled()) {
            AnalysisRun(generation, mediaId, format, startPositionMs)
        } else null
    }

    private fun supports(format: AudioProcessor.AudioFormat): Boolean =
        format.sampleRate > 0 && format.channelCount > 0 && when (format.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN,
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN,
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> true
            else -> false
        }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer = outputBuffer.also { outputBuffer = AudioProcessor.EMPTY_BUFFER }

    override fun isEnded(): Boolean = inputEnded && !outputBuffer.hasRemaining()

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        synchronized(lock) {
            if (format != pendingFormat) {
                format = pendingFormat
                beginAnalysis()
            }
        }
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        synchronized(lock) {
            format = AudioProcessor.AudioFormat.NOT_SET
            pendingFormat = AudioProcessor.AudioFormat.NOT_SET
            startPositionMs = 0L
            mediaId = null
            beginAnalysis()
        }
    }

    private companion object {
        val EMPTY_PROFILE = ByteArray(0)
    }
}
