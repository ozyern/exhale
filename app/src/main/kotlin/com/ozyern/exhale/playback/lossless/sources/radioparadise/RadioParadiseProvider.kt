/*
 * Exhale Project (2026)
 * Ported from Strawberry Music Player (Jonas Kvinge)
 * Licensed Under GPL-3.0
 */

package com.ozyern.exhale.playback.lossless.sources.radioparadise

import com.ozyern.exhale.playback.lossless.sources.audiosource.DirectStream
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

object RadioParadiseProvider {
    private const val TAG = "RadioParadise"
    private const val USER_AGENT = "Exhale-Android (+https://github.com/ozyern/exhale)"

    data class ChannelStream(
        val channelId: String,
        val name: String,
        val streamUrl: String,
    )

    val CHANNELS = listOf(
        ChannelStream("0", "Main Mix", "https://stream.radioparadise.com/flac"),
        ChannelStream("1", "Mellow Mix", "https://stream.radioparadise.com/mellow-flac"),
        ChannelStream("2", "Rock Mix", "https://stream.radioparadise.com/rock-flac"),
        ChannelStream("3", "World Mix", "https://stream.radioparadise.com/world-flac"),
        ChannelStream("4", "Serenity Mix", "https://stream.radioparadise.com/serenity-flac"),
        ChannelStream("5", "Radio 2050", "https://stream.radioparadise.com/radio2050-flac"),
        ChannelStream("6", "KFAT", "https://stream.radioparadise.com/kfat-flac"),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    fun resolve(title: String, artist: String): DirectStream? {
        val cleanT = title.lowercase().trim()
        val cleanA = artist.lowercase().trim()
        if (cleanT.isBlank()) return null

        for (ch in CHANNELS) {
            val nowPlaying = runCatching {
                val req = Request.Builder()
                    .url("https://api.radioparadise.com/api/now_playing?chan=${ch.channelId}")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank() || body.trimStart().startsWith("<")) null else JSONObject(body)
                }
            }.getOrNull() ?: continue

            val npTitle = nowPlaying.optString("title").lowercase().trim()
            val npArtist = nowPlaying.optString("artist").lowercase().trim()
            if (npTitle.isBlank()) continue

            val titleMatch = npTitle.contains(cleanT) || cleanT.contains(npTitle)
            val artistMatch = cleanA.isBlank() || npArtist.contains(cleanA) || cleanA.contains(npArtist)

            if (titleMatch && artistMatch) {
                Timber.tag(TAG).i("Matched live Radio Paradise track on %s: %s - %s", ch.name, npTitle, npArtist)
                return DirectStream(
                    uri = ch.streamUrl,
                    mimeType = "audio/flac",
                    codecs = "flac",
                    contentLength = null,
                    label = "Radio Paradise · FLAC",
                    source = AudioSourceType.RADIOPARADISE,
                    matchedTitle = nowPlaying.optString("title"),
                    matchedArtist = nowPlaying.optString("artist"),
                    matchedAlbum = nowPlaying.optString("album"),
                    sampleRate = 44100,
                    bitDepth = 16,
                )
            }
        }
        return null
    }
}
