package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.AccountViewModel
import com.ozyern.exhale.viewmodels.AccountSettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val accountPresentationModule = module {
    viewModelOf(::AccountViewModel)
    viewModelOf(::AccountSettingsViewModel)
}
