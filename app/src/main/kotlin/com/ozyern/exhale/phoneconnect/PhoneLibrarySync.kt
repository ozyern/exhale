/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.phoneconnect

import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem
import com.ozyern.exhale.connect.ConnectMessage
import com.ozyern.exhale.connect.ConnectTrack
import com.ozyern.exhale.connect.LibrarySync
import com.ozyern.exhale.connect.PlayEntry
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Event
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The phone's half of library sync: its liked songs and plays to the PC, and the PC's into its own
 * database. The shared [LibrarySync] decides what changed and which change wins; this only reads
 * and writes the phone's library. Likes made here from the PC's side stay local: they are not
 * pushed to YouTube Music a second time, since the side that made them already did.
 */
class PhoneLibrarySync(
    directory: File,
    private val database: MusicDatabase,
    private val scope: CoroutineScope,
    private val send: (ConnectMessage) -> Unit,
) {
    private val sync = LibrarySync(directory)

    fun start() {
        scope.launch(Dispatchers.IO) {
            database.likedSongsByRowIdAsc().collect { songs ->
                val changes = sync.localLikes(songs.map { it.toMediaMetadata().toTrack() })
                if (changes.isNotEmpty()) send(ConnectMessage.Library(likes = changes))
            }
        }
        scope.launch(Dispatchers.IO) {
            database.latestEvent().filterNotNull().collect { latest ->
                val at = latest.event.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val plays = sync.localPlays(listOf(PlayEntry(latest.song.toMediaMetadata().toTrack(), at)))
                if (plays.isNotEmpty()) send(ConnectMessage.Library(plays = plays))
            }
        }
    }

    fun snapshot(): ConnectMessage.Library = sync.snapshot()

    /** Brings the phone's likes and history in line with what the PC sent. */
    fun apply(message: ConnectMessage.Library) {
        scope.launch(Dispatchers.IO) {
            val merge = sync.merge(message)
            if (merge.isEmpty) return@launch
            database.transaction {
                merge.like.forEach { track ->
                    insert(track.toMediaMetadata())
                    getSongByIdBlocking(track.id)?.song?.let { song ->
                        if (!song.liked) update(song.copy(liked = true, likedDate = LocalDateTime.now(), inLibrary = song.inLibrary ?: LocalDateTime.now()))
                    }
                }
                merge.unlike.forEach { id ->
                    getSongByIdBlocking(id)?.song?.let { song ->
                        if (song.liked) update(song.copy(liked = false, likedDate = null))
                    }
                }
                merge.plays.forEach { play ->
                    insert(play.track.toMediaMetadata())
                    insert(
                        Event(
                            songId = play.track.id,
                            // The PC's own time of the play, so it lands in its place in history
                            // and the sync recognises it if it comes back round.
                            timestamp = LocalDateTime.ofInstant(Instant.ofEpochMilli(play.atMs), ZoneId.systemDefault()),
                            playTime = play.track.durationMs,
                        ),
                    )
                }
            }
        }
    }
}

internal fun MediaMetadata.toTrack() = ConnectTrack(
    id = id,
    title = title,
    artists = artists.joinToString { it.name },
    album = album?.title,
    artworkUrl = thumbnailUrl,
    durationMs = duration.coerceAtLeast(0) * 1000L,
)

internal fun ConnectTrack.toMediaMetadata() = SongItem(
    id = id,
    title = title,
    artists = artists.split(", ").filter { it.isNotBlank() }.map { Artist(it, null) },
    // No album: the PC sends its name but not its id, and an album without an id would land in
    // the library as an empty one.
    duration = (durationMs / 1000).toInt().takeIf { it > 0 },
    thumbnail = artworkUrl.orEmpty(),
).toMediaMetadata()
