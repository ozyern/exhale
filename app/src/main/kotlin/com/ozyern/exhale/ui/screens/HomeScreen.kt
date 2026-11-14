/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.ios.component.AppleContextMenu
import com.ozyern.exhale.ui.ios.component.IosGlyph
import com.ozyern.exhale.ui.ios.component.IosMenuItem
import com.ozyern.exhale.ui.ios.component.IosMenuSection
import com.ozyern.exhale.ui.screens.library.apple.Cover
import com.ozyern.exhale.ui.screens.library.apple.SfStyle
import com.ozyern.exhale.ui.screens.library.apple.songMenu
import com.ozyern.exhale.ui.menu.AddToPlaylistDialog
import androidx.compose.ui.graphics.vector.ImageVector
import android.content.Intent
import com.composables.sfsymbols.monochrome.SFSquareAndArrowUp
import com.composables.sfsymbols.monochrome.SFTextLineFirstAndArrowtriangleForward
import com.composables.sfsymbols.monochrome.SFTextLineLastAndArrowtriangleForward
import com.composables.sfsymbols.monochrome.SFDotRadiowavesLeftAndRight
import com.composables.sfsymbols.monochrome.SFPlay
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSquareStack
import com.composables.sfsymbols.monochrome.SFMusicMicrophone
import com.composables.sfsymbols.monochrome.SFEllipsis
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.component.LocalItemPrefs
import com.ozyern.exhale.ui.component.BlurredBackdropImage
import com.ozyern.exhale.ui.component.flipAndMultiply
import com.ozyern.exhale.ui.utils.rememberGridSpacing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.Button
import androidx.compose.material3.ButtonDefaults
import com.ozyern.exhale.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.surfaceColorAtElevation
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.request.ImageRequest
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.MoodAndGenres
import com.music.innertube.utils.parseCookieString
import com.music.innertube.YouTube
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.HomeSignInCardDismissedKey
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.constants.LocalSongSortDescendingKey
import com.ozyern.exhale.constants.HideHomeFavoriteIconKey
import com.ozyern.exhale.constants.LocalSongSortTypeKey
import com.ozyern.exhale.constants.RandomizeHomeOrderKey
import com.ozyern.exhale.constants.SongSortType
import com.ozyern.exhale.constants.ShowHomeFabKey
import com.ozyern.exhale.ui.component.homeCardRim
import com.ozyern.exhale.ui.component.rememberHomeCardShape
import com.ozyern.exhale.constants.HomeHeroCardEnabledKey
import com.ozyern.exhale.constants.HomeSectionHiddenKey
import com.ozyern.exhale.constants.HomeSectionOrderKey
import com.ozyern.exhale.constants.ShowHomeMoodFiltersKey
import com.ozyern.exhale.constants.ThumbnailCornerRadius
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.ui.component.SpotlightCard
import com.ozyern.exhale.ui.component.SpotlightCardArtworkSize
import com.ozyern.exhale.ui.component.SpotlightCardInfoHeight
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.PlaylistEntity
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.ui.utils.bleedStart
import com.ozyern.exhale.ui.utils.plusStart
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.utils.LocalFolderIndex
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.ui.utils.albumRoute
import com.ozyern.exhale.ui.utils.artistRoute
import com.ozyern.exhale.ui.utils.localPlaylistRoute
import com.ozyern.exhale.ui.utils.onlinePlaylistRoute
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.SimilarRecommendation
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.playback.queues.LocalAlbumRadio
import com.ozyern.exhale.playback.queues.YouTubeAlbumRadio
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.R
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFXmark
import com.composables.sfsymbols.monochrome.SFMusicNote
import com.composables.sfsymbols.monochrome.SFPersonCropCircle
import com.ozyern.exhale.ui.component.AlbumGridItem
import com.ozyern.exhale.ui.component.ListItem
import com.ozyern.exhale.ui.component.PlaylistListItem
import com.ozyern.exhale.ui.component.ArtistGridItem
import com.ozyern.exhale.ui.component.HideOnScrollFAB
import com.ozyern.exhale.ui.component.HomeHeroCard
import com.ozyern.exhale.ui.component.HomeImageBackground
import com.ozyern.exhale.ui.component.hasCustomHomeBackground
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.music.innertube.pages.ChartsPage.ChartType
import com.ozyern.exhale.viewmodels.ChartsViewModel
import com.ozyern.exhale.viewmodels.NewReleaseViewModel
import com.ozyern.exhale.ui.component.rememberAppBackgroundColor
import com.ozyern.exhale.ui.component.rememberTitleCollapseProgress
import com.ozyern.exhale.ui.component.shimmer.GridItemPlaceHolder
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.ui.component.SongGridItem
import com.ozyern.exhale.ui.component.SortHeader
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.ArtistMenu
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.menu.YouTubeArtistMenu
import com.ozyern.exhale.ui.menu.YouTubePlaylistMenu
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.AppPage
import com.ozyern.exhale.ui.theme.LocalPageMetrics
import com.ozyern.exhale.ui.theme.LocalMetadataColor
import com.ozyern.exhale.ui.theme.BrandName
import com.ozyern.exhale.ui.theme.rememberBrandFontFamily
import com.ozyern.exhale.ui.theme.rememberGlobalAccentColors
import com.ozyern.exhale.utils.listItemShape
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.CommunityPlaylistItem
import com.ozyern.exhale.viewmodels.HomeViewModel
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ozyern.exhale.viewmodels.DailyDiscoverItem


