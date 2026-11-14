/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.queues

import androidx.media3.common.MediaItem
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeAlbumRadio(
    @Volatile private var playlistId: String,
    @Volatile private var albumSongCount: Int = 0,
    @Volatile private var continuation: String? = null,
    @Volatile private var firstTimeLoaded: Boolean = false,
    private var restoredStatus: Queue.Status? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    private val endpoint: WatchEndpoint
        get() = WatchEndpoint(
            playlistId = playlistId
        )

    override suspend fun getInitialStatus(): Queue.Status {
        restoredStatus?.let {
            restoredStatus = null
            return it
        }

        return withContext(IO) {
            val albumSongs = YouTube.albumSongs(playlistId).getOrThrow()
            albumSongCount = albumSongs.size
            Queue.Status(
                title = albumSongs.firstOrNull()?.album?.name.orEmpty(),
                items = albumSongs.map { it.toMediaItem() },
                mediaItemIndex = 0
            )
        }
    }

    override fun hasNextPage(): Boolean = !firstTimeLoaded || continuation != null

    fun persistenceData() = QueueState(playlistId, albumSongCount, continuation, firstTimeLoaded)

    override suspend fun nextPage(): List<MediaItem> = withContext(IO) {
        val nextResult = YouTube.next(endpoint, continuation).getOrThrow()
        continuation = nextResult.continuation
        if (!firstTimeLoaded) {
            firstTimeLoaded = true
            nextResult.items.subList(albumSongCount.coerceIn(0, nextResult.items.size), nextResult.items.size)
                .map { it.toMediaItem() }
        } else {
            nextResult.items.map { it.toMediaItem() }
        }
    }

    data class QueueState(
        val playlistId: String,
        val albumSongCount: Int,
        val continuation: String?,
        val firstTimeLoaded: Boolean,
    )
}
