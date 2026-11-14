package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.LibraryAlbumsViewModel
import com.ozyern.exhale.viewmodels.LibraryArtistsViewModel
import com.ozyern.exhale.viewmodels.LibraryMixViewModel
import com.ozyern.exhale.viewmodels.LibraryPlaylistsViewModel
import com.ozyern.exhale.viewmodels.LibrarySongsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val librarySectionsPresentationModule = module {
    viewModelOf(::LibrarySongsViewModel)
    viewModelOf(::LibraryArtistsViewModel)
    viewModelOf(::LibraryAlbumsViewModel)
    viewModelOf(::LibraryPlaylistsViewModel)
    viewModelOf(::LibraryMixViewModel)
}
