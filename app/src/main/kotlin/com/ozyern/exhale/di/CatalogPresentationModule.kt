package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.AlbumViewModel
import com.ozyern.exhale.viewmodels.AiDjSetViewModel
import com.ozyern.exhale.viewmodels.ArtistAlbumsViewModel
import com.ozyern.exhale.viewmodels.ArtistViewModel
import com.ozyern.exhale.viewmodels.ArtistSongsViewModel
import com.ozyern.exhale.viewmodels.SongInsightViewModel
import com.ozyern.exhale.viewmodels.TopPlaylistViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val catalogPresentationModule = module {
    viewModelOf(::AlbumViewModel)
    viewModelOf(::ArtistViewModel)
    viewModelOf(::ArtistSongsViewModel)
    viewModelOf(::ArtistAlbumsViewModel)
    viewModelOf(::TopPlaylistViewModel)
    viewModelOf(::AiDjSetViewModel)
    viewModelOf(::SongInsightViewModel)
}
