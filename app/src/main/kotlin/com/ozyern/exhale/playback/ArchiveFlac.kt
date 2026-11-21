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
    private val retried = ConcurrentHashMap.newKeySet<String>()
    private val lookups = ConcurrentHashMap<String, Deferred<Copy?>>()
    @Volatile private var store: SharedPreferences? = null

    /**
     * Told the song's id when a search finds its copy. A search that outran [LOOKUP_TIMEOUT_MS] has
     * already let the song start from a lossy source; the player uses this to move it onto the copy.
     */
    @Volatile var onCopyFound: ((mediaId: String) -> Unit)? = null

    /** Starts the search for [song]'s copy without waiting for it, so it is known by the time it plays. */
    fun prefetch(song: MediaMetadata) {
        if (!enabled || song.id in rejected || known(song.id) != null) return
        scope.async { find(song) }
    }

    fun init(context: Context) {
        store = context.applicationContext.getSharedPreferences("flac_copies", Context.MODE_PRIVATE)
        OpenFlac.log = { message, error -> Timber.tag(TAG).w(error, message) }
    }

    /** The copy already known for [mediaId], without searching. */
    fun known(mediaId: String): Copy? {
        if (!enabled || mediaId in rejected) return null
        found[mediaId]?.let { return it }
        val stored = runCatching { store?.getString(mediaId, null) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val parts = stored.split('\t')
        // Earlier builds saved the item server's address, which stops working when the item moves.
        return Copy(OpenFlac.canonicalUrl(parts[0]), parts.getOrNull(1)?.toIntOrNull(), parts.getOrNull(2)?.toIntOrNull()).also { found[mediaId] = it }
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
        val artist = song.artists.firstOrNull { it.name.isNotBlank() }?.name
        if (artist == null || song.title.isBlank() || song.duration <= 0) {
            Timber.tag(TAG).d("not searched for $id: title=\"${song.title}\" artist=$artist duration=${song.duration}")
            return null
        }
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
                        runCatching { onCopyFound?.invoke(id) }
                    }
                }
            }
        }
        return withTimeoutOrNull(LOOKUP_TIMEOUT_MS) { search.await() }
    }

    /**
     * The copy failed for the first time this session: the item server it was read from is
     * forgotten, so the next try goes through archive.org, which redirects to wherever the file is
     * now. True when that retry is worth making; false once it has been made.
     */
    fun retryThroughArchive(mediaId: String): Boolean {
        val copy = known(mediaId) ?: return false
        if (!retried.add(mediaId)) return false
        OpenFlac.forgetServerFor(copy.url)
        return true
    }

    /**
     * The copy would not play: the song goes to its ordinary sources for the rest of the session.
     * The copy stays remembered on the phone. A failure is far more often a bad moment on the
     * network or a slow archive server than a missing file, and deleting it on the first one left
     * the song lossy for good.
     */
    fun reject(mediaId: String) {
        found[mediaId]?.let { OpenFlac.forgetServerFor(it.url) }
        rejected.add(mediaId)
    }

    fun cacheKey(mediaId: String) = "flac-copy:$mediaId"
}
