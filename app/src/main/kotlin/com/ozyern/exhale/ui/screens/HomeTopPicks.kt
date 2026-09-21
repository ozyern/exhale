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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.Album
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.db.entities.Playlist
import com.ozyern.exhale.db.entities.Song
import com.ozyern.exhale.ui.component.NavigationTitle

/**
 * Apple Music's "Top Picks for You": a row of tall cards, the artwork above and a caption panel below
 * washed in that artwork's own colour — the panel *is* the artwork, stretched and blurred, which is
 * how Apple's cards come to match their covers without anything being guessed at.
 *
 * It replaces a Spotify-style six-tile shortcut grid, which was the one thing at the top of Home that
 * belonged to another app.
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
        NavigationTitle(title = "Recently Played")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                TopPickCard(
                    item = item,
                    active = activeId != null && item.id == activeId,
                    isPlaying = isPlaying,
                    onClick = { onItemClick(item) },
                    onLongClick = { onItemLongClick(item) },
                    modifier = cardIntro(index),
                )
            }
        }
    }
}

private fun eyebrowFor(item: LocalItem): String = when (item) {
    is Artist -> "YOUR ARTIST"
    is Album -> "ON REPEAT"
    is Playlist -> "YOUR PLAYLIST"
    is Song -> "KEEP LISTENING"
    else -> "FOR YOU"
}

private fun subtitleFor(item: LocalItem): String? = when (item) {
    is Song -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }
    is Album -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() } ?: "Album"
    is Artist -> "Artist"
    is Playlist -> "Playlist"
    else -> null
}

private val TileSize = 150.dp

/**
 * One tile of Apple Music's "Recently Played": the artwork square (round for an artist), and a
 * quiet two-line caption under it. No coloured panel, no badges — the covers are the design.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopPickCard(
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
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.62f, stiffness = 600f), label = "recentPress")
    val round = item is Artist
    val shape = if (round) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(10.dp)
    Column(
        modifier
            .width(TileSize)
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
        Box(Modifier.size(TileSize).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painterResource(R.drawable.music_note),
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp).align(Alignment.Center),
                )
            }
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitleFor(item)?.let {
            Text(
                it,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
