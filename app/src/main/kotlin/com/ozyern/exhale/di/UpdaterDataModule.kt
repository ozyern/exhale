package com.ozyern.exhale.di

import com.ozyern.exhale.core.updater.ApkVerifier
import com.ozyern.exhale.core.updater.GitHubReleaseSource
import com.ozyern.exhale.core.updater.UpdateInstaller
import com.ozyern.exhale.core.updater.UpdateRepository
import com.ozyern.exhale.core.updater.UpdateStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val updaterDataModule = module {
    factoryOf(::ApkVerifier)
    singleOf(::GitHubReleaseSource)
    singleOf(::UpdateStore)
    singleOf(::UpdateInstaller)
    singleOf(::UpdateRepository)
}
