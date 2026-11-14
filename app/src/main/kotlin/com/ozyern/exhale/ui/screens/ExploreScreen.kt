/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.utils.plusStart
import com.ozyern.exhale.ui.utils.bleedStart
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.screens.library.apple.SfStyle
import com.ozyern.exhale.ui.screens.library.apple.YtPopTile
import com.music.innertube.pages.ChartsPage.ChartType
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import com.ozyern.exhale.ui.utils.youtubeBrowseRoute
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import com.ozyern.exhale.ui.utils.rememberEdgeAwareFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.hazeSource
import com.ozyern.exhale.ui.component.TopFadeBlur
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.foundation.layout.windowInsetsPadding
import org.koin.androidx.compose.koinViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.pages.BrowseResult
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.composables.sfsymbols.SfSymbols
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.ui.component.rememberAppBackgroundColor
import com.ozyern.exhale.ui.component.SpotlightCard
import com.ozyern.exhale.ui.component.SpotlightCardArtworkSize
import com.ozyern.exhale.ui.component.SpotlightCardHeight
import com.ozyern.exhale.ui.component.SpotlightCardSpacing
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.theme.LocalAccentTextColor
import com.ozyern.exhale.ui.component.shimmer.GridItemPlaceHolder
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.component.shimmer.TextPlaceholder
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.menu.YouTubeArtistMenu
import com.ozyern.exhale.ui.menu.YouTubePlaylistMenu
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.ui.utils.albumRoute
import com.ozyern.exhale.ui.utils.artistRoute
import com.ozyern.exhale.ui.utils.onlinePlaylistRoute
import com.ozyern.exhale.ui.utils.SnapLayoutInfoProvider
import com.ozyern.exhale.utils.listItemShape
import com.ozyern.exhale.utils.reportException
import com.ozyern.exhale.viewmodels.ChartsViewModel
import com.ozyern.exhale.viewmodels.ExploreViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Explore, as an Apple Music category page (the "Spatial Audio" page pattern): a large
 * title, a row of large editorial cards (new releases), then titled shelves —
 * Recommended for You, New Releases, Top Songs — and Moods & Genres as Search's duotone
 * category tiles. Every card pops Apple's menu on hold.
 */
@Composable
fun ExploreScreen(
    navController: NavController,
    exploreViewModel: ExploreViewModel = koinViewModel(),
    chartsViewModel: ChartsViewModel = koinViewModel(),
    tilesViewModel: com.ozyern.exhale.ui.screens.search.AppleSearchViewModel = koinViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val explorePage by exploreViewModel.explorePage.collectAsStateWithLifecycle()
    val recommended by exploreViewModel.recommendedForYou.collectAsStateWithLifecycle()
    val chartsPage by chartsViewModel.chartsPage.collectAsStateWithLifecycle()
    val art by tilesViewModel.art.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (chartsPage == null) chartsViewModel.loadCharts() }

    val colors = com.ozyern.exhale.ui.ios.foundation.IosTheme.colors
    val pageInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val sideInset = pageInsets.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
    val bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    fun open(item: YTItem) {
        when (item) {
            is SongItem -> playerConnection.playQueue(
                YouTubeQueue(com.music.innertube.models.WatchEndpoint(videoId = item.id), item.toMediaMetadata()),
            )
            is AlbumItem -> navController.navigate(albumRoute(item.id, item.thumbnail))
            is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
            is PlaylistItem -> navController.navigate(onlinePlaylistRoute(item.id, item.title, item.thumbnail))
        }
    }

    fun sheet(item: YTItem) {
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
            }
        }
    }

    val albums = explorePage?.newReleaseAlbums.orEmpty().distinctBy { it.id }
    val hero = albums.take(6)
    val newReleases = albums.drop(6)
    val topSongs = chartsPage?.sections?.firstOrNull { it.chartType == ChartType.TOP }?.items.orEmpty().distinctBy { it.id }
    val moods = explorePage?.moodAndGenres.orEmpty()
    val recommendedTitle = stringResource(R.string.recommended_for_you)
    val newTitle = stringResource(R.string.new_release_albums)
    val topTitle = stringResource(R.string.top_songs)

    @Composable
    fun Tile(item: YTItem, card: @Composable () -> Unit) = YtPopTile(
        item = item,
        navController = navController,
        player = playerConnection,
        subtitle = item.browseSubtitle(),
        onClick = { open(item) },
        onMore = { sheet(item) },
    ) { card() }

    // The activity owns the shared title and frosted top bar.
    Box(Modifier.fillMaxSize().background(colors.systemBackground)) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            start = sideInset,
            top = maxOf(pageInsets.calculateTopPadding(), if (sideInset > 0.dp) 96.dp else 116.dp),
            bottom = bottom + 16.dp,
        ),
    ) {
        if (hero.isNotEmpty()) {
            item(key = "hero", contentType = "hero") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp).plusStart(sideInset),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 8.dp).bleedStart(sideInset),
                ) {
                    items(hero, key = { "hero_" + it.id }, contentType = { "hero_card" }) { item ->
                        Tile(item) { EditorialCard(item) }
                    }
                }
            }
        }
        exploreShelf("recommended", recommendedTitle, recommended.orEmpty().distinctBy { it.id }, sideInset) { item ->
            Tile(item) { ShelfCard(item) }
        }
        exploreShelf("new", newTitle, newReleases, sideInset) { item ->
            Tile(item) { ShelfCard(item) }
        }
        exploreShelf("top", topTitle, topSongs, sideInset) { item ->
            Tile(item) { ShelfCard(item) }
        }
        if (moods.isNotEmpty()) {
            item(key = "moods_title", contentType = "shelf_title") { ShelfTitle(stringResource(R.string.mood_and_genres)) }
            item(key = "moods_row", contentType = "mood_tiles") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp).plusStart(sideInset),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp).bleedStart(sideInset),
                ) {
                    items(
                        items = moods,
                        key = { "mood_${it.endpoint.browseId}_${it.endpoint.params}" },
                        contentType = { "mood_tile" },
                    ) { mood ->
                        LaunchedEffect(mood) { tilesViewModel.loadArt(mood) }
                        com.ozyern.exhale.ui.screens.search.CategoryTile(
                            title = mood.title,
                            color = Color(mood.stripeColor),
                            art = art[mood.endpoint.browseId + "|" + mood.endpoint.params],
                            modifier = Modifier.width(180.dp),
                        ) {
                            navController.navigate(youtubeBrowseRoute(mood.endpoint.browseId, mood.endpoint.params))
                        }
                    }
                }
            }
        }
    }
    }
}

/** A titled row of cards; nothing when [items] is empty. */
private fun androidx.compose.foundation.lazy.LazyListScope.exploreShelf(
    key: String,
    title: String,
    items: List<YTItem>,
    sideInset: androidx.compose.ui.unit.Dp,
    card: @Composable (YTItem) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "${key}_title", contentType = "shelf_title") { ShelfTitle(title) }
    item(key = "${key}_row", contentType = "shelf") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp).plusStart(sideInset),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.bleedStart(sideInset),
        ) {
            items(items, key = { "${key}_" + it.id }, contentType = { "shelf_card" }) { card(it) }
        }
    }
}

@Composable
private fun ShelfTitle(title: String) {
    Text(
        text = title,
        style = SfStyle(22.sp, 28.sp, FontWeight.Bold, (-0.26).sp),
        color = com.ozyern.exhale.ui.ios.foundation.IosTheme.colors.label,
        maxLines = 1,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
    )
}
