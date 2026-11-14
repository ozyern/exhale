package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.NetworkBrowserViewModel
import com.ozyern.exhale.viewmodels.NetworkStorageViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val networkStoragePresentationModule = module {
    viewModelOf(::NetworkStorageViewModel)
    viewModelOf(::NetworkBrowserViewModel)
}
