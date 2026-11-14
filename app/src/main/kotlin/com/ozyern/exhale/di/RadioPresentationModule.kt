package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.RadioStationViewModel
import com.ozyern.exhale.viewmodels.RadioViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val radioPresentationModule = module {
    viewModelOf(::RadioStationViewModel)
    viewModelOf(::RadioViewModel)
}
