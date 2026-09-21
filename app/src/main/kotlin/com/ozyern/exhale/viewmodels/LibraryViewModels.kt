/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.constants.AlbumFilter
import com.ozyern.exhale.constants.AlbumFilterKey
import com.ozyern.exhale.constants.AlbumSortDescendingKey
import com.ozyern.exhale.constants.AlbumSortType
import com.ozyern.exhale.constants.AlbumSortTypeKey
import com.ozyern.exhale.constants.ArtistFilter
import com.ozyern.exhale.constants.ArtistFilterKey
import com.ozyern.exhale.constants.ArtistSongSortDescendingKey
import com.ozyern.exhale.constants.ArtistSongSortType
import com.ozyern.exhale.constants.ArtistSongSortTypeKey
import com.ozyern.exhale.constants.ArtistSortDescendingKey
import com.ozyern.exhale.constants.ArtistSortType
import com.ozyern.exhale.constants.ArtistSortTypeKey
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoKey
import com.ozyern.exhale.constants.LibraryFilter
import com.ozyern.exhale.constants.PlaylistSortDescendingKey
import com.ozyern.exhale.constants.PlaylistSortType
import com.ozyern.exhale.constants.PlaylistSortTypeKey
import com.ozyern.exhale.constants.SongFilter
import com.ozyern.exhale.constants.SongFilterKey
import com.ozyern.exhale.constants.SongSortDescendingKey
import com.ozyern.exhale.constants.SongSortType
import com.ozyern.exhale.constants.SongSortTypeKey
import com.ozyern.exhale.constants.TopSize
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.filterExplicit
import com.ozyern.exhale.extensions.filterExplicitAlbums
import com.ozyern.exhale.extensions.reversed
import com.ozyern.exhale.extensions.toEnum
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject

