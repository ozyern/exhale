package com.ozyern.exhale.di

import com.ozyern.exhale.ui.screens.equalizer.EQViewModel
import com.ozyern.exhale.ui.screens.equalizer.axion.AxionEqViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val equalizerPresentationModule = module {
    viewModelOf(::EQViewModel)
    viewModelOf(::AxionEqViewModel)
}
