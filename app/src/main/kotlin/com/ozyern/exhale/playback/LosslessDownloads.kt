/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.Context
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.openflac.OpenFlac
import com.ozyern.exhale.playback.lossless.sources.LosslessStreamResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Which downloads hold a lossless file, and where a download finds one.
 *
 * Downloads used to always save YouTube's lossy stream, and a downloaded song always played that
 * file. So a song with a FLAC copy played lossless from an album you hadn't downloaded and lossy
 * from one you had (the same song on Short n' Sweet and on its deluxe edition, say). The song's
 * format row can't answer which kind the download is: playback rewrites it whenever the song
 * streams. This keeps the answer on its own.
 */
object LosslessDownloads {
    private const val PREFS = "lossless_downloads"
    private const val KEY = "ids"
    private const val LOOKUP_TIMEOUT_MS = 12_000L

    data class Copy(val url: String, val sampleRate: Int?, val bitDepth: Int?, val bitrate: Int?, val size: Long? = null)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isLossless(context: Context, mediaId: String): Boolean =
        runCatching { prefs(context).getStringSet(KEY, emptySet())!!.contains(mediaId) }.getOrDefault(false)

    @Synchronized
    fun mark(context: Context, mediaId: String, lossless: Boolean) {
        val current = prefs(context).getStringSet(KEY, emptySet()).orEmpty()
        if ((mediaId in current) == lossless) return
        val updated = if (lossless) current + mediaId else current - mediaId
        prefs(context).edit().putStringSet(KEY, updated).apply()
    }

    /**
     * A remote file's length from a one-byte range request's Content-Range (a HEAD isn't answered by
     * every catalogue server), or its Content-Length. Null for a non-HTTP address or no answer.
     * Blocking: call off the main thread.
     */
    fun remoteFileSize(client: okhttp3.OkHttpClient, url: String): Long? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return runCatching {
            val quick = client.newBuilder().callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
            val request = okhttp3.Request.Builder().url(url).header("Range", "bytes=0-0").build()
            quick.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull()
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { response.code == 200 }
            }
        }.getOrNull()?.takeIf { it > 0L }
    }

    /**
     * A lossless file of [song] a plain HTTP download can fetch: the public catalogues (OpenFlac) and
     * any configured source that answers with an ordinary URL. Sources that stream through their own
     * scheme (DASH segments, decrypting readers) are left to playback.
     */
    suspend fun find(context: Context, mediaId: String, song: MediaMetadata): Copy? {
        val (title, artist) = com.ozyern.exhale.lyrics.SongQuery.songAndArtist(song.title, song.artists.map { it.name })
        if (title.isBlank() || artist.isBlank()) return null
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
            coroutineScope {
                val open = async {
                    runCatching { OpenFlac.findCopy(title, artist, song.duration, song.album?.title) }.getOrNull()
                        ?.let { Copy(OpenFlac.fastUrl(it.url), it.sampleRate, it.bitDepth, null) }
                }
                val cloud = async {
                    runCatching {
                        LosslessStreamResolver.resolve(
                            context,
                            LosslessStreamResolver.Request(
                                mediaId = mediaId,
                                title = title,
                                artists = listOf(artist) + song.artists.map { it.name }.filter { it != artist },
                                album = song.album?.title,
                                durationMs = song.duration.takeIf { it > 0 }?.let { it * 1000L },
                            ),
                        )
                    }.getOrNull()
                        ?.takeIf { it.isLossless && (it.uri.startsWith("https://") || it.uri.startsWith("http://")) }
                        ?.let { Copy(it.uri, it.sampleRate, it.bitDepth, it.bitrate) }
                }
                val pending = mutableListOf(open, cloud)
                var found: Copy? = null
                while (found == null && pending.isNotEmpty()) {
                    val (done, copy) = select { pending.forEach { d -> d.onAwait { d to it } } }
                    pending.remove(done)
                    found = copy
                }
                pending.forEach { it.cancel() }
                found
            }
        }
    }
}
