/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

/**
 * Links people share from the app, as the Android app makes them (utils/ShareUtils): Exhale's own
 * domain, which opens the app where it's installed and offers it (or the song on YouTube Music) where
 * it isn't. The paths keep YouTube Music's shape, so either app reads them as a YouTube Music link.
 */
object ShareLinks {
    const val DOMAIN = "exhale.ozyern.me"
    private const val BASE_URL = "https://$DOMAIN"

    fun song(songId: String): String = "$BASE_URL/watch/?v=$songId"

    fun playlist(playlistId: String): String = "$BASE_URL/playlist/?list=$playlistId"

    /** An album is shared by its playlist, as YouTube Music shares it. */
    fun album(playlistId: String): String = playlist(playlistId)

    fun artist(artistId: String): String = "$BASE_URL/channel/$artistId"

    /** Any YouTube Music link the app was handed, moved onto Exhale's domain; others as they are. */
    fun fromYouTubeMusic(link: String): String {
        val path = link.substringAfter("music.youtube.com", missingDelimiterValue = "").takeIf { it.isNotEmpty() } ?: return link
        return when {
            path.startsWith("/watch?") -> "$BASE_URL/watch/?" + path.removePrefix("/watch?")
            path.startsWith("/playlist?") -> "$BASE_URL/playlist/?" + path.removePrefix("/playlist?")
            else -> BASE_URL + path
        }
    }
}
