/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */


package com.ozyern.exhale.ui.screens.search

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import com.ozyern.exhale.extensions.toMediaItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.ozyern.exhale.innertube.YouTube.SearchFilter
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.ozyern.exhale.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.audioTrackFor
import com.ozyern.exhale.innertube.models.bestSongFor
import com.ozyern.exhale.innertube.models.isNamedBy
import com.ozyern.exhale.innertube.models.isMusicVideo
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.constants.SearchFilterHeight
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.ChipsRow
import com.ozyern.exhale.ui.component.EmptyPlaceholder
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.YouTubeListItem
import com.ozyern.exhale.ui.component.shimmer.ListItemPlaceHolder
import com.ozyern.exhale.ui.component.shimmer.ShimmerHost
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.menu.YouTubeArtistMenu
import com.ozyern.exhale.ui.menu.YouTubePlaylistMenu
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.viewmodels.OnlineSearchViewModel
import kotlinx.coroutines.launch
import kotlin.text.get

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchResult(
    navController: NavController,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    // The lyric matches arrive after the results and go in above them; a list still at its top
    // would otherwise keep its place and leave them hidden above the first row.
    androidx.compose.runtime.LaunchedEffect(viewModel.lyricMatches, viewModel.linkMatch) {
        if ((viewModel.lyricMatches.isNotEmpty() || viewModel.linkMatch != null) && lazyListState.firstVisibleItemIndex <= 3) {
            lazyListState.animateScrollToItem(0)
        }
    }

    val searchFilter by viewModel.filter.collectAsState()
    val searchSummary = viewModel.summaryPage
    val itemsPage by remember(searchFilter) {
        derivedStateOf {
            searchFilter?.value?.let {
                viewModel.viewStateMap[it]
            }
        }
    }
    // Sections in "All" mode, each carrying the filter that shows the rest of it. The top
    // summary ("Top result") has no filter of its own, so its header gets no See-all affordance.
    val allModeSections =
        buildList<Triple<String, List<YTItem>, SearchFilter?>> {
            searchSummary?.summaries?.firstOrNull()?.takeIf { it.items.isNotEmpty() }?.let {
                add(Triple(it.title, it.items, null))
            }

            listOf(
                FILTER_SONG to stringResource(R.string.filter_songs),
                FILTER_ARTIST to stringResource(R.string.filter_artists),
                FILTER_ALBUM to stringResource(R.string.filter_albums),
                FILTER_VIDEO to stringResource(R.string.filter_videos),
                FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
            ).forEach { (sectionFilter, sectionTitle) ->
                viewModel.viewStateMap[sectionFilter.value]
                    ?.items
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { items ->
                        add(Triple(sectionTitle, items, sectionFilter))
                    }
            }
        }

    val isAllModeLoaded =
        searchSummary != null ||
                listOf(
                    FILTER_SONG,
                    FILTER_VIDEO,
                    FILTER_ALBUM,
                    FILTER_ARTIST,
                    FILTER_COMMUNITY_PLAYLIST,
                    FILTER_FEATURED_PLAYLIST,
                ).all { viewModel.viewStateMap.containsKey(it.value) }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" }
        }.collect { shouldLoadMore ->
            if (!shouldLoadMore) return@collect
            viewModel.loadMore()
        }
    }

    val openItem: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(YouTubeQueue(WatchEndpoint(videoId = item.id), item.toMediaMetadata()))
                }
            }
            is AlbumItem -> navController.navigate("album/${item.id}")
            is ArtistItem -> navController.navigate("artist/${item.id}")
            is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val ytLongClick: (YTItem) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show {
            when (item) {
                is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
            }
        }
    }
    val ytItemContent: @Composable LazyItemScope.(YTItem) -> Unit = { item: YTItem ->
        val longClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuState.show {
                when (item) {
                    is SongItem ->
                        YouTubeSongMenu(
                            song = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )

                    is AlbumItem ->
                        YouTubeAlbumMenu(
                            albumItem = item,
                            navController = navController,
                            onDismiss = menuState::dismiss,
                        )

                    is ArtistItem ->
                        YouTubeArtistMenu(
                            artist = item,
                            onDismiss = menuState::dismiss,
                        )

                    is PlaylistItem ->
                        YouTubePlaylistMenu(
                            playlist = item,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                }
            }
        }
        SearchResultRow(
            item = item,
            isCurrent = when (item) {
                is SongItem -> mediaMetadata?.id == item.id
                is AlbumItem -> mediaMetadata?.album?.id == item.id
                else -> false
            },
            isPlaying = isPlaying,
            onClick = { openItem(item) },
            onLongClick = longClick,
            modifier = Modifier.animateItem(),
        )
    }

    // With the Search bar set to the top (Appearance > Search), the field rests over this page,
    // so the filter row and the list start below it rather than underneath it.
    val (searchFieldPosition) = com.ozyern.exhale.utils.rememberEnumPreference(
        com.ozyern.exhale.constants.SettingsSearchPositionKey,
        com.ozyern.exhale.constants.SettingsSearchPosition.BOTTOM,
    )
    val topField = if (searchFieldPosition == com.ozyern.exhale.constants.SettingsSearchPosition.TOP) {
        com.ozyern.exhale.ui.component.InputFieldHeight + 14.dp
    } else {
        0.dp
    }
    val topInset = WindowInsets.systemBars.only(WindowInsetsSides.Top).asPaddingValues()
        .calculateTopPadding() + topField

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = lazyListState,
            // Results at a readable measure on a tablet, centred, rather than rows a screen wide
            // with the duration a hand-span from the title.
            modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(
                // The filter row is no longer an opaque slab pinned under a top search bar —
                // it floats over the list — so the list only has to clear the status bar plus
                // the row's own height. `AppBarHeight` used to be reserved on top of that for
                // a search field that is now docked at the bottom of the screen instead.
                top = topInset + SearchFilterHeight + 12.dp,
                // Past the mini player *and* the docked search field under it.
                bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                    .calculateBottomPadding() + 84.dp,
            ),
        ) {
            if (searchFilter == null) {
                // A Spotify playlist link: bring the whole playlist in.
                viewModel.spotifyPlaylistLink?.let { link ->
                    item(key = "spotify_import") {
                        var show by remember { androidx.compose.runtime.mutableStateOf(false) }
                        if (show) {
                            com.ozyern.exhale.spotify.SpotifyImportDialog(
                                initialLink = link,
                                onDismiss = { show = false },
                                onImported = { id ->
                                    show = false
                                    navController.navigate("local_playlist/$id")
                                },
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1ED760).copy(alpha = 0.16f))
                                .clickable { show = true }
                                .padding(16.dp),
                        ) {
                            Icon(painterResource(R.drawable.spotify_icon), null, tint = Color(0xFF1ED760), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Import this Spotify playlist", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "Every song found on YouTube Music and saved to your library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                // A link from Spotify, the iOS music app, Deezer, Tidal…: the same song or album, here.
                if (viewModel.linkLoading || viewModel.linkMatch != null) {
                    item(key = "link_header") {
                        SearchSectionHeader(title = "From your link", onSeeAll = null, modifier = Modifier.animateItem())
                    }
                    val match = viewModel.linkMatch
                    if (match != null) {
                        item(key = "link/${match.id}") { ytItemContent(match) }
                    } else {
                        item(key = "link_loading") {
                            Text(
                                text = "Finding it on YouTube Music…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
                // A line of a song typed into search: the songs it comes from, above everything
                // else, each marked as found by its lyrics with the line that matched — even one
                // YouTube also put first, so the reason it is there is never missing.
                val fromLyrics = viewModel.lyricMatches
                if (fromLyrics.isNotEmpty()) {
                    item(key = "lyrics_header") {
                        SearchSectionHeader(title = "From the lyrics", onSeeAll = null, modifier = Modifier.animateItem())
                    }
                    itemsIndexed(fromLyrics, key = { _, match -> "lyrics/${match.song.id}" }) { i, match ->
                        ytItemContent(match.song)
                        LyricsMatchNote(line = match.line)
                        if (i < fromLyrics.lastIndex) SearchRowDivider()
                    }
                }
                // The top result is the song that was typed. YouTube's own lead is often its music
                // video, a remix, an edit or a cover, so the plain track named by the search is found
                // among the top section and the Songs results (see bestSongFor) and leads instead.
                // An artist, album or playlist stays on top only when its exact name was typed.
                val topItems = allModeSections.firstOrNull()?.takeIf { it.third == null }?.second.orEmpty()
                val topLead = topItems.firstOrNull()
                val songResults = viewModel.viewStateMap[FILTER_SONG.value]?.items.orEmpty()
                val topHero = when {
                    topLead == null -> null
                    topLead.isNamedBy(viewModel.query) -> topLead
                    else -> (topItems + songResults).bestSongFor(viewModel.query)
                        ?: if (topLead.isMusicVideo) songResults.audioTrackFor(topLead) ?: topLead else topLead
                }
                // Each thing once. The top section, the Songs, the Videos and the rest are separate
                // searches that overlap heavily, so the same song turned up three times down the page,
                // and again under the matches found by link or by lyric. Everything already shown
                // above is dropped, by id, and a song is also dropped when another upload of the same
                // title by the same artist is already there. Videos are keyed apart from songs, so the
                // official video still shows beside the song it is of.
                val seenIds = HashSet<String>()
                val seenSongs = HashSet<String>()
                fun firstShowing(item: YTItem): Boolean {
                    if (!seenIds.add(item.id)) return false
                    if (item !is SongItem) return true
                    val key = (if (item.isMusicVideo) "video|" else "song|") +
                        item.title.lowercase().trim() + "|" + item.artists.firstOrNull()?.name?.lowercase()?.trim()
                    return seenSongs.add(key)
                }
                viewModel.linkMatch?.let { firstShowing(it) }
                fromLyrics.forEach { firstShowing(it.song) }
                topHero?.let { firstShowing(it) }
                val dedupedSections = allModeSections.mapIndexed { i, (title, sectionItems, sectionFilter) ->
                    // The hero stays in the top section (it is drawn from there) and nowhere else,
                    // even when it was borrowed from the Songs results.
                    val isTop = i == 0 && sectionFilter == null
                    Triple(
                        title,
                        sectionItems.filter { if (it.id == topHero?.id) isTop else firstShowing(it) },
                        sectionFilter,
                    )
                }
                dedupedSections.forEachIndexed { index, (title, sectionItems, sectionFilter) ->
                    val hero = if (index == 0 && sectionFilter == null) topHero else null
                    if (hero != null) {
                        item(key = "hero_${hero.id}") {
                            TopResultCard(
                                item = hero,
                                isCurrent = hero.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                onOpen = { openItem(hero) },
                                onAddToQueue = {
                                    (hero as? SongItem)?.let {
                                        playerConnection.addToQueue(it.toMediaItem())
                                        android.widget.Toast.makeText(
                                            context, "Added to queue", android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onMore = { ytLongClick(hero) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    val rows = if (hero != null) sectionItems.filter { it.id != hero.id } else sectionItems
                    if (rows.isNotEmpty()) item(key = "section_header_${title}_$index") {
                        SearchSectionHeader(
                            title = title,
                            // "See all" swaps the chip row onto this section's filter, which is
                            // the same thing tapping the chip does — so the header is a shortcut
                            // to the full list rather than a dead label.
                            onSeeAll = sectionFilter?.let {
                                {
                                    if (viewModel.filter.value != it) viewModel.filter.value = it
                                    coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    itemsIndexed(
                        items = rows,
                        key = { itemIndex, item -> "$title/${item.id}/$itemIndex" },
                    ) { itemIndex, item ->
                        // Each section is one grouped card, as the iOS music app lists results: the rows
                        // share a plate, rounded at its ends, parted by hairlines inset to the text.
                        androidx.compose.foundation.layout.Column(
                            Modifier
                                .padding(horizontal = 12.dp)
                                .clip(groupedShape(itemIndex, rows.size))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .padding(
                                    top = if (itemIndex == 0) 6.dp else 0.dp,
                                    bottom = if (itemIndex == rows.lastIndex) 6.dp else 0.dp,
                                ),
                        ) {
                            ytItemContent(item)
                            if (itemIndex < rows.lastIndex) SearchRowDivider()
                        }
                    }
                }

                // Not under a link's match or a playlist import: something was found, the rest
                // is only still on its way.
                if (allModeSections.isEmpty() && isAllModeLoaded && viewModel.linkMatch == null &&
                    !viewModel.linkLoading && viewModel.spotifyPlaylistLink == null
                ) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                        )
                    }
                }
            } else {
                val filtered = itemsPage?.items.orEmpty().distinctBy { it.id }
                itemsIndexed(
                    items = filtered,
                    key = { _, it -> "filtered_${it.id}" },
                ) { i, item ->
                    ytItemContent(item)
                    if (i < filtered.lastIndex) SearchRowDivider()
                }

                if (itemsPage?.continuation != null) {
                    item(key = "loading") {
                        ShimmerHost {
                            repeat(3) {
                                ListItemPlaceHolder()
                            }
                        }
                    }
                }

                if (itemsPage?.items?.isEmpty() == true) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.search,
                            text = stringResource(R.string.no_results_found),
                        )
                    }
                }
            }

            if (searchFilter == null && allModeSections.isEmpty() && !isAllModeLoaded ||
                searchFilter != null && itemsPage == null
            ) {
                item {
                    ShimmerHost {
                        repeat(8) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            }
        }

        // Floating filter row. It used to be a `Surface` with a shadow — an opaque grey plank
        // welded across the top of the results, which is the single most dated thing on this
        // page. Now the chips ride on a scrim that fades to nothing, so results scroll up and
        // dissolve under them instead of hitting a hard edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        0.72f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(top = topInset, bottom = 10.dp),
        ) {
            SearchFilterPills(
                chips = listOf(
                    null to stringResource(R.string.filter_all),
                    FILTER_SONG to stringResource(R.string.filter_songs),
                    FILTER_ARTIST to stringResource(R.string.filter_artists),
                    FILTER_ALBUM to stringResource(R.string.filter_albums),
                    FILTER_VIDEO to stringResource(R.string.filter_videos),
                    FILTER_COMMUNITY_PLAYLIST to stringResource(R.string.filter_community_playlists),
                    FILTER_FEATURED_PLAYLIST to stringResource(R.string.filter_featured_playlists),
                ),
                current = searchFilter,
                onSelect = {
                    if (viewModel.filter.value != it) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.filter.value = it
                    }
                    coroutineScope.launch { lazyListState.animateScrollToItem(0) }
                },
            )
        }
    }
}

/**
 * Section heading for "All" results.
 *
 * The old one was a 3dp accent tick beside 14sp semibold text — a Material-2 era list caption.
 * This is a real heading: the title at `titleLarge` weight so sections separate at a glance
 * while scrolling fast, and a "See all" chip that jumps the chip row to that category instead
 * of leaving the user to find the matching filter themselves.
 */
@Composable
private fun SearchSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 10.dp, top = 18.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSeeAll != null) {
            Text(
                text = "See all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Rounded at the top of a group's first row and the bottom of its last, square between. */
private fun groupedShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val r = 22.dp
    val z = 0.dp
    return when {
        count <= 1 -> androidx.compose.foundation.shape.RoundedCornerShape(r)
        index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(r, r, z, z)
        index == count - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(z, z, r, r)
        else -> androidx.compose.ui.graphics.RectangleShape
    }
}

/**
 * The best match, big: its artwork, what it is and who by, and a play disc — the one result a
 * search is usually for, set apart from the list under it.
 */
@Composable
private fun SearchHeroCard(
    item: YTItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
    val (kind, by) = when (item) {
        is SongItem -> "Song" to item.artists.joinToString(", ") { it.name }
        is AlbumItem -> "Album" to item.artists.orEmpty().joinToString(", ") { it.name }
        is ArtistItem -> "Artist" to ""
        is PlaylistItem -> "Playlist" to item.author?.name.orEmpty()
        else -> "" to ""
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .liquidGlassSurface(cardShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        val artShape = if (item is ArtistItem) androidx.compose.foundation.shape.CircleShape
        else androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
        coil3.compose.AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(92.dp)
                .clip(artShape),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(kind, by).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (item is SongItem) {
            Spacer(Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
                    .clickable(onClick = onClick),
            ) {
                Icon(
                    painter = painterResource(if (isCurrent && isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * The search filters, as the reference sets them: full capsules, the unselected ones a quiet dark
 * grey with warm off-white words, the chosen one filled in the app's accent with white words —
 * colour, not inversion, says which is on. Scrolls sideways so no label is ever squeezed.
 */
@Composable
private fun SearchFilterPills(
    chips: List<Pair<com.ozyern.exhale.innertube.YouTube.SearchFilter?, String>>,
    current: com.ozyern.exhale.innertube.YouTube.SearchFilter?,
    onSelect: (com.ozyern.exhale.innertube.YouTube.SearchFilter?) -> Unit,
) {
    // The same chip as Home's mood row: Convx 2.0.4's, red when chosen.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        chips.forEach { (value, label) ->
            com.ozyern.exhale.ui.component.GlassChip(
                label = label,
                selected = value == current,
                iconRes = null,
                onClick = { onSelect(value) },
            )
        }
    }
}

/** The hairline between results, inset to start under the titles. */
@Composable
private fun SearchRowDivider() {
    Box(
        Modifier
            .padding(start = 78.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

/**
 * One result: 52dp artwork (round for an artist), the title, what it is and
 * who by, the running time for a song, and a "more" at the end.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    item: YTItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artShape = if (item is ArtistItem) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> listOfNotNull("Album", item.artists?.joinToString(", ") { it.name }, item.year?.toString())
            .filter { it.isNotBlank() }.joinToString(" \u00b7 ")
        is ArtistItem -> "Artist"
        is PlaylistItem -> listOfNotNull("Playlist", item.author?.name).joinToString(" \u00b7 ")
        else -> ""
    }
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            coil3.compose.AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(artShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (isCurrent) {
                Box(
                    Modifier.size(52.dp).clip(artShape).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.graphic_eq else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.explicit) {
                    Icon(
                        painterResource(R.drawable.explicit),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        (item as? SongItem)?.duration?.takeIf { it > 0 }?.let { seconds ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = "%d:%02d".format(seconds / 60, seconds % 60),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.more_vert),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * The search's best match, promoted: "Top result", a 72dp cover beside the title and who it's by,
 * and for a song two plain buttons under it — Play, and Add to queue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopResultCard(
    item: YTItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onAddToQueue: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (kind, by) = when (item) {
        is SongItem -> "Song" to item.artists.joinToString(", ") { it.name }
        is AlbumItem -> "Album" to item.artists.orEmpty().joinToString(", ") { it.name }
        is ArtistItem -> "Artist" to ""
        is PlaylistItem -> "Playlist" to item.author?.name.orEmpty()
        else -> "" to ""
    }
    val artShape = if (item is ArtistItem) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)
            .combinedClickable(onClick = onOpen, onLongClick = onMore),
    ) {
        Text(
            text = "Top result",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            coil3.compose.AsyncImage(
                model = item.thumbnail,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(artShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(kind, by).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onMore),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.more_vert), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (item is SongItem) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                TopResultButton(
                    icon = if (isCurrent && isPlaying) R.drawable.pause else R.drawable.play,
                    label = if (isCurrent && isPlaying) "Pause" else stringResource(R.string.play),
                    onClick = onOpen,
                )
                TopResultButton(icon = R.drawable.queue_music, label = "Add to queue", onClick = onAddToQueue)
            }
        }
    }
}

@Composable
private fun TopResultButton(icon: Int, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Under a song found through its lyrics: a small "Lyrics match" capsule, then the line that
 * matched, so it is plain why a song with another title came up for what was typed.
 */
@androidx.compose.runtime.Composable
private fun LyricsMatchNote(line: String?) {
    val accent = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.padding(start = 84.dp, end = 20.dp, bottom = 10.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier
                .background(accent.copy(alpha = 0.16f), androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.lyrics),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
            Text(
                text = "Lyrics match",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
            )
        }
        if (line != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            Text(
                text = "“$line”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
