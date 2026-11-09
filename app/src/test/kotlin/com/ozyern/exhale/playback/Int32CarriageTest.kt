/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Int32CarriageTest {

    @Test
    fun `24-bit samples land in the top 24 bits unchanged`() {
        for (sample in listOf(0, 1, -1, 4_194_303, -8_388_608, 8_388_607, 123_456)) {
            val asFloat = sample / 8_388_608f
            assertEquals(sample shl 8, Int32Carriage.toInt32(asFloat))
        }
    }

    @Test
    fun `16-bit samples land in the top 16 bits unchanged`() {
        for (sample in listOf(0, 1, -1, 32_767, -32_768, 1_000)) {
            assertEquals(sample shl 16, Int32Carriage.toInt32(sample / 32_768f))
        }
    }

    @Test
    fun `full scale and beyond clamp instead of wrapping`() {
        assertEquals(Int.MAX_VALUE, Int32Carriage.toInt32(1f))
        assertEquals(Int.MAX_VALUE, Int32Carriage.toInt32(1.5f))
        assertEquals(Int.MIN_VALUE, Int32Carriage.toInt32(-1f))
        assertEquals(Int.MIN_VALUE, Int32Carriage.toInt32(-3f))
    }

    @Test
    fun `processor declares float and writes integers`() {
        val processor = Int32Carriage.ToInt32Processor(active = { true })
        val out = processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        assertEquals(C.ENCODING_PCM_FLOAT, out.encoding)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder())
            .putFloat(0.5f).putFloat(-0.25f).flip() as ByteBuffer
        processor.queueInput(input)
        val written = processor.output
        assertEquals(1 shl 30, written.int)
        assertEquals(-(1 shl 29), written.int)
    }

    @Test
    fun `16-bit input is widened`() {
        val processor = Int32Carriage.ToInt32Processor(active = { true })
        processor.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            .putShort(-2).putShort(300).flip() as ByteBuffer
        processor.queueInput(input)
        val written = processor.output
        assertEquals(-2 shl 16, written.int)
        assertEquals(300 shl 16, written.int)
    }

    @Test
    fun `inactive processor is skipped`() {
        val processor = Int32Carriage.ToInt32Processor(active = { false })
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        assertFalse(processor.isActive)
    }
}
