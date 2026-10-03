/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    fun isEnabled(context: Context): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String>

    /**
     * The same, for a source that can use the recording itself — its ISRC, or the document another
     * search already found — rather than only its name. Everything else ignores [recording].
     */
    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        recording: Recording?,
    ): Result<String> = getLyrics(id, title, artist, album, duration)

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }
}

/**
 * Which recording a song is, once something has worked it out: the ISRC that names exactly one
 * recording, and where the search that found it left the lyrics document.
 */
data class Recording(
    val isrc: String?,
    val lyricsUrl: String?,
    val durationSeconds: Int?,
)
