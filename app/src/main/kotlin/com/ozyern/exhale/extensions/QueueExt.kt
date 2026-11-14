/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.extensions

import com.music.innertube.models.WatchEndpoint
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.PersistQueue
import com.ozyern.exhale.models.QueueData
import com.ozyern.exhale.models.QueueType
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.playback.queues.LocalAlbumRadio
import com.ozyern.exhale.playback.queues.Queue
import com.ozyern.exhale.playback.queues.YouTubeAlbumRadio
import com.ozyern.exhale.playback.queues.YouTubeQueue
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val endpointJson = Json { encodeDefaults = true }

fun Queue.toPersistQueue(
    title: String?,
    items: List<MediaMetadata>,
    mediaItemIndex: Int,
    position: Long,
): PersistQueue {
    val queueType: QueueType
    val queueData: QueueData?

    when (this) {
        is YouTubeQueue -> {
            val (endpoint, continuation) = persistenceData()
            queueType = QueueType.YOUTUBE
            queueData = QueueData.YouTubeData(
                endpoint = endpointJson.encodeToString(WatchEndpoint.serializer(), endpoint),
                continuation = continuation,
            )
        }

        is YouTubeAlbumRadio -> {
            val state = persistenceData()
            queueType = QueueType.YOUTUBE_ALBUM_RADIO
            queueData = QueueData.YouTubeAlbumRadioData(
                playlistId = state.playlistId,
                albumSongCount = state.albumSongCount,
                continuation = state.continuation,
                firstTimeLoaded = state.firstTimeLoaded,
            )
        }

        is LocalAlbumRadio -> {
            val state = persistenceData()
            queueType = QueueType.LOCAL_ALBUM_RADIO
            queueData = QueueData.LocalAlbumRadioData(
                albumId = state.albumId,
                startIndex = state.startIndex,
                playlistId = state.playlistId,
                continuation = state.continuation,
                firstTimeLoaded = state.firstTimeLoaded,
            )
        }

        else -> {
            queueType = QueueType.LIST
            queueData = null
        }
    }

    return PersistQueue(
        title = title,
        items = items,
        mediaItemIndex = mediaItemIndex,
        position = position,
        queueType = queueType,
        queueData = queueData,
    )
}

suspend fun PersistQueue.toQueue(database: MusicDatabase? = null): Queue {
    val status = Queue.Status(
        title = title,
        items = items.map { it.toMediaItem() },
        mediaItemIndex = if (items.isEmpty()) 0 else mediaItemIndex.coerceIn(items.indices),
        position = position.coerceAtLeast(0L),
    )

    return when (queueType) {
        QueueType.LIST -> ListQueue(title, status.items, status.mediaItemIndex, status.position)

        QueueType.YOUTUBE -> {
            val data = queueData as? QueueData.YouTubeData
            val endpoint = data?.endpoint?.let {
                runCatching {
                    endpointJson.decodeFromString(WatchEndpoint.serializer(), it)
                }.getOrNull()
            }
            if (data == null || endpoint == null) {
                ListQueue(title, status.items, status.mediaItemIndex, status.position)
            } else {
                YouTubeQueue(
                    endpoint = endpoint,
                    continuation = data.continuation,
                    restoredStatus = status,
                )
            }
        }

        QueueType.YOUTUBE_ALBUM_RADIO -> {
            val data = queueData as? QueueData.YouTubeAlbumRadioData
            if (data == null || data.playlistId == "youtube_album_radio") {
                ListQueue(title, status.items, status.mediaItemIndex, status.position)
            } else {
                YouTubeAlbumRadio(
                    playlistId = data.playlistId,
                    albumSongCount = data.albumSongCount,
                    continuation = data.continuation,
                    firstTimeLoaded = data.firstTimeLoaded,
                    restoredStatus = status,
                )
            }
        }

        QueueType.LOCAL_ALBUM_RADIO -> {
            val data = queueData as? QueueData.LocalAlbumRadioData
            val album = data?.takeUnless { it.albumId == "local_album_radio" }
                ?.let { database?.albumWithSongs(it.albumId)?.first() }
            if (data == null || album == null) {
                ListQueue(title, status.items, status.mediaItemIndex, status.position)
            } else {
                LocalAlbumRadio(
                    albumWithSongs = album,
                    startIndex = data.startIndex,
                    playlistId = data.playlistId,
                    continuation = data.continuation,
                    firstTimeLoaded = data.firstTimeLoaded,
                    restoredStatus = status,
                )
            }
        }

        else -> ListQueue(title, status.items, status.mediaItemIndex, status.position)
    }
}
