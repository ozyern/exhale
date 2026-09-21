/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
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

    val isLoading: Boolean by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    val accountName by viewModel.accountName.collectAsState()
    val accountImageUrl by viewModel.accountImageUrl.collectAsState()
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    // Apple-Music declutter: the mood-chip carousel is off by default (still opt-in via
    // Settings → Appearance) — the reference Home has no chip row.
    val (showHomeCategoryChips) = rememberPreference(ShowHomeCategoryChipsKey, false)
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
        // No wash of colour behind Home: Apple's is plain black, and the artwork is on the cards.

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
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
            ) {
                // Apple Music's large title: the weekday as a quiet eyebrow over a heavy
                // display-size "Home". It lives *inside* the scroll content rather than in the app
                // bar, which is the whole point of the pattern — it scrolls away under the bar as
                // you move down, leaving the compact chrome behind.
                item(key = "home_large_title", contentType = "large_title") {
                    HomeLargeTitle(
                        // Apple's large title does not simply scroll off — it drifts slower than
                        // the list under it and dissolves as it goes, which is what makes the
                        // compact bar feel like the same title rather than a replacement for it.
                        //
                        // Read in the draw phase, so a scroll frame costs a layer invalidation
                        // and nothing else.
                        modifier = Modifier.graphicsLayer {
                            val height = size.height.coerceAtLeast(1f)
                            val scrolled = if (lazylistState.firstVisibleItemIndex == 0) {
                                lazylistState.firstVisibleItemScrollOffset.toFloat()
                            } else {
                                height
                            }
                            val t = (scrolled / height).coerceIn(0f, 1f)

                            alpha = 1f - t
                            translationY = scrolled * LargeTitleParallax

                            // And it shrinks on its way out, toward the size the compact bar's
                            // title already is. Fading alone leaves a display-size word sitting
                            // still while a small one appears somewhere above it; shrinking is
                            // what makes the eye read the two as one title changing state.
                            //
                            // Anchored bottom-left, because that is the corner the two titles
                            // share -- scaling about the centre would walk the word inward off
                            // the margin every other element on the page lines up on.
                            val shrink = 1f - LargeTitleShrink * t
                            scaleX = shrink
                            scaleY = shrink
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                    )
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
                if (feedIsEmpty && !isLoading) {
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
                        )
                    }
                }

                keepListening?.takeIf { it.isNotEmpty() }?.let { items ->
                    item(key = "home_shortcuts", contentType = "shortcuts") {
                        // Apple Music's "Top Picks for You": tall cards coloured by their own artwork.
                        HomeTopPicks(
                            items = items.take(10),
                            onItemClick = onShortcutClick,
                            onItemLongClick = onShortcutLongClick,
                            modifier = Modifier.animateItem(),
                            activeId = mediaMetadata?.id,
                            isPlaying = isPlaying,
                            // The first cards on screen arrive in a short cascade rather than as one block.
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

            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }
}

/**
 * The Apple Music large title.
 *
 * Two lines: the weekday, quiet and small, over the section name at display weight. Apple leads
 * Home with the day rather than a greeting because it is the one piece of context that is always
 * true and never has to guess at the hour or the mood.
 *
 * Horizontal insets are applied the same way [com.ozyern.exhale.ui.component.NavigationTitle] does
 * them, so the title's left edge lands exactly on the section headers below it.
 *
 * No account button here. It briefly had one, on the reasoning that Apple Music puts the avatar
 * level with the large title — but this app already has one in the app bar directly above, so the
 * result was two account circles stacked twenty pixels apart. The app bar's is the original and
 * the one that is present on every tab; this is a title, and titles do not carry controls.
 */
@Composable
private fun HomeLargeTitle(
    modifier: Modifier = Modifier,
) {
    val weekday = remember {
        DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()).format(LocalDate.now())
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    ) {
        // The eyebrow steps back: semibold at label size, not bold at title size. It was
        // competing with the line under it, and an eyebrow that competes with its own headline
        // is just a two-line heading.
        Text(
            text = stringResource(R.string.home),
            // Tracked in hard. At 36sp the default fitting leaves visible gaps between letters
            // — the difference between a word that has been *set* and one that has been typed.
            // This is the largest type in the app, so it is where loose tracking shows most.
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

/* ------------------------------------------------------------------------------------------- */
/* The feed's arrival                                                                           */
/* ------------------------------------------------------------------------------------------- */

/**
 * How long the whole page takes to arrive. Long enough that the stagger is legible as one motion,
 * short enough that it is over before anyone could want to scroll.
 */
private const val FeedIntroDurationMs = 620

/** A shelf's head start over the one below it, as a fraction of [FeedIntroDurationMs]. */
private const val FeedIntroStagger = 0.07f

/** How much of the window one shelf's own rise occupies. */
private const val FeedIntroSlice = 0.55f

/** How far apart two shortcut tiles arrive, in stagger slots. */
private const val ShortcutTileStagger = 0.34f

/** How far a shelf travels on its way in. Small: this is a settle, not an entrance. */
private val FeedIntroRise = 16.dp

/**
 * How much slower the large title drifts than the list it is in. 0 would scroll it away with
 * everything else; 1 would pin it. A third is enough for the eye to read the two planes apart.
 */
private const val LargeTitleParallax = 0.35f

/** How much of itself the large title gives up on the way out. */
private const val LargeTitleShrink = 0.16f

/** How far under full size a shelf starts. Two percent: felt, never seen. */
private const val FeedIntroScale = 0.02f

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

        // A shelf grows the last two percent into place as it rises. On its own the rise is a
        // sheet of content sliding up a track; with the scale it is an object coming to rest at
        // its own distance, which is the difference between a transition that has been applied to
        // the page and one the page appears to be doing.
        //
        // Two percent because the effect has to survive being read at a glance and never be
        // legible as zooming. Anything past about four reads as a card being thrown at you.
        val grown = 1f - FeedIntroScale * (1f - eased)
        scaleX = grown
        scaleY = grown
        transformOrigin = TransformOrigin(0.5f, 0f)
    }
