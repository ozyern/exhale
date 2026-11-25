/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What YouTube actually offers for a given id, audio and video alike — the ground truth behind
 * "Switch to video". Set EXHALE_SMOKE=1 and EXHALE_VIDEO_ID to look at a particular song.
 */
class VideoFormatsProbe {
    @Test
    fun listFormats() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val videoId = java.io.File(System.getProperty("user.home"), ".exhale-video-id").takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null } ?: "1mtJvsfAtF0"
        // As the app sees it: signed in, which is what decides whether a client answers at all.
        AccountSession.applySaved()
        println("signed in: ${AccountSession.isSignedIn}")
        val stamp = NewPipeUtils.getSignatureTimestamp(videoId)
        println("signature timestamp: ${stamp.getOrNull()} ${stamp.exceptionOrNull()?.message ?: ""}")
        val signatureTimestamp = stamp.getOrNull()
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.IOS,
            YouTubeClient.MOBILE,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.ANDROID_VR_1_61_48,
            YouTubeClient.ANDROID_CREATOR,
            YouTubeClient.ANDROID_TESTSUITE,
            YouTubeClient.IPADOS,
            YouTubeClient.VISIONOS,
            YouTubeClient.TVHTML5,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.WEB,
            YouTubeClient.WEB_REMIX,
        )
        for (client in clients) {
            val response = YouTube.player(videoId, null, client, signatureTimestamp).getOrNull()
            if (response == null) {
                println("${client.clientName}: request failed")
                continue
            }
            val status = response.playabilityStatus.status
            val details = response.videoDetails
            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            val audio = formats.filter { it.isAudio }
            val video = formats.filterNot { it.isAudio }
            val muxed = response.streamingData?.formats.orEmpty()
            println("=== ${client.clientName}: $status | title=${details?.title} | musicVideoType=${details?.musicVideoType}")
            println("    audio formats: ${audio.size}, video formats: ${video.size}, muxed formats: ${muxed.size}")
            muxed.sortedByDescending { it.bitrate }.forEach {
                println("    muxed itag=${it.itag} ${it.qualityLabel} ${it.width}x${it.height} ${it.bitrate / 1000}k ${it.mimeType.substringBefore(';')} direct=${it.url != null}")
            }
            video.sortedByDescending { it.bitrate }.take(6).forEach {
                val unscrambled = NewPipeUtils.getStreamUrl(it, videoId, client)
                val verdict = unscrambled.fold({ "resolved" }, { why -> "FAILED ${why.message}" })
                println("    video itag=${it.itag} ${it.qualityLabel} ${it.width}x${it.height} ${it.fps}fps ${it.bitrate / 1000}k ${it.mimeType.substringBefore(';')} direct=${it.url != null} -> $verdict")
            }
            audio.sortedByDescending { it.bitrate }.take(3).forEach {
                val unscrambled = NewPipeUtils.getStreamUrl(it, videoId, client)
                val verdict = unscrambled.fold({ "resolved" }, { why -> "FAILED ${why.message}" })
                println("    audio itag=${it.itag} ${it.bitrate / 1000}k ${it.mimeType.substringBefore(';')} direct=${it.url != null} -> $verdict")
            }
        }
    }
}
