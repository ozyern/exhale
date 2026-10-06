/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale

import android.annotation.SuppressLint
import android.Manifest
import android.graphics.Color as AndroidColor
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.core.content.ContextCompat
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.ozyern.exhale.ui.component.scrollEdgeVisibility
import com.ozyern.exhale.ui.component.scrollEdgeScrim
import com.ozyern.exhale.utils.PreferenceStore
import kotlinx.coroutines.withContext
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.constants.AppLanguageKey
import com.ozyern.exhale.constants.CustomThemeColorKey
import com.ozyern.exhale.constants.DarkModeKey
import com.ozyern.exhale.constants.DefaultOpenTabKey
import com.ozyern.exhale.constants.DisableScreenshotKey
import com.ozyern.exhale.constants.DynamicThemeKey
import com.ozyern.exhale.constants.FloatingToolbarBottomPadding
import com.ozyern.exhale.constants.FloatingToolbarHeight
import com.ozyern.exhale.constants.FloatingToolbarHorizontalPadding
import com.ozyern.exhale.constants.HasPressedStarKey
import com.ozyern.exhale.constants.LaunchCountKey
import com.ozyern.exhale.constants.LastSeenVersionCodeKey
import com.ozyern.exhale.constants.LiquidGlassNavBarKey
import com.ozyern.exhale.constants.LyricsSyncOffsetKey
import com.ozyern.exhale.constants.MiniPlayerBottomSpacing
import com.ozyern.exhale.constants.MiniPlayerHeight
import com.ozyern.exhale.constants.MiniPlayerLastAnchorKey
import com.ozyern.exhale.constants.MiniPlayerPillCornerRadius
import com.ozyern.exhale.constants.CompactMiniPlayerHeight
import com.ozyern.exhale.constants.CompactMiniPlayerPillCornerRadius
import com.ozyern.exhale.constants.CompactMiniPlayerTopInset
import com.ozyern.exhale.constants.MiniPlayerPillHorizontalInset
import com.ozyern.exhale.constants.NavBarPillCornerRadius
import com.ozyern.exhale.constants.NavBarPillHeight
import com.ozyern.exhale.constants.NavBarRowHeight
import com.ozyern.exhale.constants.NavBarPillSideSlot
import com.ozyern.exhale.constants.NavigationBarAnimationSpec
import com.ozyern.exhale.constants.PauseSearchHistoryKey
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.constants.RemindAfterKey
import com.ozyern.exhale.constants.SabrinaThemeKey
import com.ozyern.exhale.constants.SongPreferencesCompletedKey
import com.ozyern.exhale.constants.SYSTEM_DEFAULT
import com.ozyern.exhale.constants.SearchSource
import com.ozyern.exhale.constants.SearchSourceKey
import com.ozyern.exhale.constants.SlimFloatingToolbarHeight
import com.ozyern.exhale.constants.SlimNavBarKey
import com.ozyern.exhale.constants.StopMusicOnTaskClearKey
import com.ozyern.exhale.constants.UseNewMiniPlayerDesignKey
import com.ozyern.exhale.constants.UseSystemFontKey
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.SearchHistory
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.playback.MusicService
import com.ozyern.exhale.playback.MusicService.MusicBinder
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.queues.LocalAlbumRadio
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.playback.queues.YouTubeAlbumRadio
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.AccountSettingsDialog
import com.ozyern.exhale.ui.component.BootSplash
import com.ozyern.exhale.ui.component.WelcomeScreen
import com.ozyern.exhale.ui.component.bounceClick
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.ui.component.BottomSheetMenu
import com.ozyern.exhale.ui.component.BottomSheetPage
import com.ozyern.exhale.ui.component.COLLAPSED_ANCHOR
import com.ozyern.exhale.ui.component.DISMISSED_ANCHOR
import com.ozyern.exhale.ui.component.EXPANDED_ANCHOR
import com.ozyern.exhale.ui.component.FloatingNavigationToolbar
import com.ozyern.exhale.ui.component.LiquidGlassBottomBar
import com.ozyern.exhale.ui.component.SearchBottomBar
import com.ozyern.exhale.ui.component.LiquidBackground
import com.ozyern.exhale.ui.component.SabrinaCharmField
import com.ozyern.exhale.ui.component.LiquidGlassIconButton
import com.ozyern.exhale.ui.component.NewVersionSheet
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.ozyern.exhale.ui.component.LiquidGlassMark
import com.ozyern.exhale.ui.component.clearGlass
import com.ozyern.exhale.ui.component.clearGlassContentColor
import com.ozyern.exhale.ui.component.liquid.rememberAppBackdrop
import com.ozyern.exhale.ui.component.LocalHazeState
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.mikepenz.markdown.m3.Markdown
import com.ozyern.exhale.constants.TogetherDisplayNameKey
import com.ozyern.exhale.ui.component.BottomSheetPageState
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.TopSearch
import com.ozyern.exhale.ui.component.rememberBottomSheetState
import com.ozyern.exhale.ui.component.shimmer.ShimmerTheme
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.ui.player.BottomSheetPlayer
import com.ozyern.exhale.ui.screens.LOGIN_URL_ARGUMENT
import com.ozyern.exhale.ui.screens.Screens
import com.ozyern.exhale.ui.screens.buildLoginRoute
import com.ozyern.exhale.ui.screens.musicrecognition.MusicRecognitionRoute
import com.ozyern.exhale.ui.screens.navigationBuilder
import com.ozyern.exhale.ui.screens.search.LocalSearchScreen
import com.ozyern.exhale.ui.screens.search.OnlineSearchScreen
import com.ozyern.exhale.ui.screens.settings.DarkMode
import com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager
import com.ozyern.exhale.ui.screens.settings.NavigationTab
import com.ozyern.exhale.ui.screens.settings.ThemePalettes
import com.ozyern.exhale.ui.theme.ExhaleTheme
import dev.chrisbanes.haze.HazeState
import com.ozyern.exhale.ui.theme.ColorSaver
import com.ozyern.exhale.ui.theme.DefaultThemeColor
import com.ozyern.exhale.ui.theme.SabrinaSeedPalette
import com.ozyern.exhale.ui.theme.ThemeSeedPalette
import com.ozyern.exhale.ui.theme.ThemeSeedPaletteCodec
import com.ozyern.exhale.ui.theme.extractThemeColor
import com.ozyern.exhale.ui.utils.appBarScrollBehavior
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.ui.utils.safeHorizontalChromeInset
import com.ozyern.exhale.ui.utils.resetHeightOffset
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.UpdateNotificationManager
import com.ozyern.exhale.utils.Updater
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.readUiScaleBlocking
import com.ozyern.exhale.utils.rememberAppIconPack
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.reportException
import com.ozyern.exhale.utils.setAppLocale
import com.ozyern.exhale.utils.withUiScale
import com.ozyern.exhale.viewmodels.HomeViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.ozyern.exhale.constants.EnableHapticFeedbackKey
import com.ozyern.exhale.constants.PlayerFullscreenKey

