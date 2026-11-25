/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.SearchFilter
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.pages.ExplorePage
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.PreferredArtistsKey
import com.ozyern.exhale.constants.PreferredAudioLanguagesKey
import com.ozyern.exhale.ui.screens.onboarding.artistsForLanguages
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.reportException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ExploreViewModel(
    val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    // Seeded from the last fetch so a return visit paints immediately; a refresh then replaces it.
    val explorePage = MutableStateFlow<ExplorePage?>(ExploreCache.page)

    /** Related items seeded from the most recently played track. */
    val recommendedForYou = MutableStateFlow<List<YTItem>?>(ExploreCache.recommended)

    /**
     * The top of Explore, from what was picked in setup: the latest records of the chosen artists,
     * topped up with artists for the chosen languages, dealt out one per artist in turn so no single
     * artist fills it. Empty when nothing was picked; the screen then shows the global new releases.
     */
    val forYou = MutableStateFlow(ExploreCache.forYou.orEmpty())

    /** Lower-cased names of the seed artists, so global new releases by them can lead their shelf. */
    val seedNames = MutableStateFlow<Set<String>>(emptySet())

    private suspend fun seeds(): Pair<String, List<String>> {
        val prefs = context.dataStore.data.first()
        fun csv(value: String?) = value.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val pickedCsv = prefs[PreferredArtistsKey].orEmpty()
        val languagesCsv = prefs[PreferredAudioLanguagesKey].orEmpty()
        val picked = csv(pickedCsv)
        val pickedSet = picked.toHashSet()
        val perLanguage = csv(languagesCsv)
            .map { code -> artistsForLanguages(listOf(code)).map { it.name }.filter { it !in pickedSet } }
        val fromLanguages = (0 until (perLanguage.maxOfOrNull { it.size } ?: 0))
            .flatMap { i -> perLanguage.mapNotNull { it.getOrNull(i) } }
            .distinct()
        val names = (picked + fromLanguages).take(FOR_YOU_ARTISTS)
        return "$pickedCsv|$languagesCsv" to names
    }

    private suspend fun loadForYou() {
        val (key, names) = seeds()
        seedNames.value = names.mapTo(HashSet()) { it.lowercase() }
        if (names.isEmpty()) {
            forYou.value = emptyList()
            return
        }
        // Same picks as last time and still fresh in memory: nothing to fetch.
        if (key == ExploreCache.forYouKey && !ExploreCache.forYou.isNullOrEmpty()) {
            forYou.value = ExploreCache.forYou.orEmpty()
            return
        }
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val perArtist = coroutineScope {
            names.map { name ->
                async(Dispatchers.IO) {
                    runCatching {
                        val id = YouTube.search(name, SearchFilter.FILTER_ARTIST).getOrNull()
                            ?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()?.id ?: return@runCatching emptyList()
                        // An artist page lists records newest first.
                        YouTube.artist(id).getOrNull()?.sections.orEmpty()
                            .flatMap { it.items }.filterIsInstance<AlbumItem>()
                            .distinctBy { it.id }
                            .filterExplicit(hideExplicit)
                            .take(FOR_YOU_PER_ARTIST)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        val dealt = (0 until (perArtist.maxOfOrNull { it.size } ?: 0))
            .flatMap { i -> perArtist.mapNotNull { it.getOrNull(i) } }
            .distinctBy { it.id }
            .take(FOR_YOU_SIZE)
        if (dealt.isNotEmpty()) ExploreCache.putForYou(key, dealt)
        forYou.value = dealt
    }

    private suspend fun loadRecommendedForYou() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val seedSong = database.latestEvent().first()?.song ?: return
        val endpoint = YouTube.next(WatchEndpoint(videoId = seedSong.id)).getOrNull()?.relatedEndpoint ?: return
        YouTube.related(endpoint).onSuccess { page ->
            recommendedForYou.value = (page.songs + page.albums + page.artists + page.playlists)
                .distinctBy { it.id }
                .filterExplicit(hideExplicit)
                .shuffled()
                .take(15)
            recommendedForYou.value?.let(ExploreCache::putRecommended)
        }
    }

    private suspend fun load() {
        YouTube
            .explore()
            .onSuccess { page ->
                val artists: MutableMap<Int, String> = mutableMapOf()
                val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                database.allArtistsByPlayTime().first().let { list ->
                    var favIndex = 0
                    for ((artistsIndex, artist) in list.withIndex()) {
                        artists[artistsIndex] = artist.id
                        if (artist.artist.bookmarkedAt != null) {
                            favouriteArtists[favIndex] = artist.id
                            favIndex++
                        }
                    }
                }
                explorePage.value =
                    page.copy(
                        newReleaseAlbums =
                        page.newReleaseAlbums
                            .sortedBy { album ->
                                val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                val firstArtistKey =
                                    artistIds.firstNotNullOfOrNull { artistId ->
                                        if (artistId in favouriteArtists.values) {
                                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                        } else {
                                            artists.entries.firstOrNull { it.value == artistId }?.key
                                        }
                                    } ?: Int.MAX_VALUE
                                firstArtistKey
                            }.filterExplicit(context.dataStore.get(HideExplicitKey, false)),
                    )
                explorePage.value?.let(ExploreCache::put)
            }.onFailure {
                reportException(it)
            }
    }

    init {
        // Fresh data is already on screen; only fetch when there is none or it has gone stale.
        if (!ExploreCache.isFresh) refresh()
        else viewModelScope.launch(Dispatchers.IO) { if (recommendedForYou.value == null) loadRecommendedForYou() }
        viewModelScope.launch(Dispatchers.IO) { loadForYou() }
        // Picks changed in Settings → Content: rebuild the top of the page from the new ones.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[PreferredArtistsKey].orEmpty() to it[PreferredAudioLanguagesKey].orEmpty() }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadForYou() }
        }
    }

    /** Re-fetches the explore page. Bound to the pull-to-refresh gesture. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            load()
            loadRecommendedForYou()
        }
    }
}

/** Seed artists asked for their records, records taken from each, and the hero's length. */
private const val FOR_YOU_ARTISTS = 8
private const val FOR_YOU_PER_ARTIST = 2
private const val FOR_YOU_SIZE = 10
