package com.ozyern.exhale.account.presentation

import org.koin.dsl.module

val musicAccountPresentationModule = module {
    factory { MusicAccountStateHolder(get()) }
}
