package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.CachePlaylistViewModel
import com.ozyern.exhale.viewmodels.OnlinePlaylistViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playlistDetailPresentationModule = module {
    viewModelOf(::OnlinePlaylistViewModel)
    viewModel {
        CachePlaylistViewModel(
            context = get(),
            database = get(),
            playerCache = get(named("playerCache")),
            downloadCache = get(named("downloadCache")),
            savedStateHandle = get(),
        )
    }
}
