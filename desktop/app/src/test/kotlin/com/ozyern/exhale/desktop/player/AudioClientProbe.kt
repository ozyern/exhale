/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The Android app's audio clients, in its order, asked for real songs the way it asks: player
 * response, best audio format, deciphered URL, a one-byte range probe. Which ones work on this network,
 * and how long each takes. EXHALE_SMOKE=1 to run.
 */
class AudioClientProbe {
    @Test
    fun androidOrder() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val songs = listOf(
            "Espresso" to "Sabrina Carpenter",
            "Houdini" to "Eminem",
            "Kesariya" to "Arijit Singh",
            "APT." to "ROSE Bruno Mars",
            "Blinding Lights" to "The Weeknd",
        ).map { (t, a) ->
            YouTube.search("$t $a", YouTube.SearchFilter.FILTER_SONG).getOrThrow().items
                .filterIsInstance<com.ozyern.exhale.innertube.models.SongItem>().first()
        }
        // Android's order: preferred ANDROID_VR first, then STREAM_FALLBACK_CLIENTS, then WEB_REMIX.
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.IOS, YouTubeClient.MOBILE, YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS_MUSIC, YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_VR_1_43_32,
            YouTubeClient.ANDROID_CREATOR, YouTubeClient.ANDROID_TESTSUITE, YouTubeClient.ANDROID_UNPLUGGED,
            YouTubeClient.IPADOS, YouTubeClient.VISIONOS, YouTubeClient.TVHTML5, YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.WEB, YouTubeClient.WEB_CREATOR, YouTubeClient.WEB_REMIX,
        )
        for (song in songs) {
            println("== ${song.title} (${song.id})")
            val sts = NewPipeUtils.getSignatureTimestamp(song.id).getOrNull()
            for (client in clients) {
                val started = System.currentTimeMillis()
                val response = YouTube.player(song.id, null, client, sts)
                val r = response.getOrNull()
                val status = r?.playabilityStatus?.status ?: "ERR ${response.exceptionOrNull()?.javaClass?.simpleName}"
                val audio = r?.streamingData?.adaptiveFormats.orEmpty().filter { it.isAudio && it.bitrate > 0 }.maxByOrNull { it.bitrate }
                val url = audio?.let { NewPipeUtils.getStreamUrl(it, song.id, client) }
                val code = url?.getOrNull()?.let { u ->
                    runCatching {
                        http.newCall(Request.Builder().url(u).header("User-Agent", client.userAgent).header("Range", "bytes=0-0").build())
                            .execute().use { it.code }
                    }.getOrDefault(-1)
                }
                val took = System.currentTimeMillis() - started
                val verdict = when {
                    audio == null -> "no audio"
                    url?.isFailure == true -> "decipher failed"
                    else -> "HTTP $code @ ${audio.bitrate / 1000}k ${if (audio.url != null) "direct" else "ciphered"}"
                }
                println("   ${client.clientName.padEnd(30)} ${took.toString().padStart(5)} ms  $status  $verdict")
            }
        }
    }
}
