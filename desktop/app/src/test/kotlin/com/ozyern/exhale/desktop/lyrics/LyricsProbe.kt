/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import com.ozyern.exhale.betterlyrics.BetterLyrics
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.lrclib.LrcLib
import com.ozyern.exhale.simpmusic.SimpMusicLyrics
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Which lyrics source answers for real songs, and which stays quiet. Every provider is asked the
 * same way the app asks it, so a gap here is the gap the listener sees. EXHALE_SMOKE=1 to run.
 */
class LyricsProbe {
    private suspend fun attempt(name: String, call: suspend () -> String?): String = try {
        val raw = withTimeoutOrNull(9_000) { call() }
        when {
            raw.isNullOrBlank() -> "$name: —"
            else -> {
                val parsed = LyricsParser.parse(raw)
                val sung = parsed.filter { it.text.isNotBlank() }
                if (sung.size >= 2) {
                    "$name: SYNCED ${sung.size} lines, ${sung.first().timeMs / 1000}s..${sung.last().timeMs / 1000}s" +
                        (if (sung.any { it.wordSynced }) " (word)" else "")
                } else {
                    "$name: plain ${raw.lines().count { it.isNotBlank() }} lines"
                }
            }
        }
    } catch (failure: Exception) {
        "$name: failed ${failure.message?.take(60)}"
    }

    @Test
    fun whoAnswers() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()

        // The songs actually listened to here, straight from the saved history — a gap in the wild is
        // worth more than a gap in a list of hits. A file of queries overrides it when one is left.
        val handpicked = java.io.File(System.getProperty("java.io.tmpdir"), "exhale-lyrics-queries.txt")
            .takeIf { it.isFile }
            ?.readLines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        val played = com.ozyern.exhale.desktop.data.DesktopPrefs.history.value
            .take(System.getenv("EXHALE_LYRICS_COUNT")?.toIntOrNull() ?: 10)
        val queries = handpicked?.takeIf { it.isNotEmpty() }
            ?: played.map { song -> "${song.title} ${song.artists.firstOrNull()?.name.orEmpty()}".trim() }
                .ifEmpty { listOf("Sabrina Carpenter Espresso") }