/** Maximum number of recommendation sections included in the home feed. */
internal const val MaxSimilarSections = 4

/** Maximum number of browse sections included in the home feed. */
internal const val MaxHomePageSections = 6

/** Keep local grids to two rows; deeper browsing is available in Library. */
internal const val LocalShelfRows = 2

/** Playlist and folder rows on Home. The rest are one tap away in Library. */
internal const val LocalListPreview = 6

sealed class HomeSection(val id: String, val baseWeight: Int) {
    /** The "star of the day" card. Always first, never shuffled. */
    data object Hero : HomeSection("hero", 110)
    data object SpeedDial : HomeSection("speed_dial", 100)
    data object QuickPicks : HomeSection("quick_picks", 90)
    data object DailyDiscover : HomeSection("daily_discover", 80)
    data object ListenAgain : HomeSection("keep_listening", 50)
    data object TopSongs : HomeSection("top_songs", 55)
    data object AccountPlaylists : HomeSection("account_playlists", 40)
    data object ForgottenFavorites : HomeSection("forgotten_favorites", 30)
    data object FromTheCommunity : HomeSection("from_the_community", 20)
    data class SimilarRecommendation(val index: Int) : HomeSection("similar_recommendation_$index", 10)
    data class HomePageSection(val index: Int) : HomeSection("home_page_section_$index", 10)
    data object MoodAndGenres : HomeSection("mood_and_genres", 5)
}

