package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.LocalPlaylistViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val localPlaylistPresentationModule = module {
    viewModelOf(::LocalPlaylistViewModel)
}
