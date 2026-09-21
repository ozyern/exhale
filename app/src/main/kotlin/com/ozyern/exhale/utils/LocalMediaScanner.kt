/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime

/**
 * The music that is on this phone, made into songs the Library can list and the player can play.
 *
 * A local song has the id `local:<MediaStore id>`. That one string is everything: it is the key in the
 * database, and playback turns it back into the file's `content://` address (see [uriFor]), so nothing
 * about a song here depends on a path that could move.
 */
object LocalMediaScanner {
    private const val Prefix = "local:"
    private const val TAG = "LocalMediaScanner"

    /** Anything shorter than this is a ringtone, a voice note or a notification sound, not music. */
    private const val MinDurationMs = 30_000

    private val AlbumArt: Uri = Uri.parse("content://media/external/audio/albumart")

    fun isLocalId(id: String): Boolean = id.startsWith(Prefix)

    /** The file a local song id stands for, or null if [id] isn't one. */
    fun uriFor(id: String): Uri? {
        if (!isLocalId(id)) return null
        val mediaStoreId = id.removePrefix(Prefix).toLongOrNull() ?: return null
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    const val PermissionName = Manifest.permission.READ_MEDIA_AUDIO

    /**
     * Reads the device's music and adds whatever the database doesn't have yet. Songs already there are
     * left as they are (a rescan must not undo a like or a play count). Returns how many were added.
     */
    suspend fun scan(context: Context, database: MusicDatabase): Int = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) return@withContext 0

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ? AND " +
            "(${MediaStore.Audio.Media.RELATIVE_PATH} IS NULL OR ${MediaStore.Audio.Media.RELATIVE_PATH} NOT LIKE 'Music/Exhale/%')"
        // What is already here, so a rescan adds only what is new.
        val known = database.allSongs().first().filter { it.song.isLocal }.map { it.id }.toHashSet()
        val fresh = ArrayList<MediaMetadata>()
        val albumNames = HashMap<String, String?>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(MinDurationMs.toString()),
                "${MediaStore.Audio.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = "$Prefix${cursor.getLong(idColumn)}"
                    // MediaStore fills a missing tag with the literal text "<unknown>".
                    val artistName = cursor.getString(artistColumn)?.trim()?.takeUnless { it.isBlank() || it == "<unknown>" }
                    val albumName = cursor.getString(albumColumn)?.trim()?.takeUnless { it.isBlank() || it == "<unknown>" }
                    val albumId = cursor.getLong(albumIdColumn)
                    val metadata = MediaMetadata(
                        id = id,
                        title = cursor.getString(titleColumn)?.trim().orEmpty().ifBlank { "Unknown title" },
                        artists = listOf(MediaMetadata.Artist(id = null, name = artistName ?: "Unknown artist")),
                        duration = (cursor.getLong(durationColumn) / 1000L).toInt(),
                        thumbnailUrl = ContentUris.withAppendedId(AlbumArt, albumId).toString(),
                    )
                    if (id !in known) fresh += metadata.copy(album = null).also { albumNames[id] = albumName }
                }
            }
        }.onFailure { Timber.tag(TAG).w(it, "scan failed") }

        if (fresh.isNotEmpty()) {
            val now = LocalDateTime.now()
            // One transaction for the lot: a large library is thousands of rows.
            database.transaction {
                fresh.forEach { metadata ->
                    insert(metadata) { it.copy(isLocal = true, albumName = albumNames[metadata.id], inLibrary = now, dateDownload = now) }
                }
            }
        }
        fresh.size
    }
}
