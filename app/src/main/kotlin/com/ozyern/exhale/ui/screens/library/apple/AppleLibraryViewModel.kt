/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.library.apple

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.constants.AlbumSortType
import com.ozyern.exhale.constants.ArtistSortType
import com.ozyern.exhale.constants.AlbumSortTypeKey
import com.ozyern.exhale.constants.AlbumSortDescendingKey
import com.ozyern.exhale.constants.ArtistSortTypeKey
import com.ozyern.exhale.constants.ArtistSortDescendingKey
import com.ozyern.exhale.constants.SongSortType
import com.ozyern.exhale.constants.SongSortTypeKey
import com.ozyern.exhale.constants.SongSortDescendingKey
import com.ozyern.exhale.extensions.toEnum
import com.ozyern.exhale.constants.PlaylistSortType
import com.ozyern.exhale.constants.LocalOnlyModeKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Apple Music's Library main page data: pinned items on top, and "Recently Added" —
 * device albums and playlists created in this app, newest first.
 */
class AppleLibraryViewModel(
    private val database: MusicDatabase,
    private val context: Context,
) : ViewModel() {

    /** Local-only mode: device items only. Otherwise the whole library, online items included. */
    val localOnly: StateFlow<Boolean> = context.dataStore.data
        .map { it[LocalOnlyModeKey] ?: false }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val playlists = combine(database.playlists(PlaylistSortType.CREATE_DATE, true), localOnly) { list, local ->
        list.filter { !local || it.playlist.browseId == null }.distinctBy { it.id }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localAlbums = context.dataStore.data
        .map { Triple(it[AlbumSortTypeKey].toEnum(AlbumSortType.NAME), it[AlbumSortDescendingKey] ?: false, it[LocalOnlyModeKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending, local) ->
            if (local) database.albumsLocal(sort, descending) else database.albums(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localArtists = context.dataStore.data
        .map { Triple(it[ArtistSortTypeKey].toEnum(ArtistSortType.NAME), it[ArtistSortDescendingKey] ?: false, it[LocalOnlyModeKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending, local) ->
            if (local) database.artistsLocal(sort, descending) else database.artists(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val localSongs = context.dataStore.data
        .map { Triple(it[SongSortTypeKey].toEnum(SongSortType.NAME), it[SongSortDescendingKey] ?: false, it[LocalOnlyModeKey] ?: false) }
        .distinctUntilChanged()
        .flatMapLatest { (sort, descending, local) ->
            if (local) database.localSongs(sort, descending) else database.songs(sort, descending)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Pinned albums and playlists together, in pin order (Apple's grid order). */
    val pinned: StateFlow<List<LocalItem>> = combine(database.pinnedAlbums(), playlists, localOnly) { albums, lists, local ->
        (albums.filter { !local || it.album.isLocal }.map { it to it.album.pinnedAt } +
            lists.filter { it.playlist.pinnedAt != null }.map { it to it.playlist.pinnedAt })
            .sortedBy { it.second }
            .map { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: StateFlow<List<LocalItem>> = combine(
        localAlbums,
        playlists,
    ) { albums, lists ->
        (albums.map { it to (it.album.bookmarkedAt ?: it.album.inLibrary ?: it.album.lastUpdateTime) } +
            lists.map { it to (it.playlist.bookmarkedAt ?: it.playlist.createdAt ?: LocalDateTime.MIN) })
            .sortedByDescending { it.second }
            .take(RECENTLY_ADDED_LIMIT)
            .map { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var scanJob: kotlinx.coroutines.Job? = null
    fun scanDevice() {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                com.ozyern.exhale.utils.LocalAudioScanner.scanAndInsert(context, database)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                com.ozyern.exhale.utils.reportException(error)
            }
        }
    }

    /**
     * Pinned order is the pinnedAt order; a drag-rearrange rewrites the stamps in the new
     * order so it survives (Apple lets pinned items be dragged around).
     */
    fun reorderPinned(ordered: List<LocalItem>) {
        val base = LocalDateTime.now()
        viewModelScope.launch(Dispatchers.IO) {
            database.query {
                ordered.forEachIndexed { i, item ->
                    val at = base.plusNanos(i * 1_000_000L)
                    when (item) {
                        is Playlist -> update(item.playlist.copy(pinnedAt = at))
                        is com.ozyern.exhale.db.entities.Album -> update(item.album.copy(pinnedAt = at))
                        else -> Unit
                    }
                }
            }
        }
    }

    private companion object {
        const val RECENTLY_ADDED_LIMIT = 24
    }
}
