package com.ozyern.exhale.di

import com.ozyern.exhale.library.libraryCoreModule
import com.ozyern.exhale.search.searchHistoryModule

val appModules = listOf(
    chartsPresentationModule,
    catalogPresentationModule,
    backupRestorePresentationModule,
    accountPresentationModule,
    homePresentationModule,
    androidAppDependenciesModule,
    equalizerPresentationModule,
    networkStoragePresentationModule,
    playlistDetailPresentationModule,
    integrationPresentationModule,
    artistItemsPresentationModule,
    browsePresentationModule,
    lyricsPresentationModule,
    historyPresentationModule,
    libraryCoreModule,
    searchHistoryModule,
    localLibraryPresentationModule,
    librarySectionsPresentationModule,
    playlistPresentationModule,
    localPlaylistPresentationModule,
    searchPresentationModule,
    statsPresentationModule,
    radioPresentationModule,
    updaterDataModule,
    updaterPresentationModule,
)
