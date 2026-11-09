/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.NewPipeUtils
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.YouTubeClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Which tracks of an album YouTube hands a FLAC rendition for, and through which clients. Run for the
 * standard and deluxe editions side by side to see whether the same song differs between them.
 * EXHALE_SMOKE=1 to run.
 */
class LosslessAlbumProbe {
    @Test
    fun shortNSweetEditions() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        // Signed in as the desktop app is, when it is: YouTube offers FLAC only to an account.
        val cookie = com.ozyern.exhale.desktop.data.DesktopPrefs.cookie
        if (cookie != null) {
            YouTube.cookie = cookie
            YouTube.dataSyncId = com.ozyern.exhale.desktop.data.DesktopPrefs.dataSyncId
            YouTube.visitorData = com.ozyern.exhale.desktop.data.DesktopPrefs.visitorData ?: YouTube.visitorData().getOrThrow()
        } else {
            YouTube.visitorData = YouTube.visitorData().getOrThrow()
        }
        println("signed in: ${cookie != null}")
        val albums = YouTube.search("Short n' Sweet Deluxe Sabrina Carpenter", YouTube.SearchFilter.FILTER_ALBUM).getOrThrow()
            .items.filterIsInstance<AlbumItem>().filter { it.title.contains("Short n' Sweet", true) && "Piano" !in it.title }.take(3)
        val clients = listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH, YouTubeClient.VISIONOS, YouTubeClient.IPADOS, YouTubeClient.IOS,
            YouTubeClient.MOBILE, YouTubeClient.ANDROID_MUSIC, YouTubeClient.IOS_MUSIC, YouTubeClient.TVHTML5,
            YouTubeClient.WEB_REMIX, YouTubeClient.WEB, YouTubeClient.ANDROID_VR_1_61_48, YouTubeClient.ANDROID_UNPLUGGED,
        )
        // The deluxe edition doesn't come up in that search; its id is the one the desktop app played from.
        val browseIds = (listOf("MPREb_T0ssT2HczFT") + albums.map { it.browseId }).distinct().take(2)
        for (browseId in browseIds) {
            val page = YouTube.album(browseId).getOrThrow()
            println("=== ${page.album.title} ($browseId) ${page.songs.size} songs")
            page.songs.forEach { println("   # ${it.title} | ${it.artists.joinToString { a -> a.name }} | ${it.duration} | ${it.album?.name} | ${it.id}") }
            if (System.getenv("EXHALE_FORMATS") != "1") continue
            for (song in page.songs.take(5)) {
                val sts = NewPipeUtils.getSignatureTimestamp(song.id).getOrNull()
                val verdicts = clients.map { client ->
                    val r = YouTube.player(song.id, null, client, sts).getOrNull()
                    val audio = r?.streamingData?.adaptiveFormats.orEmpty().filter { it.isAudio }
                    val tag = when {
                        r == null -> "ERR"
                        r.playabilityStatus.status != "OK" -> r.playabilityStatus.status
                        audio.any { StreamResolver.isLosslessMime(it.mimeType) } -> "FLAC"
                        else -> "lossy"
                    }
                    "${client.clientName}=$tag"
                }
                println("  ${song.title.padEnd(28)} ${song.id}  ${verdicts.joinToString(" ")}")
            }
        }
    }
}
