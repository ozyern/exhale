package com.ozyern.exhale.di

import com.ozyern.exhale.viewmodels.BackupRestoreViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val backupRestorePresentationModule = module {
    viewModelOf(::BackupRestoreViewModel)
}
