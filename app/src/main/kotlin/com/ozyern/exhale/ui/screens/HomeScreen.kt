/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.extensions.toMediaItem
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import com.ozyern.exhale.ui.component.IosRefreshSpinner
import com.ozyern.exhale.ui.component.IosRefreshTravel
import com.ozyern.exhale.ui.component.iosRefreshPull
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ozyern.exhale.innertube.utils.parseCookieString
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.ShowHomeCategoryChipsKey
import com.ozyern.exhale.ui.component.AmbientArtworkGlow
import com.ozyern.exhale.ui.component.ChipsRow
import com.ozyern.exhale.ui.component.LocalBottomSheetPageState
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.rememberArtworkAmbientColors
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.menu.SongMenu
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.HomeViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val quickPicks by viewModel.quickPicks.collectAsState()
    val keepListening by viewModel.keepListening.collectAsState()
    val madeForYou by viewModel.madeForYou.collectAsState()
    val homePage by viewModel.homePage.collectAsState()
    val allItemsMetadata by viewModel.allItemsMetadata.collectAsState()

    val selectedChip by viewModel.selectedChip.collectAsState()

    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    // Apple-Music declutter: the mood-chip carousel is off by default (still opt-in via
    // Settings → Appearance) — the reference Home has no chip row.
    val (showHomeCategoryChips) = rememberPreference(ShowHomeCategoryChipsKey, false)
    val online by com.ozyern.exhale.utils.NetworkStatus.online.collectAsState()
    val (offlineMode) = rememberPreference(com.ozyern.exhale.constants.OfflineModeKey, false)
    val offline = offlineMode || !online
    // Back online: fetch what failed while the phone had no connection.
    var wasOffline by remember { mutableStateOf(false) }
    LaunchedEffect(online) {
        if (!online) wasOffline = true
        else if (wasOffline) { wasOffline = false; viewModel.refresh() }
    }
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val url = if (isLoggedIn) accountImageUrl else null

    val scope = rememberCoroutineScope()
    val lazylistState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazylistState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { lazylistState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                val len = lazylistState.layoutInfo.totalItemsCount
                if (lastVisibleIndex != null && lastVisibleIndex >= len - 3) {
                    viewModel.loadMoreYouTubeItems(homePage?.continuation)
                }
            }
    }

    if (selectedChip != null) {
        BackHandler {
            // if a chip is selected, go back to the normal homepage first
            viewModel.toggleChip(selectedChip)
        }
    }

    LaunchedEffect(showHomeCategoryChips, selectedChip) {
        if (!showHomeCategoryChips && selectedChip != null) {
            viewModel.toggleChip(selectedChip)
        }
    }

    // The same menu the rest of the app opens on a long press, for the six tiles that had none.
    // Only songs get one: an album, artist or playlist tile has a menu on the page it opens, and
    // a second sheet on the way there would be a different affordance for the same object.
    val onShortcutLongClick: (LocalItem) -> Unit = { item ->
        if (item is Song) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                SongMenu(
                    originalSong = item,
                    navController = navController,
                    metadata = allItemsMetadata[item.id],
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    // One handler for the shortcut grid. Songs play in place; everything else is a destination.
    val onShortcutClick: (LocalItem) -> Unit = { item ->
        when (item) {
            is Song ->
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue(
                            endpoint = WatchEndpoint(item.id),
                            preloadItem = item.toMediaMetadata(),
                        )
                    )
                }

            is Album -> navController.navigate("album/${item.id}")
            is Artist -> navController.navigate("artist/${item.id}")
            is Playlist -> navController.navigate("local_playlist/${item.id}")
        }
    }

    // Ambient colour from the current cover, washing down from the top of the surface.
    //
    // This is NOT the old 5-blob mesh backdrop that used to live here — that one covered the whole
    // screen, animated across all of it, and cost a full-screen overdraw every frame for a texture
    // you read as noise. This is confined to the top, drawn in the draw phase only, and gone by
    // mid-viewport, so the list below it sits on the plain surface exactly as before.
    //
    // Keyed to the current song when there is one and to the top of the feed when there is not.
    // Keying it to playback alone meant the wash did not exist on a cold start — you opened the
    // app, nothing was playing, and Home was the flat page this was added to fix, right up until
    // you tapped something. The first artwork on the page is a perfectly good stand-in: it is the
    // picture the user is looking at anyway, so the room is lit by what is on screen.
    val ambientSource: Pair<String, String?>? = mediaMetadata?.let { it.id to it.thumbnailUrl }
        ?: quickPicks?.firstOrNull { it.thumbnailUrl != null }?.let { it.id to it.thumbnailUrl }
        ?: keepListening?.firstOrNull { it.thumbnailUrl != null }?.let { it.id to it.thumbnailUrl }

    val ambientColors by rememberArtworkAmbientColors(
        songId = ambientSource?.first,
        thumbnailUrl = ambientSource?.second,
    )

    // Has anything actually arrived to look at?
    val feedIsEmpty = quickPicks.isNullOrEmpty() &&
            keepListening.isNullOrEmpty() &&
            homePage?.sections.isNullOrEmpty()

    // One clock for the whole feed's arrival.
    //
    // Every shelf reads this same Animatable and offsets itself by its position, which is what
    // makes the page arrive as a page — a stagger built from per-item animations all starting when
    // their own item happens to compose instead reads as things popping in at random, because in a
    // LazyColumn that is exactly what it is.
    //
    // It is read inside `graphicsLayer` lambdas only, so the whole sequence runs in the draw phase:
    // no recomposition, no relayout, on a screen that is simultaneously doing its first network
    // parse. And anything composed after it finishes (everything you scroll to) evaluates straight
    // to 1 and renders at rest, so the intro never replays on scroll.
    val feedIntro = remember { Animatable(0f) }
    LaunchedEffect(feedIsEmpty) {
        if (!feedIsEmpty && feedIntro.value == 0f) {
            feedIntro.animateTo(
                targetValue = 1f,
                animationSpec = tween(FeedIntroDurationMs, easing = LinearOutSlowInEasing),
            )
        }
    }
    val introProgress: () -> Float = { feedIntro.value }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // No wash of colour behind Home: iOS's is plain black, and the artwork is on the cards.

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    onRefresh = viewModel::refresh
                )
        ) {
            LazyColumn(
                state = lazylistState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                // Pulled, the feed itself comes down to uncover the spinner, as iOS's does.
                modifier = Modifier.iosRefreshPull(pullRefreshState),
                // The refresh pull is this list's rubber-band at the top; see iosRefreshPull.
                overscrollEffect = null,
            ) {
                // The title no longer lives in the feed: it is pinned over it (PinnedPageHeader),
                // and the first shelf starts right under the status bar so the title sits on its
                // artwork, as the reference has it.
                // Everything starts below the pinned title: the cards have a heading of their own
                // now, and a heading under "Home" would be two titles stacked.
                item(key = "home_top_gap", contentType = "top_gap") {
                    Spacer(Modifier.size(com.ozyern.exhale.ui.component.PinnedPageHeaderHeight))
                }

                // No internet, or offline mode on: what the phone can play by itself, first.
                if (offline) {
                    item(key = "home_offline", contentType = "offline") {
                        HomeOfflineSection(manual = offlineMode, modifier = Modifier.animateItem())
                    }
                }

                // Signed out: the card, one line of why and a white Sign in pill.
                if (!isLoggedIn && !offline) {
                    item(key = "home_sign_in", contentType = "sign_in") {
                        HomeSignInCard(onSignIn = { navController.navigate("login") })
                    }
                }

                // Cold start with nothing cached used to be a black page with a title on it for
                // as long as the first request took. Three placeholder shelves say the same thing
                // a spinner would — something is coming — while also showing its shape, so the
                // real content lands into a layout the eye has already accepted.
                if (feedIsEmpty && isLoading) {
                    items(
                        count = 3,
                        key = { "home_skeleton_$it" },
                        contentType = { "shimmer" },
                    ) {
                        HomeLoadingShimmer(modifier = Modifier.animateItem())
                    }
                }

                // Loading finished and nothing came back.
                //
                // Until now that was a black page with the word "Home" on it and no explanation,
                // which is what the app looks like on a plane, on a dead connection, or on a
                // first launch that failed — the three moments it most needs to say something.
                // A page with nothing on it reads as a broken app; the same page saying why, and
                // offering the one thing that works with no network, reads as an app that knows
                // where it is.
                if (feedIsEmpty && !isLoading && !offline) {
                    item(key = "home_empty", contentType = "empty") {
                        HomeEmptyState(
                            onRetry = viewModel::refresh,
                            onOpenLibrary = {
                                navController.navigate(Screens.Library.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                madeForYou?.takeIf { it.isNotEmpty() }?.let { picks ->
                    item(key = "home_made_for_you", contentType = "made_for_you") {
                        HomeMadeForYou(
                            picks = picks,
                            activeId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            onPlay = { song ->
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        YouTubeQueue(
                                            endpoint = song.endpoint ?: WatchEndpoint(song.id),
                                            preloadItem = song.toMediaMetadata(),
                                        )
                                    )
                                }
                            },
                            onPlayNext = { song -> playerConnection.playNext(song.toMediaItem()) },
                            modifier = Modifier.animateItem(),
                            cardIntro = { index ->
                                Modifier.feedIntro(index.coerceAtMost(3) * ShortcutTileStagger, introProgress)
                            },
                        )
                    }
                }

                if (showHomeCategoryChips) {
                    item(key = "chips_row", contentType = "chips_row") {
                        ChipsRow(
                            chips = homePage?.chips.orEmpty().map { it to it.title },
                            segmented = false,
                            currentValue = selectedChip,
                            onValueUpdate = {
                                viewModel.toggleChip(it)
                            }
                        )
                    }
                }

                quickPicks?.takeIf { it.isNotEmpty() }?.let { picks ->
            /*
                item {
                    NavigationTitle(
                        title = stringResource(R.string.quick_picks),
                        modifier = Modifier.animateItem()
                    )
                }
            */

                item(key = "quick_picks", contentType = "quick_picks") {
                    QuickPicksSection(
                        modifier = Modifier.feedIntro(1f, introProgress),
                        quickPicks = picks,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        metadataMap = allItemsMetadata
                    )
                }
            }

            // New here: the artists picked in setup, right under the cards and Quick picks, so the
            // top of Home is built from those choices until listening history can take over.
            SimilarRecommendationsContainer(
                viewModel = viewModel,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                fromSetupPicks = true,
            )

            // Recents follow Quick picks: the reference leads with the cards and the song list.
            keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                item(key = "home_shortcuts", contentType = "shortcuts") {
                    HomeTopPicks(
                        items = items.take(10),
                        onItemClick = onShortcutClick,
                        onItemLongClick = onShortcutLongClick,
                        modifier = Modifier.animateItem(),
                        activeId = mediaMetadata?.id,
                        isPlaying = isPlaying,
                        // The first cards on screen arrive in a short cascade rather than as one block.
                        cardIntro = { index ->
                            Modifier.feedIntro(1.5f + index.coerceAtMost(3) * ShortcutTileStagger, introProgress)
                        },
                    )
                }
            }

            // Keep Listening used to be a horizontal shelf of grid cards down here, behind
            // Quick Picks. It is the shelf people actually came for, and a sideways-scrolling
            // row shows four items at a time — so it moved to the top of the page and became a
            // Spotify shortcut grid instead, which shows six at once and needs no aiming.

            AccountPlaylistsContainer(
                viewModel = viewModel,
                accountName = accountName,
                accountImageUrl = url,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope
            )

            SimilarRecommendationsContainer(
                viewModel = viewModel,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope
            )

            // Stable per-section keys keep the LazyColumn from recomposing/rebinding every
            // section (and its whole nested LazyRow of thumbnails) when the list grows via
            // pagination. Fall back to the title and index if a section has no endpoint identity.
            //
            // The browse id alone is NOT unique, which is what crashed the app with
            // `Key "home_section_title_FEmusic_new_releases_albums" was already used`: YouTube
            // Music happily returns the same endpoint twice in one feed — "New releases" and
            // "New albums & singles" are both `FEmusic_new_releases_albums` — and a continuation
            // can append a section the first page already had. LazyColumn treats a repeated key
            // as a programming error and throws during measure, so the whole screen went down the
            // moment such a feed arrived.
            //
            // Repeats get an occurrence suffix rather than everything getting the index appended:
            // the first section under a given id keeps the key it always had, so the common case
            // (an append-only feed with no duplicates) is byte-for-byte the old behaviour and
            // pagination still does not rebind a single existing row.
            val sectionKeys = buildList {
                val seen = HashMap<String, Int>()
                homePage?.sections?.forEachIndexed { index, section ->
                    val base = section.endpoint?.browseId ?: "title_${section.title}_$index"
                    val occurrence = (seen[base] ?: 0) + 1
                    seen[base] = occurrence
                    add(if (occurrence == 1) base else "${base}__$occurrence")
                }
            }

            // The feed leads with its first shelf as big cards, whatever is in it.
            val heroSectionIndex = homePage?.sections?.indexOfFirst { it.items.size >= 2 } ?: -1
            homePage?.sections?.forEachIndexed { index, section ->
                val sectionKey = sectionKeys.getOrElse(index) { "section_$index" }

                // Title and shelf share one stagger slot so they arrive together — a heading that
                // lands before the row it names reads as two separate things.
                val introOrder = (index + 2).toFloat()

                item(key = "home_section_title_$sectionKey", contentType = "section_title") {
                    HomePageSectionTitle(
                        section = section,
                        navController = navController,
                        modifier = Modifier
                            .animateItem()
                            .feedIntro(introOrder, introProgress)
                    )
                }

                item(key = "home_section_content_$sectionKey", contentType = "home_section") {
                    HomePageSectionContent(
                        hero = index == heroSectionIndex,
                        modifier = Modifier.feedIntro(introOrder, introProgress),
                        section = section,
                        mediaMetadata = mediaMetadata,
                        isPlaying = isPlaying,
                        navController = navController,
                        playerConnection = playerConnection,
                        menuState = menuState,
                        haptic = haptic,
                        scope = scope,
                        metadataMap = allItemsMetadata
                    )
                }
            }

            if (isLoading || homePage?.continuation != null && homePage?.sections?.isNotEmpty() == true) {
                item(key = "home_loading_shimmer", contentType = "shimmer") {
                    HomeLoadingShimmer(modifier = Modifier.animateItem())
                }
            }
            }

            // In the gap the pull opens above the feed, centred in it.
            IosRefreshSpinner(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .padding(top = (IosRefreshTravel - 26.dp) / 2),
            )

            com.ozyern.exhale.ui.component.PinnedPageHeader(
                title = "Home",
                scrolled = { lazylistState.firstVisibleItemIndex > 0 || lazylistState.firstVisibleItemScrollOffset > 0 },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}


/**
 * What Home says when the feed came back with nothing.
 *
 * Deliberately not a spinner and not an error dialog. There is no way to tell from here whether
 * this is a dead connection, a signed-out account or a service that is having a bad morning, and
 * guessing wrong is worse than not saying — so it states the one thing that is certainly true,
 * and then offers the two things that are worth doing about it either way.
 *
 * The library button is the one that matters. Downloaded music plays with no network at all, and
 * a music player that goes blank the moment the internet does is failing at the thing it is for.
 */
@Composable
private fun HomeEmptyState(
    onRetry: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 32.dp)
            .padding(top = 72.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.size(18.dp))

        Text(
            text = stringResource(R.string.home_offline_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(6.dp))

        Text(
            text = stringResource(R.string.home_offline_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.size(22.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeEmptyAction(
                label = stringResource(R.string.retry),
                onClick = onRetry,
                emphasised = false,
            )
            HomeEmptyAction(
                label = stringResource(R.string.filter_library),
                onClick = onOpenLibrary,
                emphasised = true,
            )
        }
    }
}

@Composable
private fun HomeEmptyAction(
    label: String,
    onClick: () -> Unit,
    emphasised: Boolean,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = if (emphasised) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (emphasised) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
    )
}

/**
 * The colours the reference's sign-in card drifts through, read off its screen recording: each is
 * a dark corner and a lit one, and the card eases from one pair to the next.
 */
private val SignInWashes = listOf(
    Color(0xFF5B1F7A) to Color(0xFF9B2F6B), // violet into magenta
    Color(0xFF8A3A1C) to Color(0xFFC0263F), // amber into red
    Color(0xFF4B1A6E) to Color(0xFFB0305A), // purple into rose
    Color(0xFF1C2F7A) to Color(0xFF3A4FB0), // deep blue
    Color(0xFF0E4E6A) to Color(0xFF2B45A8), // teal into blue
    Color(0xFF3A1F8C) to Color(0xFF6A2BC0), // indigo into violet
    Color(0xFF6A1E5E) to Color(0xFFB03A52), // plum into rose
)

/** One trip through every wash, as slow as the reference's: about half a minute. */
private const val SignInWashCycleMs = 30_000

/**
 * Signed out: the reference design's card — slim, the line in white, a compact white "Sign in with
 * Google" capsule — on a living wash that drifts through [SignInWashes], with a soft light wandering
 * across it.
 */
@Composable
private fun HomeSignInCard(onSignIn: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "signInWash")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = SignInWashes.size.toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(SignInWashCycleMs, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "signInWashPhase",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .drawBehind {
                // Eased between neighbouring washes, so each colour holds a moment before moving on.
                val i = phase.toInt() % SignInWashes.size
                val f = phase - phase.toInt()
                val t = f * f * (3f - 2f * f)
                val (a0, b0) = SignInWashes[i]
                val (a1, b1) = SignInWashes[(i + 1) % SignInWashes.size]
                val dark = androidx.compose.ui.graphics.lerp(a0, a1, t)
                val lit = androidx.compose.ui.graphics.lerp(b0, b1, t)
                drawRect(
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(dark, lit),
                        start = androidx.compose.ui.geometry.Offset(0f, size.height),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    ),
                )
                // A soft light wandering across the card, so the wash reads as moving, not swapping.
                val angle = phase / SignInWashes.size * 2f * Math.PI.toFloat()
                val center = androidx.compose.ui.geometry.Offset(
                    size.width * (0.5f + 0.35f * kotlin.math.cos(angle)),
                    size.height * (0.5f + 0.45f * kotlin.math.sin(angle * 2f)),
                )
                drawRect(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                        center = center,
                        radius = size.width * 0.55f,
                    ),
                )
            }
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
    ) {
        Text(
            text = "Sign in to Exhale",
            fontSize = 20.sp,
            lineHeight = 24.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            letterSpacing = (-0.3).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.size(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onSignIn)
                .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.google_g),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Sign in with Google",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------- */
/* The feed's arrival                                                                           */
/* ------------------------------------------------------------------------------------------- */

/**
 * How long the whole page takes to arrive. Long enough that the stagger is legible as one motion,
 * short enough that it is over before anyone could want to scroll.
 */
private const val FeedIntroDurationMs = 760

/** A shelf's head start over the one below it, as a fraction of [FeedIntroDurationMs]. */
private const val FeedIntroStagger = 0.07f

/** How much of the window one shelf's own rise occupies. */
private const val FeedIntroSlice = 0.55f

/** How far apart two shortcut tiles arrive, in stagger slots. */
private const val ShortcutTileStagger = 0.34f

/** How far a shelf travels on its way in: enough to read as an entrance, as the reference does. */
private val FeedIntroRise = 28.dp

/** How far under full size a shelf starts. Under four percent, so it never reads as a zoom. */
private const val FeedIntroScale = 0.035f

/**
 * One shelf's share of the feed's arrival: rise and fade, offset by its position on the page.
 *
 * Everything is read inside `graphicsLayer`, so a frame of this animation costs a layer
 * invalidation and neither a recomposition nor a relayout — which matters because it runs during
 * the exact window Home is also parsing its first response.
 *
 * Late shelves share the last slot rather than being given ever-later starts: past a certain
 * order a shelf's window would end after the clock does, and it would sit at zero alpha forever.
 * Nothing below the seventh shelf is on screen during the intro anyway.
 */
internal fun Modifier.feedIntro(order: Float, progress: () -> Float): Modifier =
    this.graphicsLayer {
        val start = (order * FeedIntroStagger).coerceAtMost(1f - FeedIntroSlice)
        val raw = ((progress() - start) / FeedIntroSlice).coerceIn(0f, 1f)
        val eased = FastOutSlowInEasing.transform(raw)

        alpha = eased
        translationY = (1f - eased) * FeedIntroRise.toPx()

        // A shelf grows the last few percent into place as it rises. On its own the rise is a
        // sheet of content sliding up a track; with the scale it is an object coming to rest at
        // its own distance, which is the difference between a transition that has been applied to
        // the page and one the page appears to be doing.
        //
        // Kept under four percent because the effect has to survive being read at a glance and never be
        // legible as zooming. Anything past about four reads as a card being thrown at you.
        val grown = 1f - FeedIntroScale * (1f - eased)
        scaleX = grown
        scaleY = grown
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
