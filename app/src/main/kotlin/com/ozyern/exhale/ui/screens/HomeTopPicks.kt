/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song

/**
 * Apple Music's "Recently Played": square covers a little under two fifths of the phone wide, so
 * two and a half show, each with its title under it and who made it in grey. An artist is round.
 * Tap opens or plays it; long-press for its menu.
 */
@Composable
fun HomeTopPicks(
    items: List<LocalItem>,
    onItemClick: (LocalItem) -> Unit,
    onItemLongClick: (LocalItem) -> Unit,
    modifier: Modifier = Modifier,
    activeId: String? = null,
    isPlaying: Boolean = false,
    cardIntro: (Int) -> Modifier = { Modifier },
) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        HomeShelfTitle("Recently Played")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A tablet shows more of them rather than bigger ones.
            val cardWidth = if (maxWidth >= 600.dp) 176.dp else maxWidth * 0.388f
            LazyRow(
                contentPadding = PaddingValues(horizontal = HomeShelfMargin),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    RecentTile(
                        item = item,
                        active = activeId != null && item.id == activeId,
                        isPlaying = isPlaying,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        modifier = cardIntro(index).width(cardWidth),
                    )
                }
            }
        }
    }
}

/** Who made it — or, for an artist or a playlist, what it is — for the grey line under a cover. */
private fun subtitleFor(item: LocalItem): String? = when (item) {
    is Song -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }
    is Album -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() } ?: "Album"
    is Artist -> "Artist"
    is Playlist -> "Playlist"
    else -> null
}

/**
 * One recent: the cover is the card, square with Apple's small corner (an artist round), and the
 * caption sits under it. The one playing wears its bars on the cover; a press dips the tile.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentTile(
    item: LocalItem,
    active: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.7f, stiffness = 700f), label = "recentTilePress")
    val round = item is Artist
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Column(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        horizontalAlignment = if (round) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                com.ozyern.exhale.ui.component.PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = isPlaying,
                    color = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitleFor(item)?.let { caption ->
            Text(
                caption,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
