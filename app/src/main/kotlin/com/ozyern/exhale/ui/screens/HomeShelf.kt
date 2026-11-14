/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import com.ozyern.exhale.ui.utils.sharedTitleSource
import com.ozyern.exhale.ui.utils.sharedArtworkImageListener
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import com.ozyern.exhale.ui.utils.rememberArtworkSourceOwner
import com.ozyern.exhale.ui.utils.ArtworkSourceHost
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.ui.component.LocalItemPrefs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.PlaylistEntity
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.models.toMediaMetadata
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.SpotlightCard
import com.ozyern.exhale.ui.component.SpotlightCardInfoHeight
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.component.AppleContextMenu
import com.ozyern.exhale.ui.ios.component.IosMenuItem
import com.ozyern.exhale.ui.ios.component.IosMenuSection
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.menu.AddToPlaylistDialog
import com.ozyern.exhale.ui.menu.YouTubeAlbumMenu
import com.ozyern.exhale.ui.menu.YouTubeArtistMenu
import com.ozyern.exhale.ui.menu.YouTubePlaylistMenu
import com.ozyern.exhale.ui.menu.YouTubeSongMenu
import com.ozyern.exhale.ui.screens.library.apple.Cover
import com.ozyern.exhale.ui.screens.library.apple.SfStyle
import com.ozyern.exhale.ui.screens.library.apple.songMenu
import com.ozyern.exhale.ui.theme.AppPage
import com.ozyern.exhale.ui.utils.albumRoute
import com.ozyern.exhale.ui.utils.artistRoute
import com.ozyern.exhale.ui.utils.bleedStart
import com.ozyern.exhale.ui.utils.combinedBounceClick
import com.ozyern.exhale.ui.utils.localPlaylistRoute
import com.ozyern.exhale.ui.utils.onlinePlaylistRoute
import com.ozyern.exhale.ui.utils.plusStart
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope

/**
 * Stable cross-section plumbing. Cheap plain-data re-created on recomposition; holds
 * the non-UI references and per-row composables each section needs. Volatile data
 * (media metadata, song maps, lists) travels as explicit parameters instead so the
 * sections stay free of composition-local reads.
 */
@OptIn(ExperimentalFoundationApi::class)
internal data class HomeSectionDeps(
    val context: Context,
    /** Speed dial's shuffle pick; null where there is no online feed to pick from. */
    val randomItem: suspend () -> YTItem?,
    val navController: NavController,
    val playerConnection: PlayerConnection,
    val menuState: MenuState,
    val haptic: HapticFeedback,
    val scope: CoroutineScope,
    val sideInset: Dp,
    val localGridItem: @Composable (LocalItem) -> Unit,
    val onLocalItemClick: (LocalItem) -> Unit,
    val isScrolling: () -> Boolean = { false },
)


@OptIn(ExperimentalFoundationApi::class)
/**
 * One entry on a Home shelf. Sections keep their own data and their own click
 * handling; only the row and the tile are shared.
 */
internal data class ShelfEntry(
    val id: String,
    val thumbnailUrl: String?,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
    /** What press-and-hold pops (Apple menu); null = no menu. */
    val pop: HomePop? = null,
    /** Artwork identity when an entry has custom click handling instead of a menu item. */
    val sharedArtworkId: String? = null,
)

/** Draws a Home shelf with a header and a horizontal row of cards. */
internal fun LazyListScope.shelfRow(
    key: String,
    deps: HomeSectionDeps,
    entries: List<ShelfEntry>,
    compact: Boolean = false,
) {
    if (entries.isEmpty()) return
    item(key = key, contentType = if (compact) "shelf_row" else "spotlight_row") {
        if (compact) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppPage.Gutter).plusStart(deps.sideInset),
                horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeShelfCardHeight)
                    .animateItem()
                    .bleedStart(deps.sideInset),
            ) {
                items(entries, key = { it.id }, contentType = { "shelf_card" }) { entry ->
                    HomeShelfCard(entry, deps)
                }
            }
        } else {
            SpotlightRow(deps.sideInset, Modifier.animateItem()) { cardWidth ->
                items(entries, key = { it.id }, contentType = { "spotlight_card" }) { entry ->
                    val card = @Composable {
                        SpotlightCard(
                            thumbnailUrl = entry.thumbnailUrl,
                            title = entry.title,
                            subtitle = entry.subtitle,
                            // The card handles its own tap; the pop menu around it defers to it.
                            onClick = entry.onClick,
                            artworkSize = cardWidth,
                        )
                    }
                    val pop = entry.pop
                    if (pop == null) card() else HomePopMenu(
                        pop, deps, entry.thumbnailUrl, entry.title, entry.subtitle, entry.onClick,
                    ) { card() }
                }
            }
        }
    }
}

