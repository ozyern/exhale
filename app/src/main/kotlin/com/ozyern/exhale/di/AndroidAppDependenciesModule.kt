package com.ozyern.exhale.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.ozyern.exhale.constants.MaxSongCacheSizeKey
import com.ozyern.exhale.db.DatabaseDao
import com.ozyern.exhale.db.InternalDatabase
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.eq.EqualizerService
import com.ozyern.exhale.eq.data.EQProfileRepository
import com.ozyern.exhale.history.HistoryLocalDataSource
import com.ozyern.exhale.history.DataStoreListenHistorySettingsRepository
import com.ozyern.exhale.history.ListenHistoryRepository
import com.ozyern.exhale.history.ListenHistorySettingsRepository
import com.ozyern.exhale.history.RoomHistoryLocalDataSource
import com.ozyern.exhale.library.LocalPlaylistSongDataSource
import com.ozyern.exhale.library.MusicLibraryRemoteDataSource
import com.ozyern.exhale.library.MusicPlaylistRemoteDataSource
import com.music.innertube.InnertubeRepository
import com.music.innertube.YouTube
import com.music.innertube.library.InnertubeMusicLibraryDataSource
import com.music.innertube.library.InnertubeMusicPlaylistDataSource
import com.ozyern.exhale.library.DataStorePlaylistDisplayPreferencesRepository
import com.ozyern.exhale.library.DataStorePendingPlaylistDeletionRepository
import com.ozyern.exhale.library.PendingPlaylistDeletionRepository
import com.ozyern.exhale.library.PlaylistDisplayPreferencesRepository
import com.ozyern.exhale.library.PlaylistRepository
import com.ozyern.exhale.library.RoomPlaylistRepository
import com.ozyern.exhale.search.RoomSearchHistoryRepository
import com.ozyern.exhale.search.SearchHistoryRepository
import com.ozyern.exhale.search.DataStoreSearchHistorySettingsRepository
import com.ozyern.exhale.search.SearchHistorySettingsRepository
import com.ozyern.exhale.jam.JamManager
import com.ozyern.exhale.listentogether.ListenTogetherClient
import com.ozyern.exhale.listentogether.ListenTogetherManager
import com.ozyern.exhale.listentogether.ListenTogetherPlayerConnectionBridge
import com.ozyern.exhale.lyrics.LyricsHelper
import com.ozyern.exhale.lyrics.LyricsProviderRepository
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.playback.MediaLibrarySessionCallback
import com.ozyern.exhale.playback.PlaybackServiceConnector
import com.ozyern.exhale.utils.NetworkConnectivityObserver
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.widget.ExhaleWidgetManager
import com.ozyern.storage.data.EncryptedNetworkServerStore
import com.ozyern.storage.data.NetworkMusicRepository
import com.ozyern.storage.domain.NetworkServerStore
import com.ozyern.exhale.ui.screens.wrapped.WrappedAudioService
import com.ozyern.exhale.ui.screens.wrapped.WrappedManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidAppDependenciesModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single<MusicDatabase> { InternalDatabase.newInstance(androidContext()) }
    single<InternalDatabase> { get<MusicDatabase>().delegate }
    single<DatabaseDao> { get<InternalDatabase>().dao }
    // Must be the signed-in global client; a new instance has no session.
    single<InnertubeRepository> { YouTube }
    single<MusicLibraryRemoteDataSource> { InnertubeMusicLibraryDataSource(get()) }
    single<MusicPlaylistRemoteDataSource> { InnertubeMusicPlaylistDataSource(get()) }
    single<DatabaseProvider> { StandaloneDatabaseProvider(androidContext()) }
    single<SimpleCache>(named("playerCache")) {
        val context: Context = androidContext()
        val cacheSize = context.dataStore[MaxSongCacheSizeKey] ?: 1024
        SimpleCache(
            context.filesDir.resolve("exoplayer"),
            if (cacheSize == -1) NoOpCacheEvictor() else LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L),
            get(),
        )
    }
    single<SimpleCache>(named("downloadCache")) {
        val context: Context = androidContext()
        SimpleCache(context.filesDir.resolve("download"), NoOpCacheEvictor(), get())
    }
    single<NetworkServerStore> { EncryptedNetworkServerStore(androidContext()) }
    single { NetworkMusicRepository(get()) }
    single { NetworkConnectivityObserver(androidContext()) }
    single { RoomHistoryLocalDataSource(get()) }
    single<HistoryLocalDataSource> { get<RoomHistoryLocalDataSource>() }
    single<ListenHistoryRepository> { get<RoomHistoryLocalDataSource>() }
    single<ListenHistorySettingsRepository> {
        DataStoreListenHistorySettingsRepository(androidContext())
    }
    single<RoomPlaylistRepository> { RoomPlaylistRepository(get()) }
    single<PlaylistRepository> { get<RoomPlaylistRepository>() }
    single<LocalPlaylistSongDataSource> { get<RoomPlaylistRepository>() }
    single<PlaylistDisplayPreferencesRepository> {
        DataStorePlaylistDisplayPreferencesRepository(androidContext())
    }
    single<PendingPlaylistDeletionRepository> {
        DataStorePendingPlaylistDeletionRepository(androidContext())
    }
    single<SearchHistoryRepository> { RoomSearchHistoryRepository(get()) }
    single<SearchHistorySettingsRepository> {
        DataStoreSearchHistorySettingsRepository(androidContext())
    }
    single { ListenTogetherPlayerConnectionBridge() }
    single { ListenTogetherClient(androidContext()) }
    single { ListenTogetherManager(get(), androidContext(), get()) }
    single { JamManager(androidContext(), get(), ValueProvider { getKoin().get() }) }
    single { LyricsProviderRepository(androidContext()) }
    single { LyricsHelper(androidContext(), get(), get()) }
    single { SyncUtils(androidContext(), get(), get(), get()) }
    single { EqualizerService() }
    single { EQProfileRepository(androidContext()) }
    single { WrappedManager(get(), androidContext()) }
    single { WrappedAudioService(androidContext()) }
    single {
        DownloadUtil(
            context = androidContext(),
            database = get(),
            databaseProvider = get(),
            downloadCache = get(named("downloadCache")),
            playerCache = get(named("playerCache")),
            lyricsHelper = get(),
        )
    }
    single { MediaLibrarySessionCallback(androidContext(), get(), get()) }
    single { ExhaleWidgetManager(androidContext(), get()) }
    factory { PlaybackServiceConnector(ValueProvider { getKoin().get() }, get()) }
}