// ──────────────────────────────────────────────────────────────────────────────
// LibrarySongsViewModel (sin cambios)
// ──────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.LIKED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoKey] ?: false,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit, hideVideo) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                    SongFilter.LOCAL ->
                        database.allSongs().flowOn(Dispatchers.IO).map { songs ->
                            val local = songs.filter { it.song.isLocal }
                            when (sortType) {
                                SongSortType.CREATE_DATE -> local.sortedBy { it.song.inLibrary ?: it.song.dateDownload ?: java.time.LocalDateTime.MIN }
                                SongSortType.NAME -> local.sortedBy { it.song.title.lowercase() }
                                SongSortType.ARTIST -> local.sortedBy { song -> song.artists.joinToString("") { it.name }.lowercase() }
                                SongSortType.PLAY_TIME -> local.sortedBy { it.song.totalPlayTime }
                            }.reversed(descending).filterExplicit(hideExplicit)
                        }
                    SongFilter.DOWNLOADED ->
                        combine(
                            database.allSongs().flowOn(Dispatchers.IO),
                            downloadUtil.downloads
                        ) { songs, downloads ->
                            songs.filter {
                                downloads[it.id]?.state == Download.STATE_COMPLETED ||
                                    com.ozyern.exhale.export.SavedFiles.has(it.id)
                            }.let { filteredSongs ->
                                when (sortType) {
                                    SongSortType.CREATE_DATE -> filteredSongs.sortedBy {
                                        downloads[it.id]?.updateTimeMs ?: 0L
                                    }
                                    SongSortType.NAME -> filteredSongs.sortedBy { it.song.title }
                                    SongSortType.ARTIST -> {
                                        val collator = Collator.getInstance(Locale.getDefault())
                                        collator.strength = Collator.PRIMARY
                                        filteredSongs.sortedWith(
                                            compareBy(collator) { song ->
                                                song.artists.joinToString("") { it.name }
                                            }
                                        )
                                    }
                                    SongSortType.PLAY_TIME -> filteredSongs.sortedBy { it.song.totalPlayTime }
                                }.reversed(descending).filterExplicit(hideExplicit)
                            }
                        }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh(filter: SongFilter) {
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                when (filter) {
                    SongFilter.LIKED -> syncUtils.syncLikedSongs()
                    SongFilter.LIBRARY -> syncUtils.syncLibrarySongs()
                    SongFilter.LOCAL -> com.ozyern.exhale.utils.LocalMediaScanner.scan(context, database)
                    SongFilter.DOWNLOADED -> Unit
                }
            } catch (e: Exception) {
                reportException(e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun syncLikedSongs() {
        refresh(SongFilter.LIKED)
    }

    fun syncLibrarySongs() {
        refresh(SongFilter.LIBRARY)
    }

    /** Reads the music on this phone into the Library. */
    fun scanLocal() {
        refresh(SongFilter.LOCAL)
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh(filter: ArtistFilter) {
        if (filter != ArtistFilter.LIKED) return
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                syncUtils.syncArtistsSubscriptions()
            } catch (e: Exception) {
                reportException(e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun sync() {
        refresh(ArtistFilter.LIKED)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null || Duration.between(
                            it.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        it[AlbumSortDescendingKey] ?: true,
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.DOWNLOADED ->
                        combine(
                            database.allSongs().flowOn(Dispatchers.IO),
                            downloadUtil.downloads
                        ) { songs, downloads ->
                            songs.filter { downloads[it.id]?.state == Download.STATE_COMPLETED || com.ozyern.exhale.export.SavedFiles.has(it.id) }
                                .mapNotNull { it.song.albumId }.toSet()
                        }.flatMapLatest { downloadedAlbumIds ->
                            database.albumsByIds(downloadedAlbumIds, sortType, descending)
                                .map { albums -> albums.filterExplicitAlbums(hideExplicit) }
                        }

                    AlbumFilter.DOWNLOADED_FULL ->
                        combine(
                            database.allSongs().flowOn(Dispatchers.IO),
                            downloadUtil.downloads
                        ) { songs, downloads ->
                            songs.filter { downloads[it.id]?.state == Download.STATE_COMPLETED || com.ozyern.exhale.export.SavedFiles.has(it.id) }
                                .mapNotNull { song -> song.song.albumId?.let { it to song } }
                                .groupBy({ it.first }, { it.second })
                                .mapValues { it.value.size }
                        }.flatMapLatest { downloadedCountByAlbum ->
                            database.albumsByIds(downloadedCountByAlbum.keys, sortType, descending)
                                .map { albums ->
                                    albums.filter { album ->
                                        val totalSongsInAlbum = album.album.songCount
                                        val downloadedSongsCount = downloadedCountByAlbum[album.album.id] ?: 0
                                        totalSongsInAlbum > 0 && downloadedSongsCount >= totalSongsInAlbum
                                    }.filterExplicitAlbums(hideExplicit)
                                }
                        }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh(filter: AlbumFilter) {
        if (filter != AlbumFilter.LIKED) return
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                syncUtils.syncLikedAlbums()
            } catch (e: Exception) {
                reportException(e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun sync() {
        refresh(AlbumFilter.LIKED)
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (it[PlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            syncUtils.syncSavedPlaylists()
            syncUtils.syncAutoSyncPlaylists()
            _isRefreshing.value = false
        }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Pair(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {

    // ── Estado de refresco ──────────────────────────────────────────────────
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    // ── Top Value (para "My Top N") ─────────────────────────────────────────
    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()

    // ── Artistas (bookmarked) ──────────────────────────────────────────────
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Álbumes (bookmarked) ────────────────────────────────────────────────
    var albums =
        context.dataStore.data
            .map { it[HideExplicitKey] ?: false }
            .distinctUntilChanged()
            .flatMapLatest { hideExplicit ->
                database.albumsLiked(AlbumSortType.CREATE_DATE, true)
                    .map { it.filterExplicitAlbums(hideExplicit) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Playlists ────────────────────────────────────────────────────────────
    var playlists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to
                        (it[PlaylistSortDescendingKey] ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Sincronización completa ─────────────────────────────────────────────
    fun syncAllLibrary() {
        if (_isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                syncUtils.performFullSync()
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Error during manual sync")
                reportException(e)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    // ── Inicialización: actualizar metadatos de álbumes y artistas ─────────
    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter {
                        it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}