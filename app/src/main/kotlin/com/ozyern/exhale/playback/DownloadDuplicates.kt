package com.ozyern.exhale.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.DownloaderFactory
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** Completed downloads by title and artist, so the same recording under another id reuses its bytes. */
object DownloadDuplicates {
    private const val DURATION_TOLERANCE_SECONDS = 2

    private class Entry(val id: String, val durationSeconds: Int)

    private val byIdentity = ConcurrentHashMap<String, MutableList<Entry>>()

    fun register(id: String, title: String, artist: String?, durationSeconds: Int) {
        val identity = identity(title, artist) ?: return
        val entries = byIdentity.getOrPut(identity) { java.util.Collections.synchronizedList(mutableListOf()) }
        synchronized(entries) {
            entries.removeAll { it.id == id }
            entries += Entry(id, durationSeconds)
        }
    }

    fun unregister(id: String) {
        byIdentity.values.forEach { entries -> synchronized(entries) { entries.removeAll { it.id == id } } }
    }

    /** The id of a finished download of the same recording, or null. */
    fun ownerOf(id: String, title: String, artist: String?, durationSeconds: Int): String? {
        if (durationSeconds <= 0) return null
        val entries = byIdentity[identity(title, artist) ?: return null] ?: return null
        return synchronized(entries) {
            entries.firstOrNull { it.id != id && abs(it.durationSeconds - durationSeconds) <= DURATION_TOLERANCE_SECONDS }?.id
        }
    }

    private fun identity(title: String, artist: String?): String? {
        val name = normalize(title)
        val by = normalize(artist.orEmpty())
        return if (name.isEmpty() || by.isEmpty()) null else "$name|$by"
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s*[\\[(](?:feat\\.?|ft\\.?|featuring)\\s+[^\\])]*[\\])]"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

/** The key a download's bytes live under; differs from its id when it shares another song's file. */
internal fun DownloadRequest.storageKey(): String = customCacheKey ?: id

/**
 * Lets a download that shares another song's bytes finish without fetching, and keeps the shared
 * bytes until the last download that points at them is removed.
 */
@UnstableApi
internal class SharedKeyDownloaderFactory(
    private val delegate: DownloaderFactory,
    private val index: WritableDownloadIndex,
) : DownloaderFactory {
    override fun createDownloader(request: DownloadRequest): Downloader {
        val inner = delegate.createDownloader(request)
        return object : Downloader by inner {
            override fun remove() {
                val key = request.storageKey()
                val stillShared = index.getDownloads().use { cursor ->
                    var found = false
                    while (!found && cursor.moveToNext()) {
                        val other = cursor.download
                        found = other.request.id != request.id &&
                            other.request.storageKey() == key &&
                            other.state != Download.STATE_REMOVING
                    }
                    found
                }
                if (!stillShared) inner.remove()
            }
        }
    }
}
