/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Reads a lossless file as several byte ranges at once, handed on in order.
 *
 * The hosts lossless copies come from hold each connection to a few hundred KB/s or less (archive.org
 * served a 37 MB FLAC at 66 KB/s on one connection, where the song plays at 187 KB/s), so a single
 * stream fell behind the music and stalled it mid-song. Four connections side by side came to about
 * 1.3 MB/s. A piece that stops arriving is given up on and fetched again rather than left to hold
 * playback in "buffering" for good.
 *
 * Only URIs [markParallel] was told about are read this way; everything else passes straight through.
 */
@UnstableApi
class ParallelRangeDataSource(private val upstream: DataSource.Factory) : DataSource {

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource() = ParallelRangeDataSource(upstream)
    }

    private val listeners = mutableListOf<TransferListener>()
    private var direct: DataSource? = null
    private var spec: DataSpec? = null
    private var pool: ExecutorService? = null
    private val pending = HashMap<Long, Future<ByteArray>>()

    /** Absolute byte offsets: where the next piece starts, and where the read ends (exclusive). */
    private var nextPieceAt = 0L
    private var end = 0L
    private var scheduledUpTo = 0L
    private var piece = ByteArray(0)
    private var pieceOffset = 0

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        spec = dataSpec
        if (dataSpec.uri.toString() !in parallelUris) return openDirect(dataSpec)

        // The first piece is fetched here, small so the song starts at once; its Content-Range says
        // how long the file is, which every later piece is planned against.
        val start = dataSpec.position
        val firstLength = if (dataSpec.length == C.LENGTH_UNSET.toLong()) FIRST_PIECE else minOf(FIRST_PIECE, dataSpec.length)
        val first = newSource()
        val opened = first.open(dataSpec.buildUpon().setPosition(start).setLength(firstLength).build())
        val total = first.contentRangeLength()
        if (total == null) {
            // No ranges here after all (a plain 200): read it the ordinary way.
            runCatching { first.close() }
            parallelUris.remove(dataSpec.uri.toString())
            return openDirect(dataSpec)
        }
        val bytes = try {
            readAll(first, if (opened == C.LENGTH_UNSET.toLong()) firstLength else opened)
        } finally {
            runCatching { first.close() }
        }
        end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) total else minOf(total, start + dataSpec.length)
        piece = bytes
        pieceOffset = 0
        nextPieceAt = start + bytes.size
        scheduledUpTo = nextPieceAt
        pool = Executors.newFixedThreadPool(WORKERS) { r -> Thread(r, "exhale-parallel-range").apply { isDaemon = true } }
        scheduleAhead()
        return end - start
    }

    private fun openDirect(dataSpec: DataSpec): Long {
        val source = newSource()
        direct = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        direct?.let { return it.read(buffer, offset, length) }
        if (pieceOffset == piece.size) {
            if (nextPieceAt >= end) return C.RESULT_END_OF_INPUT
            piece = awaitPiece(nextPieceAt)
            pieceOffset = 0
            nextPieceAt += piece.size
            if (piece.isEmpty()) return C.RESULT_END_OF_INPUT
            scheduleAhead()
        }
        val n = minOf(length, piece.size - pieceOffset)
        System.arraycopy(piece, pieceOffset, buffer, offset, n)
        pieceOffset += n
        return n
    }

    /** Keeps [AHEAD] pieces in flight past the one being read. */
    private fun scheduleAhead() {
        val executor = pool ?: return
        while (scheduledUpTo < end && pending.size < AHEAD) {
            val at = scheduledUpTo
            val length = minOf(PIECE, end - at)
            pending[at] = executor.submit<ByteArray> { fetchPiece(at, length) }
            scheduledUpTo += length
        }
    }

    /**
     * The piece at [at], waiting at most [PIECE_TIMEOUT_MS] for it. One that does not come is fetched
     * once more on a fresh connection; if that stalls too, the read fails and the player reconnects.
     */
    private fun awaitPiece(at: Long): ByteArray {
        val future = pending.remove(at) ?: throw IOException("Piece at $at was never scheduled")
        return try {
            future.get(PIECE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            Timber.tag(TAG).w("Range at $at stalled; fetching it again")
            val length = minOf(PIECE, end - at)
            val retry = pool?.submit<ByteArray> { fetchPiece(at, length) } ?: throw IOException("Closed")
            try {
                retry.get(PIECE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                retry.cancel(true)
                throw IOException("Range at $at stalled twice")
            }
        } catch (e: java.util.concurrent.ExecutionException) {
            throw (e.cause as? IOException) ?: IOException(e.cause)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw java.io.InterruptedIOException("Interrupted waiting for range at $at")
        }
    }

    private fun fetchPiece(at: Long, length: Long): ByteArray {
        val base = requireNotNull(spec)
        var lastError: IOException? = null
        repeat(PIECE_ATTEMPTS) { attempt ->
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
            val source = newSource()
            try {
                val opened = source.open(base.buildUpon().setPosition(at).setLength(length).build())
                return readAll(source, if (opened == C.LENGTH_UNSET.toLong()) length else opened)
            } catch (e: IOException) {
                lastError = e
                if (e is java.io.InterruptedIOException) throw e
                Timber.tag(TAG).w("Range at $at failed (attempt ${attempt + 1}): ${e.message}")
            } finally {
                runCatching { source.close() }
            }
        }
        throw lastError ?: IOException("Range at $at failed")
    }

    private fun readAll(source: DataSource, expected: Long): ByteArray {
        val out = ByteArrayOutputStream(expected.coerceIn(0, PIECE).toInt())
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = source.read(chunk, 0, chunk.size)
            if (read == C.RESULT_END_OF_INPUT) break
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private fun newSource(): DataSource = upstream.createDataSource().also { s -> listeners.forEach(s::addTransferListener) }

    override fun getUri(): Uri? = direct?.uri ?: spec?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = direct?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            direct?.close()
        } finally {
            direct = null
            pending.values.forEach { it.cancel(true) }
            pending.clear()
            pool?.shutdownNow()
            pool = null
            piece = ByteArray(0)
            pieceOffset = 0
            spec = null
        }
    }

    companion object {
        private const val TAG = "ParallelRange"
        private const val FIRST_PIECE = 256L * 1024
        private const val PIECE = 1024L * 1024
        private const val WORKERS = 4
        private const val AHEAD = 8
        private const val PIECE_ATTEMPTS = 3
        private const val PIECE_TIMEOUT_MS = 20_000L

        private val parallelUris: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Reads of [uri] from here on go over parallel ranges. */
        fun markParallel(uri: Uri) {
            if (parallelUris.size > 64) parallelUris.clear()
            parallelUris += uri.toString()
        }
    }
}
