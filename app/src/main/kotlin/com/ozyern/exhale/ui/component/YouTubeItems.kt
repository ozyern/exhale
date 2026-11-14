/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.utils.sharedTitleSource

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalDownloadUtil
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.constants.ListRowArtShape
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull

internal val rowSongCache = android.util.LruCache<String, Song>(256)
internal val rowAlbumCache = android.util.LruCache<String, Album>(128)

/** Per-row DB lookup for a badge, cached in memory so fast scroll does not spam SQLite. */
@Composable
internal fun rememberRowSong(item: YTItem): Song? {
    if (item !is SongItem) return null
    val cached = rowSongCache.get(item.id)
    if (cached != null) return cached

    val database = LocalDatabase.current.get()
    val song by produceState<Song?>(initialValue = null, item.id) {
        val result = database.song(item.id).firstOrNull()
        if (result != null) {
            rowSongCache.put(item.id, result)
        }
        value = result
    }
    return song
}

@Composable
internal fun rememberRowAlbum(item: YTItem): Album? {
    if (item !is AlbumItem) return null
    val cached = rowAlbumCache.get(item.id)
    if (cached != null) return cached

    val database = LocalDatabase.current.get()
    val album by produceState<Album?>(initialValue = null, item.id) {
        val result = database.album(item.id).firstOrNull()
        if (result != null) {
            rowAlbumCache.put(item.id, result)
        }
        value = result
    }
    return album
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    /** Pass these from a map the screen already collected to avoid a per-row DB
     *  query. Left null, the row looks itself up as before — dropping the lookup
     *  outright silently removed the liked/bookmarked badge everywhere. */
    song: Song? = null,
    album: Album? = null,
    badges: @Composable RowScope.() -> Unit = {
        val resolvedSong = song ?: rememberRowSong(item)
        val resolvedAlbum = album ?: rememberRowAlbum(item)
        if ((item is SongItem && resolvedSong?.song?.liked == true) ||
            (item is AlbumItem && resolvedAlbum?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem) {
            // Flow is remembered; collectAsState is composable and stays outside.
            val downloadUtil = LocalDownloadUtil.current.get()
            val download = LocalDownloads.current[item.id]
            Icon.Download(download?.state)
        }
    },
    shape: Shape = RectangleShape,
    drawHighlight: Boolean = true,
    flat: Boolean = false,
    /** See the base ListItem: subtitle tier + flat divider via additiveChrome. */
    secondaryAdditiveChrome: Boolean = false,
    subtitleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    subtitleColor: Color? = null,
    /** See the base ListItem: full rule above the first row of a section. */
    leadingDivider: Boolean = false,
    /** See the base ListItem: start inset of the trailing rule. */
    trailingDividerInset: Dp? = null,
    /** See the base ListItem: gap between the row gutter and the thumbnail. */
    thumbnailStartInset: Dp = 12.dp,
    /** Replaces the generated subtitle (e.g. iOS artist pages show the album, not the artists). */
    subtitle: String? = null,
) {
    val swipeEnabled = LocalItemPrefs.current.swipeToSong

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle = subtitle ?: when (item) {
                is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                is ArtistItem -> null
                is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
            },
            subtitleColor = subtitleColor,
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = item.thumbnail,
                    appleArtQuery = when (item) {
                        is AlbumItem -> com.ozyern.exhale.utils.AppleArtQuery.Album(item.title, item.artists?.joinToString { it.name })
                        is ArtistItem -> com.ozyern.exhale.utils.AppleArtQuery.Artist(item.title)
                        else -> null
                    },
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    // List rows keep one artwork shape for every item type; the circle
                    // stays on artist tiles and heroes, where it reads as a portrait.
                    shape = ListRowArtShape,
                    targetSizePx = thumbnailPx(ListThumbnailSize),
                    // Song rows can open their album from the context menu. Register the
                    // album key so that route can morph from the same loaded cover.
                    sharedArtworkId = if (item is SongItem) item.album?.id else item.id,
                    modifier = Modifier.size(ListThumbnailSize)
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isSelected = isSelected,
            isActive = isActive,
            shape = shape,
            drawHighlight = drawHighlight,
            flat = flat,
            secondaryAdditiveChrome = secondaryAdditiveChrome,
            subtitleAdditiveChrome = subtitleAdditiveChrome,
            leadingDivider = leadingDivider,
            trailingDividerInset = trailingDividerInset,
            thumbnailStartInset = thumbnailStartInset,
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        val mediaItem = remember(item.id) { item.copy(thumbnail = item.thumbnail.resize(544,544)).toMediaItem() }
        SwipeToSongBox(
            mediaItem = mediaItem,
            modifier = Modifier.fillMaxWidth()
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    /** See [YouTubeListItem]: hoisted when the screen already has the data, looked
     *  up per row otherwise so the badge does not silently disappear. */
    song: Song? = null,
    album: Album? = null,
    badges: @Composable RowScope.() -> Unit = {
        val resolvedSong = song ?: rememberRowSong(item)
        val resolvedAlbum = album ?: rememberRowAlbum(item)
        if (item is SongItem && resolvedSong?.song?.liked == true ||
            item is AlbumItem && resolvedAlbum?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem) {
            // Flow is remembered; collectAsState is composable and stays outside.
            val downloadUtil = LocalDownloadUtil.current.get()
            val download = LocalDownloads.current[item.id]
            Icon.Download(download?.state)
        }
    },
    // Keep thumbnails square to align them with other grid tiles.
    thumbnailRatio: Float = 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = item.title,
            style = AppTheme.typography.bodyMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.23).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.sharedTitleSource(item.title).fillMaxWidth()
        )
    },
    subtitle = {
        val subtitle = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = AppTheme.typography.bodySmall,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = AppleTokens.Metadata,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    badges = badges,
    thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                appleArtQuery = when (item) {
                    is AlbumItem -> com.ozyern.exhale.utils.AppleArtQuery.Album(item.title, item.artists?.joinToString { it.name })
                    is ArtistItem -> com.ozyern.exhale.utils.AppleArtQuery.Artist(item.title)
                    else -> null
                },
                isActive = isActive,
                isPlaying = isPlaying,
                shape = GridCardArtShape,
            sharedArtworkId = if (item is SongItem) item.album?.id else item.id,
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)
