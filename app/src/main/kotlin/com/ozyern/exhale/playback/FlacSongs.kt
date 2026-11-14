/**
 * Exhale Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import android.net.Uri
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.openflac.OpenFlac

/**
 * Songs from the FLAC page as the player knows them.
 *
 * A FLAC song's id is `flac:<file address>`: the id is the whole story, so a queue restored after a
 * restart still knows what to play, and none of it is ever looked up on YouTube.
 */
object FlacSongs {
    private const val PREFIX = "flac:"

    fun isFlacId(id: String): Boolean = id.startsWith(PREFIX)

    /** The FLAC file [id] stands for, through the fastest server known for it, or null. */
    fun uriFor(id: String): Uri? =
        if (isFlacId(id)) Uri.parse(OpenFlac.fastUrl(id.removePrefix(PREFIX))) else null

    /** The fast server for this song failed; the next try goes through the ordinary address. */
    fun forgetServer(id: String) {
        if (isFlacId(id)) OpenFlac.forgetServerFor(id.removePrefix(PREFIX))
    }

    fun metadataFor(track: OpenFlac.Track) = MediaMetadata(
        id = PREFIX + track.url,
        title = track.title,
        artists = listOf(MediaMetadata.Artist(id = null, name = track.artist)),
        duration = track.durationSeconds ?: -1,
        thumbnailUrl = track.artworkUrl,
        album = null,
    )
}
