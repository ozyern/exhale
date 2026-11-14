package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.JamViewModel
import com.ozyern.exhale.viewmodels.ListenTogetherViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val integrationPresentationModule = module {
    viewModelOf(::JamViewModel)
    viewModelOf(::ListenTogetherViewModel)
}
