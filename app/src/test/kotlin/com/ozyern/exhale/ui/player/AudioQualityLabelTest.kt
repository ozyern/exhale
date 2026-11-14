package com.ozyern.exhale.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityLabelTest {
    @Test
    fun `apple wording for lossless, hi-res and atmos`() {
        assertEquals(AudioQuality("Lossless", true), audioQualityLabel("audio/flac", null, 900_000, 44_100))
        assertEquals(AudioQuality("Hi-Res Lossless", true), audioQualityLabel("audio/flac", null, null, 96_000))
        assertEquals(AudioQuality("Dolby Atmos", false), audioQualityLabel("audio/mp4", "ec-3", 768_000, 48_000))
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
