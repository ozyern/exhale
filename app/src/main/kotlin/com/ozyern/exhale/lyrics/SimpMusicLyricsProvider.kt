/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.constants.EnableSimpMusicLyricsKey
import com.ozyern.exhale.simpmusic.SimpMusicLyrics
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get

object SimpMusicLyricsProvider : LyricsProvider {
    override val name: String = "SimpMusic"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableSimpMusicLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val exact = SimpMusicLyrics.getLyrics(videoId = id, duration = duration)
        if (exact.isSuccess) return exact
        // Fallback for music videos / live cuts: take video-matched lyrics regardless of duration drift
        return SimpMusicLyrics.getLyrics(videoId = id, duration = 0)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        SimpMusicLyrics.getAllLyrics(videoId = id, duration = duration, callback = callback)
    }
}

