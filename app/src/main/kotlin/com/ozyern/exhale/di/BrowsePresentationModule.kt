package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.YouTubeBrowseViewModel
import com.ozyern.exhale.viewmodels.BrowseViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val browsePresentationModule = module {
    viewModelOf(::YouTubeBrowseViewModel)
    viewModelOf(::BrowseViewModel)
}
