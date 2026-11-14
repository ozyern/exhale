package com.ozyern.exhale.di

import com.ozyern.exhale.ui.screens.library.LocalMusicViewModel
import com.ozyern.exhale.ui.screens.library.apple.AppleLibraryViewModel
import com.ozyern.exhale.ui.screens.library.apple.AppleLocalSongsViewModel
import com.ozyern.exhale.viewmodels.LocalHomeViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val localLibraryPresentationModule = module {
    viewModelOf(::LocalMusicViewModel)
    viewModelOf(::LocalHomeViewModel)
    viewModelOf(::AppleLibraryViewModel)
    viewModelOf(::AppleLocalSongsViewModel)
}
