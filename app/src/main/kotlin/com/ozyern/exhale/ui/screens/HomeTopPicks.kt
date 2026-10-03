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
import androidx.compose.foundation.layout.aspectRatio
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.constants.HomeRecentsAsListKey
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.clickable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
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
    val (asList, setAsList) = rememberPreference(HomeRecentsAsListKey, false)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            NavigationTitle(title = "Recents")
            // Covers or rows: the same shelf, read two ways. Rows show four to a page and the next
            // page peeks in, so a long history is a swipe rather than a scroll past it.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable { setAsList(!asList) },
            ) {
                Icon(
                    painterResource(if (asList) R.drawable.grid_view else R.drawable.list),
                    contentDescription = if (asList) "Show as covers" else "Show as a list",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        AnimatedContent(
            targetState = asList,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "recentsLayout",
        ) { list ->
            if (list) {
                BoxWithConstraints {
                    val columnWidth = minOf(maxWidth * 0.88f, 420.dp)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(items.chunked(4), key = { _, page -> page.first().id }) { _, page ->
                            Column(Modifier.width(columnWidth)) {
                                page.forEach { item ->
                                    RecentRow(
                                        item = item,
                                        active = activeId != null && item.id == activeId,
                                        isPlaying = isPlaying,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // The grid: the lead-shelf hero cards, seven tenths of the row wide.
                BoxWithConstraints {
                    val cardWidth = minOf(maxWidth * 0.70f, 320.dp)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            RecentHeroCard(
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
    }
}

/** One recent as a row: the artwork small, the title and what it is, and a way to its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentRow(
    item: LocalItem,
    active: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val round = item is Artist
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 5.dp),
    ) {
        Box(Modifier.size(50.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(eyebrowFor(item), subtitleFor(item)).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onLongClick),
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

/** One recent: the artwork filling it, the caption over a scrim. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentHeroCard(
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
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.62f, stiffness = 600f), label = "recentHeroPress")
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(0.92f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.15f), shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
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
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                .padding(start = 16.dp, end = 16.dp, top = 34.dp, bottom = 14.dp),
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitleFor(item)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
