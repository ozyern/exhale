/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.lrclib.LrcLib
import com.ozyern.exhale.constants.EnableLrcLibKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val exact = LrcLib.getLyrics(title, artist, duration, album)
        if (exact.isSuccess) return exact
        if (album != null) {
            val withoutAlbum = LrcLib.getLyrics(title, artist, duration, null)
            if (withoutAlbum.isSuccess) return withoutAlbum
        }
        if (duration > 0) {
            val relaxed = LrcLib.getLyrics(title, artist, -1, album)
            if (relaxed.isSuccess) return relaxed
            if (album != null) {
                val relaxedNoAlbum = LrcLib.getLyrics(title, artist, -1, null)
                if (relaxedNoAlbum.isSuccess) return relaxedNoAlbum
            }
        }
        return exact
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}
