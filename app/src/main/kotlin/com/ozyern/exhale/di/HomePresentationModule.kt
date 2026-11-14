package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.NewReleaseViewModel
import com.ozyern.exhale.viewmodels.HomeViewModel
import com.ozyern.exhale.history.ListenHistoryRepository
import com.ozyern.exhale.history.ListenHistorySettingsRepository
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.ui.screens.wrapped.WrappedAudioService
import com.ozyern.exhale.ui.screens.wrapped.WrappedManager
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homePresentationModule = module {
    viewModelOf(::NewReleaseViewModel)
    viewModel {
        val koin = getKoin()
        HomeViewModel(
            context = get(),
            database = get(),
            listenHistoryRepository = ValueProvider { koin.get<ListenHistoryRepository>() },
            listenHistorySettingsRepository = ValueProvider { koin.get<ListenHistorySettingsRepository>() },
            syncUtils = ValueProvider { koin.get<SyncUtils>() },
            wrappedManager = get<WrappedManager>(),
            wrappedAudioService = get<WrappedAudioService>(),
        )
    }
}
