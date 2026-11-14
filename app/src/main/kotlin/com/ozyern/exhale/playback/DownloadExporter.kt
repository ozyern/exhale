/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.PlaceholderDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.ozyern.exhale.utils.YTPlayerUtils
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/** Exports a copy of downloaded audio and optional sidecars to shared storage. */
object DownloadExporter {
    const val FOLDER = "Download/Exhale"

    /** Cover and canvas files live in a hidden folder so Gallery never lists them next to the audio. */
    private const val ART_FOLDER = "Download/Exhale/.artwork"

    class Package(
        val songId: String,
        val title: String,
        val artist: String,
        val album: String?,
        val audioMimeType: String?,
        val lyrics: String?,
        val coverUrl: String?,
        val video: YTPlayerUtils.VideoStream?,
    )

    fun export(context: Context, cache: Cache, pkg: Package): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val mime = pkg.audioMimeType?.substringBefore(';')?.trim().takeUnless { it.isNullOrEmpty() } ?: "audio/mp4"
        val base = exportBaseName(pkg.artist, pkg.title)
        val audio = write(context, "$base.${extensionFor(mime)}", mime) {
            val source = CacheDataSource(cache, PlaceholderDataSource.INSTANCE)
            try {
                source.open(DataSpec.Builder().setUri(pkg.songId.toUri()).setKey(pkg.songId).build())
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = source.read(buffer, 0, buffer.size)
                    if (n == androidx.media3.common.C.RESULT_END_OF_INPUT) break
                    if (n == 0) throw IOException("Download cache read made no progress")
                    it.write(buffer, 0, n)
                }
            } finally {
                source.close()
            }
        } ?: return null
        pkg.lyrics?.takeIf { it.isNotBlank() }?.let { text ->
            write(context, "$base.lrc", "application/octet-stream") { it.write(text.toByteArray()) }
        }
        pkg.coverUrl?.let { url ->
            ensureNoMedia(context)
            write(context, "$base.jpg", "application/octet-stream", ART_FOLDER) { out -> fetch(largeCover(url)) { it.copyTo(out) } }
        }
        pkg.video?.let { v ->
            ensureNoMedia(context)
            write(context, "$base.${extensionFor(v.mimeType)}", "application/octet-stream", ART_FOLDER) { out -> fetch(v.url) { it.copyTo(out) } }
        }
        return audio
    }

    private fun fetch(url: String, consume: (InputStream) -> Unit) {
        val request = okhttp3.Request.Builder().url(url).build()
        YTPlayerUtils.httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            consume(response.body.byteStream())
        }
    }

    /** A `.nomedia` marker keeps the scanner, and so Gallery, out of the artwork folder. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ensureNoMedia(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val exists = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("$ART_FOLDER/", ".nomedia"),
            null,
        )?.use { it.moveToFirst() } == true
        if (!exists) write(context, ".nomedia", "application/octet-stream", ART_FOLDER) { }
    }

    /** Keep the MediaStore row hidden until writing succeeds. */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun write(
        context: Context,
        name: String,
        mime: String,
        folder: String = FOLDER,
        body: (java.io.OutputStream) -> Unit,
    ): Uri? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf("$folder/", name),
            null,
        )?.use { c -> if (c.moveToFirst()) resolver.delete(android.content.ContentUris.withAppendedId(collection, c.getLong(0)), null, null) }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use(body) ?: error("No output stream")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            Timber.e(e, "Export of %s failed", name)
            resolver.delete(uri, null, null)
            null
        }
    }
}

private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private val coverSizeSuffix = Regex("=w\\d+-h\\d+[^/]*$")

internal fun exportBaseName(artist: String, title: String): String =
    listOf(artist, title).filter { it.isNotBlank() }.joinToString(" - ").ifBlank { "Untitled" }
        .replace(invalidFileNameCharacters, "_").trim().take(120)

internal fun extensionFor(mimeType: String): String = when {
    "flac" in mimeType -> "flac"
    "video/webm" in mimeType -> "webm"
    "video/" in mimeType -> "mp4"
    "webm" in mimeType -> "webm"
    "mpeg" in mimeType || "mp3" in mimeType -> "mp3"
    "ogg" in mimeType -> "ogg"
    else -> "m4a"
}

internal fun largeCover(url: String): String =
    url.replace(coverSizeSuffix, "=w1200-h1200-l90-rj")
