/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import com.ozyern.exhale.desktop.ui.library.LibrarySectionScreen
import com.ozyern.exhale.desktop.ui.library.LibrarySection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.ContentLocale
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.ui.account.DiscordLoginWindow
import com.ozyern.exhale.desktop.ui.account.SignInWindow
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.PageBackdropHost
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.HomeFeed
import com.ozyern.exhale.desktop.ui.home.HomeScreen
import com.ozyern.exhale.desktop.ui.library.BrowseScreen
import com.ozyern.exhale.desktop.ui.library.LibraryScreen
import com.ozyern.exhale.desktop.ui.library.MoodAndGenresScreen
import com.ozyern.exhale.desktop.ui.onboarding.OnboardingScreen
import com.ozyern.exhale.desktop.ui.pages.AlbumScreen
import com.ozyern.exhale.desktop.ui.pages.ArtistItemsScreen
import com.ozyern.exhale.desktop.ui.pages.ArtistScreen
import com.ozyern.exhale.desktop.ui.pages.FlacReleaseScreen
import com.ozyern.exhale.desktop.ui.pages.FlacScreen
import com.ozyern.exhale.desktop.ui.pages.PlaylistScreen
import com.ozyern.exhale.desktop.ui.pages.SoundChemScreen
import com.ozyern.exhale.desktop.ui.player.FullPlayer
import com.ozyern.exhale.desktop.ui.player.LocalPlayerReveal
import com.ozyern.exhale.desktop.ui.player.PlayerPanel
import com.ozyern.exhale.desktop.ui.player.VideoSurfaceHost
import com.ozyern.exhale.desktop.ui.search.SearchModel
import com.ozyern.exhale.desktop.ui.search.SearchScreen
import com.ozyern.exhale.desktop.ui.settings.SettingsScreen
import com.ozyern.exhale.desktop.ui.shell.ChromeNav
import com.ozyern.exhale.desktop.ui.shell.AppSidebar
import com.ozyern.exhale.desktop.ui.shell.PlayerBar
import com.ozyern.exhale.desktop.ui.shell.PlayerBarHeight
import com.ozyern.exhale.desktop.ui.shell.SidebarItem
import com.ozyern.exhale.desktop.ui.shell.SidebarWidth
import com.ozyern.exhale.desktop.ui.shell.DockHeight
import com.ozyern.exhale.desktop.ui.shell.DockMaxWidth
import com.ozyern.exhale.desktop.ui.shell.DockTab
import com.ozyern.exhale.desktop.ui.shell.LiquidDock
import com.ozyern.exhale.desktop.ui.shell.LocalTitleBarHeight
import com.ozyern.exhale.desktop.ui.shell.MiniPlayerPill
import com.ozyern.exhale.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/** Window-wide keys (media keys, Ctrl+F…), handed over from the Window before any field sees them. */
object AppShortcuts {
    @Volatile var handler: ((KeyEvent) -> Boolean)? = null

    /** A page's own shortcuts (Settings' Ctrl+F), tried before the app-wide ones. */
    @Volatile var overlay: ((KeyEvent) -> Boolean)? = null

    /**
     * Plain keys — Space, N, M — for when nothing on screen has used them. A text field doesn't consume
     * the keys it types on this platform, so fields say when they hold focus and these stand down.
     */
    @Volatile var plain: ((KeyEvent) -> Boolean)? = null

    private val fieldsFocused = java.util.concurrent.atomic.AtomicInteger(0)

    /** True while any text field has focus: what's typed there is text, never a shortcut. */
    val typing: Boolean get() = fieldsFocused.get() > 0

    /**
     * Called by every text field as focus comes and goes. Counted rather than set, because focus moving
     * from one field to another arrives as the new field gaining before the old one loses.
     */
    fun setTyping(focused: Boolean) {
        if (focused) fieldsFocused.incrementAndGet() else fieldsFocused.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    fun dispatch(event: KeyEvent): Boolean = overlay?.invoke(event) == true || handler?.invoke(event) == true

    fun dispatchPlain(event: KeyEvent): Boolean = !typing && plain?.invoke(event) == true
}

/**
 * Marks a text field: while it (or anything inside it) has focus, the app-wide plain-key shortcuts
 * stand down, so a space typed into a search box is a space rather than a pause.
 */
fun Modifier.typingField(): Modifier = onFocusChanged { AppShortcuts.setTyping(it.hasFocus) }

/** The main window's state, so F11 and full-screen lyrics can take the whole screen. */
val LocalWindowState = staticCompositionLocalOf<WindowState?> { null }

private sealed class Route(val key: String) {
    data object Home : Route("home")
    data object Mood : Route("mood")
    data object Library : Route("library")
    data object Search : Route("search")
    data object Settings : Route("settings")
    data class Browse(val browseId: String, val params: String?, val title: String) : Route("browse:$browseId:$params")

