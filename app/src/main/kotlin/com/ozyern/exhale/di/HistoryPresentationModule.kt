package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.HistoryViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val historyPresentationModule = module {
    viewModelOf(::HistoryViewModel)
}
