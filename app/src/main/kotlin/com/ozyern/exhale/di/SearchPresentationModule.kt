package com.ozyern.exhale.di

import com.ozyern.exhale.ui.screens.search.AppleSearchViewModel
import com.ozyern.exhale.ui.screens.search.suggestions.SuggestionsViewModel
import com.ozyern.exhale.viewmodels.MoodAndGenresViewModel
import com.ozyern.exhale.viewmodels.OnlineSearchViewModel
import com.ozyern.exhale.viewmodels.OnlineSearchSuggestionViewModel
import com.ozyern.exhale.viewmodels.ExploreViewModel
import com.ozyern.exhale.viewmodels.LocalSearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchPresentationModule = module {
    viewModelOf(::AppleSearchViewModel)
    viewModelOf(::SuggestionsViewModel)
    viewModelOf(::MoodAndGenresViewModel)
    viewModelOf(::OnlineSearchViewModel)
    viewModelOf(::OnlineSearchSuggestionViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::LocalSearchViewModel)
}