    // What a page already knows (title, cover) rides along so its header draws before it loads; the
    // shared key is the card the cover flies out of.
    data class Artist(val browseId: String, val name: String?, val thumbnail: String?) : Route("artist:$browseId")
    data class Album(val browseId: String, val title: String?, val thumbnail: String?, val shared: String?) : Route("album:$browseId")
    data class Playlist(val playlistId: String, val title: String?, val thumbnail: String?, val shared: String?) : Route("playlist:$playlistId")
    data class ArtistItems(val browseId: String, val params: String?, val title: String) : Route("artist-items:$browseId:$params")
    data object SoundChem : Route("sound-chem")
    /** A page behind one of the sidebar's library rows: New, Radio, Songs, Albums, All Playlists… */
    data class Collection(val section: LibrarySection) : Route("collection:${section.name}")
    data object Flac : Route("flac")
    data class FlacRelease(val releaseKey: String) : Route("flac:$releaseKey")
}

/** Which way the page stack moved, so the transition knows whether to fade through, push or pop. */
private enum class NavKind { Tab, Push, Pop }

private val TabRoots = listOf(Route.Home, Route.Mood, Route.Library)

/** Windows at least this wide get the desktop dock; narrower ones keep the Android dock. */
private val WideDockMinWidth = 1040.dp

/** ColorOS 17's page springs: a hair under critical, so a page lands soft with no visible bounce. */
private val ColorOsPageSpring = spring<Float>(dampingRatio = 0.92f, stiffness = 340f)
private val ColorOsSlideSpring = spring<IntOffset>(dampingRatio = 0.92f, stiffness = 340f, visibilityThreshold = IntOffset(1, 1))

private fun initialRoute(): Route = when (AppSettings.current.defaultTab.uppercase()) {
    "MOOD", "NEW", "RADIO" -> Route.Mood
    "LIBRARY", "RECENT", "ARTISTS" -> Route.Library
    "SEARCH" -> Route.Search
    else -> Route.Home
}

/** The morph's shape clock: a smoothstep, so the island leaves the pill and lands on the window gently. */
private fun morphEase(t: Float): Float = t * t * (3f - 2f * t)

/** The share of the open where the pill's glass hands over to the player's own surface. */
private const val MorphHandoff = 0.16f

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExhaleApp(player: DesktopPlayer) {
    val scope = rememberCoroutineScope()
    val state by player.state.collectAsState()
    val artists by DesktopPrefs.artists.collectAsState()
    val history by DesktopPrefs.history.collectAsState()
    val settings = LocalSettings.current
    val focusManager = LocalFocusManager.current
    val home = remember { HomeFeed(scope) }
    val search = remember { SearchModel(scope) }
    val searchFocus = remember { FocusRequester() }

    var stack by remember { mutableStateOf(listOf(initialRoute())) }
    // The Library section the sidebar last opened: Recently Played, Artists or Stations.
    var libraryTab by remember { mutableIntStateOf(0) }
    var navKind by remember { mutableStateOf(NavKind.Tab) }
    var onboarding by remember { mutableStateOf(!DesktopPrefs.onboardingCompleted) }
    var editingTaste by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    var discordLogin by remember { mutableStateOf(false) }

    fun selectRoot(root: Route) {
        UiMotion.poke(700)
        if (stack.size == 1 && stack[0] == root) return
        // Tapping the tab you're already under goes back to its top, like Android.
        navKind = if (stack.first() == root) NavKind.Pop else NavKind.Tab
        stack = listOf(root)
    }

    fun push(route: Route) {
        UiMotion.poke(700)
        if (stack.last().key == route.key) return
        navKind = NavKind.Push
        stack = stack + route
    }

    fun pop() {
        UiMotion.poke(700)
        if (stack.size <= 1) return
        navKind = NavKind.Pop
        stack = stack.dropLast(1)
    }

    fun reloadHome(force: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { ContentLocale.ensureVisitorData() }
            home.load(DesktopPrefs.artists.value, force)
        }
    }

    LaunchedEffect(Unit) {
        if (DesktopPrefs.onboardingCompleted) reloadHome(force = false)
        AccountSession.refreshAccountInfo()
    }

    if (signingIn) {
        SignInWindow(
            onClose = { signingIn = false },
            onSignedIn = {
                signingIn = false
                reloadHome(force = true)
            },
        )
    }

    if (discordLogin) DiscordLoginWindow(onClose = { discordLogin = false })

