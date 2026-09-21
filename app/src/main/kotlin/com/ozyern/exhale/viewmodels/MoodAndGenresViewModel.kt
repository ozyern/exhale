/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.pages.MoodAndGenres
import com.ozyern.exhale.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
@Inject
constructor() : ViewModel() {
    /**
     * The whole catalogue, in YouTube's own sections ("Moods & moments", "Genres"). Explore's shelf
     * — what this used to read — is a fifth of it with the sections flattened away; that is kept only
     * as the fallback when the full page can't be had.
     */
    val sections = MutableStateFlow<List<MoodAndGenres>?>(null)

    init {
        viewModelScope.launch {
            YouTube
                .moodAndGenres()
                .onSuccess { found ->
                    sections.value = found.filter { it.items.isNotEmpty() }
                }.onFailure { error ->
                    reportException(error)
                    YouTube.explore().onSuccess { page ->
                        sections.value = listOf(MoodAndGenres(title = "", items = page.moodAndGenres))
                    }
                }
        }
    }
}
