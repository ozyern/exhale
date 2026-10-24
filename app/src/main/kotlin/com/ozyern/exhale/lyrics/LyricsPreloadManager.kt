/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ozyern.exhale.constants.PreloadQueueLyricsEnabledKey
import com.ozyern.exhale.constants.QueueLyricsPreloadCountKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.LyricsEntity
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.utils.NetworkConnectivityObserver
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.reportException
import javax.inject.Inject

/**
 * Manages pre-loading of lyrics for upcoming songs in the queue.
 * This improves user experience by having lyrics ready when songs change.
 */
class LyricsPreloadManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
    private val lyricsHelper: LyricsHelper,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var preloadJob: Job? = null
    
    // Track current queue to detect changes
    private var currentQueueIds: List<String> = emptyList()
    private var currentIndex: Int = -1

    /**
     * Called when the current song changes in the player.
     * Triggers pre-loading of lyrics for the next N songs in the queue.
     *
     * @param currentIndex The index of the currently playing song in the queue
     * @param queue List of MediaMetadata for songs in the queue
     */
    fun onSongChanged(currentIndex: Int, queue: List<MediaMetadata>) {
        // Cancel any existing preload job
        preloadJob?.cancel()
        
        // Held until the playing song has had its own lookup to itself: started at once, three
        // more full races ran alongside it on the same connection, and the song you were actually
        // listening to ran out its deadline and was saved as having no lyrics. Kept as the preload
        // job, so skipping on cancels the wait too.
        preloadJob = scope.launch {
            kotlinx.coroutines.delay(PRELOAD_DELAY_MS)
            try {
                val preferences = context.dataStore.data.first()
                val isEnabled = preferences[PreloadQueueLyricsEnabledKey] ?: true
                
                if (!isEnabled) {
                    Log.d(TAG, "Queue lyrics pre-load is disabled")
                    return@launch
                }
                
                // Check network connectivity
                val isNetworkAvailable = try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
                
                if (!isNetworkAvailable) {
                    Log.w(TAG, "Network unavailable, skipping lyrics pre-load")
                    return@launch
                }
                
                val preloadCount = preferences[QueueLyricsPreloadCountKey] ?: DEFAULT_PRELOAD_COUNT
                
                // Get next N songs after current index
                val nextSongs = getNextSongs(queue, currentIndex, preloadCount)
                
                if (nextSongs.isEmpty()) {
                    Log.d(TAG, "No songs to pre-load")
                    return@launch
                }
                
                Log.d(TAG, "Starting pre-load for ${nextSongs.size} songs")
                preloadLyrics(nextSongs)
                
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    /**
     * Get the next N songs from the queue after the current index.
     */
    private fun getNextSongs(queue: List<MediaMetadata>, currentIndex: Int, count: Int): List<MediaMetadata> {
        if (queue.isEmpty() || currentIndex < 0) {
            return emptyList()
        }
        
        val startIndex = currentIndex + 1
        val endIndex = minOf(startIndex + count, queue.size)
        
        if (startIndex >= queue.size) {
            return emptyList()
        }
        
        return queue.subList(startIndex, endIndex)
    }

    /**
     * Pre-load lyrics for the given songs.
     * Uses parallel fetching with limited concurrency.
     */
    private suspend fun preloadLyrics(songs: List<MediaMetadata>) {
        kotlinx.coroutines.coroutineScope {
            try {
                // Process songs with limited concurrency
                songs.forEach { song ->
                    // Check if lyrics already exist in database
                    val existingLyrics = database.lyrics(song.id).first()
                    if (existingLyrics != null && existingLyrics.lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                        Log.d(TAG, "Lyrics already cached for: ${song.title}")
                        return@forEach
                    }
                    
                    // Fetch lyrics for this song
                    try {
                        val lyrics = fetchLyricsForSong(song)
                        if (lyrics != null && lyrics != LyricsEntity.LYRICS_NOT_FOUND) {
                            // Save to database using query block
                            database.query {
                                upsert(
                                    LyricsEntity(
                                        id = song.id,
                                        lyrics = lyrics
                                    )
                                )
                            }
                            Log.d(TAG, "Pre-loaded lyrics for: ${song.title}")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to pre-load lyrics for ${song.title}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            }
        }
    }

    /**
     * Fetch lyrics for a single song via the SHARED [LyricsHelper] — reusing the service's
     * instance means every prefetch also lands in its in-memory LRU, so the lyrics tab
     * reads straight from memory. (Previously a fresh helper — with an empty cache — was
     * constructed per song, throwing the warm cache away.)
     */
    private suspend fun fetchLyricsForSong(song: MediaMetadata): String? {
        return try {
            // Every source, as when the song plays: what this saves is what the song will show,
            // and asking only the first source saved its answer — or its miss — over better ones.
            lyricsHelper.getLyrics(song, trackLookup = false)
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching lyrics for ${song.title}: ${e.message}")
            null
        }
    }

    /**
     * Cancel any ongoing preload operations.
     */
    fun cancel() {
        preloadJob?.cancel()
        preloadJob = null
    }

    /**
     * Clean up resources when no longer needed.
     */
    fun destroy() {
        cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "LyricsPreloadManager"
        private const val DEFAULT_PRELOAD_COUNT = 3
        private const val PRELOAD_DELAY_MS = 20_000L
    }
}
