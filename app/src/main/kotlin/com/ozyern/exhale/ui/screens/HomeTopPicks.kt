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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
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
                            // A page is one grouped card, as Apple groups a list: the rows share
                            // a plate and are parted by hairlines inset to the text.
                            Column(
                                Modifier
                                    .width(columnWidth)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                    .padding(vertical = 4.dp),
                            ) {
                                page.forEachIndexed { i, item ->
                                    RecentRow(
                                        item = item,
                                        active = activeId != null && item.id == activeId,
                                        isPlaying = isPlaying,
                                        onClick = { onItemClick(item) },
                                        onLongClick = { onItemLongClick(item) },
                                    )
                                    if (i < page.lastIndex) {
                                        Box(
                                            Modifier
                                                .padding(start = 12.dp + 54.dp + 14.dp)
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Apple Music's Recently Played: square tiles a little under half a phone wide,
                // so two and a bit show and the shelf reads as a shelf, not a carousel of posters.
                // A tablet shows more of them rather than bigger ones.
                BoxWithConstraints {
                    val cardWidth = if (maxWidth >= 600.dp) 176.dp else 156.dp
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    }
}

/**
 * One recent as a row of its page's card: the artwork, the title, and what it is and who made it in
 * grey. Long-press for its menu - the row no longer carries a menu button, which on four rows a page
 * was four more things to look past - and the one playing shows its bars on the artwork.
 */
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
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(if (pressed) 0.08f else 0f, tween(120), label = "recentRowPress")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(54.dp)
                .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
                com.ozyern.exhale.ui.component.PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = isPlaying,
                    color = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(kindOf(item), subtitleFor(item)?.takeIf { it != kindOf(item) }).joinToString(" · "),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun kindOf(item: LocalItem): String? = when (item) {
    is Song -> "Song"
    is Album -> "Album"
    is Artist -> "Artist"
    is Playlist -> "Playlist"
    else -> null
}

private fun subtitleFor(item: LocalItem): String? = when (item) {
    is Song -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() }
    is Album -> item.artists.joinToString { it.name }.takeIf { it.isNotBlank() } ?: "Album"
    is Artist -> "Artist"
    is Playlist -> "Playlist"
    else -> null
}

/**
 * One recent, as Apple Music shows it: the artwork is the card — square, an artist round — and the
 * caption sits under it, title in 14 medium and what it is and who made it in 13 grey. The one
 * playing wears its bars on the artwork; a press dips the tile, nothing more.
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
    val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
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
                .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.22f), spotColor = Color.Black.copy(alpha = 0.22f))
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val caption = listOfNotNull(kindOf(item), subtitleFor(item)?.takeIf { it != kindOf(item) }).joinToString(" · ")
        if (caption.isNotEmpty()) {
            Text(
                caption,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
