/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback.queues

import android.content.Context
import android.content.SharedPreferences
import com.ozyern.exhale.lyrics.SongQuery
import java.util.concurrent.ConcurrentHashMap

/**
 * What the listener's own thumb says about songs, for the radio to act on.
 *
 * Plays under the history threshold were never written anywhere, so a song skipped after five
 * seconds was invisible: the next radio queued it again, and an artist skipped three times in a row
 * kept coming. This keeps two short memories, on the phone only:
 *
 *  - **Skips**: a song left early, kept out of radio for [SKIP_MEMORY_MS], and its artist counted;
 *    an artist skipped [ARTIST_SKIPS_TO_DEMOTE] times recently goes to the back of each page.
 *  - **Heard**: a song played through, kept out of radio for [HEARD_MEMORY_MS], so a radio started
 *    an hour later doesn't open with what was just on.
 *
 * Nothing here removes anything from a queue the listener built: only what the radio adds.
 */
object ListeningSignals {
    private const val PREFS = "listening_signals"
    private const val KEY_SKIPS = "skips"
    private const val KEY_HEARD = "heard"

    private const val SKIP_MEMORY_MS = 7L * 24 * 60 * 60 * 1000
    private const val HEARD_MEMORY_MS = 3L * 60 * 60 * 1000
    private const val ARTIST_SKIPS_TO_DEMOTE = 2
    private const val MAX_ENTRIES = 400

    /** A play shorter than this, and short of half the song, is a skip. */
    private const val SKIP_PLAY_MS = 30_000L

    private val skips = ConcurrentHashMap<String, Entry>()
    private val heard = ConcurrentHashMap<String, Long>()
    private var prefs: SharedPreferences? = null

    private data class Entry(val at: Long, val artist: String)

    /** Loads what was remembered. Cheap, and safe to call more than once. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val now = System.currentTimeMillis()
        p.getStringSet(KEY_SKIPS, emptySet())?.forEach { line ->
            val parts = line.split('|', limit = 3)
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (now - at < SKIP_MEMORY_MS) skips[parts[0]] = Entry(at, parts.getOrNull(2).orEmpty())
        }
        p.getStringSet(KEY_HEARD, emptySet())?.forEach { line ->
            val parts = line.split('|', limit = 2)
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (now - at < HEARD_MEMORY_MS) heard[parts[0]] = at
        }
    }

    /**
     * One song's play has ended. [playedMs] against [durationMs] decides whether it was skipped or
     * heard; a play of unknown length is only ever counted as heard.
     */
    fun onPlayEnded(mediaId: String, artist: String?, playedMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        val skipped = durationMs > 0 && playedMs < SKIP_PLAY_MS && playedMs < durationMs / 2
        if (skipped) {
            // A second or two is a tap through a list, not a judgement on the song.
            if (playedMs < 2_000) return
            skips[mediaId] = Entry(now, normalizedArtist(artist))
        } else if (playedMs >= SKIP_PLAY_MS) {
            heard[mediaId] = now
            // Played through after all: whatever the thumb said before, it isn't saying it now.
            skips.remove(mediaId)
        }
        save()
    }

    /** Whether the radio should leave [mediaId] out. */
    fun avoid(mediaId: String): Boolean {
        val now = System.currentTimeMillis()
        skips[mediaId]?.let { if (now - it.at < SKIP_MEMORY_MS) return true }
        heard[mediaId]?.let { if (now - it < HEARD_MEMORY_MS) return true }
        return false
    }

    /** Whether [artist] (as [normalizedArtist] writes it) has been skipped enough lately to wait. */
    fun demoted(artist: String): Boolean {
        if (artist.isBlank()) return false
        val now = System.currentTimeMillis()
        return skips.values.count { it.artist == artist && now - it.at < SKIP_MEMORY_MS } >= ARTIST_SKIPS_TO_DEMOTE
    }

    fun normalizedArtist(name: String?): String = SongQuery.normalized(name.orEmpty())

    private fun save() {
        val p = prefs ?: return
        val now = System.currentTimeMillis()
        val keptSkips = skips.entries
            .filter { now - it.value.at < SKIP_MEMORY_MS }
            .sortedByDescending { it.value.at }
            .take(MAX_ENTRIES)
            .mapTo(HashSet()) { "${it.key}|${it.value.at}|${it.value.artist}" }
        val keptHeard = heard.entries
            .filter { now - it.value < HEARD_MEMORY_MS }
            .sortedByDescending { it.value }
            .take(MAX_ENTRIES)
            .mapTo(HashSet()) { "${it.key}|${it.value}" }
        p.edit().putStringSet(KEY_SKIPS, keptSkips).putStringSet(KEY_HEARD, keptHeard).apply()
    }
}
