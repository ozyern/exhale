package com.ozyern.exhale.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.ozyern.exhale.constants.EnableBetterLyricsKey
import com.ozyern.exhale.constants.EnableKugouKey
import com.ozyern.exhale.constants.EnableLrcLibKey
import com.ozyern.exhale.constants.EnableMusixmatchKey
import com.ozyern.exhale.constants.EnablePaxsenixKey
import com.ozyern.exhale.constants.EnableSimpMusicKey
import com.ozyern.exhale.constants.EnableUnisonKey
import com.ozyern.exhale.constants.EnableYouLyPlusKey
import com.ozyern.exhale.constants.PreferredLyricsProvider
import com.ozyern.exhale.lyrics.bitchord.PaxSenix
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get

object LyricsProviderSettings {
    fun idForLegacyPreference(provider: PreferredLyricsProvider): String = when (provider) {
        PreferredLyricsProvider.LRCLIB -> "LrcLib"
        PreferredLyricsProvider.KUGOU -> "Kugou"
        PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
        PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
        PreferredLyricsProvider.YOULYPLUS -> "YouLyPlus"
        PreferredLyricsProvider.PAXSENIX -> "Paxsenix"
    }

    fun enabledKey(id: String): Preferences.Key<Boolean> = when (id) {
        "BetterLyrics" -> EnableBetterLyricsKey
        "Kugou" -> EnableKugouKey
        "LrcLib" -> EnableLrcLibKey
        "Musixmatch" -> EnableMusixmatchKey
        "Paxsenix" -> EnablePaxsenixKey
        "SimpMusic" -> EnableSimpMusicKey
        "Unison" -> EnableUnisonKey
        "YouLyPlus" -> EnableYouLyPlusKey
        else -> booleanPreferencesKey("enableLyrics$id")
    }

    fun isEnabled(context: Context, provider: LyricsProvider): Boolean =
        context.dataStore[enabledKey(provider.id)] ?: true

    fun initialize(context: Context, provider: LyricsProvider) {
        if (provider !is BitChordLyricsProvider) return
        val credentials = context.applicationContext.getSharedPreferences("lyrics_credentials", Context.MODE_PRIVATE)
        PaxSenix.setApiKey(credentials.getString("paxsenix_api_key", "").orEmpty())
    }
}
