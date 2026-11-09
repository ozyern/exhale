/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
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

    /**
     * Puts each album's other editions beside it: "Short n' Sweet (Deluxe)" after "Short n' Sweet".
     * YouTube's artist page lists one edition of an album and keeps the rest on the album's own
     * page, so a deluxe or bonus-track release was only reachable from inside the standard one.
     *
     * Only editions under a title of their own are added; the clean copy of the same album, which
     * the same shelf also lists, would just be the album twice. An album whose page fails simply
     * gets nothing added.
     *
     * Each album's editions go in as soon as its own page answers, in shelf order, and are kept for
     * the session. They used to wait for every album's page — sixteen, four at a time — so the
     * deluxe arrived seconds after the album beside it, and again on every visit.
     */
    private suspend fun addOtherEditions(hideExplicit: Boolean) {
        val page = artistPage ?: return
        val albums = page.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
            .distinctBy { it.id }
            .take(MAX_ALBUMS_TO_EXPAND)
        if (albums.isEmpty()) return
        val editions = java.util.concurrent.ConcurrentHashMap<String, List<AlbumItem>>()
        // Known from an earlier visit: in with the page's first frame.
        albums.forEach { album -> editionCache[album.id]?.let { editions[album.id] = it } }
        if (editions.isNotEmpty()) applyEditions(editions, hideExplicit)
        val missing = albums.filter { !editions.containsKey(it.id) }
        if (missing.isEmpty()) return
        val gate = kotlinx.coroutines.sync.Semaphore(EDITION_FETCHES_AT_ONCE)
        val arrivals = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
        kotlinx.coroutines.coroutineScope {
            val fetches = missing.map { album ->
                async(Dispatchers.IO) {
                    val found = gate.withPermit {
                        YouTube.album(album.browseId, withSongs = false).getOrNull()?.otherVersions
                    }
                    if (found != null) {
                        editionCache[album.id] = found
                        editions[album.id] = found
                        arrivals.trySend(Unit)
                    }
                }
            }
            // Arrivals within a frame or two are put in together, so the shelf moves once for a
            // burst of answers rather than once per album.
            launch {
                while (true) {
                    arrivals.receive()
                    kotlinx.coroutines.delay(EDITION_BATCH_MS)
                    applyEditions(editions, hideExplicit)
                }
            }.also { applier ->
                fetches.awaitAll()
                applier.cancel()
            }
        }
        applyEditions(editions, hideExplicit)
    }

    /** Puts [editions] beside their albums on the page as it is now. Idempotent: an edition already in is skipped. */
    private fun applyEditions(editions: Map<String, List<AlbumItem>>, hideExplicit: Boolean) {
        // Read again: the page may have been refreshed while the editions were fetched.
        val current = artistPage ?: return
        val seenTitles = current.sections.flatMap { it.items }.filterIsInstance<AlbumItem>()
            .mapTo(HashSet()) { titleKey(it.title) }
        val seenIds = current.sections.flatMap { it.items }.mapTo(HashSet()) { it.id }
        val sections = current.sections.map { section ->
            if (section.items.none { it is AlbumItem }) return@map section
            val items = section.items.flatMap { item ->
                val base = titleKey(item.title)
                val extra = (editions[item.id].orEmpty())
                    // The album page's shelves carry other artists' records as well as its own
                    // editions; an edition is named after the album ("… (Deluxe)").
                    .filter { base.isNotEmpty() && titleKey(it.title).startsWith(base) }
                    .filter { !hideExplicit || !it.explicit }
                    .filter { seenIds.add(it.id) && seenTitles.add(titleKey(it.title)) }
                listOf(item) + extra
            }
            section.copy(items = items)
        }
        artistPage = current.copy(sections = sections)
    }

    private fun titleKey(title: String): String = com.ozyern.exhale.lyrics.SongQuery.normalized(title)

    private companion object {
        const val MAX_TOP_SONGS = 20

        /** How many of the artist's albums are looked into for other editions. */
        const val MAX_ALBUMS_TO_EXPAND = 16
        const val EDITION_FETCHES_AT_ONCE = 8

        /** How long arrivals are gathered before the shelf is updated with them. */
        const val EDITION_BATCH_MS = 120L

        /** Album id → its other editions, for the life of the process: a revisit has them at once. */
        val editionCache = java.util.concurrent.ConcurrentHashMap<String, List<AlbumItem>>()
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
                    launch { addOtherEditions(hideExplicit) }

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
