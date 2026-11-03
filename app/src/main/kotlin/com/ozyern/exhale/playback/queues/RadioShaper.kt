/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback.queues

import com.ozyern.exhale.extensions.metadata
import kotlin.math.ceil

/**
 * Puts a radio's songs in an order worth listening to, page by page.
 *
 * YouTube's radio leans hard on a few artists: from one Sabrina Carpenter song it sent 26 of hers in
 * 100, its second page cycling through the same four artists, and the same song twice off two albums.
 * YouTube's order still decides who comes first; this only holds a song back while its artist was just
 * heard, drops one already queued, and drops an artist's songs past a share of the whole queue.
 */
internal class RadioShaper<T>(
    private val idOf: (T) -> String,
    private val songOf: (T) -> String,
    private val artistOf: (T) -> String,
) {
    private val ids = HashSet<String>()
    private val songs = HashSet<String>()
    private val perArtist = HashMap<String, Int>()
    private val recent = ArrayDeque<String>()
    private var kept = 0

    /** Songs already in the queue as they are, such as the one playing: counted, not moved. */
    fun keep(items: List<T>) {
        items.forEach { item ->
            ids += idOf(item)
            songs += songOf(item)
            place(artistOf(item))
        }
    }

    /** The next page's songs, in the order to play them. */
    fun shape(items: List<T>): List<T> {
        val pending = items.filterTo(ArrayList()) { ids.add(idOf(it)) && songs.add(songOf(it)) }
        val cap = maxOf(MIN_ARTIST_CAP, ceil((kept + pending.size) * MAX_ARTIST_SHARE).toInt())
        val shaped = ArrayList<T>(pending.size)
        while (pending.isNotEmpty()) {
            val allowed = pending.filter { (perArtist[artistOf(it)] ?: 0) < cap }
            if (allowed.isEmpty()) break
            // The first the listener hasn't just heard; failing that, simply the first.
            val next = allowed.firstOrNull { artistOf(it) !in recent } ?: allowed.first()
            pending.remove(next)
            shaped += next
            place(artistOf(next))
        }
        return shaped
    }

    private fun place(artist: String) {
        kept++
        perArtist[artist] = (perArtist[artist] ?: 0) + 1
        recent.addLast(artist)
        if (recent.size > ARTIST_SPACING) recent.removeFirst()
    }

    private companion object {
        /** An artist comes back only after this many songs by others, when there are others to play. */
        const val ARTIST_SPACING = 3

        /** The most of a radio any one artist may be, and how low that may round for a short one. */
        const val MAX_ARTIST_SHARE = 0.15
        const val MIN_ARTIST_CAP = 4
    }
}

/** A shaper over songs already in the player, for what is added to a queue on the fly. */
internal fun mediaItemRadioShaper(): RadioShaper<androidx.media3.common.MediaItem> {
    fun artist(item: androidx.media3.common.MediaItem) =
        com.ozyern.exhale.lyrics.SongQuery.normalized(item.metadata?.artists?.firstOrNull()?.name.orEmpty())
    return RadioShaper(
        idOf = { it.mediaId },
        songOf = { item ->
            val title = com.ozyern.exhale.lyrics.SongQuery.cleanTitle(item.metadata?.title ?: item.mediaId)
            "${com.ozyern.exhale.lyrics.SongQuery.normalized(title)}|${artist(item)}"
        },
        artistOf = ::artist,
    )
}
