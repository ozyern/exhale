/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The idea is BitChord's PrecisionAudioSink (github.com/kushagrasinghx/BitChord, GPL-3.0): run the
 * app's own DSP in front of Media3's sink, so float output stops costing the DSP. This is a
 * smaller take on it, built on Media3's own AudioProcessingPipeline and Exhale's processors.
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * Exhale's processors — equaliser, Automix's filter sweeps, the spatialiser and the level meter —
 * run *here*, ahead of [delegate], on whatever PCM the decoder produces.
 *
 * ### Why this exists
 *
 * They used to sit in `DefaultAudioSink`'s processor chain, and that chain only runs on the sink's
 * 16-bit path: with float output on, `DefaultAudioSink.configure` puts a float converter in the
 * pipeline and *nothing else*. Float output was on by default, so the decoder handed over float,
 * and every one of them was silently skipped — no equaliser, no spatial stage, and Automix
 * reduced to a volume fade because its filter sweeps and bass swap never touched a sample. And
 * because the float path then carried a 16-bit-sourced stream anyway, the precision switch itself
 * changed nothing you could hear.
 *
 * ### What float means now
 *
 * [floatAllowed] decides, per track, whether the decoder is told float is welcome. When it is, the
 * decoder's float output goes through the processors in float and reaches the AudioTrack in float,
 * with no quantisation anywhere between the codec and the mixer. When it is not, the decoder
 * produces 16-bit and the whole chain runs as it always did. The built-in speaker is kept on
 * 16-bit: OEM speaker mixers are where float has caused distortion.
 */
@UnstableApi
class PrecisionAudioSink(
    private val delegate: AudioSink,
    processors: List<AudioProcessor>,
    private val floatAllowed: () -> Boolean,
) : ForwardingAudioSink(delegate) {

    private val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(processors))

    /** Whether the configured stream goes through [pipeline], or straight to [delegate]. */
    private var processing = false

    /** Processed bytes the delegate has not taken yet, and the time they were stamped with. */
    private var pending: ByteBuffer? = null
    private var pendingTimeUs = C.TIME_UNSET
    private var endOfStreamQueued = false

    override fun getFormatSupport(format: Format): Int {
        // The renderer asks this about float PCM to decide whether to request float from the
        // decoder at all. "Directly" only where float will really reach the track.
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && format.pcmEncoding == C.ENCODING_PCM_FLOAT && !floatAllowed()) {
            return AudioSink.SINK_FORMAT_SUPPORTED_WITH_TRANSCODING
        }
        return delegate.getFormatSupport(format)
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        clearPending()
        val linear = MimeTypes.AUDIO_RAW == inputFormat.sampleMimeType &&
            Util.isEncodingLinearPcm(inputFormat.pcmEncoding) &&
            (inputFormat.pcmEncoding == C.ENCODING_PCM_16BIT || inputFormat.pcmEncoding == C.ENCODING_PCM_FLOAT) &&
            inputFormat.channelCount in 1..8 && inputFormat.sampleRate > 0
        if (linear) {
            val out = runCatching { pipeline.configure(AudioProcessor.AudioFormat(inputFormat)) }.getOrNull()
            if (out != null && out != AudioProcessor.AudioFormat.NOT_SET) {
                pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
                if (pipeline.isOperational) {
                    val processed = inputFormat.buildUpon()
                        .setPcmEncoding(out.encoding)
                        .setSampleRate(out.sampleRate)
                        .setChannelCount(out.channelCount)
                        .build()
                    delegate.configure(processed, specifiedBufferSize, outputChannels)
                    processing = true
                    return
                }
            }
        }
        processing = false
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (!processing) return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        while (true) {
            // What the delegate refused last time goes first, and until it is taken nothing new
            // is processed: the pipeline reuses its output buffer.
            pending?.let { out ->
                if (out.hasRemaining()) {
                    delegate.handleBuffer(out, pendingTimeUs, 1)
                    if (out.hasRemaining()) return false
                }
                pending = null
            }
            if (!buffer.hasRemaining()) return true
            val before = buffer.remaining()
            pipeline.queueInput(buffer)
            val out = pipeline.getOutput()
            if (out.hasRemaining()) {
                pending = out
                pendingTimeUs = presentationTimeUs
            } else if (buffer.remaining() == before) {
                // Nothing taken and nothing made: wait for the next call rather than spin.
                return false
            }
        }
    }

    override fun playToEndOfStream() {
        if (processing) {
            if (!endOfStreamQueued) {
                pipeline.queueEndOfStream()
                endOfStreamQueued = true
            }
            while (true) {
                val out = pending?.takeIf { it.hasRemaining() } ?: pipeline.getOutput().also { pending = it }
                if (!out.hasRemaining()) break
                delegate.handleBuffer(out, pendingTimeUs, 1)
                if (out.hasRemaining()) return
            }
        }
        delegate.playToEndOfStream()
    }

    override fun isEnded(): Boolean =
        if (processing) {
            pending?.hasRemaining() != true &&
                (!pipeline.isOperational || !endOfStreamQueued || pipeline.isEnded) &&
                delegate.isEnded
        } else {
            delegate.isEnded
        }

    override fun hasPendingData(): Boolean =
        (processing && pending?.hasRemaining() == true) || delegate.hasPendingData()

    override fun flush() {
        clearPending()
        if (processing) pipeline.flush(AudioProcessor.StreamMetadata.DEFAULT)
        delegate.flush()
    }

    override fun reset() {
        clearPending()
        pipeline.reset()
        processing = false
        delegate.reset()
    }

    private fun clearPending() {
        pending = null
        pendingTimeUs = C.TIME_UNSET
        endOfStreamQueued = false
    }
}
