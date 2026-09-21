/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.models

import androidx.compose.runtime.Immutable
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.db.entities.SongEntity
import com.ozyern.exhale.ui.utils.resize
import java.io.Serializable
import java.time.LocalDateTime

@Immutable
data class MediaMetadata(
    val id: String,
    val title: String,
    val artists: List<Artist>,
    val duration: Int,
    val thumbnailUrl: String? = null,
    val album: Album? = null,
    val setVideoId: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val inLibrary: LocalDateTime? = null,
    val spotifyTrackId: String? = null,
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L

        const val UNKNOWN_DURATION = -1
    }

    data class Artist(
        val id: String?,
        val name: String,
        val thumbnailUrl: String? = null,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    data class Album(
        val id: String,
        val title: String,
    ) : Serializable {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    fun toSongEntity() =
        SongEntity(
            id = id,
            title = title,
            duration = duration,
            thumbnailUrl = thumbnailUrl,
            albumId = album?.id,
            albumName = album?.title,
            explicit = explicit,
            liked = liked,
            likedDate = likedDate,
            inLibrary = inLibrary,
        )
}

private fun resolveAlbum(
    album: com.ozyern.exhale.db.entities.Album?,
    albumId: String?,
    albumName: String?
): MediaMetadata.Album? =
    album?.let {
        MediaMetadata.Album(id = it.id, title = it.title)
    } ?: albumId?.let {
        MediaMetadata.Album(id = it, title = albumName.orEmpty())
    }


/**
 * "1.2M plays", "340K views", "3:45" — a subtitle's tail, not a name.
 *
 * Songs saved before the parser learned to drop these kept them as artists, and the mini player showed
 * a play count where the artist belonged. Filtering on the way out of the database mends those rows as
 * they are read, so nobody has to re-fetch a library to get their artist names back. A name that is
 * simply numeric ("21 Savage", "50 Cent") has neither a magnitude nor a colon and is left alone.
 */
private val CountOrRuntime = Regex("""^\s*\d[\d.,   ]*(?:[KMBkmb]|Mio\.?|Mrd\.?|Mil|lakh|crore|万|億|억|만|천)?\+?(?:\s+\S+)?\s*$""")

private fun looksLikeCountOrRuntime(name: String): Boolean {
    val text = name.trim()
    if (text.isEmpty()) return true
    // A runtime: digits and colons only.
    if (text.all { it.isDigit() || it == ':' } && ':' in text) return true
    if (!text.first().isDigit()) return false
    // A bare number with a magnitude, optionally followed by one word ("plays", "views", "Aufrufe").
    return CountOrRuntime.matches(text) && (text.any { it.isLetter() } || text.all { it.isDigit() || it in ".,   " })
}

/** Artists as they should be shown: whatever in the list is really a name. */
private fun List<MediaMetadata.Artist>.withoutCounts(): List<MediaMetadata.Artist> {
    val names = filterNot { it.id == null && looksLikeCountOrRuntime(it.name) }
    // Never leave a song with no artist at all when every entry looked like a count.
    return names.ifEmpty { this }
}

fun Song.toMediaMetadata() =
    MediaMetadata(
        id = song.id,
        title = song.title,
        artists = artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
                thumbnailUrl = it.thumbnailUrl,
            )
        }.withoutCounts(),
        duration = song.duration,
        thumbnailUrl = song.thumbnailUrl,
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.title,
            )
        } ?: song.albumId?.let { albumId ->
            MediaMetadata.Album(
                id = albumId,
                title = song.albumName.orEmpty(),
            )
        },
    )

fun SongItem.toMediaMetadata() =
    MediaMetadata(
        id = id,
        title = title,
        artists =
        artists.map {
            MediaMetadata.Artist(
                id = it.id,
                name = it.name,
                thumbnailUrl = null,
            )
        },
        duration = duration ?: MediaMetadata.UNKNOWN_DURATION,
        thumbnailUrl = thumbnail.resize(544, 544),
        album =
        album?.let {
            MediaMetadata.Album(
                id = it.id,
                title = it.name,
            )
        },
        setVideoId = setVideoId,
        explicit = explicit,
    )