        var synced = 0
        for (query in queries) {
            val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            if (song == null) {
                println("== $query -> no search result")
                continue
            }
            val duration = song.duration ?: 0
            val title = SongQuery.cleanTitle(song.title)
            val lead = song.artists.firstOrNull()?.name.orEmpty()
            val album = song.album?.name
            println("== ${song.title} — ${song.artists.joinToString { it.name }} (${song.id}, ${duration}s) cleaned='$title' lead='$lead' album='$album'")

            println("   " + attempt("BetterLyrics") { BetterLyrics.getLyrics(title = title, artist = lead, album = album, durationSeconds = duration).getOrNull() })
            println("   " + attempt("SimpMusic") {
                val tracks = SimpMusicLyrics.getLyricsByVideoId(song.id)
                val best = tracks.firstOrNull()
                best?.richSyncLyrics?.takeIf { it.isNotBlank() } ?: best?.syncedLyrics?.takeIf { it.isNotBlank() } ?: best?.plainLyrics
            })
            println("   " + attempt("LRCLIB(album)") { LrcLib.getLyrics(title, lead, duration, album).getOrNull() })
            println("   " + attempt("LRCLIB(bare)") { LrcLib.getLyrics(title, lead, duration, null).getOrNull() })
            println("   " + attempt("KuGou") { KuGou.getLyrics(title, lead, duration).getOrNull() })
            println("   " + attempt("YouTube") {
                YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.lyricsEndpoint?.let { YouTube.lyrics(it).getOrNull() }
            })

            val verdict = LyricsRepository.lyricsFor(song, duration)
            val summary = when (verdict) {
                is Lyrics.Synced -> { synced++; "SYNCED from ${verdict.source} (${verdict.lines.size} lines, word=${verdict.wordLevel})" }
                is Lyrics.Plain -> "plain from ${verdict.source}"
                Lyrics.NotFound -> "NOTHING"
            }
            println("   => $summary")
        }
        println("synced for $synced of ${queries.size}")
    }

    /** How long the listener waits before the words appear, on the songs actually played here. */
    @Test
    fun howFast() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val played = com.ozyern.exhale.desktop.data.DesktopPrefs.history.value.take(8)
        assumeTrue("no history to measure", played.isNotEmpty())
        var synced = 0
        var worst = 0L
        for (song in played) {
            LyricsRepository.clearCache()
            // What the player knows the moment the song is loaded: plenty of songs carry no runtime of
            // their own, and mpv supplies it. Asking without one is a different, worse question.
            val runtime = song.duration ?: YouTube.search(
                "${song.title} ${song.artists.firstOrNull()?.name.orEmpty()}",
                YouTube.SearchFilter.FILTER_SONG,
            ).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()?.duration ?: 0
            val started = System.currentTimeMillis()
            val lyrics = LyricsRepository.lyricsFor(song, runtime)
            val took = System.currentTimeMillis() - started
            worst = maxOf(worst, took)
            val what = when (lyrics) {
                is Lyrics.Synced -> { synced++; "synced from ${lyrics.source}${if (lyrics.wordLevel) " (word)" else ""}" }
                is Lyrics.Plain -> "plain from ${lyrics.source}"
                Lyrics.NotFound -> "nothing"
            }
            println("   ${took}ms  [dur=$runtime] ${song.title} -> $what")
        }
        println("synced $synced of ${played.size}, slowest ${worst}ms")
    }

    /** Whether the words come through spelled out, on songs that have words worth spelling out. */
    @Test
    fun wordsComeThroughWhole() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val queries = listOf(
            "Eminem Rap God",
            "Kendrick Lamar Not Like Us",
            "Eminem Lose Yourself",
            "Travis Scott FE!N",
            "Playboi Carti Sky",
        )
        val masked = Regex("""\p{L}\*{2,}|\*{2,}\p{L}|\p{L}\*+\p{L}""")
        var clean = 0
        for (query in queries) {
            val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
            LyricsRepository.clearCache()
            val lyrics = LyricsRepository.lyricsFor(song, song.duration ?: 0)
            val text = when (lyrics) {
                is Lyrics.Synced -> lyrics.lines.joinToString(" ") { it.text }
                is Lyrics.Plain -> lyrics.lines.joinToString(" ")
                Lyrics.NotFound -> ""
            }
            val source = when (lyrics) {
                is Lyrics.Synced -> lyrics.source
                is Lyrics.Plain -> lyrics.source
                Lyrics.NotFound -> "none"
            }
            val bleeped = masked.containsMatchIn(text)
            if (!bleeped && text.isNotEmpty()) clean++
            println("   ${song.title} -> $source, bleeped=$bleeped")
        }
        println("spelled out for $clean of ${queries.size}")
        assertTrue("only $clean of ${queries.size} came through unbleeped", clean >= queries.size - 1)
    }

    /**
     * A live medley is not the single. Two songs run together for five minutes, so the studio timings
     * for either of them cover half the performance at best and drift apart within a verse. The words
     * are still the right words, so they stay — but they must not be scrolled against the recording.
     */
    @Test
    fun aLiveMedleyIsNotScrolledAgainstStudioTimings() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val video = YouTube.search("Sabrina Carpenter Espresso Bed Chem live", YouTube.SearchFilter.FILTER_VIDEO)
            .getOrNull()?.items?.filterIsInstance<SongItem>()
            ?.firstOrNull { it.title.contains("/") && (it.duration ?: 0) > 250 }
        assumeTrue("the medley is not in the search results today", video != null)
        val runtime = video!!.duration ?: 0
        LyricsRepository.clearCache()
        val lyrics = LyricsRepository.lyricsFor(video, runtime)
        val covered = (lyrics as? Lyrics.Synced)?.let { it.lines.last().timeMs / 1000.0 / runtime }
        println("   ${video.title} (${video.id}, ${runtime}s) -> ${(lyrics as? Lyrics.Synced)?.source ?: (lyrics as? Lyrics.Plain)?.source ?: "nothing"}" +
            (covered?.let { ", synced covering ${(it * 100).toInt()}%" } ?: ""))
        assertTrue(
            "one song's timings were scrolled against the whole performance",
            covered == null || covered >= 0.75,
        )
    }

    /**
     * A song played as its video. The video runs longer than the song it carries, so every source
     * that matches on length misses — and the loose one answers with whatever else runs that long,
     * which is how these all ended up singing another of the artist's songs.
     */
    @Test
    fun aVideosLyricsAreItsOwn() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        // A word from deep in each song, not its title: a wrong match by the same artist would share
        // the title far more often than it shares a verse.
        val expected = mapOf(
            "Sabrina Carpenter Espresso" to "espresso",
            "Sabrina Carpenter Please Please Please" to "heartbreak",
        )
        for ((query, word) in expected) {
            val video = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                ?.items?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
            LyricsRepository.clearCache()
            val lyrics = LyricsRepository.lyricsFor(video, video.duration ?: 0)
            val words = when (lyrics) {
                is Lyrics.Synced -> lyrics.lines.joinToString(" ") { it.text }
                is Lyrics.Plain -> lyrics.lines.joinToString(" ")
                Lyrics.NotFound -> ""
            }.lowercase()
            println("   ${video.title} (${video.duration}s) -> ${words.take(60)}")
            assertTrue("$query came back with another song's words", words.isEmpty() || word in words)
        }
    }
}
