/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import com.ozyern.exhale.betterlyrics.BetterLyrics
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.kugou.KuGou
import com.ozyern.exhale.lrclib.LrcLib
import com.ozyern.exhale.simpmusic.SimpMusicLyrics
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What the Android app's lyrics pick would show for real songs: every source in the Android order,
 * asked the way Android asks, then Android's rule (highest-placed, word-timed preferred) — and whether
 * the pick actually fits the song: its last line inside the song's length, its first line not past
 * most of it, and the song's name somewhere in the words. EXHALE_SMOKE=1 to run.
 */
class AndroidPickProbe {
    private data class Answer(val source: String, val raw: String) {
        val lines by lazy { LyricsParser.parse(raw).filter { it.text.isNotBlank() } }
        val level: Int get() = when {
            lines.size < 2 -> 2
            lines.any { it.wordSynced } -> 0
            else -> 1
        }
    }

    @Test
    fun whatAndroidPicks() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val queries = System.getenv("EXHALE_LYRICS_QUERIES")?.split('|') ?: listOf(
            "Eminem Lose Yourself", "Eminem Houdini", "Eminem Mockingbird", "Eminem Without Me", "Eminem Rap God",
            "Arijit Singh Kesariya", "Arijit Singh Tum Hi Ho", "Pritam Chaleya", "Diljit Dosanjh Lover",
            "BLACKPINK Pink Venom", "NewJeans Super Shy", "BTS Dynamite",
            "Taylor Swift Cruel Summer", "The Weeknd Blinding Lights", "Billie Eilish Birds of a Feather",
            "Kendrick Lamar Not Like Us", "Travis Scott FE!N", "Drake God's Plan", "Coldplay Viva La Vida",
            "Bad Bunny Titi Me Pregunto", "Imagine Dragons Believer", "Linkin Park Numb",
        )
        val order = listOf("lrc.red", "Binimum", "BetterLyrics", "PaxSenix", "LyricsPlus", "SimpMusic", "Unison", "Musixmatch", "Megalobiz", "KuGou", "LRCLIB", "Genius")
        var misfits = 0
        var none = 0
        for (query in queries) {
            val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items
                ?.filterIsInstance<SongItem>()?.firstOrNull() ?: continue
            val duration = song.duration ?: 0
            val title = SongQuery.cleanTitle(song.title)
            val artist = SongQuery.creditedArtist(song.title, song.artists.map { it.name }) ?: song.artists.firstOrNull()?.name.orEmpty()
            val album = song.album?.name
            val recording = withTimeoutOrNull(5_000) { Recordings.identify(song.id, title, artist, album, duration) }
            val answers = coroutineScope {
                order.map { name ->
                    async {
                        val raw = withTimeoutOrNull(12_000) {
                            runCatching {
                                when (name) {
                                    "lrc.red" -> LrcRed.lyrics(title, artist, album, duration, recording)
                                    "Binimum" -> Binimum.lyrics(title, artist, album, duration, recording)
                                    "PaxSenix" -> PaxSenix.lyrics(title, artist, album, duration, null)
                                    "LyricsPlus" -> LyricsPlus.lyrics(title, artist, album, duration, recording)
                                    "Unison" -> Unison.lyrics(title, artist, album, duration, null)
                                    "Musixmatch" -> Musixmatch.lyrics(title, artist, album, duration, null)
                                    "Megalobiz" -> Megalobiz.lyrics(title, artist, album, duration, null)
                                    "Genius" -> Genius.lyrics(title, artist, album, duration, null)
                                    "BetterLyrics" -> BetterLyrics.getLyrics(title = title, artist = artist, album = album, durationSeconds = duration).getOrNull()
                                    "SimpMusic" -> SimpMusicLyrics.getLyricsByVideoId(song.id).let { tracks ->
                                        val best = if (duration > 0 && tracks.size > 1) tracks.minByOrNull { abs((it.duration ?: 0) - duration) } else tracks.firstOrNull()
                                        best?.richSyncLyrics?.takeIf { it.isNotBlank() } ?: best?.syncedLyrics?.takeIf { it.isNotBlank() } ?: best?.plainLyrics
                                    }
                                    "KuGou" -> KuGou.getLyrics(title, artist, duration).getOrNull()
                                    "LRCLIB" -> LrcLib.getLyrics(title, artist, duration, album).getOrNull()
                                    else -> null
                                }
                            }.getOrNull()
                        }
                        raw?.takeIf { it.isNotBlank() }?.let { Answer(name, it) }
                    }
                }.map { it.await() }
            }
            // Android's rule: word-timed first, then line, then plain; the source order breaks ties.
            val pick = answers.withIndex().filter { it.value != null }.minByOrNull { (i, a) -> a!!.level * order.size + i }?.value
            println("== ${song.title} — ${song.artists.joinToString { it.name }} (${duration}s, ISRC ${recording?.isrc ?: "—"})")
            answers.forEachIndexed { i, a ->
                if (a == null) return@forEachIndexed
                println("   ${order[i].padEnd(12)} ${verdict(a, duration, title)}")
            }
            if (pick == null) {
                none++
                println("   >> ANDROID SHOWS: nothing")
            } else {
                val v = verdict(pick, duration, title)
                if (v.contains("MISFIT") || v.contains("NOT THIS SONG")) misfits++
                println("   >> ANDROID SHOWS: ${pick.source}  $v")
            }
        }
        println("SUMMARY: ${queries.size} songs, $misfits wrong picks, $none with nothing")
    }

    private fun verdict(a: Answer, duration: Int, title: String): String {
        val kind = listOf("word", "line", "plain")[a.level]
        val words = title.lowercase().split(Regex("""[^\p{L}\p{N}']+""")).filter { it.length >= 4 }
        val body = a.lines.joinToString(" ") { it.text }.lowercase().ifEmpty { a.raw.lowercase() }
        val echo = words.isEmpty() || words.any { it in body }
        if (a.level == 2) return "$kind ${a.raw.lines().count { it.isNotBlank() }} lines" + if (!echo) "  NOT THIS SONG?" else ""
        val first = a.lines.first().timeMs / 1000
        val last = a.lines.last().timeMs / 1000
        val misfit = duration > 30 && (last > duration + 10 || first > duration * 0.6 || (duration > 90 && last < duration * 0.4))
        return "$kind ${a.lines.size} lines ${first}s..${last}s" + (if (misfit) "  MISFIT" else "") + (if (!echo) "  NOT THIS SONG?" else "")
    }
}
