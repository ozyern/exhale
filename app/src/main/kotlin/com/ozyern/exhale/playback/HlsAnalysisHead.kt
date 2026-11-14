package com.ozyern.exhale.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.UriUtil
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream

/** An init section plus opening fMP4 fragments for the existing DJ model's MediaExtractor. */
@UnstableApi
internal object HlsAnalysisHead {
    private const val MAX_BYTES = 12 * 1024 * 1024
    private const val HEAD_US = 48_000_000L

    suspend fun prefetch(mediaId: String, uri: Uri, cache: SimpleCache, factory: DataSource.Factory) {
        val key = "$mediaId#hls-analysis"
        if (cache.getCachedLength(key, 0, MAX_BYTES.toLong()) >= 512 * 1024) return
        var parsed = playlist(uri, factory)
        if (parsed is HlsMultivariantPlaylist) {
            val audio = parsed.audios.firstOrNull { it.url != null }?.url ?: parsed.variants.firstOrNull()?.url ?: return
            parsed = playlist(audio, factory)
        }
        val media = parsed as? HlsMediaPlaylist ?: return
        val init = media.segments.firstOrNull()?.initializationSegment ?: return
        // Encrypted/DRM renditions stay on the player's normal HLS pipeline.
        if (media.protectionSchemes != null || init.fullSegmentEncryptionKeyUri != null ||
            media.segments.any { it.drmInitData != null || it.fullSegmentEncryptionKeyUri != null }) return
        val bytes = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(64 * 1024)
        if (!append(media.baseUri, init, factory, bytes, buffer)) return
        for (segment in media.segments) {
            currentCoroutineContext().ensureActive()
            if (segment.relativeStartTimeUs >= HEAD_US || bytes.size() >= MAX_BYTES) break
            val segmentInit = segment.initializationSegment ?: break
            if (segmentInit.url != init.url || segmentInit.byteRangeOffset != init.byteRangeOffset ||
                segmentInit.byteRangeLength != init.byteRangeLength ||
                segment.relativeDiscontinuitySequence != media.segments.first().relativeDiscontinuitySequence) break
            if (!append(media.baseUri, segment, factory, bytes, buffer)) break
        }
        if (bytes.size() < 64 * 1024) return
        currentCoroutineContext().ensureActive()
        val hole = cache.startReadWriteNonBlocking(key, 0, bytes.size().toLong()) ?: return
        if (hole.isCached) return
        try {
            val file = cache.startFile(key, 0, bytes.size().toLong())
            file.outputStream().use { bytes.writeTo(it) }
            cache.commitFile(file, bytes.size().toLong())
        } finally {
            cache.releaseHoleSpan(hole)
        }
    }

    private fun playlist(uri: Uri, factory: DataSource.Factory) =
        DataSourceInputStream(factory.createDataSource(), DataSpec(uri)).use { HlsPlaylistParser().parse(uri, it) }

    private suspend fun append(
        base: String, segment: HlsMediaPlaylist.Segment, factory: DataSource.Factory, output: ByteArrayOutputStream,
        buffer: ByteArray,
    ): Boolean {
        val remaining = MAX_BYTES - output.size()
        if (segment.byteRangeLength != C.LENGTH_UNSET.toLong() && segment.byteRangeLength > remaining) return false
        val fragment = ByteArrayOutputStream(minOf(remaining, buffer.size))
        val source = factory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(UriUtil.resolveToUri(base, segment.url))
                .setPosition(segment.byteRangeOffset).setLength(segment.byteRangeLength).build())
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                if (read == 0) return false
                if (fragment.size() + read > remaining) return false
                fragment.write(buffer, 0, read)
            }
        } finally { source.close() }
        fragment.writeTo(output)
        return true
    }
}
