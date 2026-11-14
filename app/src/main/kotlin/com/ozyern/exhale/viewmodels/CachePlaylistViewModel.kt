/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoSongsKey
import com.ozyern.exhale.constants.DataSaverEnabledKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.filterExplicit
import com.ozyern.exhale.extensions.filterVideoSongs
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class CachePlaylistViewModel(
    private val context: Context,
    private val database: MusicDatabase,
    private val playerCache: SimpleCache,
    private val downloadCache: SimpleCache,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val thumbArg = savedStateHandle.get<String>("thumb")?.takeIf { it.isNotEmpty() }

    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    init {
        viewModelScope.launch {
            while (true) {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false) || context.dataStore.get(DataSaverEnabledKey, false)
                // Player cache keys carry suffixes ("id#source-<hash>", "id#aac"), so group them by song id.
                val keysById = playerCache.keys.groupBy { it.substringBefore('#') }
                val downloadedIds = downloadCache.keys.map { it.substringBefore('#') }.toSet()
                val pureCacheIds = keysById.keys.subtract(downloadedIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                val completeSongs = songs.filter { song ->
                    val contentLength = song.format?.contentLength?.takeIf { it > 0 } ?: return@filter false
                    keysById[song.song.id].orEmpty().any { playerCache.isCached(it, 0, contentLength) }
                }

                if (completeSongs.isNotEmpty()) {
                    database.query {
                        completeSongs.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = completeSongs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)

                delay(1000)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}
