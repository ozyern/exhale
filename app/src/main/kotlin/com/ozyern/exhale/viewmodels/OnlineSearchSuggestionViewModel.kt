/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.SearchSuggestions
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.models.songsFirst
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.SearchHistory
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How long the field must be still before a suggestion request goes out.
 *
 * Without this every keystroke fired its own network round trip. `flatMapLatest` cancels the
 * previous *collector*, but the request was already dispatched, so typing "sabrina" opened
 * seven connections and paid seven TLS handshakes to throw six of them away — and each reply
 * that did land rewrote the list under the user's finger. 220ms is below the threshold where
 * a suggestion list feels sluggish and above a fast typist's inter-key interval.
 */
private const val SUGGESTION_DEBOUNCE_MS = 220L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        val historyFlow =
            // StateFlow already conflates equal values, so no distinctUntilChanged here.
            query
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        database.searchHistory()
                    } else {
                        database.searchHistory(query).map { it.take(3) }
                    }
                }

        val remoteFlow =
            query
                // Zero delay for the empty query so opening the field renders its history
                // immediately; `combine` cannot emit until BOTH sources have, and a flat
                // debounce would have held the whole list back by the debounce window.
                .debounce { query -> if (query.isEmpty()) 0L else SUGGESTION_DEBOUNCE_MS }
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        flowOf<SearchSuggestions?>(null)
                    } else {
                        flow<SearchSuggestions?> {
                            emit(null) // clear stale suggestions immediately
                            emit(YouTube.searchSuggestions(query).getOrNull())
                        }
                    }
                }

        // One combined state instead of two collectors both assigning `_viewState.value`.
        // The old history collector built a WHOLE new SearchSuggestionViewState, so every
        // database emission silently reset `suggestions` and `items` to empty — the results
        // list visibly appeared, blanked, and reappeared on each keystroke. Deriving one
        // state from both sources makes that class of race impossible rather than unlikely.
        viewModelScope.launch {
            combine(historyFlow, remoteFlow) { history, remote ->
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                SearchSuggestionViewState(
                    history = history,
                    suggestions = remote
                        ?.queries
                        ?.filter { s -> history.none { it.query == s } }
                        .orEmpty(),
                    items = remote
                        ?.recommendedItems
                        ?.filterExplicit(hideExplicit)
                        ?.filterVideo(hideVideo)
                        // De-duplicated here, once per network reply, rather than in the
                        // LazyColumn's item lambda where it re-allocated the whole list on
                        // every recomposition — including every frame of a scroll.
                        ?.distinctBy { it.id }
                        ?.songsFirst()
                        .orEmpty(),
                )
            }.collect { _viewState.value = it }
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)
