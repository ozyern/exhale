package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.UpdateViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val updaterPresentationModule = module {
    viewModelOf(::UpdateViewModel)
}
