/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback.queues

import androidx.media3.common.MediaItem
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true): Status =
            if (enabled) filterItems { it.metadata?.explicit != true } else this

        fun filterVideoSongs(disableVideos: Boolean = false): Status =
            if (disableVideos) filterItems { it.metadata?.isVideoSong != true } else this

        private fun filterItems(keep: (MediaItem) -> Boolean): Status {
            var selectedIndex = 0
            val filtered = items.filterIndexed { index, item ->
                val included = keep(item)
                if (included && index < mediaItemIndex) selectedIndex++
                included
            }
            if (filtered.size == items.size) return this
            return copy(
                items = filtered,
                mediaItemIndex = if (filtered.isEmpty()) 0 else selectedIndex.coerceAtMost(filtered.lastIndex),
            )
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
