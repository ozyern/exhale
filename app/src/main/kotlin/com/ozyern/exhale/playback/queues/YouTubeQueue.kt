/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.playback.queues

import androidx.media3.common.MediaItem
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.lyrics.SongQuery
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubeQueue(
    private var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    override val shuffled: Boolean = false,
) : Queue {
    // The playlist it was started from, taken before `next` replaces the endpoint. Radios
    // ("RD…") are generated per play and are not a collection anyone sets anything on.
    override val sourceId: String? = endpoint.playlistId?.takeUnless { it.startsWith("RD") }

    // Only a generated radio is reordered: a playlist, an album or a chart ("RDCLAK…", a curated list
    // that merely shares the prefix) plays the way it was put together.
    private val shaper: RadioShaper<SongItem>? = endpoint.playlistId
        .let { it == null || (it.startsWith("RD") && !it.startsWith("RDCLAK")) }
        .takeIf { it }
        ?.let {
            RadioShaper(
                idOf = { it.id },
                songOf = { song ->
                    "${SongQuery.normalized(SongQuery.cleanTitle(song.title))}|${leadArtist(song)}"
                },
                artistOf = ::leadArtist,
            )
        }

    private var continuation: String? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube.next(endpoint, continuation).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        val index = (nextResult.currentIndex ?: 0).coerceIn(0, (nextResult.items.size - 1).coerceAtLeast(0))
        val played = nextResult.items.take(index + 1)
        shaper?.keep(played)
        val upcoming = nextResult.items.drop(index + 1).let { shaper?.shape(it) ?: it }
        return Queue.Status(
            title = nextResult.title,
            items = (played + upcoming).map { it.toMediaItem() },
            mediaItemIndex = index,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        // A page the shaper empties is not the end of the radio; the one after it may still have songs.
        repeat(MAX_PAGES_PER_LOAD) {
            val nextResult =
                withContext(IO) {
                    YouTube.next(endpoint, continuation).getOrThrow()
                }
            endpoint = nextResult.endpoint
            continuation = nextResult.continuation
            val shaped = shaper?.shape(nextResult.items) ?: nextResult.items
            if (shaped.isNotEmpty() || continuation == null) return shaped.map { it.toMediaItem() }
        }
        return emptyList()
    }

    companion object {
        private const val MAX_PAGES_PER_LOAD = 3

        fun radio(song: MediaMetadata) = YouTubeQueue(WatchEndpoint(song.id), song)

        private fun leadArtist(song: SongItem): String =
            SongQuery.normalized(song.artists.firstOrNull()?.name.orEmpty())
    }
}