    if (onboarding || editingTaste) {
        SideEffect { ChromeNav.back = if (editingTaste) ({ editingTaste = false }) else null }
        Box(Modifier.fillMaxSize().padding(top = LocalTitleBarHeight.current)) {
        OnboardingScreen(
            initialLanguages = DesktopPrefs.languages,
            initialArtists = artists,
            onFinish = { languages, picks ->
                DesktopPrefs.languages = languages
                DesktopPrefs.setArtists(picks)
                DesktopPrefs.onboardingCompleted = true
                ContentLocale.apply(languages)
                onboarding = false
                editingTaste = false
                navKind = NavKind.Tab
                stack = listOf(Route.Home)
                reloadHome(force = true)
            },
            onCancel = if (editingTaste) ({ editingTaste = false }) else null,
        )
        }
        return
    }

    // The room is lit by what's playing, or by the first thing on Home before anything is.
    val ambientUrl = state.current?.thumbnail ?: home.topPicks.firstOrNull()?.image ?: history.firstOrNull()?.thumbnail
    val ambientColors by rememberArtworkColors(ambientUrl?.resizedThumbnail(120))
    // Appearance → Colour from artwork follows the song that's actually playing.
    LaunchedEffect(state.current?.id, ambientColors) {
        ThemeSeed.artwork.value = if (state.current != null) ambientColors.firstOrNull() else null
    }

    val hasSong = state.current != null
    val appBackdrop = rememberLayerBackdrop()
    val saveable = rememberSaveableStateHolder()
    val density = LocalDensity.current