@Suppress("DEPRECATION", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Apply the user's interface scale before anything is inflated.
     *
     * This is the whole of Settings -> Appearance -> Display -> Interface scale. It is done here,
     * on the Activity's base context, rather than as a `LocalDensity` override in the theme,
     * because density is only half of what a size change means: `LocalConfiguration` and every
     * dialog and popup window read their size from resources, not from a CompositionLocal. See
     * [withUiScale] for what went wrong when this lived in the theme.
     *
     * The activity declares no `configChanges`, so it is recreated on every configuration change
     * and this runs again against a fresh base configuration -- which is what keeps the scaled
     * screen dimensions honest after a rotation.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withUiScale(readUiScaleBlocking(newBase)))
    }

    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var syncUtils: SyncUtils

    private lateinit var navController: NavHostController
    private var pendingIntent: Intent? = null
    private var pendingDeepLinkSong: PendingDeepLinkSong? = null
    private var pendingTogetherJoinLink: String? = null

    /**
     * A Listen Together link that arrived from outside the app, waiting for a yes.
     *
     * Any web page can fire an `exhale://together` link, and joining hands the session's host
     * control of what plays and this phone's address. So a link from outside is asked about first;
     * nothing is joined until the listener says so.
     */
    private var togetherInviteToConfirm by mutableStateOf<String?>(null)
    private var latestVersionName by mutableStateOf(BuildConfig.VERSION_NAME)

    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    playerConnection =
                        PlayerConnection(this@MainActivity, service, database, lifecycleScope)
                    playPendingDeepLinkSongIfReady()
                    joinPendingTogetherIfReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                playerConnection?.dispose()
                playerConnection = null
            }
        }

    private data class PendingDeepLinkSong(
        val mediaItem: MediaItem,
    )

    private fun playPendingDeepLinkSongIfReady() {
        val pending = pendingDeepLinkSong ?: return
        val connection = playerConnection ?: return
        pendingDeepLinkSong = null
        connection.playQueue(ListQueue(items = listOf(pending.mediaItem)))
    }

    private fun joinPendingTogetherIfReady() {
        val pending = pendingTogetherJoinLink ?: return
        val connection = playerConnection ?: return
        pendingTogetherJoinLink = null
        lifecycleScope.launch(Dispatchers.IO) {
            val displayName =
                runCatching { dataStore.data.first()[TogetherDisplayNameKey] }
                    .getOrNull()
                    ?.trim()
                    .orEmpty()
                    .ifBlank { Build.MODEL ?: getString(R.string.app_name) }
            withContext(Dispatchers.Main) {
                connection.service.joinTogether(pending, displayName)
            }
        }
    }


    override fun onStart() {
        super.onStart()
        isMusicServiceBound =
            bindService(
                Intent(this, MusicService::class.java),
                serviceConnection,
                BIND_AUTO_CREATE
            )
        playPendingDeepLinkSongIfReady()
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        safeUnbindMusicService()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only clear/stop presence when the activity is actually finishing (not on rotation)
        // and do not clear it for transient configuration changes.
        if (isFinishing && !isChangingConfigurations) {
            try { DiscordPresenceManager.stop() } catch (_: Exception) {}
        }

        val shouldStopOnTaskClear =
            if (!isFinishing) {
                false
            } else {
                dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            safeUnbindMusicService()
            stopService(Intent(this, MusicService::class.java))
            playerConnection = null
        }
    }





    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::navController.isInitialized) {
            handleDeepLinkIntent(intent, navController)
        } else {
            pendingIntent = intent
        }
    }


    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    /**
     * Ask for the panel's fastest mode at the current resolution. ColorOS, OxygenOS and MIUI run apps
     * that don't ask at 60 Hz on a 120 Hz screen, which halves how smooth every spring and scroll in
     * the app can possibly look. The system still drops the rate for power saving or heat.
     */
    private fun requestHighestRefreshRate() {
        val display = display ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (fastest.modeId == window.attributes.preferredDisplayModeId) return
        window.attributes = window.attributes.also { it.preferredDisplayModeId = fastest.modeId }
    }

    @OptIn(
        ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
        ExperimentalTextApi::class
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Modern edge-to-edge: draw fully behind the status & navigation bars with transparent,
        // scrim-free system bars for a truly immersive, bezel-less look. Android 15+ enforces
        // edge-to-edge regardless; declaring it explicitly keeps it correct and back-compatible.
        // System-bar ICON colors remain driven by setSystemBarAppearance() below.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
        )
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestHighestRefreshRate()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val initialLocale = PreferenceStore.get(AppLanguageKey)
                ?.takeUnless { it == SYSTEM_DEFAULT }
                ?.let { Locale.forLanguageTag(it) }
                ?: Locale.getDefault()
            setAppLocale(this, initialLocale)

            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    dataStore.data.first()[AppLanguageKey]
                }.onSuccess { lang ->
                    val targetLocale = lang
                        ?.takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()
                    if (targetLocale != initialLocale) {
                        withContext(Dispatchers.Main) {
                            setAppLocale(this@MainActivity, targetLocale)
                            recreate()
                        }
                    }
                }
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[DisableScreenshotKey] ?: false }
                .distinctUntilChanged()
                .collectLatest {
                    withContext(Dispatchers.Main) {
                        if (it) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE,
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                }
        }

        setContent {
            togetherInviteToConfirm?.let { link ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { togetherInviteToConfirm = null },
                    title = { androidx.compose.material3.Text("Join Listen Together?") },
                    text = {
                        androidx.compose.material3.Text(
                            "A link is asking Exhale to join a shared listening session. " +
                                "Whoever runs it will control what plays. Only join if you trust who sent it.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            pendingTogetherJoinLink = link
                            togetherInviteToConfirm = null
                            startMusicServiceSafely()
                            joinPendingTogetherIfReady()
                        }) { androidx.compose.material3.Text("Join") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { togetherInviteToConfirm = null }) {
                            androidx.compose.material3.Text("Not now")
                        }
                    },
                )
            }
            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        playerConnection?.service?.refreshPlaybackNotification()
                    }
                }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                if (System.currentTimeMillis() - Updater.lastCheckTime > 1.days.inWholeMilliseconds) {
                    Updater.getLatestVersionName().onSuccess {
                        latestVersionName = it
                    }
                }
                UpdateNotificationManager.checkForUpdates(this@MainActivity)
            }

            // Use remembered instances so the same state object is used everywhere
            // (previously retrieving the composition local directly created different
            // instances in different composition scopes which caused the update
            // bottom sheet to not appear and overlay interactions to be blocked).
            val bottomSheetPageState = remember { BottomSheetPageState() }
            val (liquidGlassNavBar) = rememberPreference(LiquidGlassNavBarKey, defaultValue = true)
            val menuState = remember { MenuState() }
            val uriHandler = LocalUriHandler.current
            val releaseNotesState = remember { mutableStateOf<String?>(null) }
            val sabrinaTheme by rememberPreference(SabrinaThemeKey, defaultValue = false)
            val enableDynamicTheme by rememberPreference(DynamicThemeKey, defaultValue = true)
            val customThemeColorValue by rememberPreference(CustomThemeColorKey, defaultValue = "default")
            val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
            val useSystemFont by rememberPreference(UseSystemFontKey, defaultValue = false)
            val lyricsSyncOffset by rememberPreference(LyricsSyncOffsetKey, defaultValue = 0)
            val isSystemInDarkTheme = isSystemInDarkTheme()
            val useDarkTheme =
                remember(darkTheme, isSystemInDarkTheme) {
                    if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
                }
            LaunchedEffect(useDarkTheme) {
                setSystemBarAppearance(useDarkTheme)
            }
            val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
            // Pure black yields to Sabrina: forcing surface and background to #000 would
            // erase the warm neutral that is the entire point of that palette.
            val pureBlack = pureBlackEnabled && useDarkTheme && !sabrinaTheme

            val customThemeSeedPalette = remember(customThemeColorValue, sabrinaTheme) {
                if (sabrinaTheme) {
                    SabrinaSeedPalette
                } else if (customThemeColorValue.startsWith("#")) {
                    null
                } else if (customThemeColorValue.startsWith("seedPalette:")) {
                    ThemeSeedPaletteCodec.decodeFromPreference(customThemeColorValue)
                } else {
                    ThemePalettes
                        .findById(customThemeColorValue)
                        ?.let {
                            ThemeSeedPalette(
                                primary = it.primary,
                                secondary = it.secondary,
                                tertiary = it.tertiary,
                                neutral = it.neutral,
                            )
                        }
                }
            }

            val customThemeColor = remember(customThemeColorValue, customThemeSeedPalette, sabrinaTheme) {
                if (sabrinaTheme) {
                    SabrinaSeedPalette.primary
                } else if (customThemeColorValue.startsWith("#")) {
                    try {
                        val colorString = customThemeColorValue.removePrefix("#")
                        Color("#$colorString".toColorInt())
                    } catch (e: Exception) {
                        DefaultThemeColor
                    }
                } else {
                    customThemeSeedPalette?.primary ?: DefaultThemeColor
                }
            }

            var themeColor by rememberSaveable(stateSaver = ColorSaver) {
                mutableStateOf(DefaultThemeColor)
            }

            LaunchedEffect(playerConnection, enableDynamicTheme, sabrinaTheme, isSystemInDarkTheme, customThemeColor) {
                val playerConnection = playerConnection
                // Sabrina is a fixed palette, so the artwork extraction below is skipped entirely
                // rather than allowed to run and be overwritten a frame later.
                if (sabrinaTheme || !enableDynamicTheme || playerConnection == null) {
                    themeColor = if (sabrinaTheme || !enableDynamicTheme) customThemeColor else DefaultThemeColor
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result = imageLoader.execute(
                                    ImageRequest
                                        .Builder(this@MainActivity)
                                        .data(song.thumbnailUrl)
                                        .allowHardware(false)
                                        .build(),
                                )
                                val extractedColor = result.image?.toBitmap()?.extractThemeColor()
                                withContext(Dispatchers.Main) {
                                    themeColor = extractedColor ?: DefaultThemeColor
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeColor = DefaultThemeColor
                                }
                            }
                        }
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            themeColor = DefaultThemeColor
                        } else {
                            themeColor = customThemeColor
                        }
                    }
                }
            }

            ExhaleTheme(
                darkTheme = useDarkTheme,
                pureBlack = pureBlack,
                // Standard, not expressive.
                //
                // `MotionScheme.expressive()` is what makes every Material component in the app
                // overshoot: switches bounce past their track, sliders wobble to a stop, menus
                // spring open. Against the app's own springs - which are tuned once, in
                // Aquamorphic* - that reads as two different pieces of software animating at once,
                // and it is the single loudest Android tell left in the chrome. The app's own
                // motion is unaffected; this only governs the components Material draws.
                motionScheme = MotionScheme.standard(),
                themeColor = themeColor,
                seedPalette = if (sabrinaTheme || !enableDynamicTheme) customThemeSeedPalette else null,
                useSystemFont = useSystemFont,
            ) {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                            )
                ) {
                    // Shared backdrop-blur source for all frosted "liquid glass" surfaces.
                    val hazeState = remember { HazeState() }

                    // The app's ground floor, recorded so that glass drawn on top of it can bend
                    // it.
                    //
                    // This box is a sibling drawn *beneath* everything else in the window, which
                    // is what makes it legal for in-content controls to refract: they are not
                    // inside the layer they sample, so there is no re-entrant draw. Published as
                    // `LocalPageBackdrop` below, and only when the ambient field is actually on —
                    // refracting a flat surface colour yields a flat surface colour, and glass
                    // that reveals nothing is better served by its tonal fallback.
                    val rootBackdrop = rememberLayerBackdrop()
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .layerBackdrop(rootBackdrop)
                            // Opaque: a recording of transparent pixels refracts to nothing, which
                            // is the same "glass is just a dark film" failure the NavHost layer
                            // above had to be given a fill to fix.
                            .background(
                                if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                            ),
                    ) {
                        // Ambient liquid background (opt-in via the Liquid Glass setting). Drawn
                        // first so it sits behind every other layer; theme colors keep it
                        // album-art reactive.
                        if (liquidGlassNavBar) {
                            // What's playing, moving slowly under every page — the same background the
                            // Windows app puts behind Home. It is drawn into the layer the glass chrome
                            // samples, so the dock and the top bar bend it as they move over it. With
                            // nothing playing there is no artwork to move, so the colour field stands in.
                            val ground = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface
                            val ambientArtwork by remember(playerConnection) {
                                playerConnection?.mediaMetadata ?: MutableStateFlow(null)
                            }.collectAsState()
                            val artworkUrl = ambientArtwork?.thumbnailUrl
                            if (artworkUrl != null) {
                                com.ozyern.exhale.ui.component.FluidArtworkBackground(
                                    url = artworkUrl,
                                    brightness = 0.5f,
                                    modifier = Modifier.matchParentSize(),
                                )
                                // Pages are read over this, so it is held back under a scrim that
                                // deepens down the screen — bright enough to see, quiet enough to read on.
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(ground.copy(alpha = 0.30f), ground.copy(alpha = 0.72f)),
                                            ),
                                        ),
                                )
                            } else {
                                // Nothing playing: the same slow field, but deep.
                                //
                                // It used to take the theme's primary, secondary and tertiary at full
                                // strength over the surface colour, and those are bright — on a light
                                // theme, or any pale palette, the page washed out to near-white and
                                // every card on it lost its edges. Sunk towards black and drawn faint,
                                // it reads as the same material the artwork background is made of,
                                // waiting for a song rather than shouting without one.
                                val idleGround = if (isSystemInDarkTheme()) Color.Black else ground
                                LiquidBackground(
                                    colors = listOf(
                                        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.primary, idleGround, 0.62f),
                                        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.tertiary, idleGround, 0.68f),
                                        androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.secondary, idleGround, 0.72f),
                                    ),
                                    baseColor = idleGround,
                                    blobAlpha = 0.30f,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }

                        // Bows, hearts and sparkles, in the layer the glass chrome samples.
                        //
                        // Here rather than over the content on purpose: everything frosted in this
                        // app — the floating top bar, the navigation bar, the mini-player pill,
                        // every sheet — is a lens onto this backdrop, so the charms refract through
                        // them and bend as those surfaces move. Painted above the content instead,
                        // they would have had to be faint enough not to disturb reading, which is
                        // another way of saying invisible.
                        if (sabrinaTheme) {
                            val charmsPlaying by remember(playerConnection) {
                                playerConnection?.isPlaying ?: MutableStateFlow(false)
                            }.collectAsState()
                            SabrinaCharmField(
                                playing = charmsPlaying,
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary,
                                ),
                                // Lower in light: M3 puts light-mode primary down at tone 40, so
                                // the same charm is a deep rose on cream rather than a pastel on
                                // near-black, and carries much further at the same alpha.
                                alpha = if (useDarkTheme) 0.42f else 0.34f,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }

                    val focusManager = LocalFocusManager.current
                    val density = LocalDensity.current
                    // ---- Cutout safety (OxygenOS 16 / ColorOS 16 "Fluid Cloud") ----
                    //
                    // `systemBars` alone is not the unsafe region on these skins. OnePlus and Oppo
                    // draw a live capsule AROUND the camera cutout — playback state, timers, call
                    // status — and that capsule can be TALLER than the status bar it sits in. On
                    // top of that, `displayCutout` is the only inset that reports a *side* cutout
                    // at all, which is what a landscape device actually has.
                    //
                    // Unioning the two gives the region that is genuinely unsafe to paint chrome
                    // in, and everything downstream (the top gradient, the floating bars, the
                    // per-screen content insets) derives from this one value rather than each
                    // guessing separately.
                    val windowsInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    val bottomInset = with(density) { windowsInsets.getBottom(density).toDp() }
                    // The status bar plus any cutout, i.e. the first row of pixels the top-docked
                    // island is allowed to occupy.
                    val topInset = with(density) { windowsInsets.getTop(density).toDp() }
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
                    val bottomInsetDp = windowsInsets.asPaddingValues().calculateBottomPadding()
                    // Shared by the floating bars, the mini-player pill and the morph target that
                    // has to land on them — see `safeHorizontalChromeInset` for why it is a single
                    // symmetric value rather than a per-edge pair.
                    val safeChromeInset = safeHorizontalChromeInset()
                    val chromeHorizontalPadding =
                        FloatingToolbarHorizontalPadding + safeChromeInset

                    val useRail = currentWindowAdaptiveInfo().windowSizeClass
                        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

                    val navController = rememberNavController()

                    // The update prompt lives HERE, not up beside the other top-level state.
                    //
                    // It used to be declared ~200 lines above this, and its "Update Now" read
                    // `navController` from that scope — which resolved to the Activity's
                    // `private lateinit var navController`, a property nothing ever assigns (see
                    // `onNewIntent`, which guards it with `isInitialized` and quietly does
                    // nothing). So the button compiled, looked correct, and threw
                    // `UninitializedPropertyAccessException` the moment anyone pressed it. The
                    // local `val` two lines up is the real controller; declaring the sheet after
                    // it means the name can only resolve to the right one.
                    val updateSheetContent: @Composable ColumnScope.() -> Unit = {
                        NewVersionSheet(
                            latestVersion = latestVersionName,
                            currentVersion = BuildConfig.VERSION_NAME,
                            notes = releaseNotesState.value,
                            // Into the app, not out of it.
                            //
                            // This used to hand the download URL to the system browser: you left
                            // Exhale, waited in a notification shade, found the file and installed
                            // it by hand. The Updates page already owns a real transfer — byte
                            // progress, a reused cache file, a hand-off straight to the package
                            // installer — and it was reachable only by digging through Settings.
                            // So the button goes there and starts it, which is what "Update Now"
                            // has always implied.
                            onUpdate = {
                                bottomSheetPageState.dismiss()
                                navController.navigate("settings/update/download")
                            },
                            onLater = { bottomSheetPageState.dismiss() },
                        )
                    }

                    // fetch release notes and show sheet when a new version is detected
                    LaunchedEffect(latestVersionName) {
                        // Only for a release that is actually newer. "Not the same" also matched an
                        // older release, which offered 1.0.203 as an update to 1.0.304 on every launch.
                        if (Updater.hasUpdate(latestVersionName, BuildConfig.VERSION_NAME)) {
                            Updater.getLatestReleaseNotes().onSuccess {
                                releaseNotesState.value = it
                            }.onFailure {
                                releaseNotesState.value = null
                            }

                            bottomSheetPageState.show(updateSheetContent)
                        }
                    }

                    // Publish the controller to the Activity so `onNewIntent` can route a deep
                    // link that arrives while the app is already running. The property has been
                    // declared since forever and assigned nowhere, so that branch has always
                    // fallen through to `pendingIntent` and the link only opened if the process
                    // happened to be cold. Same one-line omission that produced the crash above;
                    // fixing it here closes both.
                    LaunchedEffect(navController) {
                        this@MainActivity.navController = navController
                    }
                    val homeViewModel: HomeViewModel = hiltViewModel()
                    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

                    // The mark the app wears in its own chrome. See AppIconPack.
                    val appIconPack = rememberAppIconPack()
                    val allLocalItems by homeViewModel.allLocalItems.collectAsState()
                    val allYtItems by homeViewModel.allYtItems.collectAsState()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val (previousTab) = rememberSaveable { mutableStateOf("home") }
                    val currentRoute = navBackStackEntry?.destination?.route
                    val isYearInMusicScreen = currentRoute == "year_in_music"
                    val isAlwaysOnDisplayScreen = currentRoute == "always_on_display"


                    val haptic = LocalHapticFeedback.current
                    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
                    // Off means off everywhere: views that vibrate for themselves rather than through
                    // Compose (system controls, the Android Auto page) ask the window, so the window
                    // is told too.
                    LaunchedEffect(enableHapticFeedback) {
                        window.decorView.isHapticFeedbackEnabled = enableHapticFeedback
                    }
                    val (hapticIntensity) = rememberPreference(com.ozyern.exhale.constants.HapticIntensityKey, 0.75f)
                    val hapticFeel by rememberEnumPreference(com.ozyern.exhale.constants.HapticFeelKey, com.ozyern.exhale.utils.HapticFeel.CRISP)
                    val hapticContext = androidx.compose.ui.platform.LocalContext.current
                    val customHaptic = remember(haptic, enableHapticFeedback, hapticIntensity, hapticFeel) {
                        object : HapticFeedback {
                            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                                if (!enableHapticFeedback) return
                                // Exhale's own feel, at the chosen strength; or the phone's, as it was.
                                if (hapticFeel == com.ozyern.exhale.utils.HapticFeel.SYSTEM) {
                                    haptic.performHapticFeedback(hapticFeedbackType)
                                } else {
                                    com.ozyern.exhale.utils.ExhaleHaptics.perform(hapticContext, hapticFeedbackType, hapticFeel, hapticIntensity)
                                }
                            }
                        }
                    }

                    val navigationItems = remember { Screens.MainScreens }
                    val (slimNav) = rememberPreference(SlimNavBarKey, defaultValue = false)
                    val (useNewMiniPlayerDesign) = rememberPreference(UseNewMiniPlayerDesignKey, defaultValue = true)
                    val (savedMiniPlayerAnchor, setSavedMiniPlayerAnchor) = rememberPreference(
                        MiniPlayerLastAnchorKey,
                        defaultValue = COLLAPSED_ANCHOR
                    )
                    val defaultOpenTab by rememberEnumPreference(DefaultOpenTabKey, NavigationTab.HOME)
                    val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)
                    val tabOpenedFromShortcut =
                        remember {
                            when (intent?.action) {
                                ACTION_LIBRARY -> NavigationTab.LIBRARY
                                ACTION_SEARCH -> NavigationTab.SEARCH
                                else -> null
                            }
                        }

                    val topLevelScreens =
                        listOf(
                            Screens.Home.route,
                            Screens.Search.route,
                            Screens.MoodAndGenres.route,
                            Screens.Library.route,
                            "settings",
                        )

                    val (query, onQueryChange) =
                        rememberSaveable(stateSaver = TextFieldValue.Saver) {
                            mutableStateOf(TextFieldValue())
                        }

                    var active by rememberSaveable {
                        mutableStateOf(false)
                    }

                    val onActiveChange: (Boolean) -> Unit = { newActive ->
                        active = newActive
                        if (!newActive) {
                            focusManager.clearFocus()
                            if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                onQueryChange(TextFieldValue())
                            }
                        }
                    }



                    var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)

                    val searchBarFocusRequester = remember { FocusRequester() }

                    val onSearch: (String) -> Unit = {
                        if (it.isNotEmpty()) {
                            onActiveChange(false)
                            navController.navigate("search/${URLEncoder.encode(it, "UTF-8")}")
                            if (!pauseSearchHistory) {
                                database.query {
                                    insert(SearchHistory(query = it))
                                }
                            }
                        }
                    }

                    var openSearchImmediately: Boolean by remember {
                        mutableStateOf(intent?.action == ACTION_SEARCH)
                    }

                    // The Search tab is a real NavHost destination; its docked "Artists, Songs,
                    // Lyrics…" pill requests the type-in field through the back stack entry's
                    // savedStateHandle (same pattern as Home's scrollToTop signal).
                    val openSearchFieldRequest = navBackStackEntry
                        ?.savedStateHandle
                        ?.getStateFlow("openSearchField", false)
                        ?.collectAsState()
                    LaunchedEffect(openSearchFieldRequest?.value) {
                        if (openSearchFieldRequest?.value == true) {
                            navBackStackEntry?.savedStateHandle?.set("openSearchField", false)
                            onActiveChange(true)
                        }
                    }

                    val shouldShowSearchBar =
                        remember(active, navBackStackEntry) {
                            active ||
                                    navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } ||
                                    navBackStackEntry?.destination?.route?.startsWith("search/") == true
                        }

                    // True on any route that is a *committed* search result page ("search/{q}").
                    // Those pages keep the search field docked at the bottom rather than throwing
                    // it up into the top bar, so they need the bottom chrome on screen and the
                    // content padded for it exactly like a tab route does.
                    val isSearchResultsRoute =
                        navBackStackEntry?.destination?.route?.startsWith("search/") == true

                    // Everywhere except Home, the collapsed player is the *slim* pill rather
                    // than the full-height one.
                    //
                    // Home is where you start something: the dock is right underneath, and the
                    // wide pill's swipe-to-skip and like button earn the strip of screen they
                    // cost. Everywhere else you are reading, and the player is a status line you
                    // occasionally reach for.
                    //
                    // A null route counts as Home: it is the start destination, and a pill that
                    // changes height for one frame of the first composition is worse than one
                    // that is briefly the wrong size.
                    val useCompactPlayer = currentRoute != null && currentRoute != Screens.Home.route

                    val isTabRoute =
                        navBackStackEntry?.destination?.route == null ||
                            navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }
                    // Off the tabs — Settings, an album, an artist — the dock comes along folded,
                    // as State B: home | the song | search. It used to drop away and leave the
                    // full mini player floating on its own there.
                    // In Settings the dock only comes along while something plays: with nothing to
                    // show it is two buttons hovering over the foot of a page that has its own
                    // back button and its own search capsule there.
                    val dockSong by remember(playerConnection) {
                        playerConnection?.mediaMetadata ?: MutableStateFlow(null)
                    }.collectAsState()
                    val onSettingsRoute =
                        navBackStackEntry?.destination?.route?.startsWith("settings") == true
                    val dockOnPage = !active && !isTabRoute && !isSearchResultsRoute &&
                        !(onSettingsRoute && dockSong == null)

                    val shouldShowNavigationBar =
                        remember(navBackStackEntry, active, isSearchResultsRoute, dockOnPage) {
                            !active && (isTabRoute || isSearchResultsRoute || dockOnPage)
                        }

                    val shouldShowHomeShuffleButton =
                        currentRoute == Screens.Home.route &&
                                (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())

                    fun getBottomNavPadding(): Dp {
                        return if (shouldShowNavigationBar && !useRail) {
                            if (slimNav) SlimFloatingToolbarHeight else FloatingToolbarHeight
                        } else {
                            0.dp
                        }
                    }

                    val floatingBarsBottomPadding = FloatingToolbarBottomPadding
                    val navVisibleHeight = if (slimNav) SlimFloatingToolbarHeight else FloatingToolbarHeight

                    val bottomNavigationBarHeight by animateDpAsState(
                        targetValue = if (shouldShowNavigationBar && !useRail) navVisibleHeight else 0.dp,
                        animationSpec = NavigationBarAnimationSpec,
                        label = "",
                    )

                    val playerBottomSheetState =
                        rememberBottomSheetState(
                            dismissedBound = 0.dp,
                            collapsedBound =
                                bottomInset +
                                        (if (shouldShowNavigationBar && !useRail) floatingBarsBottomPadding else 0.dp) +
                                        getBottomNavPadding() +
                                        (if (useNewMiniPlayerDesign) MiniPlayerBottomSpacing else 0.dp) +
                                        MiniPlayerHeight,
                            expandedBound = maxHeight,
                            // The player is the only sheet that morphs geometrically, and it is
                            // the outermost one — the queue and lyrics sheets nested inside it
                            // stay silent so a single flick never fires two vibrations.
                            hapticFeedback = true,
                        )

                    val miniPlayerAnchor by remember {
                        derivedStateOf {
                            when {
                                playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                else -> COLLAPSED_ANCHOR
                            }
                        }
                    }

                    var miniPlayerAnchorPersistenceEnabled by remember(playerConnection) {
                        mutableStateOf(false)
                    }

                    val isPlayerExpanded by remember {
                        derivedStateOf { playerBottomSheetState.isExpanded }
                    }

                    LaunchedEffect(
                        miniPlayerAnchor,
                        isYearInMusicScreen,
                        isAlwaysOnDisplayScreen,
                        miniPlayerAnchorPersistenceEnabled
                    ) {
                        if (!isYearInMusicScreen && !isAlwaysOnDisplayScreen && miniPlayerAnchorPersistenceEnabled) {
                            setSavedMiniPlayerAnchor(miniPlayerAnchor)
                        }
                    }

                    var yearInMusicSavedPlayerAnchor by rememberSaveable { mutableStateOf(-1) }


                    val (playerFullscreen) = rememberPreference(
                        PlayerFullscreenKey,
                        defaultValue = false
                    )

                    LaunchedEffect(
                        isYearInMusicScreen,
                        isAlwaysOnDisplayScreen,
                        isPlayerExpanded,
                        playerFullscreen
                    ) {
                        val controller = WindowCompat.getInsetsController(window, window.decorView)

                        when {
                            isAlwaysOnDisplayScreen -> {
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            }

                            isPlayerExpanded && playerFullscreen -> {
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.systemBars())
                            }

                            isYearInMusicScreen -> {
                                controller.systemBarsBehavior =
                                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                controller.hide(WindowInsetsCompat.Type.statusBars())
                            }

                            else -> {
                                controller.show(WindowInsetsCompat.Type.systemBars())
                            }
                        }
                    }

                    LaunchedEffect(isYearInMusicScreen, playerConnection) {
                        val player = playerConnection?.player ?: return@LaunchedEffect

                        if (isYearInMusicScreen) {
                            if (yearInMusicSavedPlayerAnchor == -1) {
                                yearInMusicSavedPlayerAnchor =
                                    when {
                                        playerBottomSheetState.isExpanded -> EXPANDED_ANCHOR
                                        playerBottomSheetState.isCollapsed -> COLLAPSED_ANCHOR
                                        playerBottomSheetState.isDismissed -> DISMISSED_ANCHOR
                                        else -> COLLAPSED_ANCHOR
                                    }
                            }

                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else if (yearInMusicSavedPlayerAnchor != -1) {
                            val anchorToRestore = yearInMusicSavedPlayerAnchor
                            yearInMusicSavedPlayerAnchor = -1

                            if (player.currentMediaItem == null) {
                                playerBottomSheetState.dismiss()
                            } else {
                                when (anchorToRestore) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.dismiss()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                    }



                    val playerAwareWindowInsets =
                        remember(
                            useRail,
                            bottomInset,
                            shouldShowNavigationBar,
                            playerBottomSheetState.isDismissed,
                            dockOnPage,
                        ) {
                            var bottom = bottomInset
                            if (shouldShowNavigationBar && !useRail) bottom += getBottomNavPadding()
                            // Always the full slot, even for the slim pill: the compact layout is
                            // drawn *inside* an unchanged `MiniPlayerHeight` slot rather than
                            // shortening it, because the sheet's collapsed bound is captured once
                            // in `rememberBottomSheetState` and cannot be re-derived per route
                            // without resetting the sheet.
                            // Off the tabs the song rides inside the folded dock, so there is no separate
                            // mini player above it to leave room for — that room was the empty band.
                            if (!playerBottomSheetState.isDismissed && !(dockOnPage && !useRail)) {
                                bottom += MiniPlayerHeight
                            }
                            windowsInsets
                                .only((if(useRail) {
                                    WindowInsetsSides.Right
                                } else WindowInsetsSides.Horizontal) + WindowInsetsSides.Top)
                                .add(WindowInsets(top = AppBarHeight, bottom = bottom))
                        }

                    appBarScrollBehavior(
                        canScroll = {
                            navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                    (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                        }
                    )

                    val searchBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )
                    val topAppBarScrollBehavior =
                        appBarScrollBehavior(
                            canScroll = {
                                navBackStackEntry?.destination?.route?.startsWith("search/") == false &&
                                        (playerBottomSheetState.isCollapsed || playerBottomSheetState.isDismissed)
                            },
                        )

                    var previousRoute by rememberSaveable { mutableStateOf<String?>(null) }

                    LaunchedEffect(navBackStackEntry) {
                        val currentRoute = navBackStackEntry?.destination?.route
                        val wasOnNonTopLevelScreen = previousRoute != null &&
                                previousRoute !in topLevelScreens &&
                                previousRoute?.startsWith("search/") != true
                        val isReturningToHomeOrLibrary = currentRoute == Screens.Home.route ||
                                currentRoute == Screens.Library.route

                        if (wasOnNonTopLevelScreen && isReturningToHomeOrLibrary) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                        }

                        previousRoute = currentRoute

                        if (navBackStackEntry?.destination?.route?.startsWith("search/") == true) {
                            // CRASH FIX: capture the argument ONCE on the main thread before the
                            // dispatcher hop. navBackStackEntry is a Compose state — re-reading it
                            // inside withContext(IO) races navigation and the old `!!` chain NPE'd
                            // the moment a search was executed. Decode defensively: URLDecoder
                            // throws IllegalArgumentException on stray '%' sequences.
                            val rawQuery = navBackStackEntry?.arguments?.getString("query")
                            if (rawQuery != null) {
                                val searchQuery =
                                    withContext(Dispatchers.IO) {
                                        if (rawQuery.contains("%")) {
                                            rawQuery
                                        } else {
                                            runCatching {
                                                URLDecoder.decode(rawQuery, "UTF-8")
                                            }.getOrDefault(rawQuery)
                                        }
                                    }
                                onQueryChange(
                                    TextFieldValue(
                                        searchQuery,
                                        TextRange(searchQuery.length)
                                    )
                                )
                            }
                        } else if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route } || navBackStackEntry?.destination?.route in topLevelScreens) {
                            onQueryChange(TextFieldValue())
                            if (navBackStackEntry?.destination?.route != Screens.Home.route) {
                                searchBarScrollBehavior.state.resetHeightOffset()
                                topAppBarScrollBehavior.state.resetHeightOffset()
                            }
                        }
                    }
                    LaunchedEffect(active) {
                        if (active) {
                            searchBarScrollBehavior.state.resetHeightOffset()
                            topAppBarScrollBehavior.state.resetHeightOffset()
                            searchBarFocusRequester.requestFocus()
                        }
                    }

                    var restoredMiniPlayerAnchor by remember(playerConnection) { mutableStateOf(false) }

                    LaunchedEffect(playerConnection, savedMiniPlayerAnchor, isYearInMusicScreen) {
                        if (restoredMiniPlayerAnchor) return@LaunchedEffect
                        val player = playerConnection?.player ?: return@LaunchedEffect
                        val connection = playerConnection ?: return@LaunchedEffect
                        connection.queueRestoreCompleted.first { it }
                        if (player.currentMediaItem == null) {
                            if (!playerBottomSheetState.isDismissed) {
                                playerBottomSheetState.dismiss()
                            }
                        } else {
                            if (!isYearInMusicScreen) {
                                when (savedMiniPlayerAnchor) {
                                    EXPANDED_ANCHOR -> playerBottomSheetState.expandSoft()
                                    COLLAPSED_ANCHOR -> playerBottomSheetState.collapseSoft()
                                    DISMISSED_ANCHOR -> playerBottomSheetState.dismiss()
                                    else -> playerBottomSheetState.collapseSoft()
                                }
                            }
                        }
                        restoredMiniPlayerAnchor = true
                        miniPlayerAnchorPersistenceEnabled = true
                    }

                    DisposableEffect(playerConnection, playerBottomSheetState) {
                        val player =
                            playerConnection?.player ?: return@DisposableEffect onDispose { }
                        val listener =
                            object : Player.Listener {
                                override fun onMediaItemTransition(
                                    mediaItem: MediaItem?,
                                    reason: Int,
                                ) {
                                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                                        mediaItem != null &&
                                        playerBottomSheetState.isDismissed &&
                                        !isYearInMusicScreen
                                    ) {
                                        playerBottomSheetState.collapseSoft()
                                    }
                                }
                            }
                        player.addListener(listener)
                        onDispose {
                            player.removeListener(listener)
                        }
                    }

                    var shouldShowTopBar by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(navBackStackEntry) {
                        shouldShowTopBar =
                            !active && navBackStackEntry?.destination?.route in topLevelScreens && navBackStackEntry?.destination?.route != "settings"
                    }

                    val coroutineScope = rememberCoroutineScope()
                    var sharedSong: SongItem? by remember {
                        mutableStateOf(null)
                    }

                    LaunchedEffect(Unit) {
                        if (pendingIntent != null) {
                            handleDeepLinkIntent(pendingIntent!!, navController)
                            pendingIntent = null
                        } else {
                            handleDeepLinkIntent(intent, navController)
                        }
                    }

                    // First-launch Song Preferences gate: if the user has never completed the
                    // language/artist onboarding, push it once on top of the start destination so
                    // it presents as an initial setup step (also reachable later via Settings).
                    LaunchedEffect(Unit) {
                        val completed = withContext(Dispatchers.IO) {
                            dataStore[SongPreferencesCompletedKey] ?: false
                        }
                        if (!completed) {
                            navController.navigate("song_preferences")
                        }
                    }

                    var showStarDialog by remember { mutableStateOf(false) }

                    LaunchedEffect(Unit) {
                        delay(3000)

                        withContext(Dispatchers.IO) {
                            val current = dataStore[LaunchCountKey] ?: 0
                            val newCount = current + 1
                            dataStore.edit { prefs ->
                                prefs[LaunchCountKey] = newCount
                            }
                        }

                        val shouldShow = withContext(Dispatchers.IO) {
                            val hasPressed = dataStore[HasPressedStarKey] ?: false
                            val remindAfter = dataStore[RemindAfterKey] ?: 3
                            !hasPressed && (dataStore[LaunchCountKey] ?: 0) >= remindAfter
                        }

                        if (shouldShow) {
                            var waited = 0L
                            val waitStep = 500L
                            val maxWait = 30_000L
                            while (bottomSheetPageState.isVisible && waited < maxWait) {
                                delay(waitStep)
                                waited += waitStep
                            }
                            showStarDialog = true
                        }
                    }


                    val currentTitleRes = remember(navBackStackEntry) {
                        when (navBackStackEntry?.destination?.route) {
                            Screens.Home.route -> R.string.home
                            Screens.Search.route -> R.string.search
                            Screens.Library.route -> R.string.filter_library
                            else -> null
                        }
                    }

                    var showAccountDialog by remember { mutableStateOf(false) }

                    // App-wide backdrop every liquid-glass surface refracts. This is a real
                    // off-screen recording of the NavHost (published below via
                    // `Modifier.layerBackdrop`), NOT the empty canvas it used to be.
                    val appBackdrop = rememberAppBackdrop()
                    val iosOverscrollFactory = com.ozyern.exhale.ui.utils.rememberIosOverscrollFactory()
                    // Drives the State-B mini-player pill in the bottom bar.
                    val nowPlayingMetadata by remember(playerConnection) {
                        playerConnection?.mediaMetadata ?: MutableStateFlow(null)
                    }.collectAsState()

                    CompositionLocalProvider(
                        LocalAppBackdrop provides appBackdrop,
                        // iOS rubber-band at every list's edge instead of Android's stretch.
                        androidx.compose.foundation.LocalOverscrollFactory provides iosOverscrollFactory,
                        // What glass *inside* the NavHost refracts. The ambient colour field
                        // painted at the very back of the window, which every screen is drawn
                        // over. Pages that lay down their own opaque ground (Settings) override
                        // this with their own so their glass bends what is genuinely behind it.
                        LocalPageBackdrop provides rootBackdrop.takeIf { liquidGlassNavBar },
                        LocalDatabase provides database,
                        LocalContentColor provides if (pureBlack) Color.White else contentColorFor(MaterialTheme.colorScheme.surface),
                        LocalHapticFeedback provides customHaptic,
                        LocalPlayerConnection provides playerConnection,
                        LocalPlayerAwareWindowInsets provides playerAwareWindowInsets,
                        LocalDownloadUtil provides downloadUtil,
                        LocalShimmerTheme provides ShimmerTheme,
                        LocalSyncUtils provides syncUtils,
                        LocalBottomSheetPageState provides bottomSheetPageState,
                        LocalMenuState provides menuState,
                        LocalHazeState provides hazeState,
                    ) {
                        Row {
                            AnimatedVisibility(useRail && shouldShowNavigationBar) {
                                // A floating pill of the dock's own liquid glass, centred on the
                                // edge, rather than a flat strip the full height of the screen.
                                val railShape = androidx.compose.foundation.shape.RoundedCornerShape(36.dp)
                                Box(
                                    modifier = Modifier
                                        .then(Modifier.fillMaxHeight())
                                        .then(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)))
                                        .padding(start = 12.dp, end = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .shadow(18.dp, railShape, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
                                        .clip(railShape)
                                        .liquidGlassSurface(railShape)
                                        .width(84.dp)
                                        .padding(vertical = 14.dp),
                                ) {
                                    navigationItems.fastForEach { screen ->
                                        val isSelected =
                                            navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } == true

                                        NavigationRailItem(
                                            selected = isSelected,
                                            icon = {
                                                Icon(
                                                    painter = painterResource(
                                                        id = if (isSelected) screen.iconIdActive else screen.iconIdInactive
                                                    ),
                                                    contentDescription = null,
                                                )
                                            },
                                            label = {
                                                if (!slimNav) {
                                                    Text(
                                                        text = stringResource(screen.titleId),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                            },
                                            onClick = {
                                                val wasPlayerActive = playerBottomSheetState.isExpanded

                                                if(wasPlayerActive) {
                                                    playerBottomSheetState.collapse(spring())
                                                }

                                                if (screen.route == Screens.Search.route && isSelected) {
                                                    // Second tap on the already-open Search tab
                                                    // opens the type-in field.
                                                    onActiveChange(true)
                                                } else if (isSelected) {
                                                    if(wasPlayerActive) return@NavigationRailItem

                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                    coroutineScope.launch {
                                                        searchBarScrollBehavior.state.resetHeightOffset()
                                                    }
                                                } else {
                                                    // Search included: it is a standard, peer-level
                                                    // NavHost destination — NOT an overlay.
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.startDestinationId) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            },
                                        )
                                    }
                                }
                                }
                            }

                            Scaffold(
                                topBar = {
                                    if (shouldShowTopBar) {
                                        val shouldUseFloatingTopBar = remember(navBackStackEntry) {
                                            navBackStackEntry?.destination?.route == Screens.Home.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Search.route ||
                                                    navBackStackEntry?.destination?.route == Screens.MoodAndGenres.route ||
                                                    navBackStackEntry?.destination?.route == Screens.Library.route
                                        }

                                        val surfaceColor = MaterialTheme.colorScheme.surface
                                        val currentScrollBehavior = if (shouldUseFloatingTopBar) searchBarScrollBehavior else topAppBarScrollBehavior

                                        // Held in place on the tab pages. The logo and account
                                        // discs used to ride the scroll offset up and out of the
                                        // window, which read as the chrome running away from the
                                        // thumb; they stay put now, and the
                                        // page's colour rising behind them is what scrolling changes.
                                        Box(
                                            modifier = Modifier.offset {
                                                IntOffset(
                                                    x = 0,
                                                    y = if (shouldUseFloatingTopBar) 0
                                                    else currentScrollBehavior.state.heightOffset.toInt()
                                                )
                                            }
                                        ) {
                                            // The bar is transparent on every page. This is the
                                            // page's colour rising behind it only once content
                                            // scrolls beneath, so the title never sits on a list.
                                            val edgeColor = if (pureBlack) Color.Black else surfaceColor
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    // `windowsInsets` is systemBars ∪ cutout:
                                                    // on a device whose Fluid Cloud capsule is
                                                    // taller than the status bar, sizing this
                                                    // to systemBars alone left a strip of raw
                                                    // content showing beside the camera, above
                                                    // where the gradient stopped.
                                                    .height(AppBarHeight + with(density) {
                                                        windowsInsets.getTop(density).toDp()
                                                    })
                                                    // Never fully down: some of the page's colour
                                                    // always sits behind the logo and account disc,
                                                    // so a heading scrolled up under them fades out
                                                    // instead of colliding, even before the bar's
                                                    // scroll state has noticed anything.
                                                    .scrollEdgeScrim(edgeColor) {
                                                        maxOf(0.7f, currentScrollBehavior.scrollEdgeVisibility())
                                                    }
                                            )

                                            TopAppBar(
                                                windowInsets = WindowInsets.safeDrawing.only(
                                                    (if (useRail) {
                                                        WindowInsetsSides.Right
                                                    } else {
                                                        WindowInsetsSides.Horizontal
                                                    }) + WindowInsetsSides.Top
                                                ),
                                                title = {
                                                    val googleSans = FontFamily(
                                                        Font(
                                                            R.font.anybody,
                                                            variationSettings = FontVariation.Settings(
                                                                FontVariation.weight(650),
                                                                FontVariation.width(110f),
                                                                FontVariation.slant(-4f)
                                                            )
                                                        )
                                                    )

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(
                                                            6.dp
                                                        )
                                                    ) {
                                                        // Brand logo replaces the app-name title.
                                                        //
                                                        // Which mark is the user's, not ours: the
                                                        // app icon pack they picked in Settings
                                                        // supplies it, so choosing the dark icon
                                                        // changes the disc here too. Leaving this
                                                        // hard-coded meant picking the dark mark
                                                        // and then being met by the gold one two
                                                        // taps later, which reads as the setting
                                                        // not having worked.
                                                        //
                                                        // The art is a full-bleed square, so Crop +
                                                        // a CircleShape clip masks it to a PERFECT
                                                        // circle at any density — never a rounded
                                                        // square, never letterboxed. The hairline
                                                        // ring keeps the mark's dark backdrop from
                                                        // dissolving into a dark app bar.
                                                        // The splash's own transparent mark, in a
                                                        // disc of live liquid glass over whatever
                                                        // scrolls beneath the bar — the same lens
                                                        // the dock is made of. Legal here: the bar
                                                        // is a sibling of the NavHost it refracts.
                                                        // The mark: the logo as a silhouette
                                                        // in white (black in light theme) on a 44dp
                                                        // disc of clear glass, matched by the account
                                                        // disc opposite. Taps home to the top.
                                                        LiquidGlassMark(
                                                            // Exhale's own mark, in its own colours —
                                                            // the one the icon pack in Settings picks.
                                                            markRes = appIconPack.splashLogoRes,
                                                            backdrop = appBackdrop,
                                                            contentDescription = stringResource(R.string.app_name),
                                                            diameter = 44.dp,
                                                            tint = Color.Unspecified,
                                                            modifier = Modifier.bounceClick(
                                                                onClick = {
                                                                    navController.currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
                                                                },
                                                                shape = CircleShape,
                                                            ),
                                                        )
                                                    }
                                                },
                                                actions = {
                                                    // Header is intentionally minimal: brand logo (left) + account (right).
                                                    // The notification bell was removed — new releases remain reachable
                                                    // from the Account area, so it no longer clutters the top bar.
                                                    // A glass disc, not a bare glyph. An IconButton's
                                                    // icon floats in the bar with nothing under it and
                                                    // no relationship to the sheet it opens; giving it
                                                    // the same plate every other control in the app
                                                    // sits on makes it read as a target and matches the
                                                    // logo disc on the opposite side of the bar. The
                                                    // Material `Badge` (a filled error-red pill hanging
                                                    // off the corner) becomes an accent dot punched out
                                                    // of the bar's own colour — the same update
                                                    // affordance, in this app's language.
                                                    val hasUpdate = !Updater.isSameVersion(
                                                        latestVersionName,
                                                        BuildConfig.VERSION_NAME,
                                                    )
                                                    val signedIn = accountImageUrl != null
                                                    Box(
                                                        modifier = Modifier.padding(end = 4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        // The account motion: tapped, the glass ring lets
                                                        // go first and the face shrinks to a point as the panel
                                                        // grows out of this corner; closing, it grows back.
                                                        val discCollapse by animateFloatAsState(
                                                            targetValue = if (showAccountDialog) 1f else 0f,
                                                            animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
                                                            label = "accountDisc",
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .bounceClick(
                                                                    onClick = { showAccountDialog = true },
                                                                    shape = CircleShape,
                                                                ),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Box(
                                                                Modifier
                                                                    .matchParentSize()
                                                                    .graphicsLayer {
                                                                        val ring = (1f - discCollapse * 1.8f).coerceIn(0f, 1f)
                                                                        alpha = ring
                                                                        val s = 0.85f + 0.15f * ring
                                                                        scaleX = s
                                                                        scaleY = s
                                                                    }
                                                                    .clearGlass(CircleShape, appBackdrop),
                                                            )
                                                            Box(
                                                                Modifier.graphicsLayer {
                                                                    val s = (1f - discCollapse).coerceIn(0f, 1f)
                                                                    scaleX = s
                                                                    scaleY = s
                                                                    alpha = (s * 1.4f).coerceAtMost(1f)
                                                                },
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                            if (signedIn) {
                                                                AsyncImage(
                                                                    model = accountImageUrl,
                                                                    contentDescription = stringResource(R.string.account),
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(CircleShape)
                                                                        .border(
                                                                            width = 0.5.dp,
                                                                            color = Color.White.copy(alpha = 0.18f),
                                                                            shape = CircleShape,
                                                                        ),
                                                                )
                                                            } else {
                                                                // Solid, as iOS draws person.crop.circle.fill.
                                                                // The hairline outline glyph read as a
                                                                // placeholder rather than as an account.
                                                                Box(
                                                                    modifier = Modifier
                                                                        .size(28.dp)
                                                                        .clip(CircleShape)
                                                                        .background(clearGlassContentColor().copy(alpha = 0.16f)),
                                                                    contentAlignment = Alignment.Center,
                                                                ) {
                                                                    Icon(
                                                                        painter = painterResource(R.drawable.account),
                                                                        contentDescription = stringResource(R.string.account),
                                                                        tint = clearGlassContentColor(),
                                                                        modifier = Modifier.size(19.dp),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        }

                                                        if (hasUpdate) {
                                                            // Offset onto the disc's rim rather than
                                                            // outside it, so the badge belongs to the
                                                            // button instead of hanging off it.
                                                            Box(
                                                                modifier = Modifier
                                                                    .align(Alignment.TopEnd)
                                                                    .offset(x = (-2).dp, y = 2.dp)
                                                                    .size(11.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.surface)
                                                                    .padding(2.dp)
                                                                    .clip(CircleShape)
                                                                    .background(MaterialTheme.colorScheme.primary),
                                                            )
                                                        }
                                                    }
                                                },
                                                scrollBehavior = if (shouldUseFloatingTopBar) {
                                                    null
                                                } else {
                                                    topAppBarScrollBehavior
                                                },
                                                // No colour of its own on any page; what it sits
                                                // on once content scrolls under is the scroll
                                                // edge above.
                                                colors = TopAppBarDefaults.topAppBarColors(
                                                    containerColor = Color.Transparent,
                                                    scrolledContainerColor = Color.Transparent,
                                                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                                                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            )
                                        }
                                    }
                                    // Only while the user is actually typing. This used to also be
                                    // shown for `search/{q}` routes, and on those the collapsed
                                    // TopSearch renders as a slim bar in the Scaffold's TOP slot —
                                    // which is the "the search bar moves to the top when results
                                    // come" behaviour. Results now keep the field docked at the
                                    // bottom via `SearchBottomBar` in the bottomBar slot below.
                                    AnimatedVisibility(
                                        visible = active,
                                        enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                                        exit = fadeOut(animationSpec = tween(durationMillis = 200))
                                    ) {
                                        TopSearch(
                                            query = query,
                                            onQueryChange = onQueryChange,
                                            onSearch = onSearch,
                                            active = active,
                                            onActiveChange = onActiveChange,
                                            placeholder = {
                                                Text(
                                                    // iOS-style field: the placeholder is a single
                                                    // plain "Search" — never the verbose
                                                    // "Search YouTube Music…" service string.
                                                    text = stringResource(R.string.search),
                                                    // The frosted pill is a single thick line — never let the
                                                    // placeholder wrap onto two rows inside the capsule.
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            leadingIcon = {
                                                if (active) {
                                                    // Apple-Music active field: a plain magnifying
                                                    // glass INSIDE the pill on the left — dismissal
                                                    // is handled by the trailing "Cancel" button
                                                    // (and the system back gesture), not a back arrow.
                                                    Icon(
                                                        painterResource(R.drawable.search),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                                                    )
                                                } else {
                                                    IconButton(
                                                        onClick = {
                                                            if (!navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                                                navController.navigateUp()
                                                            } else {
                                                                onActiveChange(true)
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                                                navController.backToMain()
                                                            }
                                                        },
                                                    ) {
                                                        Icon(
                                                            painterResource(
                                                                if (!navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                                                    R.drawable.chevron_back
                                                                } else {
                                                                    R.drawable.search
                                                                },
                                                            ),
                                                            contentDescription = null,
                                                        )
                                                    }
                                                }
                                            },
                                            trailingIcon = {
                                                Row {
                                                    if (active) {
                                                        if (query.text.isNotEmpty()) {
                                                            // Clearing the field is the one
                                                            // destructive tap in this bar, so it
                                                            // gets the glass disc: it reads as a
                                                            // raised object you press rather than
                                                            // as a glyph printed on the pill.
                                                            LiquidGlassIconButton(
                                                                onClick = {
                                                                    onQueryChange(
                                                                        TextFieldValue(
                                                                            ""
                                                                        )
                                                                    )
                                                                },
                                                                icon = R.drawable.close,
                                                                diameter = 32.dp,
                                                                iconSize = 16.dp,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                // Sitting inside the pill, so it
                                                                // refracts what the pill refracts.
                                                                backdrop = appBackdrop,
                                                            )
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                searchSource =
                                                                    if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL else SearchSource.ONLINE
                                                            },
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(
                                                                    when (searchSource) {
                                                                        SearchSource.LOCAL -> R.drawable.library_music
                                                                        SearchSource.ONLINE -> R.drawable.language
                                                                    },
                                                                ),
                                                                contentDescription = null,
                                                            )
                                                        }
                                                    } else {
                                                        // Idle (docked) state: a mic affordance on the trailing
                                                        // edge, mirroring Apple Music's search pill. Tapping it
                                                        // just opens the field for now.
                                                        IconButton(
                                                            onClick = { onActiveChange(true) },
                                                        ) {
                                                            Icon(
                                                                painter = painterResource(R.drawable.mic),
                                                                contentDescription = stringResource(R.string.search),
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier
                                                    .focusRequester(searchBarFocusRequester)
                                                    .let { with(this@BoxWithConstraints) { it.align(Alignment.TopCenter) } },
                                            focusRequester = searchBarFocusRequester,
                                            colors = if (pureBlack && active) {
                                                SearchBarDefaults.colors(
                                                    containerColor = Color.Black,
                                                    dividerColor = Color.DarkGray,
                                                    inputFieldColors = TextFieldDefaults.colors(
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.Gray,
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        cursorColor = Color.White,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                    )
                                                )
                                            } else {
                                                SearchBarDefaults.colors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                                )
                                            },
                                            // Apple-Music search: dock the input pill at the bottom of the
                                            // screen, floating above the system nav bar. The mini-player sits
                                            // above it (handled by the bottomBar/BottomSheetPlayer stack).
                                            inputAtBottom = true,
                                            bottomBarPadding = bottomInset + floatingBarsBottomPadding,
                                        ) {
                                            Crossfade(
                                                targetState = searchSource,
                                                label = "",
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        // No MiniPlayerHeight reservation here: the player is
                                                        // fully removed from the composition while search is
                                                        // active, so the suggestions own the whole space.
                                                        .navigationBarsPadding(),
                                            ) { searchSource ->
                                                when (searchSource) {
                                                    SearchSource.LOCAL ->
                                                        LocalSearchScreen(
                                                            query = query.text,
                                                            navController = navController,
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack,
                                                        )

                                                    SearchSource.ONLINE ->
                                                        OnlineSearchScreen(
                                                            query = query.text,
                                                            onQueryChange = onQueryChange,
                                                            navController = navController,
                                                            onSearch = {
                                                                navController.navigate(
                                                                    "search/${
                                                                        URLEncoder.encode(
                                                                            it,
                                                                            "UTF-8"
                                                                        )
                                                                    }"
                                                                )
                                                                if (!pauseSearchHistory) {
                                                                    database.query {
                                                                        insert(SearchHistory(query = it))
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = { onActiveChange(false) },
                                                            pureBlack = pureBlack
                                                        )
                                                }
                                            }
                                        }
                                    }
                                },
                                bottomBar = {
                                    // One scope over both the sheet's mini-player and the bar's pill,
                                    // so the accessory can fly between them. See NowPlayingAccessory.
                                    androidx.compose.animation.SharedTransitionLayout {
                                    androidx.compose.runtime.CompositionLocalProvider(
                                        com.ozyern.exhale.ui.component.LocalNowPlayingSharedScope provides this,
                                    ) {
                                    Box {
                                        // State-B logic: when the user scrolls down, the floating bottom bar
                                        // morphs to show its own mini-player pill. To avoid showing TWO players
                                        // at once, hide the sheet's standalone collapsed mini-player in that state.
                                        //
                                        // CRITICAL EXCEPTION — Settings: while inside any Settings screen we must
                                        // NOT collapse the player away on scroll. Force the bottom bar to stay in
                                        // State A (full tabs) and keep the standard full-sized mini-player visible.
                                        val isSettingsScreen =
                                            navBackStackEntry?.destination?.route?.startsWith("settings") == true
                                        // The Search tab uses a COMPLETELY different bottom layout: one
                                        // unified fixed frosted container (home circle + search input).
                                        // The dynamic A/B scroll-morph logic is fully disabled there.
                                        val isSearchScreen =
                                            navBackStackEntry?.destination?.route == Screens.Search.route
                                        // While the search overlay is open the floating nav bar is slid off-screen,
                                        // so its morphed mini-player pill cannot show. The sheet's standalone
                                        // player is ALSO removed below (`if (!active)`) — the search UI owns the
                                        // whole screen with nothing overlapping it.
                                        //
                                        // derivedStateOf: collapsedFraction changes on EVERY scroll frame; reading
                                        // it raw in composition recomposed this whole bottom-bar Box ~60×/s while
                                        // scrolling. Deriving the boolean means recomposition only happens on the
                                        // actual 0.5 threshold crossing.
                                        //
                                        // `shouldShowNavigationBar` is load-bearing, not defensive.
                                        // State B does not hide the player — it MOVES it, out of the
                                        // standalone pill and into the nav bar's centre capsule. So
                                        // it is only a legal state when there is a nav bar on screen
                                        // to move it into.
                                        //
                                        // On every non-tab destination — an album, an artist, a
                                        // playlist — `shouldShowNavigationBar` is false and the bar
                                        // is slid off the bottom of the screen. Without this term
                                        // the scroll threshold still flipped State B on there, so
                                        // `hideMiniPlayer` removed the standalone player while its
                                        // replacement was parked off-screen: scroll an album far
                                        // enough and the player was simply gone, with nothing to tap
                                        // to get it back. That is the "player disappears everywhere
                                        // except Home" bug, and it is a genuine disappearance rather
                                        // than an overlap.
                                        //
                                        // `isSearchResultsRoute` is excluded for the same reason as
                                        // `isSearchScreen`: results pages now keep the docked search
                                        // bar in the bottomBar slot, so there is no State-B capsule
                                        // for the player to merge into there. Without this the newly
                                        // widened `shouldShowNavigationBar` would let State B engage
                                        // on a results page and take the player away with it — the
                                        // exact disappearance described above, on a new route.
                                        // The threshold is a band, not a line.
                                        //
                                        // One value at 0.5 means the bar changes shape wherever
                                        // the finger happens to stop, and a scroll that rests
                                        // near the middle -- which is most of them, because that
                                        // is where the app bar finishes collapsing -- had the
                                        // dock morphing back and forth under the thumb. Two
                                        // thresholds a quarter apart cost one boolean and make
                                        // the morph something that happens once per intention.
                                        //
                                        // Latched from a snapshot flow rather than derived, so
                                        // the crossings are the only thing that writes state:
                                        // `collapsedFraction` changes every scroll frame and
                                        // reading it in composition is what recomposed this whole
                                        // Box sixty times a second.
                                        // The top bar no longer rides the scroll, so it no longer
                                        // tells the scroll state how far there is to go — and without
                                        // that limit `collapsedFraction` stayed at zero and the dock
                                        // never folded into its home | song | search form. Given here.
                                        val appBarTravelPx = with(LocalDensity.current) { AppBarHeight.toPx() }
                                        LaunchedEffect(searchBarScrollBehavior, appBarTravelPx) {
                                            searchBarScrollBehavior.state.heightOffsetLimit = -appBarTravelPx
                                        }
                                        var collapsedLatch by remember { mutableStateOf(false) }
                                        LaunchedEffect(searchBarScrollBehavior) {
                                            snapshotFlow {
                                                searchBarScrollBehavior.state.collapsedFraction
                                            }.collect { fraction ->
                                                if (!collapsedLatch && fraction > 0.62f) {
                                                    collapsedLatch = true
                                                } else if (collapsedLatch && fraction < 0.38f) {
                                                    collapsedLatch = false
                                                }
                                            }
                                        }

                                        val bottomBarCollapsed by remember(
                                            dockOnPage,
                                            isSettingsScreen,
                                            isSearchScreen,
                                            isSearchResultsRoute,
                                            active,
                                            shouldShowNavigationBar,
                                            useRail,
                                            nowPlayingMetadata,
                                        ) {
                                            derivedStateOf {
                                                (dockOnPage && !useRail) ||
                                                shouldShowNavigationBar &&
                                                        !useRail &&
                                                        !isSettingsScreen &&
                                                        !isSearchScreen &&
                                                        !isSearchResultsRoute &&
                                                        !active &&
                                                        // Nothing playing folds too: to the two circles
                                                        // alone.
                                                        collapsedLatch
                                            }
                                        }

                                        // PLAYER-OVERLAP FIX: while the search overlay is open (type-in
                                        // field focused / "Recent Searches" state) the player is removed
                                        // from the composition ENTIRELY — previously the mini-player kept
                                        // floating over the docked search pill and blocked the UI the
                                        // moment the keyboard was dismissed. The sheet state is hoisted
                                        // above this call, so playback and sheet position survive and the
                                        // player re-appears untouched when the search closes.
                                        // ---- Dynamic-Island morph target ----
                                        //
                                        // The player collapses into one of two *different* pieces
                                        // of chrome, and the morph has to land on whichever is
                                        // actually on screen:
                                        //
                                        //  * State A — bar expanded: the standalone mini-player
                                        //    pill floating above the nav bar, i.e. the top strip
                                        //    of the sheet's collapsed region (offset 0).
                                        //  * State B — bar collapsed: the mini-player pill is
                                        //    hidden and the nav bar shows the playback capsule
                                        //    instead, so the player must shrink straight into the
                                        //    frosted nav container: narrower (clearing the home
                                        //    and search circles), fully rounded, and pushed down
                                        //    past the gap the mini player used to occupy.
                                        //
                                        // Cheap to derive and it only changes on the A/B flip —
                                        // which can only happen while the sheet is collapsed, when
                                        // the morph layer is not even composed.
                                        val mergeIntoNavBar =
                                            bottomBarCollapsed && nowPlayingMetadata != null && !useRail

                                        // The slim pill is drawn inside the standard slot with
                                        // `CompactMiniPlayerTopInset` of air above it, so the
                                        // morph has to land on the *drawn* rectangle and not on
                                        // the slot. State B is excluded because there the player
                                        // is not a standalone pill at all — it is inside the nav
                                        // bar capsule.
                                        val useCompactPill = useCompactPlayer && !mergeIntoNavBar
                                        val morphPillTopOffset =
                                            if (mergeIntoNavBar) {
                                                (playerBottomSheetState.collapsedBound -
                                                    bottomInset - floatingBarsBottomPadding -
                                                    (NavBarRowHeight + NavBarPillHeight) / 2).coerceAtLeast(0.dp)
                                            } else {
                                                0.dp
                                            }

                                        if (!active) {
                                            BottomSheetPlayer(
                                                state = playerBottomSheetState,
                                                navController = navController,
                                                pureBlack = pureBlack,
                                                lyricsSyncOffset = lyricsSyncOffset,
                                                hideMiniPlayer = mergeIntoNavBar,
                                                compactMiniPlayer = useCompactPill,
                                                morphPillHeight = when {
                                                    mergeIntoNavBar -> NavBarPillHeight
                                                    useCompactPill -> CompactMiniPlayerHeight
                                                    else -> MiniPlayerHeight
                                                },
                                                // Both branches carry `safeChromeInset` because
                                                // both targets are padded by it — the nav bar via
                                                // `chromeHorizontalPadding`, the mini-player pill
                                                // via the same helper inside `MiniPlayer`. Drop it
                                                // from either side and the morph lands off-centre
                                                // on a cutout device.
                                                morphPillHorizontalInset = when {
                                                    mergeIntoNavBar ->
                                                        safeChromeInset + NavBarPillSideSlot
                                                    else ->
                                                        safeChromeInset + MiniPlayerPillHorizontalInset
                                                },
                                                morphPillCornerRadius = when {
                                                    mergeIntoNavBar -> NavBarPillCornerRadius
                                                    useCompactPill -> CompactMiniPlayerPillCornerRadius
                                                    else -> MiniPlayerPillCornerRadius
                                                },
                                                morphPillTopOffset = when {
                                                    // The slim pill sits centred in the standard
                                                    // slot, so the morph window has to stop that
                                                    // much short of the top of it.
                                                    useCompactPill -> CompactMiniPlayerTopInset
                                                    else -> morphPillTopOffset
                                                },
                                            )
                                        }

                                        if(useRail) return@Box

                                        val navSlideDistance =
                                            bottomInset + floatingBarsBottomPadding + navVisibleHeight

                                        Box(
                                            modifier =
                                                Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .height(navSlideDistance)
                                                    .offset {
                                                        if (bottomNavigationBarHeight == 0.dp) {
                                                            IntOffset(
                                                                x = 0,
                                                                y = navSlideDistance.roundToPx(),
                                                            )
                                                        } else {
                                                            // Still while it fades, then out of the
                                                            // way once it is invisible: sliding while
                                                            // it was still on screen pulled it apart
                                                            // from the mini player rising above it,
                                                            // which is what read as a glitch.
                                                            val slideOffset =
                                                                navSlideDistance *
                                                                        ((playerBottomSheetState.progress - 0.45f) / 0.55f)
                                                                            .coerceIn(0f, 1f)
                                                            val hideOffset =
                                                                navSlideDistance *
                                                                        (1 - bottomNavigationBarHeight.coerceAtMost(
                                                                            navVisibleHeight
                                                                        ) / navVisibleHeight)
                                                            IntOffset(
                                                                x = 0,
                                                                y = (slideOffset + hideOffset).roundToPx(),
                                                            )
                                                        }
                                                    }
                                                    // Gone by the time the player is half open.
                                                    //
                                                    // Sliding alone is not leaving: the dock only
                                                    // clears the screen at p=1, so through the
                                                    // whole expansion its glass, its tab labels
                                                    // and the mini pill were still being drawn on
                                                    // top of a player that was already three
                                                    // quarters of the way up. Fading it out over
                                                    // the first 45% hands the screen over while
                                                    // the geometry is still moving, which is what
                                                    // makes the player look like it is replacing
                                                    // the bar rather than growing behind it.
                                                    .graphicsLayer {
                                                        val leave = androidx.compose.animation.core.FastOutSlowInEasing.transform(
                                                            (
                                                                playerBottomSheetState.progress
                                                                    .coerceIn(0f, 1f) / 0.45f
                                                                ).coerceIn(0f, 1f),
                                                        )
                                                        alpha = 1f - leave
                                                        // And it is pushed back and down by the
                                                        // player rising over it: smaller about its
                                                        // own foot, sinking a little, and going out
                                                        // of focus — depth, not a wipe. The same
                                                        // curve run backwards brings it up and into
                                                        // focus as the player comes down, for either
                                                        // dock in either state.
                                                        val recede = 1f - 0.08f * leave
                                                        scaleX = recede
                                                        scaleY = recede
                                                        translationY = 22.dp.toPx() * leave
                                                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                                                        val blur = 12.dp.toPx() * leave
                                                        renderEffect =
                                                            if (blur > 0.5f && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                                                androidx.compose.ui.graphics.BlurEffect(blur, blur, androidx.compose.ui.graphics.TileMode.Decal)
                                                            } else {
                                                                null
                                                            }
                                                    },
                                        ) {
                                            if ((isSearchScreen || isSearchResultsRoute) && !active) {
                                                // ---- SEARCH LAYOUT: fixed frosted bar row ----
                                                // Leading circle in its OWN round pill + search
                                                // input in its own capsule; NO A/B morphing. The
                                                // mini-player (BottomSheetPlayer above) floats
                                                // directly over this row.
                                                //
                                                // Used on the Search tab AND on committed result
                                                // pages, so the field stays under the thumb for the
                                                // whole search flow instead of relocating to the
                                                // top bar the moment results arrive.
                                                // Decoded, because the route argument is what
                                                // `URLEncoder.encode` produced when the search was
                                                // committed — and that encodes a space as `+`. The
                                                // field was showing people `Sabrina+carpenter+`
                                                // and then handing the same string back as the
                                                // starting text when they tapped it to refine.
                                                // Navigation percent-decodes `%XX` on the way out
                                                // but leaves `+` alone, so this is the only place
                                                // it can be undone.
                                                val committed = if (isSearchResultsRoute) {
                                                    navBackStackEntry?.arguments
                                                        ?.getString("query")
                                                        ?.takeIf { it.isNotEmpty() }
                                                        ?.let {
                                                            runCatching {
                                                                URLDecoder.decode(it, "UTF-8")
                                                            }.getOrDefault(it)
                                                        }
                                                } else {
                                                    null
                                                }
                                                SearchBottomBar(
                                                    pureBlack = pureBlack,
                                                    placeholder = stringResource(R.string.search),
                                                    committedQuery = committed,
                                                    leadingIsBack = isSearchResultsRoute,
                                                    onHomeClick = {
                                                        if (isSearchResultsRoute) {
                                                            navController.navigateUp()
                                                        } else {
                                                            navController.navigate(Screens.Home.route) {
                                                                popUpTo(navController.graph.startDestinationId) {
                                                                    saveState = true
                                                                }
                                                                launchSingleTop = true
                                                                restoreState = true
                                                            }
                                                        }
                                                    },
                                                    onSearchClick = {
                                                        // Re-opening the field from a results page
                                                        // pre-fills it with the query that produced
                                                        // them, caret at the end, so refining a
                                                        // search is an edit and not a retype.
                                                        if (committed != null) {
                                                            onQueryChange(
                                                                TextFieldValue(
                                                                    text = committed,
                                                                    selection = TextRange(committed.length),
                                                                ),
                                                            )
                                                        }
                                                        onActiveChange(true)
                                                    },
                                                    // The same band the dock occupies on every
                                                    // other route, not a shorter row resting at
                                                    // the bottom of it.
                                                    //
                                                    // The sheet's collapsed bound reserves a full
                                                    // `getBottomNavPadding()` of chrome, and it is
                                                    // captured once — it cannot be re-derived per
                                                    // route without resetting the sheet. So a 48dp
                                                    // row pinned to the bottom of a 64dp reservation
                                                    // left the surplus as a band of dead space
                                                    // *above* it, and the mini player floated over
                                                    // the search field with a visible hole between
                                                    // the two. Filling the band and centring in it
                                                    // splits that surplus in half and puts the row
                                                    // where the eye expects the dock to be.
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(
                                                            start = chromeHorizontalPadding,
                                                            end = chromeHorizontalPadding,
                                                            bottom = bottomInset + floatingBarsBottomPadding,
                                                        )
                                                        .height(navVisibleHeight),
                                                )
                                            } else {
                                            LiquidGlassBottomBar(
                                                items = navigationItems,
                                                pureBlack = pureBlack,
                                                collapsed = bottomBarCollapsed,
                                                hasNowPlaying = nowPlayingMetadata != null,
                                                onMiniPlayerClick = {
                                                    coroutineScope.launch { playerBottomSheetState.expandSoft() }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(
                                                        start = chromeHorizontalPadding,
                                                        end = chromeHorizontalPadding,
                                                        bottom = bottomInset + floatingBarsBottomPadding,
                                                    ),
                                                isSelected = { screen ->
                                                    navBackStackEntry?.destination?.hierarchy?.any { it.route == screen.route } ==
                                                            true
                                                },
                                                onItemClick = { screen, isSelected ->
                                                    if (screen.route == Screens.Search.route && isSelected) {
                                                        // Already on the Search tab: open the type-in field.
                                                        onActiveChange(true)
                                                    } else if (isSelected) {
                                                        navController.currentBackStackEntry?.savedStateHandle?.set(
                                                            "scrollToTop",
                                                            true
                                                        )
                                                        coroutineScope.launch {
                                                            searchBarScrollBehavior.state.resetHeightOffset()
                                                        }
                                                    } else if ((!isTabRoute || screen.route == Screens.Home.route) &&
                                                        navController.popBackStack(screen.route, inclusive = false)
                                                    ) {
                                                        // Home is the start of everything, so it is always
                                                        // underneath: step straight down to it. Switching to it
                                                        // as a tab restored whatever page had last been opened
                                                        // from Home instead, which read as Home not working.
                                                        // From a page above a tab (Settings, an album): step
                                                        // back down to that tab. A tab switch here saved the
                                                        // page as the tab's history and restored it straight
                                                        // back, so Home looked like it did nothing.
                                                    } else {
                                                        // Search navigates like every other tab — it is a
                                                        // peer-level destination in the NavHost, so the top
                                                        // bar (account icon), mini-player and back stack all
                                                        // behave normally on it.
                                                        navController.navigate(screen.route) {
                                                            popUpTo(navController.graph.startDestinationId) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                },
                                            )
                                            }
                                        }
                                    }
                                    }
                                    }
                                },
                                // Scaffold paints the theme's background by default, and that background
                                // is opaque — which put a solid sheet over the moving artwork drawn at the
                                // back of the window, so every page showed the flat colour instead. With the
                                // ambient background on, the pages have to be see-through down to it.
                                containerColor = if (liquidGlassNavBar) Color.Transparent else MaterialTheme.colorScheme.background,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(searchBarScrollBehavior.nestedScrollConnection)
                            ) {
                                var transitionDirection =
                                    AnimatedContentTransitionScope.SlideDirection.Left

                                if (navigationItems.fastAny { it.route == navBackStackEntry?.destination?.route }) {
                                    if (navigationItems.fastAny { it.route == previousTab }) {
                                        val curIndex = navigationItems.indexOf(
                                            navigationItems.fastFirstOrNull {
                                                it.route == navBackStackEntry?.destination?.route
                                            }
                                        )

                                        val prevIndex = navigationItems.indexOf(
                                            navigationItems.fastFirstOrNull {
                                                it.route == previousTab
                                            }
                                        )

                                        if (prevIndex > curIndex)
                                            AnimatedContentTransitionScope.SlideDirection.Right.also {
                                                transitionDirection = it
                                            }
                                    }
                                }

                                NavHost(
                                    navController = navController,
                                    startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                                        NavigationTab.HOME -> Screens.Home
                                        NavigationTab.SEARCH -> Screens.Search
                                        NavigationTab.LIBRARY -> Screens.Library
                                        else -> Screens.Home
                                    }.route,
                                    enterTransition = {
                                        if (
                                            initialState.destination.route in topLevelScreens &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            // A tab switch is a change of subject, not a journey.
                                            //
                                            // This was Material's fade-through: 300ms and a scale
                                            // from 0.92, which on a page of album art reads as the
                                            // whole screen being pushed toward you and takes a
                                            // third of a second to stop moving. Tapping a tab
                                            // should feel like the page was already there. The
                                            // fade is halved and the scale is a hair off 1, so
                                            // there is a breath of motion and nothing to wait for.
                                            TabEnter
                                        } else {
                                        // Push, don't dissolve.
                                        //
                                        // Going *into* a screen and coming back out of it used to be the same
                                        // animation played at two slightly different scales, which is why back
                                        // never felt like anything: a crossfade has no direction, so there was
                                        // nothing for the gesture to undo. These four transitions now form one
                                        // reversible pair. Forward, the new screen comes in from the trailing
                                        // edge and the old one recedes into depth; back, the current screen
                                        // leaves the way it arrived and the one underneath rises back out.
                                        //
                                        // Only one of the two screens ever translates. Screens here are mostly
                                        // transparent over the root surface, so sliding both at once would show
                                        // them through each other for the whole transition — the far page
                                        // recedes on scale instead, which reads as depth and never ghosts.
                                        // iOS push: the page arrives from the full
                                        // width on UIKit's navigation curve.
                                        slideInHorizontally(
                                            initialOffsetX = { it },
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                        )
                                        }
                                    },
                                    exitTransition = {
                                        if (
                                            initialState.destination.route in topLevelScreens &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            // The other half of the pair, equally brief: the old
                                            // tab is gone before the new one has finished arriving,
                                            // so the two are never both legible at once.
                                            TabExit
                                        } else {
                                        // The page being covered. It falls away from the viewer rather than
                                        // sliding, so the incoming screen is the only thing in motion.
                                        // ...while the one it covers slips a third of the way left
                                        // and dims: the parallax is what reads as depth.
                                        slideOutHorizontally(
                                            targetOffsetX = { -(it * PushParallax).toInt() },
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                        ) + fadeOut(
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                            targetAlpha = PushDimAlpha,
                                        )
                                        }
                                    },
                                    popEnterTransition = {
                                        if (
                                            (initialState.destination.route in topLevelScreens ||
                                                    initialState.destination.route?.startsWith("search/") == true) &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            // Same brief pair as the forward tab switch: going
                                            // back to a tab is still just a change of subject.
                                            TabEnter
                                        } else {
                                        // Coming back: the screen underneath rises out of depth, from exactly
                                        // the 0.94 it receded to. The mirror of the exit above, which is what
                                        // makes back feel like an undo rather than another forward step.
                                        slideInHorizontally(
                                            initialOffsetX = { -(it * PushParallax).toInt() },
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                        ) + fadeIn(
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                            initialAlpha = PushDimAlpha,
                                        )
                                        }
                                    },
                                    popExitTransition = {
                                        if (
                                            (initialState.destination.route in topLevelScreens ||
                                                    initialState.destination.route?.startsWith("search/") == true) &&
                                            targetState.destination.route in topLevelScreens
                                        ) {
                                            TabExit
                                        } else {
                                        // The screen you backed out of leaves along the axis it came in on, and
                                        // a little further than it arrived from so it clears the frame cleanly.
                                        // Back: the page slides off to the right, the whole way —
                                        // and under predictive back the gesture drags it, as on iOS.
                                        slideOutHorizontally(
                                            targetOffsetX = { it },
                                            animationSpec = tween(PushMillis, easing = PushEasing),
                                        )
                                        }
                                    },
                                    modifier = Modifier
                                        // The app content is the blur/refraction source for every
                                        // piece of floating chrome: `layerBackdrop` records these
                                        // pixels off-screen so `drawBackdrop` can blur AND
                                        // lens-refract them.
                                        // The opaque fill matters: screens are mostly transparent
                                        // over the root Surface, and refracting transparent pixels
                                        // is what made the glass read as a dark film instead of
                                        // glass. The dock lives in the Scaffold's bottomBar slot —
                                        // a sibling drawn over this — so neither layer is
                                        // re-entrant.
                                        .then(
                                            // Skipped when the ambient liquid background is on:
                                            // those drifting blobs are painted BEHIND this, so an
                                            // opaque fill here would erase them.
                                            if (liquidGlassNavBar) Modifier
                                            else Modifier.background(
                                                if (pureBlack) Color.Black
                                                else MaterialTheme.colorScheme.surface
                                            )
                                        )
                                        // Cached layers on both sides of the recording: the
                                        // outer one keeps a redraw elsewhere in the window (the
                                        // dock animating, a progress tick) from re-recording the
                                        // page; the inner one makes the page a single render node
                                        // the recording replays instead of re-issuing every op.
                                        .graphicsLayer()
                                        .layerBackdrop(appBackdrop)
                                        .graphicsLayer()
                                        .nestedScroll(
                                            if (
                                                navigationItems.fastAny {
                                                    it.route == navBackStackEntry?.destination?.route
                                                } ||
                                                navBackStackEntry?.destination?.route?.startsWith("search/") == true
                                            ) {
                                                searchBarScrollBehavior.nestedScrollConnection
                                            } else {
                                                topAppBarScrollBehavior.nestedScrollConnection
                                            }
                                        )
                                ) {
                                    navigationBuilder(
                                        navController,
                                        topAppBarScrollBehavior,
                                        latestVersionName
                                    )
                                }
                            }
                        }

                        BottomSheetMenu(
                            state = LocalMenuState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        BottomSheetPage(
                            state = LocalBottomSheetPageState.current,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        if (showAccountDialog) {
                            AccountSettingsDialog(
                                navController = navController,
                                onDismiss = { showAccountDialog = false },
                                latestVersionName = latestVersionName
                            )
                        }

                        sharedSong?.let { song ->
                            playerConnection?.let {
                                Dialog(
                                    onDismissRequest = { sharedSong = null },
                                    properties = DialogProperties(usePlatformDefaultWidth = false),
                                ) {
                                    Surface(
                                        modifier = Modifier.padding(24.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = AlertDialogDefaults.containerColor,
                                        tonalElevation = AlertDialogDefaults.TonalElevation,
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            YouTubeSongMenu(
                                                song = song,
                                                navController = navController,
                                                onDismiss = { sharedSong = null },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LaunchedEffect(shouldShowSearchBar, openSearchImmediately) {
                        if (shouldShowSearchBar && openSearchImmediately) {
                            onActiveChange(true)
                            try {
                                delay(100)
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {
                            }
                            openSearchImmediately = false
                        }
                    }

                    // Premium boot animation: a solid brand-color layer with the centered
                    // Exhale logo scale-in, drawn ABOVE every other layer, then crossfading
                    // into the (already composed) Home screen beneath. rememberSaveable keeps
                    // it a cold-start-only moment — rotations/recreations never replay it.
                    var bootSplashDone by rememberSaveable { mutableStateOf(false) }
                    if (!bootSplashDone) {
                        BootSplash(onFinished = { bootSplashDone = true })
                    }

                    // The first run after an update, and only that.
                    //
                    // "Have I seen this build?" is one comparison once the key exists. The awkward
                    // case is the upgrade that introduces the key: someone coming from 1.0.102 has
                    // no stored code, and neither does a fresh install, so the two are
                    // indistinguishable from DataStore alone. PackageManager can tell them apart —
                    // an app that has been updated has a `lastUpdateTime` later than its
                    // `firstInstallTime` — and that is the only thing that check is used for.
                    //
                    // A fresh install deliberately gets nothing. It is already being walked
                    // through Song Preferences, and welcoming someone *back* to an app they have
                    // never opened is worse than not welcoming them.
                    //
                    // The code is written the moment the screen is decided on rather than when it
                    // is dismissed, so a user who kills the app mid-animation is not shown it
                    // again on the next launch. Seeing it once is the promise; seeing all of it is
                    // not.
                    //
                    // `welcomeDecided` is what keeps a rotation from answering the question a
                    // second time. The code has already been written by then, so the second answer
                    // is always "no" — and the screen would vanish mid-animation for anyone who
                    // turned their phone while watching it.
                    var welcomeDecided by rememberSaveable { mutableStateOf(false) }
                    var showWelcome by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (welcomeDecided) return@LaunchedEffect
                        welcomeDecided = true
                        showWelcome = withContext(Dispatchers.IO) {
                            val seen = dataStore[LastSeenVersionCodeKey]
                            val upgraded = if (seen != null) {
                                seen < BuildConfig.VERSION_CODE
                            } else {
                                runCatching {
                                    val info = packageManager.getPackageInfo(packageName, 0)
                                    info.lastUpdateTime > info.firstInstallTime
                                }.getOrDefault(false)
                            }
                            if (seen != BuildConfig.VERSION_CODE) {
                                dataStore.edit { prefs ->
                                    prefs[LastSeenVersionCodeKey] = BuildConfig.VERSION_CODE
                                }
                            }
                            upgraded
                        }
                    }

                    // Above the boot splash, because it replaces it: there is no point crossfading
                    // the mark in and then covering it with a star field a beat later.
                    if (showWelcome) {
                        WelcomeScreen(
                            version = BuildConfig.VERSION_NAME,
                            figure = BuildConfig.VERSION_CODE.toString(),
                            onDismiss = { showWelcome = false },
                        )
                    }
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        if (intent.action == ACTION_DOWNLOAD_QUEUE) {
            navController.navigate(Screens.DownloadQueue.route)
            return
        }

        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val coroutineScope = lifecycleScope

        // A song file handed over by "Open with": play the file itself, as it is.
        if (uri.scheme.equals("content", ignoreCase = true) || uri.scheme.equals("file", ignoreCase = true)) {
            // Kept past this activity where the sender allows it, so the queue survives a restart.
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            coroutineScope.launch {
                val metadata = com.ozyern.exhale.utils.LocalMediaScanner.describeOpened(this@MainActivity, uri)
                pendingDeepLinkSong = PendingDeepLinkSong(mediaItem = metadata.toMediaItem())
                startMusicServiceSafely()
                playPendingDeepLinkSongIfReady()
            }
            return
        }

        val authority = uri.authority?.lowercase()
        if (uri.scheme.equals("exhale", ignoreCase = true) && authority == "together") {
            togetherInviteToConfirm = uri.toString()
            return
        }

        if (uri.scheme.equals("exhale", ignoreCase = true) && authority == "login") {
            navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                navController.navigate("album/$browseId")
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            else -> {
                val videoId = when {
                    path == "watch" -> uri.getQueryParameter("v")
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    else -> null
                }

                val playlistId = uri.getQueryParameter("list")

                videoId?.let { vid ->
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            YouTube.queue(listOf(vid), playlistId)
                        }

                        result.onSuccess { queued ->
                            val mediaItem =
                                queued.firstOrNull { it.id == vid }?.toMediaItem()
                                    ?: queued.firstOrNull()?.toMediaItem()
                                    ?: MediaItem
                                        .Builder()
                                        .setMediaId(vid)
                                        .setUri(vid)
                                        .setCustomCacheKey(vid)
                                        .build()
                            pendingDeepLinkSong =
                                PendingDeepLinkSong(
                                    mediaItem = mediaItem,
                                )
                            startMusicServiceSafely()
                            playPendingDeepLinkSongIfReady()
                        }.onFailure {
                            reportException(it)
                        }
                    }
                }
            }
        }
    }

    private fun startMusicServiceSafely() {
        runCatching { startService(Intent(this, MusicService::class.java)) }
            .onFailure { reportException(it) }
    }

    @SuppressLint("ObsoleteSdkInt")
    private fun setSystemBarAppearance(isDark: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView.rootView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            window.statusBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            window.navigationBarColor =
                (if (isDark) Color.Transparent else Color.Black.copy(alpha = 0.2f)).toArgb()
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.ozyern.exhale.action.SEARCH"
        const val ACTION_LIBRARY = "com.ozyern.exhale.action.LIBRARY"
        const val ACTION_DOWNLOAD_QUEUE = "com.ozyern.exhale.action.DOWNLOAD_QUEUE"
    }
}

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }

/** UIKit's push timing and curve, and the covered page's parallax and dim. */
/**
 * Tab switches: the new page fades up and rises about 13dp into place on a soft spring, after the old
 * one has already gone. A change of subject, not a journey — so no horizontal travel, and no zoom
 * of the whole page, which on a screen of album art read as the page being shoved at you.
 */
private val TabEnter = fadeIn(tween(200, delayMillis = 40, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) +
    // A sixtieth of the page's height: about 13dp on a phone.
    slideInVertically(spring(dampingRatio = 0.86f, stiffness = 520f)) { it / 60 }
private val TabExit = fadeOut(tween(90, easing = androidx.compose.animation.core.FastOutLinearInEasing))

private const val PushMillis = 340
private val PushEasing = androidx.compose.animation.core.CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
private const val PushParallax = 0.30f
private const val PushDimAlpha = 0.85f
