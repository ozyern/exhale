/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.lyrics

import android.content.Context
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.constants.EnableKugouKey
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> {
        val exact = KuGou.getLyrics(title, artist, duration)
        if (exact.isSuccess) return exact
        if (duration > 0) {
            val relaxed = KuGou.getLyrics(title, artist, -1)
            if (relaxed.isSuccess) return relaxed
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
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, callback)
    }
}
