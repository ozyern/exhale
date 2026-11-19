/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import com.ozyern.exhale.lyrics.bitchord.BiniLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Which recording a song is, by ISRC, as Exhale for Windows works it out: asked of Binimum once per
 * song, before anyone is asked for lyrics, and handed to the sources that can look a recording up
 * by its code (lrc.red files Apple's syllable-timed lyrics by ISRC, and LyricsPlus takes one too).
 * Named rather than described, a single never gets its album cut's timing or the clean edit's words.
 *
 * Remembered for the session, misses included, so a song that Binimum does not know costs one
 * short wait, not one per source.
 */
internal object Recordings {
    /** How long the sources that use it wait for the recording to be named before asking without. */
    private const val IdentifyTimeoutMs = 2_500L

    private val lock = Mutex()
    private val known = LinkedHashMap<String, String?>()
    private const val Remembered = 64

    /** Held across the lookup, so the sources asking at once wait for one answer instead of each asking. */
    suspend fun isrc(title: String, artist: String, durationMs: Long, album: String?): String? = lock.withLock {
        val key = "$title|$artist|$durationMs"
        if (key in known) return@withLock known[key]
        val found = try {
            withTimeoutOrNull(IdentifyTimeoutMs) { BiniLyrics.identify(title, artist, durationMs, album)?.isrc }
                ?.takeIf { it.isNotBlank() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        known[key] = found
        if (known.size > Remembered) known.remove(known.keys.first())
        found
    }
}