/**
 * A sideways row of [SpotlightCard]s on the reference app's hero rhythm: each card is 70% of the row
 * wide (at most 320dp) so the next one peeks in, and the row is exactly as tall as a card.
 * [content] gets the card width to hand to each [SpotlightCard]'s `artworkSize`.
 */
@Composable
internal fun SpotlightRow(
    sideInset: Dp,
    modifier: Modifier = Modifier,
    content: LazyListScope.(cardWidth: Dp) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = AppPage.heroCardWidth(maxWidth)
        LazyRow(
            contentPadding = PaddingValues(horizontal = AppPage.Gutter).plusStart(sideInset),
            horizontalArrangement = Arrangement.spacedBy(AppPage.HeroCardGap),
            modifier = Modifier
                .fillMaxWidth()
                .height(cardWidth + SpotlightCardInfoHeight)
                .bleedStart(sideInset),
        ) { content(cardWidth) }
    }
}

/** Song shelves at least this long draw as [shelfList] columns instead of cards. */
internal const val HomeShelfListMinimum = 8
private const val HomeShelfListRows = 4
private val HomeShelfListArt = 52.dp
private val HomeShelfListPeek = 28.dp

/** True when most of [items] are songs and there are enough of them for list columns. */
internal fun List<YTItem>.isSongShelf(): Boolean {
    val songs = count { it is SongItem }
    return songs >= HomeShelfListMinimum && songs * 2 >= size
}

/** True when most of [items] are podcast episodes or music videos, which carry 16:9 artwork. */
internal fun List<YTItem>.isVideoShelf(): Boolean =
    count { it is SongItem && (it.isPodcastEpisode || it.isVideoSong) } * 2 >= size && isNotEmpty()

private const val HomeVideoCardHeightDp = 135

/** Large 16:9 cards for episodes and videos. */
internal fun LazyListScope.shelfVideoRow(key: String, deps: HomeSectionDeps, items: List<YTItem>) {
    if (items.isEmpty()) return
    item(key = key, contentType = "video_row") {
        val prefs = LocalItemPrefs.current
        CompositionLocalProvider(LocalItemPrefs provides prefs.copy(gridCardHeightOverrideDp = HomeVideoCardHeightDp)) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppPage.Gutter).plusStart(deps.sideInset),
                horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
                modifier = Modifier.fillMaxWidth().animateItem().bleedStart(deps.sideInset),
            ) {
                items(items, key = { it.id }, contentType = { "video_card" }) { item ->
                    HomePopMenu(HomePop.Yt(item), deps, item.thumbnail, item.title, item.shelfSubtitle(), { deps.openYtItem(item) }) {
                        YouTubeGridItem(item = item, thumbnailRatio = 16f / 9f)
                    }
                }
            }
        }
    }
}