    // The dock folds while you scroll down through a page and opens again the moment you scroll back.
    var dockCollapsed by remember { mutableStateOf(false) }
    val collapseThreshold = with(density) { 28.dp.toPx() }
    val dockScroll = remember(collapseThreshold) {
        object : NestedScrollConnection {
            private var travel = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Shelves scrolling sideways count as much as the page scrolling down.
                if (consumed != Offset.Zero) UiMotion.poke()
                if (available.y > 0f) dockCollapsed = false
                val dy = consumed.y
                if (dy == 0f) return Offset.Zero
                if (sign(dy) != sign(travel)) travel = 0f
                travel += dy
                if (travel < -collapseThreshold) dockCollapsed = true
                else if (travel > collapseThreshold) dockCollapsed = false
                return Offset.Zero
            }
        }
    }
    val route = stack.last()
    LaunchedEffect(route) { dockCollapsed = false }

    // The player grows out of the dock (the Android Dynamic Island morph) and shrinks back into it;
    // on wide windows the cover flies between the dock and the player as it does.
    var playerOpen by remember { mutableStateOf(false) }
    var requestedPanel by remember { mutableStateOf(PlayerPanel.None) }
    var pillBounds by remember { mutableStateOf<Rect?>(null) }
    var islandBounds by remember { mutableStateOf<Rect?>(null) }
    var dockArtBounds by remember { mutableStateOf<Rect?>(null) }
    var playerArtBounds by remember { mutableStateOf<Rect?>(null) }
    var rootHeight by remember { mutableFloatStateOf(1f) }
    val morph = remember { Animatable(0f) }
    LaunchedEffect(playerOpen) {
        val reduced = AppSettings.current.reducedMotion
        if (playerOpen) morph.animateTo(1f, if (reduced) tween(160) else PlayerOpenSpring)
        else morph.animateTo(0f, if (reduced) tween(140) else PlayerCloseSpring)
    }
    LaunchedEffect(hasSong) { if (!hasSong) playerOpen = false }
    val playerShown by remember { derivedStateOf { playerOpen || morph.value > 0.001f } }
    // The player fills the window: whatever animates behind it can stop.
    val playerCovering by remember { derivedStateOf { morph.value >= 0.999f } }

    fun openPlayer(panel: PlayerPanel) {
        requestedPanel = panel
        playerOpen = true
    }




    // Opening a page from inside the player (an artist's name under the title) folds the player away first.
    val navigator = remember {
        AppNavigator(
            openArtist = { id, name, thumbnail ->
                playerOpen = false
                push(Route.Artist(id, name, thumbnail))
            },
            openAlbum = { id, title, thumbnail ->
                playerOpen = false
                push(Route.Album(id, title, thumbnail, SharedArt.take()))
            },
            openPlaylist = { id, title, thumbnail ->
                playerOpen = false
                push(Route.Playlist(id, title, thumbnail, SharedArt.take()))
            },
            openArtistItems = { endpoint, title -> push(Route.ArtistItems(endpoint.browseId, endpoint.params, title)) },
            openSoundChem = {
                playerOpen = false
                push(Route.SoundChem)
            },
            openFlac = {
                playerOpen = false
                push(Route.Flac)
            },
            openFlacRelease = { key ->
                playerOpen = false
                push(Route.FlacRelease(key))
            },
            openBrowse = { browseId, params, title ->
                playerOpen = false
                push(Route.Browse(browseId, params, title))
            },
        )
    }

    // Development aid: EXHALE_OPEN="album:<search>" (or artist: / playlist:) opens the first match's
    // page on launch, so a page can be checked without clicking through to it.
    LaunchedEffect(Unit) {
        val spec = System.getenv("EXHALE_OPEN")?.takeIf { ':' in it } ?: return@LaunchedEffect
        val filter = when (spec.substringBefore(':')) {
            "artist" -> YouTube.SearchFilter.FILTER_ARTIST
            "album" -> YouTube.SearchFilter.FILTER_ALBUM
            "playlist" -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
            else -> return@LaunchedEffect
        }
        val item = withContext(Dispatchers.IO) { YouTube.search(spec.substringAfter(':'), filter).getOrNull()?.items?.firstOrNull() }
        if (item != null) navigator.open(item, player)
    }

    BenchRunner(
        openArtist = {
            val artist = DesktopPrefs.artists.value.firstOrNull()
            if (artist != null) navigator.openArtist(artist.id, artist.name, artist.thumbnail)
        },
        back = { pop() },
        openPlayer = { openPlayer(PlayerPanel.None) },
        closePlayer = { playerOpen = false },
        // The sidebar's pages, in its order, for EXHALE_BENCH_MODE=pages.
        pages = listOf(
            "Home" to { selectRoot(Route.Home) },
            "New" to { selectRoot(Route.Collection(LibrarySection.New)) },
            "Radio" to { selectRoot(Route.Collection(LibrarySection.Radio)) },
            "Artists" to { selectRoot(Route.Collection(LibrarySection.Artists)) },
            "Songs" to { selectRoot(Route.Collection(LibrarySection.Songs)) },
            "FLAC" to { selectRoot(Route.Flac) },
            "Settings" to { selectRoot(Route.Settings) },
            "Search" to { selectRoot(Route.Search) },
        ),
    )

    val tabs = remember { listOf(DockTab("Home", DockIcons.Home, DockIcons.HomeSelected), DockTab("Mood & Genres", DockIcons.Explore, DockIcons.ExploreSelected), DockTab("Library", DockIcons.Library, DockIcons.LibrarySelected)) }

    // The title bar's back button: close the player first, then pages.
    SideEffect {
        ChromeNav.back = when {
            playerOpen -> ({ playerOpen = false })
            stack.size > 1 -> ({ pop() })
            else -> null
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
    BoxWithConstraints(Modifier.fillMaxSize().onSizeChanged { rootHeight = it.height.toFloat().coerceAtLeast(1f) }) {
        val wide = maxWidth >= WideDockMinWidth
        val latestWide by rememberUpdatedState(wide)

        fun openSearch() {
            selectRoot(Route.Search)
            if (latestWide) runCatching { searchFocus.requestFocus() }
        }

        val windowState = LocalWindowState.current
        DisposableEffect(Unit) {
            AppShortcuts.handler = handler@{ event ->
                if (event.type != KeyEventType.KeyDown) return@handler false
                when {
                    event.key == Key.F11 && windowState != null -> {
                        windowState.placement =
                            if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                        true
                    }
                    event.key == Key.MediaPlayPause -> { player.togglePlayPause(); true }
                    event.key == Key.MediaNext -> { player.next(); true }
                    event.key == Key.MediaPrevious -> { player.previous(); true }
                    event.isCtrlPressed && (event.key == Key.F || event.key == Key.K) -> { openSearch(); true }
                    event.isCtrlPressed && event.key == Key.P -> { player.togglePlayPause(); true }
                    event.isCtrlPressed && event.key == Key.L && player.state.value.current != null -> { openPlayer(PlayerPanel.Lyrics); true }
                    else -> false
                }
            }
            // Reached only when no field wanted the key, so these need no modifier.
            AppShortcuts.plain = plain@{ event ->
                if (event.type != KeyEventType.KeyDown) return@plain false
                val hasSong = player.state.value.current != null
                when (event.key) {
                    Key.Spacebar, Key.K -> if (hasSong) { player.togglePlayPause(); true } else false
                    Key.N -> if (hasSong) { player.next(); true } else false
                    Key.P -> if (hasSong) { player.previous(); true } else false
                    Key.DirectionRight -> if (hasSong) { player.seekBy(AppSettings.current.seekStepSeconds.toDouble()); true } else false
                    Key.DirectionLeft -> if (hasSong) { player.seekBy(-AppSettings.current.seekStepSeconds.toDouble()); true } else false
                    Key.L -> if (hasSong) { openPlayer(PlayerPanel.Lyrics); true } else false
                    Key.Q -> if (hasSong) { openPlayer(PlayerPanel.Queue); true } else false
                    Key.M -> { player.toggleMute(); true }
                    Key.S -> { player.toggleShuffle(); true }
                    Key.Equals, Key.Plus -> { player.setVolume((player.state.value.volume + 5).coerceAtMost(100)); true }
                    Key.Minus -> { player.setVolume((player.state.value.volume - 5).coerceAtLeast(0)); true }
                    Key.Slash -> { openSearch(); true }
                    else -> false
                }
            }
            onDispose {
                AppShortcuts.handler = null
                AppShortcuts.plain = null
            }
        }

        // Content clears the dock — and on narrow windows the mini player stacked above it too.
        val insets = if (wide) {
            ChromeInsets(start = SidebarWidth + 8.dp, bottom = PlayerBarHeight + 40.dp)
        } else {
            ChromeInsets(start = 20.dp, bottom = DockHeight + 28.dp + if (hasSong) DockHeight + 10.dp else 0.dp)
        }

        // The heavier pages, each drawn once in the first half-minute after launch, underneath the page
        // where nothing of them shows and no click reaches them. A page's first draw — its code compiled,
        // its icons parsed and rasterised, its text shaped for the first time — cost a 100–400 ms freeze
        // on its first open; this pays it while nobody is waiting, one page at a time, and only while
        // nothing is scrolling or changing page.
        var warming by remember { mutableStateOf<Route?>(null) }
        LaunchedEffect(Unit) {
            delay(6_000)
            val pages = listOf(
                Route.Settings,
                Route.Collection(LibrarySection.New),
                Route.Search,
                Route.Collection(LibrarySection.Radio),
                Route.Flac,
                Route.Collection(LibrarySection.Songs),
                Route.Collection(LibrarySection.Artists),
            )
            for (page in pages) {
                while (UiMotion.busy) delay(400)
                warming = page
                delay(700)
                warming = null
                delay(1_500)
            }
        }
        warming?.takeIf { it != route }?.let { page ->
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    Route.Settings -> SettingsScreen(insets = insets, onBack = {}, onSignIn = {}, onEditTaste = {}, onHomeInputsChanged = {}, onDiscordLogin = {})
                    Route.Search -> SearchScreen(remember { SearchModel(scope) }, player, state, insets, showField = false)
                    Route.Flac -> FlacScreen(insets, onBack = null)
                    is Route.Collection -> LibrarySectionScreen(page.section, home, player, state, insets)
                    else -> Unit
                }
            }
        }

        // Everything the chrome floats over is recorded here, so the dock and the pill bend real
        // pixels. They are siblings drawn *after* this box, never inside it.
        Box(
            Modifier
                .fillMaxSize()
                // Once the player covers the window, the page under it isn't drawn at all: no glass, no
                // moving background, nothing redrawn behind a player that hides it anyway.
                .drawWithContent { if (morph.value < 0.999f) drawContent() }
                .then(if (Bench.off("backdrop")) Modifier else Modifier.layerBackdrop(appBackdrop)),
        ) {
            PageBackdropHost(
                modifier = Modifier.fillMaxSize(),
                background = {
                    val ground = MaterialTheme.colorScheme.background
                    Box(Modifier.fillMaxSize().background(ground))
                    if (settings.animatedBackground && settings.ambientGlow && fluidBackgroundAvailable && hasSong && !Bench.off("fluid")) {
                        // What's playing, moving slowly under every page — and under the glass, which bends it.
                        FluidArtwork(state.current?.thumbnail?.resizedThumbnail(120), Modifier.fillMaxSize(), brightness = 0.5f, paused = playerCovering)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(ground.copy(alpha = 0.3f), ground.copy(alpha = 0.72f)))))
                    } else {
                        AmbientGlow(ambientColors, Modifier.fillMaxSize(), paused = playerCovering)
                    }
                },
            ) {
                // Pages share one transition scope, so a card's cover can fly into the page it opens.
                SharedTransitionLayout(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = route,
                    contentKey = { it.key },
                    transitionSpec = {
                        if (AppSettings.current.reducedMotion) {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                        } else {
                            // ColorOS 17's page motion, sized for a pointer: both pages move at once and from the
                            // first frame — nothing waits for the old page to leave — on springs that land soft,
                            // with the page behind receding a little and dimming rather than just vanishing.
                            when (navKind) {
                                // Switching sections: the new one grows up out of 94% as it fades in, the old one
                                // sinks back to 96% and fades under it.
                                NavKind.Tab -> (fadeIn(tween(180, easing = LinearOutSlowInEasing)) + scaleIn(ColorOsPageSpring, initialScale = 0.94f)) togetherWith
                                    (fadeOut(tween(140)) + scaleOut(ColorOsPageSpring, targetScale = 0.96f))
                                // Opening a page: it comes in from the right with a little growth, the page under it
                                // drifts left a third as far and recedes — the parallax that makes it read as depth.
                                NavKind.Push -> ((slideInHorizontally(ColorOsSlideSpring) { (it * 0.16f).toInt() } + scaleIn(ColorOsPageSpring, initialScale = 0.97f) + fadeIn(tween(160))) togetherWith
                                    (slideOutHorizontally(ColorOsSlideSpring) { -(it * 0.05f).toInt() } + scaleOut(ColorOsPageSpring, targetScale = 0.95f) + fadeOut(tween(220)))).apply { targetContentZIndex = 1f }
                                // Going back: the same, reversed — the page underneath comes forward out of its depth.
                                NavKind.Pop -> ((slideInHorizontally(ColorOsSlideSpring) { -(it * 0.05f).toInt() } + scaleIn(ColorOsPageSpring, initialScale = 0.95f) + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(ColorOsSlideSpring) { (it * 0.16f).toInt() } + scaleOut(ColorOsPageSpring, targetScale = 0.97f) + fadeOut(tween(160)))).apply { targetContentZIndex = -1f }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(top = LocalTitleBarHeight.current).nestedScroll(dockScroll),
                    label = "route",
                ) { current ->
                    CompositionLocalProvider(LocalSharedScopes provides SharedScopes(this@SharedTransitionLayout, this@AnimatedContent)) {
                    saveable.SaveableStateProvider(current.key) {
                        when (current) {
                            Route.Home -> HomeScreen(
                                feed = home,
                                player = player,
                                state = state,
                                insets = insets,
                                onRetry = { reloadHome(force = true) },
                                onOpenSettings = { push(Route.Settings) },
                                onSignIn = { signingIn = true },
                                sidebar = wide,
                            )
                            Route.Mood -> MoodAndGenresScreen(insets) { browseId, params, title -> push(Route.Browse(browseId, params, title)) }
                            Route.Library -> LibraryScreen(
                                home, player, state, insets,
                                onOpenSettings = { push(Route.Settings) },
                                initialTab = libraryTab,
                                onTabChange = { libraryTab = it },
                            )
                            // The sidebar has no field of its own: on a wide window the page's field is the one you type in.
                            Route.Search -> SearchScreen(search, player, state, insets, showField = true)
                            Route.Settings -> SettingsScreen(
                                insets = insets,
                                onBack = { pop() },
                                onSignIn = { signingIn = true },
                                onEditTaste = { editingTaste = true },
                                onHomeInputsChanged = { reloadHome(force = true) },
                                onDiscordLogin = { discordLogin = true },
                            )
                            is Route.Browse -> BrowseScreen(current.browseId, current.params, current.title, player, state, insets, onBack = { pop() })
                            is Route.Artist -> ArtistScreen(current.browseId, current.name, current.thumbnail, player, state, insets, onBack = { pop() })
                            is Route.Album -> AlbumScreen(current.browseId, current.title, current.thumbnail, current.shared, player, state, insets, onBack = { pop() })
                            is Route.Playlist -> PlaylistScreen(current.playlistId, current.title, current.thumbnail, current.shared, player, state, insets, onBack = { pop() })
                            is Route.ArtistItems -> ArtistItemsScreen(current.browseId, current.params, current.title, player, state, insets, onBack = { pop() })
                            is Route.Collection -> LibrarySectionScreen(current.section, home, player, state, insets)
                            Route.SoundChem -> SoundChemScreen(player, state, insets, onBack = if (stack.size > 1) ({ pop() }) else null)
                            Route.Flac -> FlacScreen(insets, onBack = if (stack.size > 1) ({ pop() }) else null)
                            is Route.FlacRelease -> FlacReleaseScreen(current.releaseKey, player, state, insets, onBack = { pop() })
                        }
                    }
                    }
                }
                }
            }
        }

        CompositionLocalProvider(LocalAppBackdrop provides appBackdrop) {
            if (wide) {
                fun itemFor(r: Route): SidebarItem? = when (r) {
                    Route.Search -> SidebarItem.Search
                    Route.Home -> SidebarItem.Home
                    Route.Settings -> SidebarItem.Settings
                    Route.Flac, is Route.FlacRelease -> SidebarItem.Flac
                    is Route.Collection -> SidebarItem.entries.firstOrNull { it.section == r.section }
                    else -> null
                }
                val sidebarItem = itemFor(route) ?: itemFor(stack.first())
                AppSidebar(
                    selected = sidebarItem,
                    selectedPlaylist = (route as? Route.Playlist)?.playlistId,
                    onSelect = { item ->
                        focusManager.clearFocus()
                        playerOpen = false
                        val section = item.section
                        when {
                            section != null -> selectRoot(Route.Collection(section))
                            item == SidebarItem.Search -> selectRoot(Route.Search)
                            item == SidebarItem.Home -> selectRoot(Route.Home)
                            item == SidebarItem.Flac -> selectRoot(Route.Flac)
                            item == SidebarItem.Settings -> selectRoot(Route.Settings)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight(),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // Fades as the player grows out of it, and returns as the player folds away.
                        .drawWithContent { if (morph.value < 0.3f) drawContent() }
                        .graphicsLayer { alpha = 1f - (morph.value / 0.3f).coerceIn(0f, 1f) }
                        .padding(start = SidebarWidth + 24.dp, end = 24.dp, bottom = 16.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    PlayerBar(
                        state = state,
                        player = player,
                        onExpandPlayer = { panel -> if (hasSong) openPlayer(panel) },
                        onBarBounds = { islandBounds = it },
                        onArtBounds = { dockArtBounds = it },
                        artAlpha = { if (morph.value > 0.001f) 0f else 1f },
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    )
                }
            } else {
                val handoff: () -> Float = { 1f - (morph.value / MorphHandoff).coerceIn(0f, 1f) }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = DockMaxWidth)
                        .fillMaxWidth()
                        .drawWithContent { if (morph.value < 0.3f) drawContent() }
                        .graphicsLayer { alpha = 1f - (morph.value / 0.3f).coerceIn(0f, 1f) }
                        .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // State A's pill rides above the dock; scrolled, it moves into the dock itself.
                    AnimatedVisibility(
                        visible = hasSong && !dockCollapsed,
                        enter = fadeIn(tween(190, easing = LinearEasing)) +
                            expandVertically(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness), expandFrom = Alignment.Bottom) +
                            scaleIn(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
                        exit = fadeOut(tween(190, easing = LinearEasing)) +
                            shrinkVertically(spring(1f, 420f), shrinkTowards = Alignment.Bottom) +
                            scaleOut(tween(190), targetScale = 0.9f, transformOrigin = TransformOrigin(0.5f, 1f)),
                    ) {
                        MiniPlayerPill(
                            state = state,
                            player = player,
                            onExpand = { openPlayer(PlayerPanel.None) },
                            onBounds = { pillBounds = it },
                            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = handoff() },
                        )
                    }
                    LiquidDock(
                        tabs = tabs,
                        selected = TabRoots.indexOf(stack.first()),
                        searchSelected = stack.first() == Route.Search,
                        collapsed = dockCollapsed,
                        state = state,
                        player = player,
                        onTab = { selectRoot(TabRoots[it]) },
                        onSearch = { selectRoot(Route.Search) },
                        onExpandPlayer = { openPlayer(PlayerPanel.None) },
                        onPillBounds = { pillBounds = it },
                    )
                }
            }
        }

        if (hasSong) {
            // The page recedes behind the player as it opens.
            if (playerShown) Box(Modifier.fillMaxSize().graphicsLayer { alpha = morph.value.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.5f)))
            // The player stays composed for as long as there's a song: building it at the start of the
            // morph, and tearing it down at the end of the fold, were the hitches either side of every
            // open. Closed, it isn't drawn and sits off the window, where no click can reach it.
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { if (playerShown) IntOffset.Zero else IntOffset(0, rootHeight.toInt() * 4) }
                    .drawWithContent { if (playerShown) drawContent() },
            ) {
            PlayerMorph(progress = { morph.value }, origin = { if (latestWide) islandBounds else pillBounds }) {
                FullPlayer(
                    state = state,
                    player = player,
                    onClose = { playerOpen = false },
                    onDrag = { dy -> scope.launch { morph.snapTo((morph.value - dy / rootHeight * 1.4f).coerceIn(0f, 1f)) } },
                    onDragEnd = {
                        if (morph.value < 0.8f) {
                            playerOpen = false
                            // Already false after an earlier drag? Then the effect won't rerun; finish it here.
                            scope.launch { morph.animateTo(0f, PlayerCloseSpring) }
                        } else {
                            scope.launch { morph.animateTo(1f, PlayerOpenSpring) }
                        }
                    },
                    initialPanel = requestedPanel,
                    onArtworkBounds = { playerArtBounds = it },
                    artworkAlpha = { if (latestWide && dockArtBounds != null && morph.value < 0.999f) 0f else 1f },
                    active = playerShown,
                )
            }
            }
            if (wide && playerShown) {
                FlyingArtwork(
                    url = state.current!!.thumbnail,
                    from = { dockArtBounds },
                    to = { playerArtBounds },
                    progress = { morph.value },
                )
            }
        }

        // Watching outlives the screen that started it. The picture's window belongs to the app, not
        // to the player: fold the player away and it shrinks to a speck and keeps playing, rather than
        // being destroyed and having to start the song over to come back.
        val watching by player.videoMode.collectAsState()
        if (watching) VideoSurfaceHost(player)
    }
    }
}

