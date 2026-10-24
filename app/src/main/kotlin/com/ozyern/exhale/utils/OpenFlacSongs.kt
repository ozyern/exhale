/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.net.Uri
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.openflac.OpenFlac

/**
 * Songs from the FLAC page, made into songs the player can queue.
 *
 * A FLAC song has the id `flac:<file address>`, the way a file opened from outside is
 * `local:uri:<address>`: the id is the whole story, so a queue restored after a restart still knows
 * what to play, and none of it is ever sent to YouTube.
 */
object OpenFlacSongs {
    private const val Prefix = "flac:"

    fun isFlacId(id: String): Boolean = id.startsWith(Prefix)

    /** The FLAC file a song id stands for, or null if [id] isn't one. */
    fun uriFor(id: String): Uri? = if (isFlacId(id)) Uri.parse(id.removePrefix(Prefix)) else null

    fun metadataFor(track: OpenFlac.Track) = MediaMetadata(
        id = Prefix + track.url,
        title = track.title,
        artists = listOf(MediaMetadata.Artist(id = null, name = track.artist)),
        duration = track.durationSeconds ?: MediaMetadata.UNKNOWN_DURATION,
        thumbnailUrl = track.artworkUrl,
        // No album link: these releases have no album page outside the FLAC page.
        album = null,
    )
}
