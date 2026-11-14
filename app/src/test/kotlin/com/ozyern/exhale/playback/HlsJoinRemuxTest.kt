package com.ozyern.exhale.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsJoinRemuxTest {
    private fun box(type: String, vararg body: ByteArray): ByteArray {
        val payload = body.fold(ByteArray(0)) { a, b -> a + b }
        val size = payload.size + 8
        return byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) +
            type.toByteArray() + payload
    }

    @Test
    fun `init dfLa becomes a flac header, segments yield mdat payloads`() {
        val blocks = byteArrayOf(0x80.toByte(), 0, 0, 2, 9, 9) // last STREAMINFO-ish block
        val init = box("ftyp", ByteArray(8)) + box(
            "moov",
            box("trak", box("mdia", box("minf", box("stbl", box(
                "stsd", ByteArray(8), box("fLaC", ByteArray(28), box("dfLa", ByteArray(4), blocks)),
            ))))),
        )
        assertArrayEquals("fLaC".toByteArray() + blocks, flacHeader(init))
        assertNull(flacHeader(box("moov", box("trak"))))

        val segment = box("styp") + box("moof", ByteArray(4)) + box("mdat", byteArrayOf(1, 2)) +
            box("moof") + box("mdat", byteArrayOf(3))
        assertArrayEquals(byteArrayOf(1, 2, 3), mdatPayloads(segment))
    }
}
