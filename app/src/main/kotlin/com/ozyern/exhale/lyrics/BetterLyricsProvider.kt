/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.betterlyrics.BetterLyrics
import com.ozyern.exhale.constants.EnableBetterLyricsKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get

import com.ozyern.exhale.utils.GlobalLog
import android.util.Log

object BetterLyricsProvider : LyricsProvider {
    init {
        BetterLyrics.logger = { message ->
            GlobalLog.append(Log.INFO, "BetterLyrics", message)
        }
    }

    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val exact = BetterLyrics.getLyrics(title = title, artist = artist, album = album, durationSeconds = duration)
        if (exact.isSuccess) return exact
        if (album != null) {
            val withoutAlbum = BetterLyrics.getLyrics(title = title, artist = artist, album = null, durationSeconds = duration)
            if (withoutAlbum.isSuccess) return withoutAlbum
        }
        val relaxed = BetterLyrics.getLyrics(title = title, artist = artist, album = null, durationSeconds = 0)
        if (relaxed.isSuccess) return relaxed
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
        BetterLyrics.getAllLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
