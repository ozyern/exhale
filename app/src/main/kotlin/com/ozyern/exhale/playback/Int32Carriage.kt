/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.media.AudioFormat
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOutput
import androidx.media3.exoplayer.audio.AudioOutputProvider
import androidx.media3.exoplayer.audio.ForwardingAudioOutputProvider
import java.nio.ByteBuffer
import kotlin.math.roundToLong

/**
 * 24-bit to a USB DAC whose only bit-perfect modes are integer.
 *
 * Media3's sink writes 16-bit or float PCM and nothing else, so a DAC that offers 16- and 32-bit
 * integer modes but no float one used to get a 24-bit file dithered down to 16. A 32-bit integer
 * frame is the same size as a float frame, though, and the sink only ever hands the AudioTrack raw
 * bytes. So the samples are made 32-bit integers at the end of the bit-perfect chain
 * ([ToInt32Processor]) and travel the sink labelled float; [Provider] opens the AudioTrack as
 * `ENCODING_PCM_32BIT` instead of float. A 24-bit sample sits in the top 24 bits unchanged, and a
 * 16-bit one in the top 16: the file's own samples, bit for bit.
 *
 * [armed] is true only while the configured track really carries integers this way: the provider
 * must never reinterpret real float.
 */
@UnstableApi
object Int32Carriage {

    @Volatile
    var armed: Boolean = false
        internal set

    /**
     * Float or 16-bit PCM in, 32-bit integers out, declared as float so the sink passes them through.
     * Inactive (and so skipped) unless [active].
     */
    class ToInt32Processor(private val active: () -> Boolean) : BaseAudioProcessor() {
        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (!active()) return AudioProcessor.AudioFormat.NOT_SET
            if (inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT && inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
                return AudioProcessor.AudioFormat.NOT_SET
            }
            return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount, C.ENCODING_PCM_FLOAT)
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                val samples = inputBuffer.remaining() / 2
                if (samples == 0) return
                val out = replaceOutputBuffer(samples * 4)
                repeat(samples) { out.putInt(inputBuffer.short.toInt() shl 16) }
                out.flip()
                return
            }
            val samples = inputBuffer.remaining() / 4
            if (samples == 0) return
            val out = replaceOutputBuffer(samples * 4)
            repeat(samples) { out.putInt(toInt32(inputBuffer.float)) }
            out.flip()
        }
    }

    /**
     * A float sample as a 32-bit integer. Decoders make float by dividing by 2^15 (16-bit) or 2^23
     * (24-bit), so multiplying by 2^31 puts those samples back exactly, shifted to the top.
     */
    internal fun toInt32(sample: Float): Int =
        (sample.toDouble() * 2_147_483_648.0).roundToLong()
            .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    /** Opens float AudioTracks as 32-bit integer ones while [armed]; otherwise changes nothing. */
    class Provider(delegate: AudioOutputProvider) : ForwardingAudioOutputProvider(delegate) {
        override fun getAudioOutput(config: AudioOutputProvider.OutputConfig): AudioOutput {
            val carried = armed && config.encoding == AudioFormat.ENCODING_PCM_FLOAT &&
                !config.isOffload && !config.isTunneling
            return super.getAudioOutput(
                if (carried) config.buildUpon().setEncoding(AudioFormat.ENCODING_PCM_32BIT).build() else config,
            )
        }
    }
}