/** Opening: settles without a bounce, quick off the mark. Closing: stiffer, so it gets out of the way. */
private val PlayerOpenSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 210f)
private val PlayerCloseSpring = spring<Float>(dampingRatio = 1f, stiffness = 330f)

/**
 * The Android DynamicIslandMorphShape, for a window: a rounded rect that grows out of the dock's
 * now-playing island to the whole window. The spring already eases it, so the geometry follows the
 * progress directly — width a touch ahead of height, so the island widens into a card and then rises
 * to fill the window — and the corners relax from the island's radius as it goes. The player's own
 * background is inside the shape from the start; its sections arrive in order through
 * [LocalPlayerReveal] (see FullPlayer).
 */
@Composable
private fun PlayerMorph(progress: () -> Float, origin: () -> Rect?, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val fallbackWidth = with(density) { 440.dp.toPx() }
    val fallbackHeight = with(density) { DockHeight.toPx() }
    val fallbackBottom = with(density) { 20.dp.toPx() }
    val minCorner = with(density) { 28.dp.toPx() }

    fun originIn(width: Float, height: Float): Rect = origin() ?: Rect(
        left = (width - fallbackWidth) / 2f,
        top = height - fallbackBottom - fallbackHeight,
        right = (width + fallbackWidth) / 2f,
        bottom = height - fallbackBottom,
    )

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress().coerceIn(0f, 1f)
                val wide = (p * 1.12f).coerceAtMost(1f)
                val o = originIn(size.width, size.height)
                val left = lerp(o.left, 0f, wide)
                val right = lerp(o.right, size.width, wide)
                val top = lerp(o.top, 0f, p)
                val bottom = lerp(o.bottom, size.height, wide)
                val corner = lerp(maxOf(o.height / 2f, minCorner), 0f, sqrt(p))
                shape = GenericShape { _, _ -> addRoundRect(RoundRect(left, top, right, bottom, CornerRadius(corner))) }
                clip = true
                shadowElevation = 30.dp.toPx() * (1f - p)
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (progress() / MorphHandoff).coerceIn(0f, 1f) }
                .background(Color(0xFF141116)),
        )
        CompositionLocalProvider(LocalPlayerReveal provides progress) { content() }
    }
}

