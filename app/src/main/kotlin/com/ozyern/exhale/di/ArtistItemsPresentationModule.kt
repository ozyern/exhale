package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.ArtistItemsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val artistItemsPresentationModule = module {
    viewModelOf(::ArtistItemsViewModel)
}