/** Song rows four deep in columns that page sideways; the next column peeks in. */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.shelfList(
    key: String,
    deps: HomeSectionDeps,
    entries: List<ShelfEntry>,
) {
    if (entries.isEmpty()) return
    val columns = entries.chunked(HomeShelfListRows)
    item(key = key, contentType = "shelf_list") {
        BoxWithConstraints(Modifier.fillMaxWidth().animateItem()) {
            val columnWidth = maxWidth - AppPage.Gutter - HomeShelfListPeek - deps.sideInset
            val listState = rememberLazyListState()
            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
                contentPadding = PaddingValues(horizontal = AppPage.Gutter).plusStart(deps.sideInset),
                horizontalArrangement = Arrangement.spacedBy(HomeShelfCardSpacing),
                modifier = Modifier.fillMaxWidth().bleedStart(deps.sideInset),
            ) {
                items(columns, key = { it.first().id }, contentType = { "shelf_list_column" }) { column ->
                    Column(Modifier.width(columnWidth)) {
                        column.forEach { entry -> HomeShelfListRow(entry, deps) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeShelfListRow(entry: ShelfEntry, deps: HomeSectionDeps) {
    val pop = entry.pop
    if (pop == null) {
        HomeShelfListRowBody(entry, Modifier.combinedBounceClick(onClick = entry.onClick))
    } else {
        HomePopMenu(pop, deps, entry.thumbnailUrl, entry.title, entry.subtitle, entry.onClick) {
            HomeShelfListRowBody(entry)
        }
    }
}

/** Albums, playlists and artists morph into their page; songs have no page to open. */
private fun ShelfEntry.artworkId(): String? =
    sharedArtworkId ?: when (val item = (pop as? HomePop.Yt)?.item) {
        is com.music.innertube.models.SongItem -> item.album?.id
        is com.music.innertube.models.AlbumItem -> item.id
        is com.music.innertube.models.PlaylistItem -> item.id
        else -> null
    }

@Composable
private fun HomeShelfListRowBody(entry: ShelfEntry, modifier: Modifier = Modifier) {
    ArtworkSourceHost(modifier) { host ->
    val owner = rememberArtworkSourceOwner()
    val artworkId = entry.artworkId()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = host.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        AsyncImage(
            model = entry.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = sharedArtworkImageListener(owner, entry.thumbnailUrl),
            modifier = Modifier
                .sharedArtworkSource(artworkId, entry.thumbnailUrl, owner)
                .size(HomeShelfListArt)
                .clip(RoundedCornerShape(8.dp))
                .border(0.25.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
        )
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = entry.title,
                style = AppTheme.typography.bodyMedium,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = (-0.23).sp,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (artworkId != null) Modifier.sharedTitleSource(entry.title) else Modifier,
            )
            if (entry.subtitle.isNotEmpty()) {
                Text(
                    text = entry.subtitle,
                    style = AppTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = AppTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    }
}

/** Square artwork edge for [HomeShelfCard]. */
internal val HomeShelfCardWidth = 154.dp

/** Space reserved for the title and subtitle below the artwork. */
internal val HomeShelfCardInfoHeight = 46.dp
internal val HomeShelfCardHeight = HomeShelfCardWidth + HomeShelfCardInfoHeight
internal val HomeShelfCardSpacing = 14.dp

/**
 * Compact shelf card with square artwork, a title, and a subtitle.
 */
@Composable
internal fun HomeShelfCard(entry: ShelfEntry, deps: HomeSectionDeps) {
    val pop = entry.pop
    val artworkId = entry.artworkId()
    if (pop == null) {
        HomeShelfCardBody(entry.thumbnailUrl, entry.title, entry.subtitle, Modifier.combinedBounceClick(onClick = entry.onClick), artworkId)
    } else {
        HomePopMenu(pop, deps, entry.thumbnailUrl, entry.title, entry.subtitle, entry.onClick) {
            HomeShelfCardBody(entry.thumbnailUrl, entry.title, entry.subtitle, artworkId = artworkId)
        }
    }
}

@Composable
internal fun HomeShelfCardBody(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    artworkId: String? = null,
) {
    // Shelf covers keep their shared shape when the Home corner setting changes.
    val cardShape = GridCardArtShape
    ArtworkSourceHost(modifier) { host ->
    val owner = rememberArtworkSourceOwner()
    Column(
        modifier = host.width(HomeShelfCardWidth),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = sharedArtworkImageListener(owner, thumbnailUrl),
            modifier = Modifier
                .sharedArtworkSource(artworkId, thumbnailUrl, owner)
                .size(HomeShelfCardWidth)
                .clip(cardShape)
                .border(0.25.dp, Color.Black.copy(alpha = 0.1f), cardShape),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = AppTheme.typography.bodyMedium,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.23).sp,
            color = LocalContentColor.current,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (artworkId != null) Modifier.sharedTitleSource(title) else Modifier,
        )
        Text(
            text = subtitle,
            style = AppTheme.typography.bodySmall,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            letterSpacing = (-0.23).sp,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    }
}

/** What a Home tile's press-and-hold menu is about. */
internal sealed interface HomePop {
    data class Yt(val item: YTItem) : HomePop
    data class Local(val song: Song) : HomePop
}

/**
 * Apple Music's hold-to-pop on a Home tile ([AppleContextMenu]): the tile sinks, lifts
 * into a preview card, and the menu grows under it. Tapping still opens the item.
 */
@Composable
internal fun HomePopMenu(
    pop: HomePop,
    deps: HomeSectionDeps,
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var addToPlaylist: androidx.compose.runtime.MutableState<Boolean>? = null
    val (quick, sections) = when (pop) {
        is HomePop.Local -> songMenu(pop.song, deps.navController).let { entries ->
            addToPlaylist = entries.addToPlaylist
            entries.quick to entries.sections
        }
        is HomePop.Yt -> ytItemMenu(pop.item, deps)
    }
    AppleContextMenu(
        sections = sections,
        quickActions = quick,
        preview = {
            Cover(thumbnailUrl, 12.dp)
            Text(
                text = title,
                style = SfStyle(15.sp, 20.sp, FontWeight.SemiBold, (-0.23).sp),
                color = IosTheme.colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = SfStyle(13.sp, 18.sp),
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
    val dialog = addToPlaylist
    if (dialog != null && dialog.value && pop is HomePop.Local) {
        AddToPlaylistDialog(isVisible = true, onGetSong = { listOf(pop.song.id) }, onDismiss = { dialog.value = false })
    }
}

@Composable
internal fun ytItemMenu(item: YTItem, deps: HomeSectionDeps): Pair<List<IosMenuItem>, List<IosMenuSection>> =
    com.ozyern.exhale.ui.screens.library.apple.ytItemMenu(item, deps.navController, deps.playerConnection) { deps.showYtItemMenu(item) }

/** Where a [YTItem] goes when tapped. Shared by every shelf that carries remote items. */
internal fun HomeSectionDeps.openYtItem(item: YTItem) {
    when (item) {
        is SongItem -> playerConnection.playQueue(
            YouTubeQueue(item.endpoint ?: WatchEndpoint(videoId = item.id), item.toMediaMetadata())
        )
        is AlbumItem -> navController.navigate(albumRoute(item.id, item.thumbnail))
        is ArtistItem -> navController.navigate(artistRoute(item.id, item.thumbnail))
        // A locally-created playlist (id prefixed "LP", see PlaylistEntity.generatePlaylistId)
        // is not a real YouTube playlist — online_playlist/ cannot resolve it.
        is PlaylistItem -> navController.navigate(
            if (item.id.startsWith("LP")) localPlaylistRoute(item.id, item.thumbnail)
            else onlinePlaylistRoute(item.id, item.title, item.thumbnail)
        )
    }
}

internal fun HomeSectionDeps.showYtItemMenu(item: YTItem) {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    menuState.show {
        when (item) {
            is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
            is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
            is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
            is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = scope, onDismiss = menuState::dismiss)
        }
    }
}

/** Second line under a remote tile. Empty rather than invented where there is nothing to say. */
internal fun YTItem.shelfSubtitle(): String = when (this) {
    is SongItem -> artists.joinToString { it.name }
    is AlbumItem -> artists?.joinToString { it.name }.orEmpty()
    is PlaylistItem -> author?.name ?: songCountText.orEmpty()
    is ArtistItem -> ""
}

internal fun YTItem.toShelfEntry(deps: HomeSectionDeps): ShelfEntry {
    val item = this
    return ShelfEntry(
        id = item.id,
        thumbnailUrl = item.thumbnail,
        title = item.title,
        subtitle = item.shelfSubtitle(),
        onClick = { deps.openYtItem(item) },
        pop = HomePop.Yt(item),
    )
}

/** A library song on a shelf: play it (or toggle, if it is the one already playing). */
internal fun Song.toShelfEntry(deps: HomeSectionDeps, mediaMetadata: State<MediaMetadata?>): ShelfEntry {
    val entry = this
    return ShelfEntry(
        id = entry.id,
        thumbnailUrl = entry.song.thumbnailUrl,
        title = entry.song.title,
        subtitle = entry.artists.joinToString { it.name },
        onClick = {
            if (entry.id == mediaMetadata.value?.id) {
                deps.playerConnection.togglePlayPause()
            } else {
                deps.playerConnection.playQueue(YouTubeQueue.radio(entry.toMediaMetadata()))
            }
        },
        pop = HomePop.Local(entry),
        sharedArtworkId = entry.album?.id ?: entry.song.albumId,
    )
}
