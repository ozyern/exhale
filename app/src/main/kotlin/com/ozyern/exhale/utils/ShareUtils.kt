package com.ozyern.exhale.utils

object ShareUtils {
    const val DOMAIN = "music.youtube.com"
    const val BASE_URL = "https://$DOMAIN"

    fun songUrl(songId: String): String = "$BASE_URL/watch?v=$songId"

    fun playlistUrl(playlistId: String): String = "$BASE_URL/playlist?list=$playlistId"

    fun albumUrl(playlistId: String): String = "$BASE_URL/playlist?list=$playlistId"

    fun artistUrl(artistId: String): String = "$BASE_URL/channel/$artistId"
}
