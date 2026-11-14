package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.ChartsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val chartsPresentationModule = module {
    viewModelOf(::ChartsViewModel)
}
