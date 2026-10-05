/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud the music is, and how much of that is bass, for the player's backdrop to move with.
 *
 * Written by [LevelMeterAudioProcessor] on the playback thread, one entry per audio buffer, and
 * read by the UI a frame at a time. A small ring rather than a flow: the reader wants "what was
 * playing at this moment", not every value in order, and the writer must never wait on it.
 *
 * Each entry carries two clocks — when the buffer went through the pipeline, and the media position
 * it holds — because the pipeline runs ahead of the speaker by however much the audio track has
 * buffered (a quarter of a second to most of one). [Reader] measures that lead against the player's
 * own position and reads the entry from exactly that long ago, so a kick lights the backdrop when
 * it is heard rather than when it was decoded.
 */
object AudioLevels {
    private const val SIZE = 128

    private val wallNanos = LongArray(SIZE)
    private val mediaUs = LongArray(SIZE)
    private val loudness = FloatArray(SIZE)
    private val bass = FloatArray(SIZE)

    @Volatile
    private var head = -1

    /**
     * When something on screen last asked for levels. The meter on the playback thread only
     * measures while this is recent: with the player closed or the app in the background nothing
     * reads the ring, and working through every audio buffer for nobody is battery for nothing.
     */
    @Volatile
    internal var lastReadNanos = 0L

    internal val wanted: Boolean get() = System.nanoTime() - lastReadNanos < IDLE_AFTER_NANOS

    internal fun push(wallTimeNanos: Long, mediaPositionUs: Long, loud: Float, low: Float) {
        val next = (head + 1).mod(SIZE)
        wallNanos[next] = wallTimeNanos
        mediaUs[next] = mediaPositionUs
        loudness[next] = loud
        bass[next] = low
        head = next
    }

    /**
     * Reads the levels for what is audible now. One per player screen; it keeps the measured lead
     * between frames.
     */
    class Reader {
        private var leadNanos = DEFAULT_LEAD_NANOS

        /**
         * Fills [out] with (loudness, bass), each 0..1, for what is audible at [nowNanos] given the
         * player is at [playerPositionMs]. False when nothing recent has gone through the pipeline
         * — paused, stopped, or a format the meter doesn't read — which the caller treats as silence.
         */
        fun sample(nowNanos: Long, playerPositionMs: Long, out: FloatArray): Boolean {
            lastReadNanos = System.nanoTime()
            val latest = head
            if (latest < 0) return false
            val latestWall = wallNanos[latest]
            if (nowNanos - latestWall > STALE_NANOS) return false

            // The lead, remeasured every frame it can be: where the pipeline has got to in the
            // media, against where the player says the listener is. Only trusted when the two are
            // plausibly the same song — right after a gapless change they are not, and the lead
            // from before it still holds.
            val pipelineUs = mediaUs[latest] + (nowNanos - latestWall) / 1_000L
            val measured = (pipelineUs - playerPositionMs * 1_000L) * 1_000L
            if (measured in 0L..MAX_LEAD_NANOS) {
                leadNanos += ((measured - leadNanos) * 0.1).toLong()
            }

            val target = nowNanos - leadNanos
            var index = latest
            repeat(SIZE) {
                if (wallNanos[index] <= target) {
                    out[0] = loudness[index]
                    out[1] = bass[index]
                    return target - wallNanos[index] < STALE_NANOS
                }
                index = (index - 1).mod(SIZE)
            }
            return false
        }
    }

    private const val DEFAULT_LEAD_NANOS = 300_000_000L
    private const val MAX_LEAD_NANOS = 2_000_000_000L
    private const val STALE_NANOS = 400_000_000L
    private const val IDLE_AFTER_NANOS = 1_000_000_000L
}

/**
 * A read-only level meter in the audio chain.
 *
 * It measures and passes every byte through untouched — the output buffer is a straight copy of the
 * input, so what reaches the speaker is bit-for-bit what would have reached it without this here.
 * Anything that isn't plain 16-bit or float PCM makes it inactive, and the sink then skips it
 * entirely.
 */
@UnstableApi
class LevelMeterAudioProcessor : BaseAudioProcessor() {
    private var positionOffsetUs = 0L
    private var framesSinceFlush = 0L
    private var lowPass = 0f
    private var lowPassCoefficient = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        val readable = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (readable && inputAudioFormat.channelCount > 0 && inputAudioFormat.sampleRate > 0) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        positionOffsetUs = streamMetadata.positionOffsetUs.coerceAtLeast(0L)
        framesSinceFlush = 0L
        lowPass = 0f
        val rate = inputAudioFormat.sampleRate
        // A one-pole low-pass at about 150 Hz: the kick and the bass line, not the vocal.
        lowPassCoefficient = if (rate > 0) (1.0 - exp(-2.0 * PI * 150.0 / rate)).toFloat() else 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        measure(inputBuffer)
        replaceOutputBuffer(size).put(inputBuffer).flip()
    }

    private fun measure(input: ByteBuffer) {
        if (!AudioLevels.wanted) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val rate = format.sampleRate
        if (channels <= 0 || rate <= 0) return
        // A view with its own position and byte order: the buffer handed on is left exactly as it came.
        val view = input.duplicate().order(ByteOrder.nativeOrder())
        val float = format.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (float) 4 else 2
        val frames = view.remaining() / (bytesPerSample * channels)
        if (frames == 0) return

        var sumSquares = 0.0
        var lowSquares = 0.0
        var low = lowPass
        val coefficient = lowPassCoefficient
        var position = view.position()
        for (frame in 0 until frames) {
            var mono = 0f
            for (channel in 0 until channels) {
                mono += if (float) {
                    view.getFloat(position)
                } else {
                    view.getShort(position) / 32768f
                }
                position += bytesPerSample
            }
            mono /= channels
            sumSquares += mono * mono
            low += coefficient * (mono - low)
            lowSquares += low * low
        }
        lowPass = low

        val mediaPositionUs = positionOffsetUs + framesSinceFlush * 1_000_000L / rate
        framesSinceFlush += frames
        AudioLevels.push(
            wallTimeNanos = System.nanoTime(),
            mediaPositionUs = mediaPositionUs,
            loud = toLevel(sqrt(sumSquares / frames)),
            low = toLevel(sqrt(lowSquares / frames)),
        )
    }

    override fun onReset() {
        positionOffsetUs = 0L
        framesSinceFlush = 0L
        lowPass = 0f
    }

    /** RMS to 0..1 on a 48 dB scale, which is roughly how loud it sounds rather than how big it is. */
    private fun toLevel(rms: Double): Float {
        if (rms <= 1e-6) return 0f
        return ((20.0 * log10(rms) + 48.0) / 48.0).toFloat().coerceIn(0f, 1f)
    }
}
