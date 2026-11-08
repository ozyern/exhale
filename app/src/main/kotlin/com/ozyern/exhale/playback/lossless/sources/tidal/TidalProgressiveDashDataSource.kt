/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.ozyern.exhale.playback.lossless.sources.tidal

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.CancellationException
import com.ozyern.exhale.playback.lossless.sources.tidal.TidalAudioProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class TidalProgressiveDashDataSource(
    private val httpClient: OkHttpClient,
) : BaseDataSource(true) {
    private var currentUri: Uri? = null
    private var segmentUrls: List<String> = emptyList()
    private var nextSegmentIndex = 0
    private var pendingBytes = ByteArray(0)
    private var pendingOffset = 0
    private var bytesRemaining = C.LENGTH_UNSET.toLong()
    private var opened = false

    /** Where this stream's segments are kept in [segmentCache]: one manifest file per resolved track. */
    private var cacheKey: String = ""

    /** Segments being fetched ahead of the reader, by index. */
    private val inFlight = HashMap<Int, Future<ByteArray>>()

    override fun open(dataSpec: DataSpec): Long {
        val manifestPath =
            dataSpec.uri.getQueryParameter("manifest")
                ?.takeIf(String::isNotBlank)
                ?: throw IOException("TIDAL progressive stream did not include a manifest")
        val manifestFile = File(manifestPath)
        if (!manifestFile.isFile) throw IOException("TIDAL progressive manifest is unavailable")

        val urls =
            runCatching {
                TidalAudioProvider.progressiveDashSegmentUrls(
                    manifestFile.readText(Charsets.UTF_8),
                )
            }.getOrElse { error ->
                throw IOException("TIDAL progressive manifest could not be parsed", error)
            }
        if (urls.size < 2 || urls.any { !it.startsWith("http://") && !it.startsWith("https://") }) {
            throw IOException("TIDAL progressive manifest did not contain absolute FLAC segments")
        }

        currentUri = dataSpec.uri
        segmentUrls = urls
        cacheKey = manifestFile.absolutePath
        nextSegmentIndex = 1
        pendingBytes =
            byteArrayOf(
                'f'.code.toByte(),
                'L'.code.toByte(),
                'a'.code.toByte(),
                'C'.code.toByte(),
            ) + segment(0)
        pendingOffset = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        opened = true
        transferStarted(dataSpec)

        try {
            if (dataSpec.position > 0L) skipFully(dataSpec.position)
            bytesRemaining = dataSpec.length
            return bytesRemaining
        } catch (error: CancellationException) {
            close()
            throw error
        } catch (error: Throwable) {
            close()
            if (error is IOException) throw error
            throw IOException("TIDAL progressive stream seek failed", error)
        }
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        if (!opened) throw IOException("TIDAL progressive stream is not open")
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        if (pendingOffset >= pendingBytes.size && !loadNextSegment()) {
            return C.RESULT_END_OF_INPUT
        }

        var available = pendingBytes.size - pendingOffset
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            available = minOf(available.toLong(), bytesRemaining).toInt()
        }
        if (available <= 0) return C.RESULT_END_OF_INPUT

        val count = minOf(length, available)
        pendingBytes.copyInto(buffer, offset, pendingOffset, pendingOffset + count)
        pendingOffset += count
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= count
        bytesTransferred(count)
        return count
    }

    private fun loadNextSegment(): Boolean {
        if (nextSegmentIndex >= segmentUrls.size) return false
        val segmentIndex = nextSegmentIndex
        val payload = segment(segmentIndex)
        nextSegmentIndex++
        pendingBytes = payload
        pendingOffset = 0
        return pendingBytes.isNotEmpty()
    }

    /**
     * Segment [index]'s audio, from memory when it has been fetched before or is already on its way,
     * and the next [READ_AHEAD] segments set downloading behind it.
     *
     * Segments used to be fetched one at a time, on demand: every boundary was a full round trip with
     * the reader standing still, and every seek reopened the stream and downloaded the song again from
     * its first segment up to the new position. On a slow mirror that is the stall mid-song and the
     * long buffering after a scrub.
     */
    private fun segment(index: Int): ByteArray {
        val key = "$cacheKey#$index"
        val bytes = cachedSegment(key) ?: run {
            val pending = inFlight.remove(index)
            if (pending != null) {
                try {
                    pending.get()
                } catch (error: ExecutionException) {
                    val cause = error.cause
                    if (cause is IOException) throw cause
                    throw IOException("TIDAL FLAC segment ${index + 1} failed", cause)
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("TIDAL FLAC segment ${index + 1} interrupted")
                }
            } else {
                fetchSegment(segmentUrls[index], index).also { storeSegment(key, it) }
            }
        }
        for (ahead in index + 1..minOf(index + READ_AHEAD, segmentUrls.lastIndex)) {
            val aheadKey = "$cacheKey#$ahead"
            if (ahead in inFlight || cachedSegment(aheadKey) != null) continue
            val url = segmentUrls[ahead]
            inFlight[ahead] = prefetcher.submit(
                Callable { fetchSegment(url, ahead).also { storeSegment(aheadKey, it) } },
            )
        }
        return bytes
    }

    private fun fetchSegment(
        url: String,
        segmentIndex: Int,
    ): ByteArray {
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", "audio/flac,audio/*,*/*;q=0.8")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "ArchiveTune-Android")
                .get()
                .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("TIDAL FLAC segment ${segmentIndex + 1} HTTP ${response.code}")
                }
                val bytes = response.body?.bytes() ?: throw IOException("TIDAL FLAC segment was empty")
                if (segmentIndex == 0) {
                    TidalAudioProvider.progressiveDashFlacMetadata(bytes)
                } else {
                    TidalAudioProvider.progressiveDashAudioPayload(bytes)
                }
            }
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("TIDAL FLAC segment ${segmentIndex + 1} failed", error)
        }
    }

    private fun skipFully(bytesToSkip: Long) {
        var remaining = bytesToSkip
        val scratch = ByteArray(16 * 1024)
        while (remaining > 0L) {
            val read = read(scratch, 0, minOf(remaining, scratch.size.toLong()).toInt())
            if (read == C.RESULT_END_OF_INPUT) {
                throw IOException("TIDAL progressive stream ended before the requested position")
            }
            remaining -= read
        }
    }

    override fun getUri(): Uri? = currentUri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        // Fetches already under way are left to land in the cache: a close is usually a seek or a
        // reload, and the reopened stream wants exactly those segments next.
        inFlight.clear()
        segmentUrls = emptyList()
        nextSegmentIndex = 0
        pendingBytes = ByteArray(0)
        pendingOffset = 0
        bytesRemaining = C.LENGTH_UNSET.toLong()
        currentUri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(
        private val httpClient: OkHttpClient,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = TidalProgressiveDashDataSource(httpClient)
    }

    private companion object {
        /** Segments fetched ahead of the one being read. */
        const val READ_AHEAD = 2

        /** Memory kept for recent segments across reopens: about one hi-res album side. */
        const val CACHE_LIMIT_BYTES = 48L * 1024 * 1024

        val prefetcher = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "tidal-segment-prefetch").apply { isDaemon = true }
        }

        private val segmentCache = LinkedHashMap<String, ByteArray>(64, 0.75f, true)
        private var segmentCacheBytes = 0L

        fun cachedSegment(key: String): ByteArray? = synchronized(segmentCache) { segmentCache[key] }

        fun storeSegment(key: String, bytes: ByteArray) {
            synchronized(segmentCache) {
                segmentCache.put(key, bytes)?.let { segmentCacheBytes -= it.size }
                segmentCacheBytes += bytes.size
                val oldest = segmentCache.entries.iterator()
                while (segmentCacheBytes > CACHE_LIMIT_BYTES && oldest.hasNext()) {
                    val entry = oldest.next()
                    if (entry.key == key) continue
                    segmentCacheBytes -= entry.value.size
                    oldest.remove()
                }
            }
        }
    }
}
