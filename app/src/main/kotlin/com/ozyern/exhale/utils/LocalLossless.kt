/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Lossless copies of songs that are already on this phone, played in place of YouTube's stream.
 *
 * The Windows app does the same with folders, the Internet Archive and DLNA; on a phone the library
 * that matters is the one in MediaStore. When a song starts, its title, artists and runtime are
 * matched against the FLAC, WAV and AIFF files here — the same rules the Windows app uses — and a
 * match is what gets played. YouTube is only asked when there is nothing better on the device.
 */
object LocalLossless {
    data class Track(
        val uri: Uri,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationSeconds: Double?,
        val mimeType: String,
        val sampleRate: Int?,
        val bitDepth: Int?,
        val size: Long,
    ) {
        /** What the format record says about this file, for the badge and the player's quality line. */
        val codec: String
            get() = when {
                "flac" in mimeType -> "flac"
                "aiff" in mimeType -> "aiff"
                else -> "pcm"
            }
    }

    private val LosslessMimeTypes = listOf(
        "audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "audio/aiff", "audio/x-aiff",
    )

    /** How long a read of the device's library is trusted before it is read again. */
    private const val IndexTtlMs = 5 * 60_000L

    @Volatile private var index: List<Track>? = null
    @Volatile private var indexedAt = 0L

    /** Per song, what was decided — including "nothing here" — so a stream's every chunk doesn't ask again. */
    private val decisions = ConcurrentHashMap<String, Any>()
    private object None

    /** Whether this song has been decided already — the loader asks this before anything costlier. */
    fun known(songId: String): Boolean = decisions.containsKey(songId)

    /** The decision already made for this song: its lossless copy, or null for none. */
    fun cached(songId: String): Track? = decisions[songId] as? Track

    fun invalidate() {
        index = null
        decisions.clear()
    }

    /** Builds the library index now, off the loader thread, so the first song does not wait for it. */
    fun warm(context: Context) {
        runCatching { index(context) }
    }

    private fun index(context: Context): List<Track> {
        val now = System.currentTimeMillis()
        index?.takeIf { now - indexedAt < IndexTtlMs }?.let { return it }
        if (!LocalMediaScanner.hasPermission(context)) return emptyList()

        val columns = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            if (Build.VERSION.SDK_INT >= 34) {
                add(MediaStore.Audio.AudioColumns.SAMPLERATE)
                add(MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE)
            }
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} IN (${LosslessMimeTypes.joinToString { "?" }})"

        val found = ArrayList<Track>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                columns,
                selection,
                LosslessMimeTypes.toTypedArray(),
                null,
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mime = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val rate = if (Build.VERSION.SDK_INT >= 34) cursor.getColumnIndex(MediaStore.Audio.AudioColumns.SAMPLERATE) else -1
                val bits = if (Build.VERSION.SDK_INT >= 34) cursor.getColumnIndex(MediaStore.Audio.AudioColumns.BITS_PER_SAMPLE) else -1
                fun tag(i: Int) = cursor.getString(i)?.trim()?.takeUnless { it.isBlank() || it == "<unknown>" }
                while (cursor.moveToNext()) {
                    val ms = cursor.getLong(duration)
                    found += Track(
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(id)),
                        title = tag(title) ?: continue,
                        artist = tag(artist),
                        album = tag(album),
                        durationSeconds = if (ms > 0) ms / 1000.0 else null,
                        mimeType = cursor.getString(mime).orEmpty().lowercase(Locale.US),
                        sampleRate = rate.takeIf { it >= 0 }?.let { cursor.getInt(it) }?.takeIf { it > 0 },
                        bitDepth = bits.takeIf { it >= 0 }?.let { cursor.getInt(it) }?.takeIf { it > 0 },
                        size = cursor.getLong(size),
                    )
                }
            }
        }
        index = found
        indexedAt = now
        return found
    }

    /** The lossless copy of this song on the device, or null. Decided once per song id. */
    fun find(context: Context, songId: String, title: String, artists: List<String>, durationSeconds: Int?): Track? {
        decisions[songId]?.let { return it as? Track }
        val library = index(context)
        val match = library.firstOrNull { matches(title, artists, durationSeconds, it) }
        decisions[songId] = match ?: None
        if (decisions.size > 500) decisions.clear()
        return match
    }

    // ── The Windows app's TrackMatch, unchanged in its rules ──

    private val Noise = Regex(
        "\\((?:[^)]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv|version|edit)\\b[^)]*)\\)" +
            "|\\[(?:[^\\]]*\\b(?:official|video|audio|lyric|lyrics|visualizer|remaster(?:ed)?|explicit|clean|hd|hq|mv)\\b[^\\]]*)\\]" +
            "|\\b(?:feat|ft|featuring|with)\\b[^-]*",
        RegexOption.IGNORE_CASE,
    )

    fun normalize(value: String): String {
        val stripped = Normalizer.normalize(value.lowercase(Locale.US), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Noise, " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun matches(title: String, artists: List<String>, durationSeconds: Int?, candidate: Track): Boolean {
        val wanted = normalize(title)
        val found = normalize(candidate.title)
        if (wanted.isEmpty() || found.isEmpty()) return false
        val sameTitle = wanted == found || (wanted.length > 6 && found.length > 6 && (found.startsWith(wanted) || wanted.startsWith(found)))
        if (!sameTitle) return false

        val wantedDuration = durationSeconds?.takeIf { it > 0 }?.toDouble()
        val foundDuration = candidate.durationSeconds
        // Both runtimes known and more than four seconds apart: a different recording, live or extended.
        if (wantedDuration != null && foundDuration != null && abs(wantedDuration - foundDuration) > 4.0) return false

        val names = artists.map { normalize(it) }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return true
        val theirs = listOfNotNull(candidate.artist, candidate.album).joinToString(" ") { normalize(it) }
        // A file with no artist anywhere still counts when the title and runtime already agree.
        if (theirs.isBlank()) return foundDuration != null && wantedDuration != null
        return names.any { artist -> theirs.contains(artist) || artist.split(' ').any { it.length > 3 && theirs.contains(it) } }
    }
}
