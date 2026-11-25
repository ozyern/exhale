/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.data

import com.ozyern.exhale.desktop.player.MusicVideoMatch
import com.ozyern.exhale.desktop.player.StreamResolver
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * What came over from the Android app, asked of the live services: a pasted Spotify link, a line
 * of a song, and the music video for an album track. Network; opt in with EXHALE_PROBE=1.
 */
class AndroidPortProbe {
    @Test
    fun probe() = runBlocking {
        if (System.getenv("EXHALE_PROBE") != "1") return@runBlocking
        val link = MusicLinks.resolve("https://open.spotify.com/track/2qSkIjg1o9h3YT9RAgYN75")
        println("PROBE link -> ${link?.title} (${link?.id})")
        val lyrics = LyricSearch.search("his palms are sweaty knees weak arms are heavy")
        println("PROBE lyrics -> ${lyrics.map { "${it.song.title} / ${it.song.artists.joinToString { a -> a.name }}" }}")
        val espresso = YouTube.search("Espresso Sabrina Carpenter", YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
        println("PROBE song -> ${espresso?.title} ${espresso?.id}")
        if (espresso != null) {
            val videoId = MusicVideoMatch.videoIdFor(espresso)
            println("PROBE video id -> $videoId")
            if (videoId != null) {
                val video = StreamResolver.resolveVideo(videoId, 720)
                println("PROBE video -> ${video.getOrNull()?.quality} from ${video.getOrNull()?.clientName} ${video.exceptionOrNull()?.message.orEmpty().take(200)}")
                val sound = StreamResolver.resolve(espresso.id)
                println("PROBE song sound -> ${sound.getOrNull()?.mimeType} ${sound.exceptionOrNull()?.message.orEmpty().take(200)}")
            }
        }
    }
}
