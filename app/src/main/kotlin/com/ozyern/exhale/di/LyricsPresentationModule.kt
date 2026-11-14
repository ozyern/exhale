package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.LyricsAppearanceViewModel
import com.ozyern.exhale.viewmodels.LyricsMenuViewModel
import com.ozyern.exhale.viewmodels.LyricsProviderViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val lyricsPresentationModule = module {
    viewModelOf(::LyricsAppearanceViewModel)
    viewModelOf(::LyricsMenuViewModel)
    viewModelOf(::LyricsProviderViewModel)
}
