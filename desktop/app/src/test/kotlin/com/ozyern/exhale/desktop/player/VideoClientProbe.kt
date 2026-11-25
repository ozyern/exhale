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

/**
 * For a few music videos: does each client hand out a picture URL that answers? The Android app used
 * to take the first one it could decipher, starting from WEB_REMIX. EXHALE_SMOKE=1 to run.
 */
class VideoClientProbe {
    @Test
    fun whichClientsServePictures() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val http = OkHttpClient()
        val videos = listOf("eVli-tstM5E" to "Espresso", "_r-nPqWGG6c" to "Lose Yourself (video)", "kPa7bsKwL-c" to "APT.")
        val clients = listOf(
            YouTubeClient.WEB_REMIX, YouTubeClient.IOS, YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.MOBILE,
            YouTubeClient.VISIONOS, YouTubeClient.IPADOS,
        )
        for ((id, name) in videos) {
            println("== $name ($id)")
            val sts = NewPipeUtils.getSignatureTimestamp(id).getOrNull()
            for (client in clients) {
                val response = YouTube.player(id, null, client, sts).getOrNull()
                val status = response?.playabilityStatus?.status
                val picture = response?.streamingData?.adaptiveFormats.orEmpty()
                    .filter { !it.isAudio && (it.height ?: 0) in 1..720 }
                    .sortedWith(compareByDescending<com.ozyern.exhale.innertube.models.response.PlayerResponse.StreamingData.Format> { it.url != null }.thenByDescending { it.height ?: 0 })
                    .firstOrNull()
                val url = picture?.let { NewPipeUtils.getStreamUrl(it, id, client).getOrNull() }
                val code = url?.let {
                    runCatching {
                        http.newCall(Request.Builder().url(it).header("User-Agent", client.userAgent).header("Range", "bytes=0-0").build())
                            .execute().use { r -> r.code }
                    }.getOrDefault(-1)
                }
                println("   ${client.clientName.padEnd(28)} status=$status picture=${picture?.qualityLabel ?: "—"} direct=${picture?.url != null} url=${if (url == null) "—" else "HTTP $code"}")
            }
        }
    }
}
