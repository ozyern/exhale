/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.pages.ArtistPage
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.extensions.filterExplicit
import com.ozyern.exhale.extensions.filterExplicitAlbums
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)

    /**
     * The artist's top songs, up to twenty. The landing page lists about five; the Songs shelf's
     * "See all" is the artist's own top-songs playlist, so that fills it out (as the reference design does).
     */
    var topSongs by mutableStateOf<List<SongItem>>(emptyList())
        private set

    /** The newest album, single or EP on the artist's shelves, shown on its own card at the top. */
    var latestRelease by mutableStateOf<AlbumItem?>(null)
        private set
    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistSongsByCreateDateAsc(artistId).map { it.filterExplicit(hideExplicit) } // show all
            // database.artistSongsPreview(artistId).map { it.filterExplicit(hideExplicit) } // only preview
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val libraryAlbums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Load artist page and reload when hide explicit setting changes
        viewModelScope.launch {
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .collect {
                    fetchArtistsFromYTM()
                }
        }
    }

    /**
     * Newest by year, which is all a shelf card says about when. Ties keep YouTube's own order,
     * which lists albums before singles.
     */
    private fun newestRelease(sections: List<com.ozyern.exhale.innertube.pages.ArtistSection>): AlbumItem? =
        sections.asSequence()
            .flatMap { it.items.asSequence() }
            .filterIsInstance<AlbumItem>()
            .withIndex()
            .maxWithOrNull(compareBy<IndexedValue<AlbumItem>> { it.value.year ?: 0 }.thenByDescending { it.index })
            ?.value

    private companion object {
        const val MAX_TOP_SONGS = 20
    }

    fun fetchArtistsFromYTM() {
        viewModelScope.launch {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            YouTube.artist(artistId)
                .onSuccess { page ->
                    val filteredSections = page.sections
                        .map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit))
                        }

                    artistPage = page.copy(sections = filteredSections)
                    latestRelease = newestRelease(filteredSections)

                    val songsShelf = filteredSections.firstOrNull { (it.items.firstOrNull() as? SongItem)?.album != null }
                    topSongs = songsShelf?.items?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
                    songsShelf?.moreEndpoint?.let { endpoint ->
                        launch {
                            YouTube.artistItems(endpoint).onSuccess { more ->
                                val full = more.items.filterIsInstance<SongItem>()
                                    .filter { !hideExplicit || !it.explicit }
                                    .distinctBy { it.id }
                                if (full.size > topSongs.size) topSongs = full.take(MAX_TOP_SONGS)
                            }
                        }
                    }
                }.onFailure {
                    reportException(it)
                }
        }
    }
}
