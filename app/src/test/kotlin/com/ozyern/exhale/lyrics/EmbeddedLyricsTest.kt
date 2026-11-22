/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Pins reading lyrics out of the tags a file carries. */
class EmbeddedLyricsTest {

    @Test
    fun `an mp3's USLT frame is read`() {
        val lyrics = "[00:01.00]Hello\n[00:03.50]World"
        assertEquals(lyrics, EmbeddedLyrics.fromBytes(id3v23(uslt(lyrics))))
    }

    @Test
    fun `a flac's LYRICS comment is read`() {
        assertEquals("Plain words", EmbeddedLyrics.fromBytes(flac("LYRICS=Plain words")))
    }

    @Test
    fun `a file with no lyrics reads as none`() {
        assertNull(EmbeddedLyrics.fromBytes(id3v23(ByteArray(0))))
        assertNull(EmbeddedLyrics.fromBytes("not audio".toByteArray()))
    }

    @Test
    fun `an lrc header with nothing under it is not lyrics`() {
        assertNull(EmbeddedLyrics.cleaned("[ar:Artist]\n[ti:Title]\n"))
        assertEquals("[00:01.00]Line", EmbeddedLyrics.cleaned("[ar:Artist]\n[00:01.00]Line"))
    }

    @Test
    fun `stamped lyrics count as synced and plain ones do not`() {
        assertTrue(EmbeddedLyrics.isSynced("[01:02.30]Line"))
        assertFalse(EmbeddedLyrics.isSynced("Just words"))
    }

    /** A USLT frame body: Latin-1, language "eng", an empty description, then the text. */
    private fun uslt(text: String): ByteArray {
        val body = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1)
        return "USLT".toByteArray() + int32(body.size) + byteArrayOf(0, 0) + body
    }

    private fun id3v23(frames: ByteArray): ByteArray {
        val size = frames.size + 16 // a little padding after the frames
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0) + syncsafe(size)
        return header + frames + ByteArray(16)
    }

    private fun flac(vararg comments: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(le32(0)) // vendor length
        out.write(le32(comments.size))
        comments.forEach { out.write(le32(it.length)); out.write(it.toByteArray()) }
        val block = out.toByteArray()
        // Last-block flag set, type 4 (VORBIS_COMMENT), 24-bit big-endian length.
        val header = byteArrayOf((0x80 or 4).toByte(), (block.size shr 16).toByte(), (block.size shr 8).toByte(), block.size.toByte())
        return "fLaC".toByteArray() + header + block
    }

    private fun int32(v: Int) = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())
    private fun syncsafe(v: Int) = byteArrayOf(
        ((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte(),
    )
}
