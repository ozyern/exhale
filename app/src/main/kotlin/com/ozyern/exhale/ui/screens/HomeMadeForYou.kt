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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.ozyern.exhale.ui.component.AppleTitleFont
import com.ozyern.exhale.ui.component.PlayingIndicatorBox
import com.ozyern.exhale.ui.utils.resize

/** The page margin the title "Home" sits on; both shelves line up with it, as Apple's do. */
internal val HomeShelfMargin = 20.dp

/**
 * Apple Music's "Top Picks for You": tall 3:4 cards three fifths of the phone wide, so the next one
 * peeks in. Each card stands on its own cover — blurred and dimmed, so it carries the cover's real
 * colours rather than one averaged tint — with the cover square at its top and three lines along its
 * foot: why it is here in grey, the title, the artist in grey. Tap plays, long-press queues it next.
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
        HomeShelfTitle("Top Picks for You")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A tablet shows more cards, not bigger ones.
            val cardWidth = if (maxWidth >= 600.dp) 300.dp else maxWidth * 0.60f
            // Fresh picks replace the saved ones after launch; keyed scrolling would otherwise keep
            // the row parked partway along, on whichever old card survived into the new list.
            val rowState = rememberLazyListState()
            LaunchedEffect(picks.firstOrNull()?.song?.id) { rowState.scrollToItem(0) }
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(start = HomeShelfMargin, end = HomeShelfMargin, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                itemsIndexed(picks, key = { _, pick -> pick.song.id }) { index, pick ->
                    PickCard(
                        song = pick.song,
                        reason = pick.reason,
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

/** A Home shelf's heading: Apple's Title 2, 22pt SF bold, on the page margin. */
@Composable
internal fun HomeShelfTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = AppleTitleFont,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = HomeShelfMargin, end = HomeShelfMargin, top = 22.dp, bottom = 12.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickCard(
    song: SongItem,
    reason: String,
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
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 520f),
        label = "pickCardPress",
    )
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier
            .aspectRatio(0.75f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Color(0xFF1C1C1E))
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
        // The ground: the cover itself, enlarged past the card's edges and blurred to its colours.
        // Read at a small size — the blur leaves no detail to spend pixels on — and drawn once into
        // its own layer, so scrolling the shelf moves a finished picture.
        AsyncImage(
            model = song.thumbnail.resize(160, 160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                }
                .blur(42.dp),
        )
        // Deeper toward the words, so white type reads on any cover.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.18f),
                        0.55f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.58f),
                    ),
                ),
        )
        // The reference's proportions, as shares of the card: the cover inset by a ninth of the
        // card's width on three sides, the words inset by a thirteenth.
        val coverInset = maxWidth * 0.11f
        val textInset = maxWidth * 0.075f
        Box(
            Modifier
                .padding(start = coverInset, end = coverInset, top = coverInset)
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(18.dp, RoundedCornerShape(3.dp), ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = song.thumbnail.resize(720, 720),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                PlayingIndicatorBox(
                    isActive = true,
                    playWhenReady = playing,
                    color = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = textInset, end = textInset, bottom = textInset),
        ) {
            if (reason.isNotBlank()) {
                Text(
                    reason,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
            }
            // Apple sets the title in the regular weight and lets it take a second line.
            Text(
                song.title,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val artists = song.artists.joinToString { it.name }
            if (artists.isNotBlank()) {
                Text(
                    artists,
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // A hairline over everything, as Apple rims its cards.
        Box(Modifier.fillMaxSize().border(0.5.dp, Color.White.copy(alpha = 0.14f), shape))
    }
}
