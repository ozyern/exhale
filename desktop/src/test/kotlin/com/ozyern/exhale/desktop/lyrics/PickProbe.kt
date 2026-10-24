/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * What the lyrics panel would show for the songs played here: the source that won, how its timings
 * sit against the song's length, and its opening lines, to be read against the song. EXHALE_SMOKE=1.
 */
class PickProbe {
    @Test
    fun whatIsShown() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        YouTube.visitorData = YouTube.visitorData().getOrThrow()
        val played = com.ozyern.exhale.desktop.data.DesktopPrefs.history.value
            .distinctBy { it.id }
            .take(System.getenv("EXHALE_LYRICS_COUNT")?.toIntOrNull() ?: 15)
        for (song in played) {
            val lyrics = LyricsRepository.lyricsFor(song, 0)
            println("== ${song.title} — ${song.artists.joinToString { it.name }} (${song.id}, ${song.duration}s, album='${song.album?.name}')")
            when (lyrics) {
                is Lyrics.Synced -> {
                    val sung = lyrics.lines.filter { it.text.isNotBlank() }
                    println("   ${lyrics.source} synced${if (lyrics.wordLevel) " word" else ""} ${sung.size} lines ${sung.first().timeMs / 1000}s..${sung.last().timeMs / 1000}s")
                    sung.take(3).forEach { println("     ${it.timeMs / 1000}s ${it.text.take(70)}") }
                }
                is Lyrics.Plain -> {
                    println("   ${lyrics.source} plain ${lyrics.lines.size} lines")
                    lyrics.lines.take(3).forEach { println("     ${it.take(70)}") }
                }
                else -> println("   $lyrics")
            }
        }
    }
}