/**
 * The cover in flight between the dock and the player. Drawn at one fixed size and scaled into
 * place, so the image loads once instead of at every in-between size; both real covers hide while
 * it's in the air and it hands back to them the instant it lands.
 */
@Composable
private fun FlyingArtwork(url: String, from: () -> Rect?, to: () -> Rect?, progress: () -> Float) {
    val canvasPx = 600f
    val canvas = with(LocalDensity.current) { canvasPx.toDp() }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = url.resizedThumbnail(1200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(canvas)
                .graphicsLayer {
                    val a = from()
                    val b = to()
                    val p = progress()
                    if (a == null || b == null || p <= 0.001f || p >= 0.999f) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val e = p.coerceIn(0f, 1f)
                    alpha = 1f
                    transformOrigin = TransformOrigin(0f, 0f)
                    translationX = lerp(a.left, b.left, e)
                    // A shallow arc rather than a straight line: thrown up out of the dock, then settling.
                    translationY = lerp(a.top, b.top, e) - sin(e * PI.toFloat()) * 42.dp.toPx()
                    scaleX = lerp(a.width, b.width, e) / canvasPx
                    scaleY = lerp(a.height, b.height, e) / canvasPx
                    shape = RoundedCornerShape(lerp(12.dp.toPx(), 16.dp.toPx(), e) / scaleX.coerceAtLeast(0.01f))
                    clip = true
                    shadowElevation = 24.dp.toPx() * e
                },
        )
    }
}