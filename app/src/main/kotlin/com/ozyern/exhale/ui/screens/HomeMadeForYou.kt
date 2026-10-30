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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.Recommendation
import com.ozyern.exhale.ui.component.PlayingIndicatorBox
import com.ozyern.exhale.ui.utils.resize

/**
 * The big cards that lead Home: a heading, then tall cards seven tenths of the phone wide so the
 * next one peeks in — the artwork is the card, edge to edge, with a hairline rim, and the title
 * (bold) and artist (lighter) sit on it over a soft fade at its foot. Tap plays, long-press queues
 * it next.
 */
@Composable
fun HomeMadeForYou(
    picks: List<Recommendation>,
    activeId: String?,
    isPlaying: Boolean,
    onPlay: (SongItem) -> Unit,
    onPlayNext: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
    cardIntro: (Int) -> Modifier = { Modifier },
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = "Top picks for you",
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.3).sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A tablet shows more cards, not bigger ones.
            val cardWidth = if (maxWidth >= 600.dp) 300.dp else minOf(maxWidth * 0.70f, 340.dp)
            // Fresh picks replace the saved ones after launch; keyed scrolling would otherwise keep
            // the row parked partway along, on whichever old card survived into the new list.
            val rowState = rememberLazyListState()
            LaunchedEffect(picks.firstOrNull()?.song?.id) { rowState.scrollToItem(0) }
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(picks, key = { _, pick -> pick.song.id }) { index, pick ->
                    PickCard(
                        song = pick.song,
                        active = pick.song.id == activeId,
                        playing = isPlaying,
                        onPlay = { onPlay(pick.song) },
                        onPlayNext = { onPlayNext(pick.song) },
                        modifier = cardIntro(index).width(cardWidth),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickCard(
    song: SongItem,
    active: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "pickCardPress",
    )
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier
            .aspectRatio(0.92f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                onClick = onPlay,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPlayNext()
                },
            ),
    ) {
        AsyncImage(
            model = song.thumbnail.resize(720, 720),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // The fade the words sit on: only across the lower third, so the picture stays the picture.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        if (active) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            PlayingIndicatorBox(
                isActive = true,
                playWhenReady = playing,
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
        ) {
            Text(
                song.title,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.4).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = song.artists.joinToString { it.name }
            if (artists.isNotBlank()) {
                Text(
                    artists,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
