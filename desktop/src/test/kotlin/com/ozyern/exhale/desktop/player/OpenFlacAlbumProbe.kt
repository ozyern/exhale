/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.openflac.OpenFlac
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * How many tracks of an album the public-catalogue FLAC search finds, asked the way the Android app
 * asks: title, first artist, YouTube's duration and the album's name. EXHALE_SMOKE=1 to run;
 * EXHALE_ALBUM_ID picks the album (Eminem's Encore by default).
 */
class OpenFlacAlbumProbe {
    @Test
    fun albumCoverage() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val browseId = System.getenv("EXHALE_ALBUM_ID")
            ?: YouTube.search("Encore Eminem", YouTube.SearchFilter.FILTER_ALBUM).getOrThrow().items
                .filterIsInstance<com.ozyern.exhale.innertube.models.AlbumItem>().first().browseId
        val page = YouTube.album(browseId).getOrThrow()
        println("=== ${page.album.title} $browseId ${page.album.playlistId}")
        var found = 0
        val only = System.getenv("EXHALE_ONLY_TITLE")
        for (song in page.songs.filter { only == null || it.title.startsWith(only) }) {
            val artist = song.artists.firstOrNull()?.name.orEmpty()
            val started = System.currentTimeMillis()
            val copy = OpenFlac.findCopy(song.title, artist, song.duration ?: -1, page.album.title)
            if (copy != null) found++
            println("  ${song.title.padEnd(34)} ${song.duration}s  ${System.currentTimeMillis() - started}ms  -> ${copy?.title ?: "none"} ${copy?.durationSeconds ?: ""} ${copy?.url?.substringAfter("/download/")?.take(60) ?: ""}")
        }
        println("found $found of ${page.songs.size}")
    }
}
