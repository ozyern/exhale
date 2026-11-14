/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.offline.Download
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.GridCardArtShape
import com.ozyern.exhale.constants.ListItemHeight
import com.ozyern.exhale.constants.ListRowArtShape
import com.ozyern.exhale.constants.ListThumbnailSize
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.models.MediaMetadata
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.utils.joinByBullet
import com.ozyern.exhale.utils.makeTimeString
import kotlinx.coroutines.flow.first

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            // One hoisted map, no per-row Flow and no per-row collector. See
            // [LocalDownloads] for what this replaced.
            Icon.Download(LocalDownloads.current[song.song.id]?.state)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    onSelectionChange: (Boolean) -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
    drawHighlight: Boolean = true,
    shape: Shape = RectangleShape,
    flat: Boolean = false,
    showIconOnly: Boolean = false,
    /** Off for an album tracklist, where every row repeats the same artist under
     *  the title the header already names — the reference sets those rows as a
     *  single line. Badges are unaffected. */
    showSubtitle: Boolean = true,
    /** See the base ListItem: subtitle tier + flat divider via additiveChrome. */
    secondaryAdditiveChrome: Boolean = false,
    subtitleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    subtitleColor: androidx.compose.ui.graphics.Color = AppleTokens.Metadata,
    /** See the base ListItem: full rule above the first row of a section. */
    leadingDivider: Boolean = false,
    /** See the base ListItem: start inset of the trailing rule. */
    trailingDividerInset: Dp? = null,
    /** See the base ListItem: gap between the row gutter and the thumbnail. */
    thumbnailStartInset: Dp = 12.dp,
) {
    val swipeEnabled = LocalItemPrefs.current.swipeToSong

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            // Numbered album rows use medium weight; queue rows use semibold.
            titleFontWeight = if (albumIndex != null) FontWeight.Medium else FontWeight.SemiBold,
            titleLetterSpacing = (-0.23).sp,
            subtitle = if (!showSubtitle) null else queueDetailsLine(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L),
                subtitleColor,
            ),
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
                        SymbolIcon(symbol = sf("music.note"), contentDescription = null, size = 24.dp, tint = LocalContentColor.current.copy(alpha = 0.6f))
                    }
                } else {
                    ItemThumbnail(
                        thumbnailUrl = song.song.thumbnailUrl,
                        sharedArtworkId = song.album?.id ?: song.song.albumId,
                        albumIndex = albumIndex,
                        isSelected = isSelected,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        shape = ListRowArtShape,
                        targetSizePx = thumbnailPx(ListThumbnailSize),
                        modifier = Modifier.size(ListThumbnailSize)
                    )
                }
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

    if (isSwipeable && swipeEnabled) {
        val mediaItem = remember(song.song.id) { song.toMediaItem() }
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
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download = LocalDownloads.current[song.id]
            Icon.Download(download?.state)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    showIconOnly: Boolean = false,
    // Apple Music shelves (Home) show the artist alone under a tile.
    showDuration: Boolean = true,
) = GridItem(
    title = {
        Text(
            text = song.song.title,
            style = AppTheme.typography.bodyMedium,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = (-0.23).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L).takeIf { showDuration },
            ),
            style = AppTheme.typography.bodySmall,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            color = AppleTokens.Metadata,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    },
    badges = badges,
    thumbnailContent = {
        if (showIconOnly) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LocalContentColor.current.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                SymbolIcon(symbol = sf("music.note"), contentDescription = null, modifier = Modifier.size(maxWidth / 2.5f), tint = LocalContentColor.current.copy(alpha = 0.6f))
            }
        } else {
            val gridHeight = currentGridThumbnailHeight()
            ItemThumbnail(
                thumbnailUrl = song.song.thumbnailUrl,
                sharedArtworkId = song.album?.id ?: song.song.albumId,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = GridCardArtShape,
                // Match the parent tile size when fillMaxWidth is enabled.
                modifier = if (fillMaxWidth) Modifier.fillMaxSize() else Modifier.size(gridHeight)
            )
        }
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)


@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shape: Shape = RectangleShape,
    flat: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    /** See the base ListItem: subtitle tier + flat divider via additiveChrome. */
    secondaryAdditiveChrome: Boolean = false,
    titleAdditiveChrome: Boolean = secondaryAdditiveChrome,
    subtitleColor: androidx.compose.ui.graphics.Color = AppleTokens.Metadata,
    /** Queue row height. Other lists keep the default row height. */
    rowHeight: Dp = ListItemHeight,
    gutter: Dp = AppleTokens.Gutter,
    divider: Boolean = true,
) {
    ListItem(
        title = mediaMetadata.title,
        titleFontWeight = FontWeight.SemiBold,
        titleLetterSpacing = (-0.23).sp,
        subtitle = buildAnnotatedString {
            append(
                queueDetailsLine(
                    mediaMetadata.artists.joinToString { it.name },
                    makeTimeString(mediaMetadata.duration * 1000L),
                    subtitleColor,
                )
            )
            if (mediaMetadata.suggestedBy != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(" • ")
                    append(mediaMetadata.suggestedBy)
                }
            }
        },
        badges = { if (mediaMetadata.explicit) Icon.Explicit()},
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = ListRowArtShape,
                targetSizePx = thumbnailPx(ListThumbnailSize),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
        shape = shape,
        flat = flat,
        secondaryAdditiveChrome = secondaryAdditiveChrome,
        titleAdditiveChrome = titleAdditiveChrome,
        rowHeight = rowHeight,
        gutter = gutter,
        divider = divider,
    )
}
