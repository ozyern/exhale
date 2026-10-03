/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback.automix

import android.media.MediaDataSource
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * A song's bytes, read through the same data source the player plays from, for the analyzer.
 *
 * The extractor reads a container out of order and in small pieces, so this reads in blocks and
 * keeps the last few, which turns its hundreds of reads into a handful of requests. Every block
 * comes through the player's own caches: what is already cached costs nothing, and what isn't is
 * fetched once and cached on the way through, so the player finds it there when the song plays
 * rather than fetching it again.
 */
internal class StreamAudioSource(
    private val factory: DataSource.Factory,
    private val key: String,
    private val length: Long,
    private val abort: () -> Boolean = { false },
) : MediaDataSource() {

    private val blocks = object : LinkedHashMap<Long, ByteArray>(KEPT_BLOCKS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > KEPT_BLOCKS
    }

    override fun getSize(): Long = length

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0 || position >= length) return -1
        if (size <= 0) return 0
        val index = position / BLOCK_BYTES
        val block = block(index)
        val within = (position - index * BLOCK_BYTES).toInt()
        if (within >= block.size) return -1
        val count = minOf(size, block.size - within)
        System.arraycopy(block, within, buffer, offset, count)
        return count
    }

    private fun block(index: Long): ByteArray {
        blocks[index]?.let { return it }
        if (abort()) throw IOException("Analysis no longer wanted")
        val start = index * BLOCK_BYTES
        val wanted = minOf(BLOCK_BYTES, length - start).toInt()
        val bytes = ByteArray(wanted)
        var filled = 0
        var attempts = 0
        // A resolved stream can answer a range in smaller pieces than it was asked for, so a
        // short read is reopened where it stopped rather than taken for the end of the file.
        while (filled < wanted && attempts < MAX_OPENS) {
            attempts++
            val source = factory.createDataSource()
            try {
                source.open(
                    DataSpec.Builder()
                        .setUri(key)
                        .setKey(key)
                        .setPosition(start + filled)
                        .setLength((wanted - filled).toLong())
                        .build(),
                )
                while (filled < wanted) {
                    val read = source.read(bytes, filled, wanted - filled)
                    if (read == C.RESULT_END_OF_INPUT) break
                    filled += read
                }
            } finally {
                runCatching { source.close() }
            }
        }
        if (filled == 0) throw IOException("Nothing read at $start of $key")
        val block = if (filled < wanted) bytes.copyOf(filled) else bytes
        blocks[index] = block
        return block
    }

    override fun close() {
        blocks.clear()
    }

    private companion object {
        const val BLOCK_BYTES = 256L * 1024L
        const val KEPT_BLOCKS = 12
        const val MAX_OPENS = 4
    }
}
