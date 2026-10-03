/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.PlaylistThumbnail

/** The card width for every Library row. */
internal val LibraryCardWidth = 150.dp

/** A Library row stops at five cards and offers "Show all" for the rest. */
internal const val LibraryRowMax = 5

/** A shelf's heading: the title large on the left, and "Show all" when the row is cut short. */
@Composable
internal fun LibraryShelfHeader(
    title: String,
    gutter: Dp,
    onShowAll: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter)
            .padding(top = 26.dp, bottom = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onShowAll != null) {
            Text(
                text = "Show all",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** The four ways into the library — playlists, artists, albums, songs — as a 2×2 of tiles. */
@Composable
internal fun LibraryDestinationGrid(
    destinations: List<Triple<String, Int, () -> Unit>>,
    gutter: Dp,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = gutter),
    ) {
        destinations.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { (label, icon, onClick) ->
                    DestinationTile(label, icon, onClick, Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DestinationTile(label: String, icon: Int, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = 650f), label = "libDestPress")
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One of the collections the app keeps for you. */
internal data class CollectionCard(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val colors: List<Color>,
    val onClick: () -> Unit,
)

@Composable
internal fun LibraryCollectionsRow(cards: List<CollectionCard>, gutter: Dp) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(cards, key = { it.title }) { card ->
            Column(
                Modifier
                    .width(LibraryCardWidth)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = card.onClick),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(card.colors))
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(0f, 0f),
                                radius = 420f,
                            ),
                        ),
                ) {
                    Icon(
                        painter = painterResource(card.icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                CardCaption(card.title, card.subtitle)
            }
        }
    }
}

/** A row of playlists or albums, led by an optional card that is not a thing yet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> LibraryCardRow(
    items: List<T>,
    key: (T) -> String,
    gutter: Dp,
    title: (T) -> String,
    subtitle: (T) -> String,
    thumbnails: (T) -> List<String>,
    isPlaylist: Boolean,
    onClick: (T) -> Unit,
    onLongClick: (T) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = gutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        leading?.let { item(key = "leading") { it() } }
        items(items, key = key) { entry ->
            Column(
                Modifier
                    .width(LibraryCardWidth)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = { onClick(entry) }, onLongClick = { onLongClick(entry) }),
            ) {
                val shape = RoundedCornerShape(12.dp)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape)
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    val thumbs = thumbnails(entry)
                    if (isPlaylist) {
                        PlaylistThumbnail(
                            thumbnails = thumbs,
                            size = LibraryCardWidth,
                            placeHolder = {
                                Icon(
                                    painter = painterResource(R.drawable.queue_music),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp),
                                )
                            },
                            shape = shape,
                        )
                    } else if (thumbs.isNotEmpty()) {
                        AsyncImage(
                            model = thumbs.first(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                CardCaption(title(entry), subtitle(entry))
            }
        }
    }
}

/** The "New playlist" tile, in line with the covers beside it. */
@Composable
internal fun NewPlaylistCard(onClick: () -> Unit) {
    Column(
        Modifier
            .width(LibraryCardWidth)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
        ) {
            Icon(
                painter = painterResource(R.drawable.add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        CardCaption("New playlist", "Start a collection")
    }
}

@Composable
private fun CardCaption(title: String, subtitle: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (subtitle.isNotBlank()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
