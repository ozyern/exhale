/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.models.songsFirst
import com.ozyern.exhale.innertube.pages.SearchSummaryPage
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoKey
import com.ozyern.exhale.models.ItemsPage
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    // CRASH FIX: never force-unwrap a nav argument. A malformed deep link (or a process
    // restore racing navigation) can hand us a SavedStateHandle without "query" — fall back
    // to an empty query instead of NPE-ing the whole Activity.
    private val rawQuery = savedStateHandle.get<String>("query").orEmpty()

    // The search bar hands the query over form-encoded, spaces as '+', and navigation decodes only
    // the %-escapes. Every search was sent to YouTube as "say+you+cant+sleep", which it ranks as
    // one odd token rather than four words: much of why results read as if the engine were dumb.
    // A link keeps its '+', which can be part of the URL.
    val query = if (com.ozyern.exhale.utils.MusicLinks.isLink(rawQuery)) rawQuery else rawQuery.replace('+', ' ').trim()
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    /** Songs whose lyrics contain the query, for a search that reads like a line of a song. */
    var lyricMatches by mutableStateOf<List<com.ozyern.exhale.utils.LyricSearch.Match>>(emptyList())

    /** The song or album a pasted link from another service points at, found here. */
    var linkMatch by mutableStateOf<com.ozyern.exhale.innertube.models.YTItem?>(null)
    var linkLoading by mutableStateOf(false)

    /** A pasted Spotify playlist link, offered for import. */
    var spotifyPlaylistLink by mutableStateOf<String?>(null)

    init {
        val words = query
        if (com.ozyern.exhale.spotify.SpotifyImport.playlistId(query) != null) {
            // A whole playlist: offered as an import, not looked up as a song.
            spotifyPlaylistLink = query
        } else if (com.ozyern.exhale.utils.MusicLinks.isLink(query)) {
            linkLoading = true
            viewModelScope.launch {
                linkMatch = runCatching { com.ozyern.exhale.utils.MusicLinks.resolve(query) }.getOrNull()
                linkLoading = false
                // Found: the ordinary results for that song underneath it — the page used to say
                // "No results found" right under the match. Not something it could follow: the
                // text is searched after all rather than show nothing.
                val found = linkMatch
                val by = when (found) {
                    is com.ozyern.exhale.innertube.models.SongItem -> found.artists.joinToString(" ") { it.name }
                    is com.ozyern.exhale.innertube.models.AlbumItem -> found.artists?.joinToString(" ") { it.name }.orEmpty()
                    else -> ""
                }
                YouTube.searchSummary(if (found != null) "${found.title} $by".trim() else query).onSuccess { page ->
                    val cleaned = page.filterExplicit(context.dataStore.get(HideExplicitKey, false))
                        .filterVideo(context.dataStore.get(HideVideoKey, false)).songsFirst()
                    summaryPage = if (found == null) {
                        cleaned
                    } else {
                        SearchSummaryPage(
                            cleaned.summaries
                                .map { it.copy(items = it.items.filterNot { item -> item.id == found.id }) }
                                .filter { it.items.isNotEmpty() },
                        )
                    }
                }
            }
        } else if (com.ozyern.exhale.utils.LyricSearch.looksLikeLyrics(words)) {
            viewModelScope.launch {
                lyricMatches = runCatching { com.ozyern.exhale.utils.LyricSearch.search(words) }.getOrDefault(emptyList())
            }
        }
        viewModelScope.launch {
            filter.collect { filter ->
                // Defensive: the innertube calls are already Result-wrapped, but the
                // post-processing (dataStore reads + filtering) runs outside that wrapper.
                // One malformed response must never take down the search screen.
                try {
                    if (filter == null) {
                        // A pasted link is not a search: YouTube's results for the raw URL are
                        // podcasts and fan edits. The link's own match is shown instead.
                        if (summaryPage == null && (linkLoading || linkMatch != null || spotifyPlaylistLink != null)) {
                            summaryPage = SearchSummaryPage(emptyList())
                        } else if (summaryPage == null) {
                            YouTube
                                .searchSummary(query)
                                .onSuccess {
                                    summaryPage = it.filterExplicit(context.dataStore.get(HideExplicitKey, false)).filterVideo(context.dataStore.get(HideVideoKey, false)).songsFirst()
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                    } else {
                        if (viewStateMap[filter.value] == null) {
                            YouTube
                                .search(query, filter)
                                .onSuccess { result ->
                                    viewStateMap[filter.value] =
                                        ItemsPage(
                                            result.items
                                                .distinctBy { it.id }
                                                .filterExplicit(
                                                    context.dataStore.get(
                                                        HideExplicitKey,
                                                        false
                                                    )
                                                ).filterVideo(context.dataStore.get(HideVideoKey, false)),
                                            result.continuation,
                                        )
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                }
            }
        }
    }

    fun loadMore() {
        val filter = filter.value?.value
        viewModelScope.launch {
            if (filter == null) return@launch
            val viewState = viewStateMap[filter] ?: return@launch
            val continuation = viewState.continuation
            if (continuation != null) {
                val searchResult =
                    YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
                viewStateMap[filter] = ItemsPage(
                    (viewState.items + searchResult.items).distinctBy { it.id },
                    searchResult.continuation
                )
            }
        }
    }
}