/** Picks the Home for the current mode before either view model exists, so local mode never builds the online feed. */
@Composable
fun HomeRoute(navController: NavController, snackbarHostState: SnackbarHostState) {
    if (com.ozyern.exhale.LocalLocalOnlyMode.current) {
        LocalHomeScreen(navController)
    } else {
        HomeScreen(navController, snackbarHostState)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val database = LocalDatabase.current.get()
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    // Kept as State and read only inside rows and click handlers: delegating them here
    // recomposed all of Home (and rebuilt every shelf) on each play/pause or track change.
    val isPlayingState = playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadataState = playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    // One immutable snapshot: one State read, one collector — was ~26 separate collectors.
    val homeState by viewModel.uiState.collectAsStateWithLifecycle()

    val quickPicks = homeState.quickPicks
    val forgottenFavorites = homeState.forgottenFavorites
    val topSongs = homeState.topSongs
    val similarRecommendations = homeState.similarRecommendations
    val accountPlaylists = homeState.accountPlaylists
    val homePage = homeState.homePage
    val explorePage = homeState.explorePage
    val dailyDiscover = homeState.dailyDiscover
    val communityPlaylists = homeState.communityPlaylists
    val allLocalItems = homeState.allLocalItems
    val allYtItems = homeState.allYtItems

    val selectedChip = homeState.selectedChip
    val pendingChip by viewModel.pendingChip.collectAsStateWithLifecycle()
    val shownChip = pendingChip ?: selectedChip

    val isLoading = homeState.isLoading
    val isRefreshing = homeState.isRefreshing
    val pullRefreshState = rememberPullToRefreshState()


    val accountImageUrl = homeState.accountImageUrl
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val (randomizeHomeOrder) = rememberPreference(RandomizeHomeOrderKey, true)

    val shouldShowWrappedCard = homeState.showWrappedCard
    val wrappedState by viewModel.wrappedManager.state.collectAsStateWithLifecycle()
    val isWrappedDataReady = wrappedState.isDataReady

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null
    val (signInCardDismissed, setSignInCardDismissed) = rememberPreference(HomeSignInCardDismissedKey, false)

    val scope = rememberCoroutineScope()
    // Track randomization job. MutableState<Job?> so SpeedDial's section composable
    // (a LazyListScope extension, not a @Composable) can swap the job in and out.
    val randomizeJob = remember { mutableStateOf<Job?>(null) }

    val lazylistState = rememberLazyListState()
    val filterScrollState = rememberScrollState()
    val feedScroll = remember { mutableFloatStateOf(0f) }
    val feedScrollTracker = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                feedScroll.floatValue = (feedScroll.floatValue - consumed.y).coerceAtLeast(0f)
                return Offset.Zero
            }
        }
    }
    var previousChip by remember { mutableStateOf(shownChip) }
    LaunchedEffect(shownChip) {
        if (shownChip != previousChip) {
            lazylistState.scrollToItem(0)
            feedScroll.floatValue = 0f
            previousChip = shownChip
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsStateWithLifecycle()

    val wrappedDismissed by backStackEntry?.savedStateHandle?.getStateFlow("wrapped_seen", false)
        ?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    var randomSeed by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            randomSeed = System.currentTimeMillis()
        }
    }

    val foundInSettings = stringResource(R.string.found_in_settings_content)
    val categoryLoadFailed = stringResource(R.string.home_category_load_failed)
    LaunchedEffect(viewModel) {
        viewModel.chipLoadFailures.collect {
            snackbarHostState.showSnackbar(categoryLoadFailed)
        }
    }
    LaunchedEffect(wrappedDismissed) {
        if (wrappedDismissed) {
            viewModel.markWrappedAsSeen()
            scope.launch {
                snackbarHostState.showSnackbar(foundInSettings)
            }
            backStackEntry?.savedStateHandle?.set("wrapped_seen", false)
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val homeContinuation by rememberUpdatedState(homePage?.continuation)
    LaunchedEffect(Unit) {
        snapshotFlow {
            val info = lazylistState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            homeContinuation.takeIf { last >= info.totalItemsCount - 3 }
        }
            .collect(viewModel::loadMoreYouTubeItems)
    }

    NetworkReload(
        onReload = viewModel::refresh
    )

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    val itemHandlers = rememberHomeItemHandlers(
        navController, playerConnection, menuState, haptic, scope, mediaMetadataState, isPlayingState,
    )
    val onLocalItemClick = itemHandlers.onLocalItemClick
    val localGridItem = itemHandlers.localGridItem

    val (heroCardEnabled) = rememberPreference(HomeHeroCardEnabledKey, true)
    val (savedSectionOrder) = rememberPreference(HomeSectionOrderKey, "")
    val (hiddenSectionIds) = rememberPreference(HomeSectionHiddenKey, "")
    val (showMoodFilters) = rememberPreference(ShowHomeMoodFiltersKey, true)

    val homeSections = remember(
        heroCardEnabled,
        randomizeHomeOrder,
        // Without this key a reorder saved in settings would not reach Home until
        // something else invalidated the list.
        savedSectionOrder,
        hiddenSectionIds,
        randomSeed,
        // Rebuild section order only when its available sections change.
        quickPicks.isNullOrEmpty(),
        homeState.speedDialItems.isEmpty(),
        communityPlaylists.isNullOrEmpty(),
        dailyDiscover.isNullOrEmpty(),
        homeState.listenAgain?.items.isNullOrEmpty(),
        topSongs.isNullOrEmpty(),
        accountPlaylists.isNullOrEmpty(),
        forgottenFavorites.isNullOrEmpty(),
        (similarRecommendations?.size ?: 0).coerceAtMost(MaxSimilarSections),
        (homePage?.sections?.size ?: 0).coerceAtMost(MaxHomePageSections),
        explorePage?.moodAndGenres != null,
    ) {
        buildHomeSections(homeState, heroCardEnabled, randomizeHomeOrder, savedSectionOrder, hiddenSectionIds, randomSeed)
    }

    // Batch each section lookup into one flow while keeping row updates live.
    val quickPicksSongMap = rememberSongsById(
        database,
        remember(quickPicks) { quickPicks.orEmpty().map { it.id } },
    )
    val forgottenFavoritesSongMap = rememberSongsById(
        database,
        remember(forgottenFavorites) { forgottenFavorites.orEmpty().map { it.id } },
    )

    val chartsViewModel: ChartsViewModel = koinViewModel()
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (chartsPage == null) chartsViewModel.loadCharts()
    }
    val newReleaseViewModel: NewReleaseViewModel = koinViewModel()
    val newReleaseAlbums by newReleaseViewModel.newReleaseAlbums.collectAsStateWithLifecycle()
    val topPicks = remember(chartsPage) {
        chartsPage?.sections?.firstOrNull { it.chartType == ChartType.TOP }?.items.orEmpty()
    }
    val trending = remember(chartsPage) {
        chartsPage?.sections?.firstOrNull { it.chartType == ChartType.TRENDING }?.items.orEmpty()
    }
    val topPicksTitle = stringResource(R.string.top_picks_for_you)
    val newReleaseTitle = stringResource(R.string.new_release_albums)
    val trendingTitle = stringResource(R.string.trending)

    PullToRefreshBox(
        state = pullRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh() },
        indicator = {
            // Compose the animated indicator only while it is visible to avoid idle redraws.
            if (isRefreshing || pullRefreshState.distanceFraction > 0f) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
                )
            }
        }
    ) {
        // The shared top bar renders the title and avatar; this screen starts below it.
        Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalPageMetrics provides AppPage.Metrics,
            LocalMetadataColor provides AppTheme.colorScheme.onSurfaceVariant,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                val pageBackground = rememberAppBackgroundColor(AppTheme.colorScheme.surface)
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(pageBackground)
                )
                HomeImageBackground(contentLoaded = homePage != null)
                if (!hasCustomHomeBackground()) {
                    HomeChipBackdrop(
                        url = homePage?.background?.takeIf { selectedChip != null && pendingChip == null },
                        fadeTo = pageBackground,
                        scrolledPx = {
                            val atTop = lazylistState.firstVisibleItemIndex == 0 && lazylistState.firstVisibleItemScrollOffset == 0
                            if (atTop) 0f else feedScroll.floatValue
                        },
                    )
                }

                // Space the floating side bar occupies. The vertical list keeps it as
                // content padding so rows stay clear of the panel; the carousels below
                // give it back with bleedStart so their cards run underneath the glass
                // instead of stopping at its edge.
                val sideInset = LocalPlayerAwareWindowInsets.current
                    .asPaddingValues()
                    .calculateStartPadding(LocalLayoutDirection.current)

                val deps = HomeSectionDeps(
                    context = LocalContext.current,
                    randomItem = viewModel::getRandomItem,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    sideInset = sideInset,
                    localGridItem = localGridItem,
                    onLocalItemClick = onLocalItemClick,
                    isScrolling = remember(lazylistState) { { lazylistState.isScrollInProgress } },
                )

                LazyColumn(
                    state = lazylistState,
                    modifier = Modifier.nestedScroll(feedScrollTracker),
                    // Sections had nothing but NavigationTitle's own 12dp padding
                    // between them — ran into each other, felt cramped.
                    verticalArrangement = Arrangement.spacedBy(AppPage.ItemSpacing),
                    contentPadding = run {
                        val ins = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                        val dir = LocalLayoutDirection.current
                        PaddingValues(
                            start = ins.calculateStartPadding(dir),
                            top = maxOf(ins.calculateTopPadding(), if (sideInset > 0.dp) 96.dp else 116.dp),
                            end = ins.calculateRightPadding(dir),
                            bottom = ins.calculateBottomPadding(),
                        )
                    },
                ) {
                    // Apple Music's "Transfer music" card, pointed at our one import path:
                    // signing in to YouTube Music. Gone for good once dismissed or signed in.
                    if (
                        selectedChip == null && !isLoggedIn && !signInCardDismissed
                    ) {
                        item(key = "sign_in_card", contentType = "sign_in_card") {
                            HomeSignInCard(
                                onGetStarted = { navController.navigate("login") },
                                onDismiss = { setSignInCardDismissed(true) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    item(key = "home_feed_filters", contentType = "home_feed_filters") {
                        HomeFeedFilterRow(
                            moodChips = if (showMoodFilters) homePage?.chips.orEmpty().filter { it.endpoint?.browseId?.isNotBlank() == true } else emptyList(),
                            selectedChip = shownChip,
                            onSelected = viewModel::toggleChip,
                            scrollState = filterScrollState,
                            sideInset = sideInset,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    if (selectedChip == null) {
                        item(key = "wrapped_card", contentType = "wrapped_card") {
                            AnimatedVisibility(visible = shouldShowWrappedCard) {
                                val wrappedCardShape = rememberHomeCardShape()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .homeCardRim(wrappedCardShape),
                                    colors = CardDefaults.cardColors(
                                        containerColor = LocalContentColor.current.copy(alpha = 0.1f),
                                    ),
                                    shape = wrappedCardShape
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isWrappedDataReady) {
                                            val bbhFont = try {
                                                FontFamily(Font(R.font.bbh_bartle_regular))
                                            } catch (e: Exception) {
                                                FontFamily.Default
                                            }
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.wrapped_ready_title),
                                                    style = AppTheme.typography.headlineLarge.copy(
                                                        fontFamily = bbhFont,
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(R.string.wrapped_ready_subtitle),
                                                    style = AppTheme.typography.bodyLarge.copy(
                                                        textAlign = TextAlign.Center
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))
                                                Button(onClick = {
                                                    navController.navigate("wrapped")
                                                }) {
                                                    Text(stringResource(R.string.open))
                                                }
                                            }
                                        } else {
                                            com.ozyern.exhale.ui.component.ShimmerBox(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp)
                                                    .height(160.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (pendingChip != null) {
                        // The shimmer below stands in for the chip's feed until it arrives.
                    } else if (selectedChip != null) {
                        chipFeedContent(deps, homePage)
                    } else {
                        figmaHomeContent(
                            deps = deps,
                            mediaMetadata = mediaMetadataState,
                            isPlaying = isPlayingState,
                            topPicks = topPicks.ifEmpty { trending }.ifEmpty { newReleaseAlbums },
                            topPicksTitle = topPicksTitle,
                            newReleaseAlbums = if (topPicks.isNotEmpty() || trending.isNotEmpty()) newReleaseAlbums else emptyList(),
                            newReleaseTitle = newReleaseTitle,
                            trending = if (topPicks.isNotEmpty()) trending else emptyList(),
                            trendingTitle = trendingTitle,
                        )
                        homeSectionsContent(
                            sections = homeSections,
                            deps = deps,
                            ui = homeState,
                            mediaMetadata = mediaMetadataState,
                            isPlaying = isPlayingState,
                            randomizeJob = randomizeJob,
                            quickPicksSongMap = quickPicksSongMap,
                            forgottenFavoritesSongMap = forgottenFavoritesSongMap,
                            url = url,
                        )
                    }
                    // Show this infinite shimmer only during a fetch; a continuation can remain available indefinitely.
                    if (isLoading || pendingChip != null) {
                        item(key = "loading_shimmer", contentType = "shimmer") {
                            ShimmerHost(
                                modifier = Modifier.animateItem()
                            ) {
                                repeat(2) {
                                    TextPlaceholder(
                                        height = 36.dp,
                                        modifier = Modifier
                                            .padding(AppPage.Gutter)
                                            .width(250.dp),
                                    )
                                    LazyRow(
                                        modifier = Modifier.bleedStart(sideInset),
                                        contentPadding = PaddingValues(horizontal = AppleTokens.ItemGap / 2)
                                            .plusStart(sideInset),
                                    ) {
                                        items(4) {
                                            GridItemPlaceHolder()
                                        }
                                    }
                                }

                                TextPlaceholder(
                                    height = 36.dp,
                                    modifier = Modifier
                                        .padding(AppPage.Gutter)
                                        .width(250.dp),
                                )
                                repeat(4) {
                                    Row {
                                        repeat(2) {
                                            TextPlaceholder(
                                                height = MoodAndGenresButtonHeight,
                                                shape = RoundedCornerShape(AppleTokens.Control),
                                                modifier = Modifier
                                                    .padding(AppleTokens.ItemGap / 2)
                                                    .width(200.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }

                val (showHomeFab) = rememberPreference(ShowHomeFabKey, true)
                if (showHomeFab) {
                    HideOnScrollFAB(
                        visible = allLocalItems.isNotEmpty() || allYtItems.isNotEmpty(),
                        lazyListState = lazylistState,
                        icon = R.drawable.shuffle,
                        onClick = {
                            val local = when {
                                allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5
                                allLocalItems.isNotEmpty() -> true
                                else -> false
                            }
                            scope.launch(Dispatchers.Main) {
                                if (local) {
                                    when (val luckyItem = allLocalItems.random()) {
                                        is Song -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                        is Album -> {
                                            val albumWithSongs = withContext(Dispatchers.IO) {
                                                database.albumWithSongs(luckyItem.id).first()
                                            }
                                            albumWithSongs?.let {
                                                playerConnection.playQueue(LocalAlbumRadio(it))
                                            }
                                        }
                                        is Artist -> {}
                                        is Playlist -> {}
                                    }
                                } else {
                                    when (val luckyItem = allYtItems.random()) {
                                        is SongItem -> playerConnection.playQueue(YouTubeQueue.radio(luckyItem.toMediaMetadata()))
                                        is AlbumItem -> playerConnection.playQueue(YouTubeAlbumRadio(luckyItem.playlistId))
                                        is ArtistItem -> luckyItem.radioEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                        is PlaylistItem -> luckyItem.playEndpoint?.let {
                                            playerConnection.playQueue(YouTubeQueue(it))
                                        }
                                    }
                                }
                            }
                        },
                        onRecognitionClick = {
                            navController.navigate("recognition")
                        }
                    )
                }
            }
        }
        }
    }
}
