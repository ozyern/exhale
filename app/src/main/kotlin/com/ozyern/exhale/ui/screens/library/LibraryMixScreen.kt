/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.library

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.LibraryFilter
import com.ozyern.exhale.constants.MixSortDescendingKey
import com.ozyern.exhale.constants.MixSortType
import com.ozyern.exhale.constants.MixSortTypeKey
import com.ozyern.exhale.constants.PlaylistTagsFilterKey
import com.ozyern.exhale.constants.ShowCachedPlaylistKey
import com.ozyern.exhale.constants.ShowDownloadedPlaylistKey
import com.ozyern.exhale.constants.ShowLikedPlaylistKey
import com.ozyern.exhale.constants.ShowSpotifyPlaylistsKey
import com.ozyern.exhale.constants.ShowTopPlaylistKey
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.spotify.SpotifyLibraryViewModel
import com.ozyern.exhale.ui.component.ExpressivePullToRefreshBox
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.PlaylistThumbnail
import com.ozyern.exhale.ui.component.SortHeader
import com.ozyern.exhale.ui.menu.AlbumMenu
import com.ozyern.exhale.ui.menu.PlaylistMenu
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.LibraryMixViewModel
import androidx.compose.runtime.rememberCoroutineScope
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

/*
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 * The Library home, rebuilt on Apple Music's shape.
 *
 * What was here: a display title, a row of five filter chips on a glass plate, a sort card, a
 * hero tile plus a 2-up grid of pinned collections, the full vertical list of every playlist you
 * own, and then three horizontal carousels — Spotify, Albums, Artists — each with a "View all"
 * pill. Six different ways of presenting a collection, stacked, on one screen. The page had no
 * answer to "where do I tap to get to my albums", because it had three.
 *
 * What Apple does, and what this is now:
 *
 *  1. A large title, and nothing beside it.
 *  2. A plain inset list — Playlists, Artists, Albums, Songs — one row per destination, icon,
 *     label, chevron. Not chips. A chip row is a *filter*: it says these five things are facets
 *     of one view and you may hold one at a time. These are five separate places, and a list of
 *     rows with disclosure arrows is the oldest, plainest way of saying so.
 *  3. The pinned collections in that same list, one group down: Liked, Downloaded, Cached, Top.
 *     Apple puts "Downloaded" in exactly this position for exactly this reason — it is a place
 *     you go, not a card you look at, and giving it a hero tile made the page's largest, most
 *     colourful object one that nobody needed to see twice.
 *  4. "Recently Added": a two-column grid of artwork, newest first, mixing albums and playlists.
 *     This is the whole rest of the page. It replaces all three carousels, and it is strictly
 *     more: a sideways row shows two-and-a-bit items and hides the rest behind a gesture nobody
 *     performs, while the grid shows six above the fold and keeps going as you scroll.
 *
 * The individual playlist rows are gone from this screen because they are the Playlists tab,
 * which already lists them — with drag-to-reorder, which this screen was maintaining a second
 * copy of. One list, one place.
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 */

/** Apple's library gutter. Wider than the app's usual 16dp, and the reason the page reads calm. */
private val LibraryGutter = 20.dp

/** Leading inset of the hairline between list rows — aligned to the label, not the screen edge. */
private val RowDividerInset = LibraryGutter + 30.dp + 14.dp

