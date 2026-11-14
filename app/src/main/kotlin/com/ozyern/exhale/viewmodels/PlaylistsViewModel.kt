/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.constants.AddToPlaylistSortDescendingKey
import com.ozyern.exhale.constants.AddToPlaylistSortTypeKey
import com.ozyern.exhale.constants.PlaylistSortType
import com.ozyern.exhale.extensions.toEnum
import com.ozyern.exhale.library.ObservePlaylists
import com.ozyern.exhale.library.PlaylistCommands
import com.ozyern.exhale.library.PlaylistOrder
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.dataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PlaylistsViewModel(
    context: Context,
    private val observePlaylists: ObservePlaylists,
    private val playlistCommands: PlaylistCommands,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[AddToPlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[AddToPlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                observePlaylists(sortType.toPlaylistOrder(), descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    suspend fun sync() {
        syncUtils.syncSavedPlaylists()
    }

    suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String> =
        playlistCommands.duplicateSongIds(playlistId, songIds)

    suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean =
        playlistCommands.addSongs(playlistId, songIds)

    private fun PlaylistSortType.toPlaylistOrder(): PlaylistOrder = when (this) {
        PlaylistSortType.CREATE_DATE -> PlaylistOrder.CREATED
        PlaylistSortType.NAME -> PlaylistOrder.NAME
        PlaylistSortType.SONG_COUNT -> PlaylistOrder.SONG_COUNT
        PlaylistSortType.LAST_UPDATED -> PlaylistOrder.LAST_UPDATED
    }
}
