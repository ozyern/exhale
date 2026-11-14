/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.constants.GridThumbnailHeight
import com.ozyern.exhale.constants.ListRowArtShape
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.playback.displayDownloadState
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.ui.utils.sharedArtworkSource
import com.ozyern.exhale.ui.utils.sharedTitleSource
import com.ozyern.exhale.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            SymbolIcon(symbol = sf("heart.fill"), contentDescription = null, size = 18.dp, modifier = Modifier
                    
                    .padding(end = 2.dp), tint = AppTheme.colorScheme.error)
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
    showIconOnly: Boolean = false,
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    titleFontWeight = FontWeight.Normal,
    titleLetterSpacing = (-0.43).sp,
    titleFontSize = 17.sp,
    titleLineHeight = 22.sp,
    subtitleFontSize = 15.sp,
    subtitleLineHeight = 20.sp,
    badges = badges,
    thumbnailContent = {
        if (showIconOnly) {
            Box(
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(CircleShape)
                    .background(LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("music.mic"), contentDescription = null, size = 24.dp, tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        } else {
            val context = LocalContext.current
            val targetPx = thumbnailPx(ListThumbnailSize)
            val displayUrl = com.ozyern.exhale.utils.rememberAppleArtworkUrl(
                com.ozyern.exhale.utils.AppleArtQuery.Artist(artist.artist.name), artist.artist.thumbnailUrl, targetPx,
            )
            val imageRequest = remember(displayUrl, targetPx) {
                ImageRequest.Builder(context)
                    .data(displayUrl?.resize(targetPx, targetPx))
                    .size(targetPx, targetPx)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .sharedArtworkSource(artist.id, artist.artist.thumbnailUrl)
                    .size(ListThumbnailSize)
                    .clip(CircleShape),
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    flat = flat
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
    showIconOnly: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    horizontalAlignment = Alignment.CenterHorizontally,
    badges = badges,
    thumbnailContent = {
        if (showIconOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("music.mic"), contentDescription = null, modifier = Modifier.size(ListThumbnailSize / 2), tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        } else {
            val context = LocalContext.current
            val targetPx = thumbnailPx(GridThumbnailHeight)
            val displayUrl = com.ozyern.exhale.utils.rememberAppleArtworkUrl(
                com.ozyern.exhale.utils.AppleArtQuery.Artist(artist.artist.name), artist.artist.thumbnailUrl, targetPx,
            )
            val imageRequest = remember(displayUrl, targetPx) {
                ImageRequest.Builder(context)
                    .data(displayUrl?.resize(targetPx, targetPx))
                    .size(targetPx, targetPx)
                    .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .sharedArtworkSource(artist.id, artist.artist.thumbnailUrl)
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current.get()

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads = LocalDownloads.current

        val downloadState by remember(songs, allDownloads) {
            derivedStateOf {
                displayDownloadState(songs, allDownloads) { it.id }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    showIconOnly: Boolean = false,
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    titleFontWeight = FontWeight.Normal,
    titleLetterSpacing = (-0.43).sp,
    titleFontSize = 17.sp,
    titleLineHeight = 22.sp,
    subtitleFontSize = 15.sp,
    subtitleLineHeight = 20.sp,
    badges = badges,
    thumbnailContent = {
        if (showIconOnly) {
            Box(
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(ListRowArtShape)
                    .background(LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("square.stack"), contentDescription = null, size = 24.dp, tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        } else {
            ItemThumbnail(
                thumbnailUrl = album.album.thumbnailUrl,
                appleArtQuery = com.ozyern.exhale.utils.AppleArtQuery.Album(album.album.title, album.artists.joinToString { it.name }),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = ListRowArtShape,
                targetSizePx = thumbnailPx(ListThumbnailSize),
                sharedArtworkId = album.id,
                modifier = Modifier.size(ListThumbnailSize)
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    flat = flat
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current.get()

        val songs by produceState<List<Song>>(initialValue = emptyList(), album.id) {
            withContext(Dispatchers.IO) {
                value = database.albumSongs(album.id).first()
            }
        }

        val allDownloads = LocalDownloads.current

        val downloadState by remember(songs, allDownloads) {
            derivedStateOf {
                displayDownloadState(songs, allDownloads) { it.id }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    showIconOnly: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = album.album.title,
            style = AppTheme.typography.bodyMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.23).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.sharedTitleSource(album.album.title).fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = album.artists.joinToString { it.name },
            style = AppTheme.typography.bodySmall,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = AppleTokens.Metadata,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    },
    badges = badges,
    thumbnailContent = {
        if (showIconOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(GridCardArtShape)
                    .background(LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("square.stack"), contentDescription = null, modifier = Modifier.size(ListThumbnailSize / 2), tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        } else {
            ItemThumbnail(
                thumbnailUrl = album.album.thumbnailUrl,
                appleArtQuery = com.ozyern.exhale.utils.AppleArtQuery.Album(album.album.title, album.artists.joinToString { it.name }),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = GridCardArtShape,
                sharedArtworkId = album.id,
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)
