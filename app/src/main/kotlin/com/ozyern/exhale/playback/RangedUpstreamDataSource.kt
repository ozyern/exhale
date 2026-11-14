package com.ozyern.exhale.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException

@UnstableApi
class RangedUpstreamDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : BaseDataSource(true) {
    init {
        require(chunkSize > 0)
    }

    private var upstream: DataSource? = null
    private var spec: DataSpec? = null
    private var headers: Map<String, List<String>> = emptyMap()
    private var passthrough = false
    private var opened = false
    private var remaining = C.LENGTH_UNSET.toLong()
    private var position = 0L
    private var chunkRemaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        spec = dataSpec
        remaining = dataSpec.length
        position = dataSpec.position
        passthrough = !dataSpec.uri.isGoogleVideoUrl()
        val length = if (passthrough) {
            openChild(dataSpec.length)
        } else {
            openNextChunk()
            val available = upstream?.contentRangeLength()?.let { (it - position).coerceAtLeast(0L) }
            if (available != null) {
                remaining = if (remaining == C.LENGTH_UNSET.toLong()) available else minOf(remaining, available)
            }
            remaining
        }
        transferStarted(dataSpec)
        opened = true
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        if (!passthrough && chunkRemaining == 0L) {
            closeUpstream()
            if (!openNextChunk()) return C.RESULT_END_OF_INPUT
        }
        val allowed = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val wanted = if (passthrough) allowed else minOf(allowed.toLong(), chunkRemaining).toInt()
        val read = requireNotNull(upstream).read(buffer, offset, wanted)
        if (read == C.RESULT_END_OF_INPUT) {
            closeUpstream()
            remaining = 0
        } else if (read > 0) {
            position += read
            if (!passthrough) chunkRemaining -= read
            if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = upstream?.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = headers

    override fun close() {
        try {
            closeUpstream()
        } finally {
            spec = null
            headers = emptyMap()
            remaining = C.LENGTH_UNSET.toLong()
            chunkRemaining = 0
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun openNextChunk(): Boolean {
        if (remaining == 0L) return false
        val wanted = if (remaining == C.LENGTH_UNSET.toLong()) chunkSize else minOf(chunkSize, remaining)
        val length = try {
            openChild(wanted)
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode != 416 || position == requireNotNull(spec).position) throw error
            remaining = 0
            return false
        }
        chunkRemaining = if (length == C.LENGTH_UNSET.toLong()) wanted else minOf(wanted, length)
        if (chunkRemaining < wanted) {
            remaining = if (remaining == C.LENGTH_UNSET.toLong()) chunkRemaining else minOf(remaining, chunkRemaining)
        }
        return chunkRemaining > 0
    }

    private fun openChild(length: Long): Long {
        val child = upstreamFactory.createDataSource()
        upstream = child
        return try {
            val result = child.open(requireNotNull(spec).buildUpon().setPosition(position).setLength(length).build())
            headers = child.responseHeaders
            result
        } catch (error: Exception) {
            closeUpstream()
            throw error
        }
    }

    private fun closeUpstream() {
        val child = upstream ?: return
        upstream = null
        try {
            child.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val DEFAULT_CHUNK_SIZE = 2L * 1024 * 1024
    }
}