private val CategoryIconSize = 26.dp
private val GridGutter = 16.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    onTabSelected: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
    spotifyLibraryViewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.CREATE_DATE,
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val (selectedTagsFilter) = rememberPreference(PlaylistTagsFilterKey, "")
    val selectedTagIds = remember(selectedTagsFilter) {
        selectedTagsFilter.split(",").filter { it.isNotBlank() }.toSet()
    }
    val filteredPlaylistIds by database.playlistIdsByTags(
        if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
    ).collectAsState(initial = emptyList())

    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, false)
    val spotifyPlaylists by spotifyLibraryViewModel.playlists.collectAsStateWithLifecycle()

    val albums by viewModel.albums.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)

    val topSize by viewModel.topValue.collectAsState(initial = "50")
    val likedTitle = stringResource(R.string.liked)
    val downloadedTitle = stringResource(R.string.offline)
    val cachedTitle = stringResource(R.string.cached_playlist)
    val topTitle = stringResource(R.string.my_top) + " $topSize"

    val visiblePlaylists = remember(playlists, selectedTagIds, filteredPlaylistIds) {
        if (selectedTagIds.isEmpty()) playlists else playlists.filter { it.id in filteredPlaylistIds }
    }

    // ── Recently Added ──────────────────────────────────────────────────────────────────────
    //
    // Albums and playlists in one sequence, which is the point of the section: what you added
    // last, whatever kind of thing it was. Sorting them into separate shelves — which is what the
    // old carousels did — asks you to remember the *type* of the thing you are looking for before
    // you can look for it.
    val collator = remember {
        Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
    }
    val songsLabel = stringResource(R.string.filter_songs)

    val recentlyAdded = remember(albums, visiblePlaylists, sortType, sortDescending, collator) {
        val entries = buildList {
            albums.forEach { album ->
                add(
                    RecentEntry(
                        key = "album_${album.id}",
                        title = album.album.title,
                        subtitle = album.artists.joinToString { it.name },
                        thumbnails = listOfNotNull(album.album.thumbnailUrl),
                        createdAt = album.album.bookmarkedAt,
                        updatedAt = album.album.lastUpdateTime,
                        album = album,
                        playlist = null,
                    )
                )
            }
            visiblePlaylists.forEach { playlist ->
                add(
                    RecentEntry(
                        key = "playlist_${playlist.id}",
                        title = playlist.playlist.name,
                        subtitle = "${playlist.songCount} $songsLabel",
                        thumbnails = playlist.thumbnails,
                        createdAt = playlist.playlist.bookmarkedAt ?: playlist.playlist.createdAt,
                        updatedAt = playlist.playlist.lastUpdateTime,
                        album = null,
                        playlist = playlist,
                    )
                )
            }
        }

        val sorted = when (sortType) {
            // `LocalDateTime?` does not sort itself. A null bookmark date is an item with no known
            // add time, and it belongs at the *old* end whichever way the list is facing —
            // sortedBy would silently float them all to the top of a newest-first list.
            MixSortType.CREATE_DATE -> entries.sortedWith(
                compareBy(nullsFirst<LocalDateTime>()) { it.createdAt }
            )

            MixSortType.LAST_UPDATED -> entries.sortedWith(
                compareBy(nullsFirst<LocalDateTime>()) { it.updatedAt }
            )

            MixSortType.NAME -> entries.sortedWith(compareBy(collator) { it.title })
        }
        if (sortDescending) sorted.asReversed() else sorted
    }

    val recentRows = remember(recentlyAdded) { recentlyAdded.chunked(2) }

    // ── Pinned collections ──────────────────────────────────────────────────────────────────
    //
    // Same list idiom as the destinations, one gap down. Keeping their individual accent colours
    // is the one deviation from Apple, which tints every icon in its library list the same red —
    // these four are not navigation, they are four specific auto-playlists, and colour is the
    // fastest way to tell them apart at a glance.
    val pinned = buildList {
        if (showLiked) add(
            PinnedEntry(likedTitle, R.drawable.favorite, "auto_playlist/liked", MaterialTheme.colorScheme.error)
        )
        if (showDownloaded) add(
            PinnedEntry(downloadedTitle, R.drawable.offline, "auto_playlist/downloaded", MaterialTheme.colorScheme.primary)
        )
        if (showCached) add(
            PinnedEntry(cachedTitle, R.drawable.cached, "cache_playlist/cached", MaterialTheme.colorScheme.tertiary)
        )
        if (showTop) add(
            PinnedEntry(topTitle, R.drawable.trending_up, "top_playlist/$topSize", MaterialTheme.colorScheme.secondary)
        )
    }

    val lazyListState = rememberLazyListState()

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.syncAllLibrary() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "large_title", contentType = "large_title") {
                LibraryLargeTitle()
            }

            // ── Destinations: a 2×2 of tiles rather than a settings-style list ──
            item(key = "categories", contentType = "categories") {
                val destinations = buildList {
                    add(Triple(stringResource(R.string.filter_playlists), R.drawable.queue_music) { onTabSelected(LibraryFilter.PLAYLISTS) })
                    add(Triple(stringResource(R.string.filter_artists), R.drawable.person) { onTabSelected(LibraryFilter.ARTISTS) })
                    add(Triple(stringResource(R.string.filter_albums), R.drawable.album) { onTabSelected(LibraryFilter.ALBUMS) })
                    add(Triple(stringResource(R.string.filter_songs), R.drawable.music_note) { onTabSelected(LibraryFilter.SONGS) })
                    if (showSpotifyPlaylists && spotifyPlaylists.isNotEmpty()) {
                        add(Triple(stringResource(R.string.spotify), R.drawable.spotify_icon) { onTabSelected(LibraryFilter.SPOTIFY) })
                    }
                }
                LibraryDestinationGrid(destinations = destinations, gutter = LibraryGutter)
            }

            // ── Your collections: a colour each and a glyph ──
            if (pinned.isNotEmpty()) {
                item(key = "pinned", contentType = "pinned") {
                    Column {
                        LibraryShelfHeader(title = "Collections", gutter = LibraryGutter, onShowAll = null)
                        LibraryCollectionsRow(
                            cards = pinned.map { entry ->
                                CollectionCard(
                                    title = entry.title,
                                    subtitle = when (entry.route) {
                                        "auto_playlist/liked" -> "Songs you love"
                                        "auto_playlist/downloaded" -> "On this phone"
                                        "cache_playlist/cached" -> "Played recently, kept"
                                        else -> "Your most played"
                                    },
                                    icon = entry.iconRes,
                                    colors = when (entry.route) {
                                        "auto_playlist/liked" -> listOf(Color(0xFFFF2D55), Color(0xFFB0124A))
                                        "auto_playlist/downloaded" -> listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
                                        "cache_playlist/cached" -> listOf(Color(0xFF134E5E), Color(0xFF71B280))
                                        else -> listOf(Color(0xFF3A1C71), Color(0xFFD76D77))
                                    },
                                    onClick = { navController.navigate(entry.route) },
                                )
                            },
                            gutter = LibraryGutter,
                        )
                    }
                }
            }

            // ── Playlists: led by the card that makes one, stopped at five ──
            item(key = "playlists_shelf", contentType = "card_shelf") {
                var showCreate by rememberSaveable { mutableStateOf(false) }
                if (showCreate) {
                    com.ozyern.exhale.ui.component.CreatePlaylistDialog(onDismiss = { showCreate = false })
                }
                Column {
                    LibraryShelfHeader(
                        title = stringResource(R.string.filter_playlists),
                        gutter = LibraryGutter,
                        onShowAll = { onTabSelected(LibraryFilter.PLAYLISTS) }.takeIf { visiblePlaylists.size + 1 > LibraryRowMax },
                    )
                    LibraryCardRow(
                        items = visiblePlaylists.take(LibraryRowMax - 1),
                        key = { "pl_${it.id}" },
                        gutter = LibraryGutter,
                        title = { it.playlist.name },
                        subtitle = { "${it.songCount} $songsLabel" },
                        thumbnails = { it.thumbnails },
                        isPlaylist = true,
                        onClick = { playlist ->
                            if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0) {
                                navController.navigate("online_playlist/${playlist.playlist.browseId}")
                            } else {
                                navController.navigate("local_playlist/${playlist.id}")
                            }
                        },
                        onLongClick = { playlist ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                PlaylistMenu(playlist = playlist, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                            }
                        },
                        leading = { NewPlaylistCard(onClick = { showCreate = true }) },
                    )
                }
            }

            // ── Albums ──
            if (albums.isNotEmpty()) {
                item(key = "albums_shelf", contentType = "card_shelf") {
                    Column {
                        LibraryShelfHeader(
                            title = stringResource(R.string.filter_albums),
                            gutter = LibraryGutter,
                            onShowAll = { onTabSelected(LibraryFilter.ALBUMS) }.takeIf { albums.size > LibraryRowMax },
                        )
                        LibraryCardRow(
                            items = albums.take(LibraryRowMax),
                            key = { "al_${it.id}" },
                            gutter = LibraryGutter,
                            title = { it.album.title },
                            subtitle = { it.artists.joinToString { a -> a.name } },
                            thumbnails = { listOfNotNull(it.album.thumbnailUrl) },
                            isPlaylist = false,
                            onClick = { album -> navController.navigate("album/${album.id}") },
                            onLongClick = { album ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    AlbumMenu(originalAlbum = album, navController = navController, onDismiss = menuState::dismiss)
                                }
                            },
                        )
                    }
                }
            }

            // ── Recently Added ──────────────────────────────────────────────────────────────
            item(key = "recent_header", contentType = "recent_header") {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pageGutter()
                        .padding(top = 34.dp, bottom = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.library_recently_added),
                        style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.02).em),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    // Sorting lives beside the thing it sorts. It used to be a full-width card
                    // near the top of the page, above content it did not obviously govern.
                    SortHeader(
                        sortType = sortType,
                        sortDescending = sortDescending,
                        onSortTypeChange = onSortTypeChange,
                        onSortDescendingChange = onSortDescendingChange,
                        sortTypeText = { type ->
                            when (type) {
                                MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                                MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                                MixSortType.NAME -> R.string.sort_by_name
                            }
                        },
                    )
                }
            }

            if (recentRows.isEmpty()) {
                item(key = "recent_empty", contentType = "recent_empty") {
                    LibraryEmptyState(modifier = Modifier.pageGutter())
                }
            } else {
                items(
                    items = recentRows,
                    key = { row -> "recent_row_${row.first().key}" },
                    contentType = { "recent_row" },
                ) { row ->
                    // BoxWithConstraints rather than a screen-width constant: `maxWidth` is
                    // resolved against the *current* density, so the tiles stay square and the
                    // gutters stay true under the interface-scale setting and on a tablet.
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pageGutter()
                            .padding(bottom = 22.dp),
                    ) {
                        val tileWidth = (maxWidth - GridGutter) / 2

                        Row(horizontalArrangement = Arrangement.spacedBy(GridGutter)) {
                            row.forEach { entry ->
                                RecentlyAddedTile(
                                    entry = entry,
                                    width = tileWidth,
                                    onClick = {
                                        when {
                                            entry.album != null ->
                                                navController.navigate("album/${entry.album.id}")

                                            entry.playlist != null -> {
                                                val playlist = entry.playlist
                                                if (
                                                    !playlist.playlist.isEditable &&
                                                    playlist.songCount == 0 &&
                                                    playlist.playlist.remoteSongCount != 0
                                                ) {
                                                    navController.navigate("online_playlist/${playlist.playlist.browseId}")
                                                } else {
                                                    navController.navigate("local_playlist/${playlist.id}")
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            when {
                                                entry.album != null -> AlbumMenu(
                                                    originalAlbum = entry.album,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )

                                                entry.playlist != null -> PlaylistMenu(
                                                    playlist = entry.playlist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                            // An odd final row leaves the second column empty rather than
                            // stretching one tile to full width, which would read as a
                            // different kind of object.
                            if (row.size == 1) Spacer(Modifier.width(tileWidth))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal insets for everything on the page: the display cutout, then Apple's gutter.
 *
 * One modifier rather than a `padding(horizontal = …)` per call site, because the whole design
 * rests on a single unbroken left edge — title, row labels, section header and grid all landing
 * on the same vertical line. That only survives if there is one number.
 */
@Composable
private fun Modifier.pageGutter(): Modifier =
    this
        .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
        .padding(horizontal = LibraryGutter)

/**
 * The large title, matching Home's.
 *
 * Inside the scroll content rather than in the app bar, so it rolls away under the chrome as you
 * move down — the entire point of the pattern.
 */
@Composable
private fun LibraryLargeTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.filter_library),
        // Tracked in hard, same as Home. At display size the default letter fitting leaves
        // visible gaps — the difference between a word that has been set and one that was typed.
        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.03).em),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .pageGutter()
            .padding(top = 10.dp, bottom = 18.dp),
    )
}

/**
 * One row of the library list: icon, label, chevron, hairline.
 *
 * The divider is inset to the label rather than run full-bleed, and that inset is doing more work
 * than it looks. A full-width rule cuts the list into separate bands; one that starts under the
 * text leaves the icon column visually continuous, so the rows read as a single object with
 * internal seams. It is the most recognisable detail of an iOS grouped list, and the cheapest.
 */
@Composable
private fun LibraryCategoryRow(
    title: String,
    @DrawableRes icon: Int,
    accent: Color,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onClick)
                .padding(vertical = 13.dp),
        ) {
            Box(
                modifier = Modifier.width(30.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(CategoryIconSize),
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = title,
                // 20sp semibold: the size Apple sets this list at, and noticeably larger than a
                // Material list row. These are the four most-used destinations in the app; they
                // should not be whispering.
                style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-0.015).em),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Icon(
                painter = painterResource(R.drawable.chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                modifier = Modifier.padding(start = RowDividerInset - LibraryGutter),
            )
        }
    }
}

/**
 * One artwork tile in the Recently Added grid.
 *
 * Artwork square and near-flush with the column, title and subtitle left-aligned underneath, no
 * card behind it. Apple's grid is artwork on the page — a container around each item competes
 * with the covers, which are the only thing on this screen anyone is actually scanning.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedTile(
    entry: RecentEntry,
    width: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                // A hairline rim so a dark cover on a dark page still has an edge. Without it a
                // black album sleeve simply dissolves and the grid gets a hole in it.
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.White.copy(alpha = 0.04f),
                        ),
                    ),
                    shape = shape,
                ),
        ) {
            when {
                entry.playlist != null -> PlaylistThumbnail(
                    thumbnails = entry.thumbnails,
                    size = width,
                    placeHolder = {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(width / 3),
                        )
                    },
                    shape = shape,
                )

                entry.thumbnails.isNotEmpty() -> AsyncImage(
                    model = entry.thumbnails.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.album),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(width / 3),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (entry.subtitle.isNotBlank()) {
            Text(
                text = entry.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.library_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.library_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** One item in the Recently Added grid. Exactly one of [album]/[playlist] is non-null. */
private data class RecentEntry(
    val key: String,
    val title: String,
    val subtitle: String,
    val thumbnails: List<String>,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val album: Album?,
    val playlist: Playlist?,
)

private data class PinnedEntry(
    val title: String,
    @DrawableRes val iconRes: Int,
    val route: String,
    val accentColor: Color,
)
