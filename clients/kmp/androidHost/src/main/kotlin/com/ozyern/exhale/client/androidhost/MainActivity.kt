package com.ozyern.exhale.client.androidhost

import android.os.Bundle
import android.webkit.CookieManager
import androidx.room.Room
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ozyern.exhale.account.MusicAccountState
import com.ozyern.exhale.account.SecureSessionStore
import com.ozyern.exhale.account.presentation.MusicAccountPresentationState
import com.ozyern.exhale.account.presentation.MusicAccountStateHolder
import com.ozyern.exhale.account.presentation.musicAccountPresentationModule
import com.ozyern.exhale.catalog.presentation.ExhaleMusicClientShell
import com.ozyern.exhale.catalog.presentation.MusicBrowserScreen
import com.ozyern.exhale.catalog.presentation.catalogPresentationModule
import com.ozyern.exhale.library.libraryCoreModule
import com.ozyern.exhale.library.data.libraryDataModule
import com.ozyern.exhale.library.data.MusicLibraryDatabase
import com.ozyern.exhale.library.data.buildMusicLibraryDatabase
import com.ozyern.exhale.playback.AudioStreamResolver
import com.ozyern.exhale.modules.AndroidModuleStore
import com.ozyern.exhale.modules.ModuleEnvironment
import com.ozyern.exhale.modules.ModuleAudioStreamResolver
import com.ozyern.exhale.modules.ModulesScreen
import com.ozyern.exhale.playback.PlaybackSettingsRepository
import com.ozyern.exhale.playback.PlaybackCheckpointRepository
import com.ozyern.exhale.playback.PlaybackTrackAnalysisRepository
import com.ozyern.exhale.playback.data.PlaybackSettingsDatabase
import com.ozyern.exhale.playback.data.buildPlaybackSettingsDatabase
import com.ozyern.exhale.playback.data.playbackDataModule
import com.ozyern.exhale.playback.presentation.playbackPresentationModule
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        setContent {
            val moduleEnvironment = remember(appContext) { ModuleEnvironment(AndroidModuleStore(appContext)) }
            val libraryDatabase = remember(appContext) {
                val databaseFile = appContext.getDatabasePath("exhale-client-library.db")
                val databaseDirectory = requireNotNull(databaseFile.parentFile)
                check(databaseDirectory.exists() || databaseDirectory.mkdirs()) {
                    "Unable to create the client library database directory."
                }
                buildMusicLibraryDatabase(
                    Room.databaseBuilder(appContext, MusicLibraryDatabase::class.java, databaseFile.absolutePath),
                )
            }
            val playbackSettingsDatabase = remember(appContext) {
                val databaseFile = appContext.getDatabasePath("exhale-client-playback-settings.db")
                val databaseDirectory = requireNotNull(databaseFile.parentFile)
                check(databaseDirectory.exists() || databaseDirectory.mkdirs()) {
                    "Unable to create the client playback settings database directory."
                }
                buildPlaybackSettingsDatabase(
                    Room.databaseBuilder(appContext, PlaybackSettingsDatabase::class.java, databaseFile.absolutePath),
                )
            }
            val libraryStorageModule = remember(libraryDatabase) { libraryDataModule(libraryDatabase) }
            val playbackStorageModule = remember(playbackSettingsDatabase) {
                playbackDataModule(playbackSettingsDatabase)
            }
            val playbackPresentation = remember { playbackPresentationModule() }
            val sessionStorageModule = remember(appContext) {
                module { single<SecureSessionStore> { AndroidSecureSessionStore(appContext) } }
            }
            val webPoTokenProvider = remember(appContext) { AndroidWebPoTokenProvider(appContext) }
            KoinApplication(application = {
                modules(
                    libraryStorageModule,
                    playbackStorageModule,
                    playbackPresentation,
                    sessionStorageModule,
                    innertubeDataModule(webPoTokenProvider) {
                        ModuleAudioStreamResolver(moduleEnvironment.repository, it)
                    },
                    libraryCoreModule,
                    musicAccountPresentationModule,
                    catalogPresentationModule,
                )
            }) {
                DisposableEffect(libraryDatabase, playbackSettingsDatabase) {
                    onDispose {
                        libraryDatabase.close()
                        playbackSettingsDatabase.close()
                    }
                }
                val context = LocalContext.current
                val streamResolver: AudioStreamResolver = koinInject()
                val playbackSettingsRepository: PlaybackSettingsRepository = koinInject()
                val playbackCheckpointRepository: PlaybackCheckpointRepository = koinInject()
                val trackAnalysisRepository: PlaybackTrackAnalysisRepository = koinInject()
                val playbackController = remember(
                    context,
                    streamResolver,
                    playbackSettingsRepository,
                    playbackCheckpointRepository,
                    trackAnalysisRepository,
                ) {
                    AndroidPlaybackController(
                        context.applicationContext,
                        streamResolver,
                        playbackSettingsRepository,
                        playbackCheckpointRepository,
                        trackAnalysisRepository,
                    )
                }
                val accountHolder: MusicAccountStateHolder = koinInject()
                val account by accountHolder.state.collectAsState()
                var showSignIn by remember { mutableStateOf(false) }
                var showModules by remember { mutableStateOf(false) }
                BackHandler(enabled = showModules) { showModules = false }
                LaunchedEffect(account.account, account.isWorking, account.message) {
                    if (
                        account.account is MusicAccountState.SignedIn ||
                        account.account is MusicAccountState.ChoosingChannel ||
                        (!account.isWorking && account.message != null)
                    ) {
                        showSignIn = false
                    }
                }

                DisposableEffect(playbackController, accountHolder, webPoTokenProvider) {
                    onDispose {
                        moduleEnvironment.close()
                        accountHolder.close()
                        playbackController.release()
                        webPoTokenProvider.close()
                    }
                }

                val accountSubtitle = when (val accountState = account.account) {
                    MusicAccountState.SignedOut -> "Guest"
                    is MusicAccountState.ChoosingChannel -> "Choose a YouTube channel"
                    is MusicAccountState.SignedIn -> accountState.account.name
                }
                val accountActionLabel = when (account.account) {
                    MusicAccountState.SignedOut -> when {
                        account.isRestoring -> "Checking…"
                        account.isWorking -> "Signing in…"
                        else -> "Sign in"
                    }
                    is MusicAccountState.ChoosingChannel -> "Cancel"
                    is MusicAccountState.SignedIn -> "Sign out"
                }
                ExhaleMusicClientShell(
                    onModulesAction = { showModules = !showModules },
                    accountSubtitle = accountSubtitle,
                    accountActionLabel = accountActionLabel,
                    accountActionEnabled = !account.isRestoring && !account.isWorking,
                    statusMessages = listOfNotNull(account.message),
                    onAccountAction = {
                        when (account.account) {
                            MusicAccountState.SignedOut -> showSignIn = true
                            is MusicAccountState.ChoosingChannel -> accountHolder.cancelSignIn()
                            is MusicAccountState.SignedIn -> {
                                accountHolder.signOut()
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                            }
                        }
                    },
                ) { contentModifier ->
                    if (showModules) {
                        ModulesScreen(
                            repository = moduleEnvironment.repository,
                            onPlay = { playbackController.playTrack(it); showModules = false },
                            modifier = contentModifier,
                            onBack = { showModules = false },
                        )
                    } else {
                    MusicBrowserScreen(
                        playbackController = playbackController,
                        crossfadeAvailable = true,
                        equalizerAvailable = true,
                        modifier = contentModifier,
                        isSignedIn = account.account is MusicAccountState.SignedIn,
                        onSignInRequested = { showSignIn = true },
                    )
                    }
                }

                if (showSignIn) {
                    AndroidSignInDialog(
                        accountIsWorking = account.isWorking,
                        onComplete = { cookie, visitorData, dataSyncId ->
                            accountHolder.completeWebSignIn(cookie, visitorData, dataSyncId)
                        },
                        onDismiss = {
                            accountHolder.cancelSignIn()
                            CookieManager.getInstance().removeAllCookies(null)
                            showSignIn = false
                        },
                    )
                }

                val channels = (account.account as? MusicAccountState.ChoosingChannel)?.channels.orEmpty()
                if (channels.isNotEmpty()) {
                    Dialog(onDismissRequest = accountHolder::cancelSignIn) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BasicText("Choose a YouTube channel")
                            channels.forEach { channel ->
                                BasicText(
                                    channel.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { accountHolder.selectChannel(channel.dataSyncId) }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
