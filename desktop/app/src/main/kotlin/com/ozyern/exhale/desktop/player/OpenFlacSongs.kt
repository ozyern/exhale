/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.player

import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.openflac.OpenFlac

/**
 * Songs from the FLAC page, as queue entries. A FLAC song's id is `flac:<file address>`, the same
 * shape as the Android app's, so a queue restored after a restart still knows what to play and none
 * of it is ever asked of YouTube.
 */
object OpenFlacSongs {
    private const val Prefix = "flac:"

    fun isFlacId(id: String): Boolean = id.startsWith(Prefix)

    fun songFor(track: OpenFlac.Track) = SongItem(
        id = Prefix + track.url,
        title = track.title,
        artists = listOf(Artist(name = track.artist, id = null)),
        thumbnail = track.artworkUrl.orEmpty(),
        duration = track.durationSeconds,
    )

    /** The file itself, for mpv to open — or null when [id] isn't a FLAC song. */
    fun streamFor(id: String): ResolvedStream? {
        if (!isFlacId(id)) return null
        return ResolvedStream(
            url = id.removePrefix(Prefix),
            headers = StreamHeaders(userAgent = "Exhale", origin = null, referer = null),
            mimeType = "audio/flac",
            bitrate = 0,
            clientName = "FLAC",
            loudnessDb = null,
            lossless = true,
        )
    }
}
