package com.ozyern.exhale.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityLabelTest {
    @Test
    fun `apple wording for lossless, hi-res and atmos`() {
        assertEquals(AudioQuality("Lossless", true), audioQualityLabel("audio/flac", null, 900_000, 44_100))
        assertEquals(AudioQuality("Hi-Res Lossless", true, hiRes = true), audioQualityLabel("audio/flac", null, null, 96_000))
        assertEquals(AudioQuality("Dolby Atmos", false, atmos = true), audioQualityLabel("audio/mp4", "ec-3", 768_000, 48_000))
    }

    @Test
    fun `lossy keeps codec and bitrate, unknown hides the badge`() {
        assertEquals(AudioQuality("Opus 151 kbps", false), audioQualityLabel("audio/webm; codecs=\"opus\"", null, 151_018, 48_000))
        assertEquals(AudioQuality("AAC", false), audioQualityLabel("audio/mp4", "mp4a.40.2", null, null))
        assertNull(audioQualityLabel(null, null, null, null))
    }

    @Test
    fun `card detail line`() {
        assertEquals("96 kHz FLAC", audioQualityDetail("audio/flac", null, null, 96_000))
        assertEquals("44.1 kHz ALAC", audioQualityDetail("audio/mp4", "alac", null, 44_100))
        assertEquals("Opus · 151 kbps · 48 kHz", audioQualityDetail("audio/webm", "opus", 151_018, 48_000))
        assertEquals("24-bit/96 kHz FLAC", audioQualityDetail("audio/flac", null, null, 96_000, bitDepth = 24))
        assertEquals("16-bit/44.1 kHz FLAC", audioQualityDetail("audio/flac", null, null, 44_100, bitDepth = 16))
    }

    @Test
    fun `16-bit up to 48 kHz is lossless, 24-bit is hi-res at any rate`() {
        assertEquals(AudioQuality("Lossless", true), audioQualityLabel("audio/flac", null, null, 48_000))
        assertEquals(AudioQuality("Lossless", true), audioQualityLabel("audio/flac", null, null, 44_100, bitDepth = 16))
        val hiRes = AudioQuality("Hi-Res Lossless", true, hiRes = true)
        assertEquals(hiRes, audioQualityLabel("audio/flac", null, null, 44_100, bitDepth = 24))
        assertEquals(hiRes, audioQualityLabel("audio/flac", null, null, 48_000, bitDepth = 24))
    }

    @Test
    fun `spec sheet lists every known fact and derives lossless bitrate`() {
        val specs = audioQualitySpecs("audio/flac", null, null, 96_000, 24, 2, "Internet Archive").toMap()
        assertEquals("Hi-Res Lossless (above 16-bit or 48 kHz)", specs[AudioSpec.QUALITY])
        assertEquals("FLAC", specs[AudioSpec.CODEC])
        assertEquals("24-bit", specs[AudioSpec.BIT_DEPTH])
        assertEquals("96 kHz", specs[AudioSpec.SAMPLE_RATE])
        assertEquals("Stereo", specs[AudioSpec.CHANNELS])
        assertEquals("4608 kbps", specs[AudioSpec.BITRATE])
        assertEquals("Internet Archive", specs[AudioSpec.SOURCE])

        val lossy = audioQualitySpecs("audio/webm", "opus", 151_018, 48_000, null, null).toMap()
        assertEquals("Lossy (compressed)", lossy[AudioSpec.QUALITY])
        assertNull(lossy[AudioSpec.BIT_DEPTH])
        assertNull(lossy[AudioSpec.CHANNELS])
    }

    @Test
    fun `bit depth from pcm encoding`() {
        assertEquals(16, pcmBitDepth(androidx.media3.common.C.ENCODING_PCM_16BIT))
        assertEquals(24, pcmBitDepth(androidx.media3.common.C.ENCODING_PCM_24BIT))
        assertNull(pcmBitDepth(androidx.media3.common.Format.NO_VALUE))
    }

    @Test
    fun `stored and live formats compare by codec family`() {
        assertEquals("opus", codecFamily("audio/webm", "opus"))
        assertEquals("opus", codecFamily("audio/opus", null))
        assertEquals("flac", codecFamily("audio/flac", "mp4a.40.2")) // download rows default codecs to mp4a
        assertEquals("aac", codecFamily("audio/mp4a-latm", "mp4a.40.2"))
        assertEquals("aac", codecFamily("audio/mp4", "mp4a.40.2"))
        assertEquals("eac3", codecFamily("audio/eac3-joc", null))
    }
}
