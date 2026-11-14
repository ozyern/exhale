package com.ozyern.exhale.playback.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.lang.reflect.Proxy

class DjModuleAudioTest {
    @Test fun `mixing forces decoded PCM even if the device advertises Dolby passthrough`() {
        val delegate = Proxy.newProxyInstance(AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, _ ->
            when (method.name) {
                "getFormatSupport" -> AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
                "supportsFormat" -> true
                else -> null
            }
        } as AudioSink
        var offloadAllowed = false
        val sink = PcmProcessingAudioSink(delegate) { offloadAllowed }
        val atmos = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_E_AC3_JOC).build()
        val pcm = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).build()
        assertFalse(sink.supportsFormat(atmos))
        assertEquals(AudioSink.SINK_FORMAT_UNSUPPORTED, sink.getFormatSupport(atmos))
        assertTrue(sink.supportsFormat(pcm))
        offloadAllowed = true
        assertTrue(sink.supportsFormat(atmos))
        offloadAllowed = false
        assertFalse(sink.supportsFormat(atmos))
    }

    @Test fun `idle DJ effects preserve decoded FLAC and multichannel Atmos samples`() {
        for ((rate, channels) in listOf(96_000 to 2, 48_000 to 6, 48_000 to 8)) {
            val format = AudioProcessor.AudioFormat(rate, channels, C.ENCODING_PCM_16BIT)
            for (processor in listOf(DjFilterAudioProcessor(), DjTailAudioProcessor())) {
                assertEquals(format, processor.configure(format))
                processor.flush()
                assertTrue(processor.isActive)
                val source = samples(channels)
                val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
                processor.queueInput(source)
                val output = processor.output
                assertArrayEquals(expected, ByteArray(output.remaining()).also { output.get(it) })
                processor.reset()
            }
        }
    }

    @Test fun `Atmos DJ filter keeps channels independent while applying an effect`() {
        val processor = DjFilterAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(48_000, 8, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.lowPassHz = 1000f
        processor.snapToTargets()
        val input = ByteBuffer.allocateDirect(128 * 8 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(128) { frame -> repeat(8) { channel -> input.putShort(if (frame == 0 && channel == 0) 20_000 else 0) } }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(128 * 8 * 2, output.remaining())
        repeat(128) { repeat(8) { channel -> val sample = output.short; if (channel != 0) assertEquals(0, sample.toInt()) } }
    }

    @Test fun `DJ analyzer observes Atmos PCM without altering any channel`() {
        val processor = TrackAnalyzerAudioProcessor(onAnalysisReady = {})
        processor.configure(AudioProcessor.AudioFormat(48_000, 8, C.ENCODING_PCM_16BIT))
        processor.flush()
        val source = samples(8)
        val expected = ByteArray(source.remaining()).also { source.duplicate().get(it) }
        processor.queueInput(source)
        val output = processor.output
        assertArrayEquals(expected, ByteArray(output.remaining()).also { output.get(it) })
        processor.reset()
    }

    private fun samples(channels: Int): ByteBuffer = ByteBuffer.allocateDirect(128 * channels * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(128) { frame -> repeat(channels) { channel -> putShort((frame * 71 + channel * 223).toShort()) } }
            flip()
        }
}
