/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService

data class DownloadTarget(
    val id: String,
    val title: String,
    val artist: String? = null,
    val durationSeconds: Int = -1,
)

internal fun <T> combinedDownloadState(
    items: Collection<T>?,
    downloads: Map<String, Download>,
    idOf: (T) -> String,
): Int {
    if (items.isNullOrEmpty()) return Download.STATE_STOPPED

    var allCompleted = true
    for (item in items) {
        when (downloads[idOf(item)]?.state) {
            Download.STATE_COMPLETED -> Unit
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> allCompleted = false
            else -> return Download.STATE_STOPPED
        }
    }

    return if (allCompleted) Download.STATE_COMPLETED else Download.STATE_DOWNLOADING
}

internal fun <T> displayDownloadState(
    items: Collection<T>,
    downloads: Map<String, Download>,
    idOf: (T) -> String,
): Int {
    if (items.isEmpty()) return Download.STATE_STOPPED

    var allCompleted = true
    var hasFailedDownload = false

    for (item in items) {
        when (downloads[idOf(item)]?.state) {
            Download.STATE_COMPLETED -> Unit
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> return Download.STATE_DOWNLOADING
            Download.STATE_FAILED -> {
                allCompleted = false
                hasFailedDownload = true
            }
            else -> allCompleted = false
        }
    }

    return when {
        allCompleted -> Download.STATE_COMPLETED
        hasFailedDownload -> Download.STATE_FAILED
        else -> Download.STATE_STOPPED
    }
}

fun shouldQueueDownload(state: Int?): Boolean = when (state) {
    null, Download.STATE_FAILED, Download.STATE_STOPPED -> true
    else -> false
}

/** Completed files require an explicit remove action. */
fun shouldCancelDownload(state: Int?): Boolean = when (state) {
    Download.STATE_QUEUED,
    Download.STATE_DOWNLOADING,
    Download.STATE_FAILED,
    Download.STATE_STOPPED,
    Download.STATE_RESTARTING,
    -> true

    else -> false
}

@UnstableApi
fun downloadSongs(
    context: Context,
    songs: List<DownloadTarget>,
    downloads: Map<String, Download>,
) {
    com.ozyern.exhale.utils.ArtistWordmarkAssets.prefetch(context, songs.map { it.artist })
    val queuedIds = HashSet<String>()
    songs.forEach { song ->
        if (!queuedIds.add(song.id) || !shouldQueueDownload(downloads[song.id]?.state)) return@forEach
        val owner = DownloadDuplicates.ownerOf(song.id, song.title, song.artist, song.durationSeconds)
        DownloadService.sendAddDownload(
            context,
            ExoDownloadService::class.java,
            DownloadRequest
                .Builder(song.id, song.id.toUri())
                .setCustomCacheKey(owner ?: song.id)
                .setData(song.title.toByteArray())
                .build(),
            false,
        )
    }
}

@UnstableApi
fun downloadSong(
    context: Context,
    id: String,
    title: String,
    downloads: Map<String, Download>,
) = downloadSongs(context, listOf(DownloadTarget(id, title)), downloads)

@UnstableApi
fun cancelDownloads(
    context: Context,
    ids: List<String>,
    downloads: Map<String, Download>,
) {
    val cancelledIds = HashSet<String>()
    ids.forEach { id ->
        if (!cancelledIds.add(id) || !shouldCancelDownload(downloads[id]?.state)) return@forEach
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, id, false)
    }
}

@UnstableApi
fun removeDownloads(
    context: Context,
    ids: List<String>,
) {
    val removedIds = HashSet<String>()
    ids.forEach { id ->
        if (!removedIds.add(id)) return@forEach
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, id, false)
        // Drop a quality-upgrade copy too, so no orphaned bytes stay in the cache.
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, id + UPGRADE_SUFFIX, false)
    }
}
