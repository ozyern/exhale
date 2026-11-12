/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.openflac.OpenFlac
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * The uploads each artist's songs have been found in, kept on the phone: artist → identifiers,
 * newest first. See [OpenFlac.ItemMemory] for why.
 */
class FlacItemMemory(context: Context) : OpenFlac.ItemMemory {
    private val store = context.applicationContext.getSharedPreferences("flac_items", Context.MODE_PRIVATE)

    private fun read(artist: String): List<String> =
        runCatching { store.getString(artist, null) }.getOrNull()?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    override fun itemsFor(artist: String): List<String> = read(artist)

    @Synchronized
    override fun remember(artist: String, identifier: String) {
        val known = read(artist)
        if (known.firstOrNull() == identifier) return
        runCatching {
            if (store.all.size > MAX_ARTISTS) store.edit().clear().apply()
            store.edit().putString(artist, (listOf(identifier) + (known - identifier)).take(MAX_ITEMS_PER_ARTIST).joinToString("\n")).apply()
        }
    }

    @Synchronized
    override fun forget(artist: String, identifier: String) {
        val left = read(artist) - identifier
        runCatching {
            store.edit().apply { if (left.isEmpty()) remove(artist) else putString(artist, left.joinToString("\n")) }.apply()
        }
    }

    companion object {
        private const val MAX_ARTISTS = 3_000
        private const val MAX_ITEMS_PER_ARTIST = 12
        private const val ALBUM_LOOKUP_MS = 2_500L

        private val albumNames = ConcurrentHashMap<String, String>()

        /**
         * The album [mediaId] is on, asked of YouTube Music. A song played from search, a playlist
         * or a radio queue often carries no album, and the album's name is what finds its FLAC.
         * Blank answers (a video, a single with no album page) are kept too, so it is asked once.
         */
        suspend fun albumNameFor(mediaId: String): String? {
            albumNames[mediaId]?.let { return it.ifBlank { null } }
            val result = withTimeoutOrNull(ALBUM_LOOKUP_MS) {
                YouTube.next(WatchEndpoint(videoId = mediaId), followAutomixPreview = false).getOrNull()
            } ?: return null
            val name = result.items.firstOrNull { it.id == mediaId }?.album?.name.orEmpty()
            if (albumNames.size > 500) albumNames.clear()
            albumNames[mediaId] = name
            return name.ifBlank { null }
        }
    }
}
