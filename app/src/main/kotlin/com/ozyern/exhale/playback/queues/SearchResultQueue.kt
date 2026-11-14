/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.queues

import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.toMediaMetadata
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem

/** Plays the tapped song in list order, falling back to YouTube radio when no surrounding list exists. */
fun searchResultQueue(
    song: SongItem,
    siblings: List<YTItem>,
    title: String? = null,
): Queue {
    val songs = siblings.filterIsInstance<SongItem>().distinctBy { it.id }
    val startIndex = songs.indexOfFirst { it.id == song.id }
    return if (songs.size > 1 && startIndex >= 0) {
        ListQueue(
            title = title,
            items = songs.map { it.toMediaItem() },
            startIndex = startIndex,
        )
    } else {
        YouTubeQueue(WatchEndpoint(videoId = song.id), song.toMediaMetadata())
    }
}
