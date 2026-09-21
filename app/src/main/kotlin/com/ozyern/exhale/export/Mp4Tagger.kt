/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.export

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Writes iTunes-style metadata (the `moov/udta/meta/ilst` atoms every player reads from an .m4a)
 * into an MP4 file.
 *
 * Pure JVM on purpose: no tagging library, nothing Android, so it is checked on the desktop against
 * ffprobe with files laid out both ways a muxer may write them.
 *
 * The file is rebuilt as `ftyp, moov, mdat`. Wherever the muxer put `moov` — reserved space at the
 * front, or after the media — the media data moves, so every chunk offset in `stco`/`co64` is
 * shifted by exactly how far it moved. That offset patch is the whole difficulty of tagging MP4;
 * get it wrong and the file still opens but plays noise.
 */
object Mp4Tagger {
    class Tags(
        val title: String,
        val artist: String?,
        val albumArtist: String?,
        val album: String?,
        val year: Int?,
        val lyrics: String?,
        val cover: ByteArray?,
        /** 13 for JPEG, 14 for PNG — the `data` atom's type for `covr`. */
        val coverType: Int = 13,
        val tool: String = "Exhale",
    )

    private class Box(val type: String, val start: Long, val headerSize: Int, val size: Long)

    fun write(source: File, target: File, tags: Tags) {
        RandomAccessFile(source, "r").use { input ->
            val top = readBoxes(input, 0L, input.length())
            val ftyp = top.firstOrNull { it.type == "ftyp" }
            val moov = top.firstOrNull { it.type == "moov" } ?: error("No moov atom")
            val mdat = top.firstOrNull { it.type == "mdat" } ?: error("No mdat atom")

            val ftypBytes = ftyp?.let { read(input, it.start, it.size.toInt()) } ?: ByteArray(0)
            val oldMoov = read(input, moov.start, moov.size.toInt())
            // Drop whatever udta the muxer wrote and put ours in its place.
            val moovBody = stripChild(oldMoov, 8, "udta")
            val udta = buildUdta(tags)
            val newMoovSize = moovBody.size + udta.size
            val newMoov = ByteBuffer.allocate(newMoovSize).apply {
                put(moovBody)
                putInt(0, newMoovSize)
                put(udta)
            }.array()

            val newMdatStart = ftypBytes.size.toLong() + newMoov.size
            shiftChunkOffsets(newMoov, 8, newMoov.size, newMdatStart - mdat.start)

            target.outputStream().buffered(1 shl 16).use { out ->
                out.write(ftypBytes)
                out.write(newMoov)
                input.seek(mdat.start)
                val buffer = ByteArray(1 shl 16)
                var left = mdat.size
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) error("Truncated mdat")
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
        }
    }

    private fun read(file: RandomAccessFile, at: Long, length: Int): ByteArray {
        file.seek(at)
        return ByteArray(length).also { file.readFully(it) }
    }

    private fun readBoxes(file: RandomAccessFile, from: Long, to: Long): List<Box> {
        val boxes = mutableListOf<Box>()
        var at = from
        while (at + 8 <= to) {
            file.seek(at)
            var size = file.readInt().toLong() and 0xFFFFFFFFL
            val type = ByteArray(4).also { file.readFully(it) }.toString(Charsets.ISO_8859_1)
            var header = 8
            if (size == 1L) {
                size = file.readLong()
                header = 16
            } else if (size == 0L) {
                size = to - at
            }
            if (size < header) break
            boxes += Box(type, at, header, size)
            at += size
        }
        return boxes
    }

    /** Children of a box held in memory, starting at [from] inside [bytes]. */
    private fun children(bytes: ByteArray, from: Int, to: Int): List<Triple<String, Int, Int>> {
        val out = mutableListOf<Triple<String, Int, Int>>()
        val buffer = ByteBuffer.wrap(bytes)
        var at = from
        while (at + 8 <= to) {
            val size = buffer.getInt(at)
            val type = String(bytes, at + 4, 4, Charsets.ISO_8859_1)
            if (size < 8 || at + size > to) break
            out += Triple(type, at, size)
            at += size
        }
        return out
    }

    private fun stripChild(box: ByteArray, bodyFrom: Int, type: String): ByteArray {
        val out = ByteArrayOutputStream(box.size)
        out.write(box, 0, bodyFrom)
        var last = bodyFrom
        for ((childType, at, size) in children(box, bodyFrom, box.size)) {
            if (childType == type) {
                out.write(box, last, at - last)
                last = at + size
            }
        }
        out.write(box, last, box.size - last)
        return out.toByteArray()
    }

    private val Containers = setOf("moov", "trak", "mdia", "minf", "stbl", "edts", "dinf", "mvex", "moof", "traf")

    private fun shiftChunkOffsets(bytes: ByteArray, from: Int, to: Int, delta: Long) {
        if (delta == 0L) return
        val buffer = ByteBuffer.wrap(bytes)
        for ((type, at, size) in children(bytes, from, to)) {
            when (type) {
                in Containers -> shiftChunkOffsets(bytes, at + 8, at + size, delta)
                "stco" -> {
                    val count = buffer.getInt(at + 12)
                    for (i in 0 until count) {
                        val p = at + 16 + i * 4
                        val value = (buffer.getInt(p).toLong() and 0xFFFFFFFFL) + delta
                        require(value in 0..0xFFFFFFFFL) { "Chunk offset out of 32-bit range" }
                        buffer.putInt(p, value.toInt())
                    }
                }
                "co64" -> {
                    val count = buffer.getInt(at + 12)
                    for (i in 0 until count) {
                        val p = at + 16 + i * 8
                        buffer.putLong(p, buffer.getLong(p) + delta)
                    }
                }
            }
        }
    }

    private fun box(type: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(body.size + 8)
        DataOutputStream(out).apply {
            writeInt(body.size + 8)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(body)
        }
        return out.toByteArray()
    }

    private fun dataAtom(type: Int, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream(payload.size + 8)
        DataOutputStream(body).apply {
            writeInt(type)
            writeInt(0)
            write(payload)
        }
        return box("data", body.toByteArray())
    }

    private fun textItem(name: String, value: String?): ByteArray? =
        value?.takeIf { it.isNotBlank() }?.let { box(name, dataAtom(1, it.toByteArray(Charsets.UTF_8))) }

    private fun buildUdta(tags: Tags): ByteArray {
        val copyright = '©'
        val items = listOfNotNull(
            textItem("${copyright}nam", tags.title),
            textItem("${copyright}ART", tags.artist),
            textItem("aART", tags.albumArtist),
            textItem("${copyright}alb", tags.album),
            textItem("${copyright}day", tags.year?.toString()),
            textItem("${copyright}lyr", tags.lyrics),
            textItem("${copyright}too", tags.tool),
            tags.cover?.let { box("covr", dataAtom(tags.coverType, it)) },
        )
        val ilst = box("ilst", items.fold(ByteArray(0)) { acc, item -> acc + item })
        val hdlr = box(
            "hdlr",
            ByteArray(8) + "mdir".toByteArray(Charsets.ISO_8859_1) + "appl".toByteArray(Charsets.ISO_8859_1) + ByteArray(9),
        )
        // `meta` is a full box: four bytes of version and flags before its children.
        val meta = box("meta", ByteArray(4) + hdlr + ilst)
        return box("udta", meta)
    }

    /**
     * The atom names above contain '©', which is one byte (0xA9) in ISO-8859-1 — the encoding the
     * type field is written in. Kept as a check so a refactor to UTF-8 can't silently double it.
     */
    init {
        check("©nam".toByteArray(Charsets.ISO_8859_1).size == 4)
    }
}
