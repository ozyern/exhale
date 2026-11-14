package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.AppleMusicImportViewModel
import com.ozyern.exhale.viewmodels.AutoPlaylistViewModel
import com.ozyern.exhale.viewmodels.PlaylistsViewModel
import com.ozyern.exhale.viewmodels.SpotifyImportViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val playlistPresentationModule = module {
    viewModelOf(::PlaylistsViewModel)
    viewModelOf(::AutoPlaylistViewModel)
    viewModelOf(::SpotifyImportViewModel)
    viewModelOf(::AppleMusicImportViewModel)
}
