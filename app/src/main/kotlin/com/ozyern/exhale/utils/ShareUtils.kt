package com.ozyern.exhale.utils

/**
 * Links people share from the app. They point at Exhale's own domain, which opens the app when
 * it is installed and offers it (or the song on YouTube Music) when it is not. The paths keep
 * YouTube Music's shape, so the app reads them exactly as it reads a YouTube Music link.
 */
object ShareUtils {
    const val DOMAIN = "exhale.ozyern.me"
    const val BASE_URL = "https://$DOMAIN"

    fun songUrl(songId: String): String = "$BASE_URL/watch/?v=$songId"

    fun playlistUrl(playlistId: String): String = "$BASE_URL/playlist/?list=$playlistId"

    fun albumUrl(playlistId: String): String = "$BASE_URL/playlist/?list=$playlistId"

    fun artistUrl(artistId: String): String = "$BASE_URL/channel/$artistId"
}
