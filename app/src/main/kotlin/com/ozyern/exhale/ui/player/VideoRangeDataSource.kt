package com.ozyern.exhale.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/** Bounded CDN reads while preserving the file length needed by MP4 seek maps. */
@UnstableApi
internal class VideoRangeDataSource(
    private val factory: DataSource.Factory,
    private val fileLength: Long,
) : BaseDataSource(true) {
    private var spec: DataSpec? = null
    private var child: DataSource? = null
    private var cursor = 0L
    private var remaining = 0L
    private var chunkRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        cursor = dataSpec.position
        remaining = (fileLength - cursor).coerceAtLeast(0L)
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) remaining = minOf(remaining, dataSpec.length)
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) {
            child?.close()
            chunkRemaining = minOf(remaining, 1024L * 1024)
            child = factory.createDataSource().also {
                it.open(requireNotNull(spec).buildUpon().setPosition(cursor).setLength(chunkRemaining).build())
            }
        }
        val count = requireNotNull(child).read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
        if (count == C.RESULT_END_OF_INPUT) {
            remaining = 0L
            return count
        }
        if (count > 0) {
            cursor += count
            remaining -= count
            chunkRemaining -= count
            bytesTransferred(count)
        }
        return count
    }

    override fun getUri(): Uri? = spec?.uri
    override fun close() {
        try { child?.close() } finally {
            child = null
            if (spec != null) transferEnded()
            spec = null
            chunkRemaining = 0L
            remaining = 0L
        }
    }
}
