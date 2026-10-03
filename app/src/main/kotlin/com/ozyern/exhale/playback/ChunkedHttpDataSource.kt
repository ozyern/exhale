/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Reads a googlevideo stream as a run of bounded requests instead of one open-ended one.
 *
 * YouTube's servers throttle, and for some clients refuse, a request for a whole stream at once;
 * the audio path has always asked in 4 MB pieces for that reason. The music video asked for
 * everything in one go and got nothing back, which is the black box the video mode showed. This
 * opens each piece in turn as the player reads, so to the player it is still one stream.
 */
@UnstableApi
class ChunkedHttpDataSource(
    private val upstream: DataSource,
    private val totalLength: Long?,
    private val chunkLength: Long = 2L * 1024 * 1024,
) : BaseDataSource(true) {

    private var spec: DataSpec? = null
    private var position = 0L
    private var remaining = C.LENGTH_UNSET.toLong()
    private var chunkRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        position = dataSpec.position
        remaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            totalLength != null && totalLength > 0 -> totalLength - position
            else -> C.LENGTH_UNSET.toLong()
        }
        openChunk()
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    private fun openChunk() {
        val base = spec ?: return
        val length = if (remaining == C.LENGTH_UNSET.toLong()) chunkLength else minOf(chunkLength, remaining)
        val opened = upstream.open(base.buildUpon().setPosition(position).setLength(length).build())
        chunkRemaining = if (opened == C.LENGTH_UNSET.toLong()) length else minOf(opened, length)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (chunkRemaining == 0L) {
            upstream.close()
            openChunk()
        }
        val read = upstream.read(buffer, offset, minOf(length.toLong(), chunkRemaining).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            // The server ended the piece early: the stream is shorter than we were told.
            return if (remaining == C.LENGTH_UNSET.toLong() || chunkRemaining > 0) C.RESULT_END_OF_INPUT else read
        }
        position += read
        chunkRemaining -= read
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        spec = null
        if (opened) {
            opened = false
            transferEnded()
        }
        upstream.close()
    }

    class Factory(private val upstream: DataSource.Factory, private val totalLength: Long?) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedHttpDataSource(upstream.createDataSource(), totalLength)
    }
}
