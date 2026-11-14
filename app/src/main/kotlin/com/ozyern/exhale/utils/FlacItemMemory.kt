/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import com.ozyern.exhale.openflac.OpenFlac

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

    private companion object {
        const val MAX_ARTISTS = 3_000
        const val MAX_ITEMS_PER_ARTIST = 12
    }
}
