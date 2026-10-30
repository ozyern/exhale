/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.playback.queues

import androidx.media3.common.MediaItem
import com.ozyern.exhale.extensions.ExtraIsMusicVideo
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?

    /**
     * The album or playlist this queue plays, when it plays one: what per-collection settings
     * (its transition style) are keyed by. Null for radios, mixes and one-off lists.
     */
    val sourceId: String? get() = null

    /**
     * Started from a Shuffle button: the list is already in shuffled order, with the player's own
     * shuffle mode off. Smart Shuffle treats it the same as shuffle mode.
     */
    val shuffled: Boolean get() = false

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterExplicit(),
                )
            } else {
                this
            }
        fun filterVideo(enabled: Boolean = true) =
            if (enabled) {
                copy(
                    items = items.filterVideo(),
                )
            } else {
                this
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

fun List<MediaItem>.filterVideo(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.mediaMetadata.extras?.getBoolean(ExtraIsMusicVideo, false) == true
        }
    } else {
        this
    }
