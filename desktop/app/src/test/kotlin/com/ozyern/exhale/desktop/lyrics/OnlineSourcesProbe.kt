/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.lyrics

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** What each online source hands back for a few real songs, parsed as the player would. EXHALE_SMOKE=1 to run. */
class OnlineSourcesProbe {
    private data class Song(val title: String, val artist: String, val album: String?, val seconds: Int)

    @Test
    fun whoAnswers() = runBlocking {
        assumeTrue("set EXHALE_SMOKE=1 to run", System.getenv("EXHALE_SMOKE") == "1")
        val songs = listOf(
            Song("Espresso", "Sabrina Carpenter", "Espresso", 175),
            Song("Lose Yourself", "Eminem", null, 326),
            Song("Houdini", "Eminem", null, 227),
        )
        val sources = listOf(LrcRed, Binimum, PaxSenix, LyricsPlus, Unison, Musixmatch, Megalobiz, Genius)
        for (song in songs) {
            val recording = withTimeoutOrNull(8_000) { Recordings.identify(song.title, song.title, song.artist, song.album, song.seconds) }
            println("== ${song.artist} — ${song.title}: ISRC ${recording?.isrc ?: "—"}")
            for (source in sources) {
                val started = System.currentTimeMillis()
                val raw = runCatching {
                    withTimeoutOrNull(12_000) {
                        source.lyrics(song.title, song.artist, song.album, song.seconds, recording.takeIf { source.usesRecording })
                    }
                }.getOrNull()
                val took = System.currentTimeMillis() - started
                val verdict = if (raw.isNullOrBlank()) {
                    "—"
                } else {
                    val sung = LyricsParser.parse(raw).filter { it.text.isNotBlank() }
                    when {
                        sung.size >= 2 -> "synced ${sung.size} lines ${sung.first().timeMs / 1000}s..${sung.last().timeMs / 1000}s" +
                            (if (sung.any { it.wordSynced }) ", word-timed" else "") +
                            " | \"${sung[minOf(2, sung.lastIndex)].text.take(40)}\""
                        else -> "plain ${raw.lines().count { it.isNotBlank() }} lines"
                    }
                }
                println("   ${source.name.padEnd(11)} ${took.toString().padStart(5)} ms  $verdict")
            }
        }
    }
}
