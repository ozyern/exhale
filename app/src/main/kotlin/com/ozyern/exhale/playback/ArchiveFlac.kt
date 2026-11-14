/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

import android.content.Context
import android.content.SharedPreferences
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.openflac.OpenFlac
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * A song's FLAC copy on the Internet Archive (and the other free catalogues [OpenFlac] reads),
 * preferred over every other source.
 *
 * As Exhale has always done it: the search is made from the song's own details in the queue
 * (title, artist, length), before any cached stream is considered; a copy found is remembered on
 * the phone, so the next play needs no search; a search that runs out of time is left to finish in
 * the background, so the play after that has its answer; and a copy that will not play is dropped
 * for the session so the song falls back to the ordinary sources.
 */
object ArchiveFlac {
    private const val TAG = "ArchiveFlac"
    private const val LOOKUP_TIMEOUT_MS = 10_000L
    private const val MISS_RETRY_MS = 30 * 60 * 1000L

    /** The setting, mirrored here so the stream resolver never reads DataStore per chunk. */
    @Volatile var enabled: Boolean = true

    data class Copy(val url: String, val sampleRate: Int?, val bitDepth: Int?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val found = ConcurrentHashMap<String, Copy>()
    private val missAt = ConcurrentHashMap<String, Long>()
    private val rejected = ConcurrentHashMap.newKeySet<String>()
    private val lookups = ConcurrentHashMap<String, Deferred<Copy?>>()
    @Volatile private var store: SharedPreferences? = null

    fun init(context: Context) {
        store = context.applicationContext.getSharedPreferences("flac_copies", Context.MODE_PRIVATE)
    }

    /** The copy already known for [mediaId], without searching. */
    fun known(mediaId: String): Copy? {
        if (!enabled || mediaId in rejected) return null
        found[mediaId]?.let { return it }
        val stored = runCatching { store?.getString(mediaId, null) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val parts = stored.split('\t')
        return Copy(parts[0], parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2)?.toIntOrNull()).also { found[mediaId] = it }
    }

    /** The address to stream [mediaId]'s copy from right now, or null when it has none. */
    fun streamUrl(mediaId: String): String? = known(mediaId)?.let { OpenFlac.fastUrl(it.url) }

    /**
     * The copy for [song], searching for it if it has not been looked for lately. Waits up to
     * [LOOKUP_TIMEOUT_MS]; past that the song starts from its ordinary source and the search carries
     * on, so its answer is there the next time.
     */
    suspend fun find(song: MediaMetadata): Copy? {
        val id = song.id
        if (!enabled || id in rejected) return null
        known(id)?.let { return it }
        missAt[id]?.let { at -> if (System.currentTimeMillis() - at < MISS_RETRY_MS) return null }
        val artist = song.artists.firstOrNull { it.name.isNotBlank() }?.name ?: return null
        if (song.title.isBlank() || song.duration <= 0) return null
        val search = lookups.getOrPut(id) {
            scope.async {
                val track = runCatching {
                    OpenFlac.findCopy(song.title, artist, song.duration, song.album?.title)
                }.onFailure { Timber.tag(TAG).w(it, "search failed for ${song.title}") }.getOrNull()
                lookups.remove(id)
                if (track == null) {
                    missAt[id] = System.currentTimeMillis()
                    Timber.tag(TAG).i("no FLAC copy of \"${song.title}\" by $artist")
                    null
                } else {
                    Timber.tag(TAG).i("FLAC copy of \"${song.title}\": ${track.url}")
                    Copy(track.url, track.sampleRate, track.bitDepth).also { copy ->
                        found[id] = copy
                        runCatching {
                            store?.edit()?.putString(id, listOf(copy.url, copy.sampleRate ?: "", copy.bitDepth ?: "").joinToString("\t"))?.apply()
                        }
                    }
                }
            }
        }
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { search.await() }
    }

    /** The copy would not play: forget it, and leave the song to its ordinary sources this session. */
    fun reject(mediaId: String) {
        found.remove(mediaId)?.let { OpenFlac.forgetServerFor(it.url) }
        rejected.add(mediaId)
        runCatching { store?.edit()?.remove(mediaId)?.apply() }
    }

    fun cacheKey(mediaId: String) = "flac-copy:$mediaId"
}
