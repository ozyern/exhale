package com.ozyern.exhale.playback.audio

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/** Route encoded audio through a decoder whenever the processing chain is needed. */
@UnstableApi
internal class PcmProcessingAudioSink(
    sink: AudioSink,
    private val allowOffload: () -> Boolean,
) : ForwardingAudioSink(sink) {
    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int =
        if (format.sampleMimeType == MimeTypes.AUDIO_RAW || allowOffload()) super.getFormatSupport(format)
        else AudioSink.SINK_FORMAT_UNSUPPORTED
}
