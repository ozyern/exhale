/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.Recommendation
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.utils.resize

/**
 * "Top Picks for You", the big cards at the top of Home, after the hero shelf and iOS's
 * Listen Now: tall cards, the artwork whole, and the caption laid over a frosted strip of that same
 * artwork — blurred where it sits, so the words rest on the cover's own colour rather than on a
 * panel that guesses at it. A glass play disc in the corner plays the pick without opening it.
 */
@Composable
fun HomeMadeForYou(
    picks: List<Recommendation>,
    activeId: String?,
    isPlaying: Boolean,
    onPlay: (SongItem) -> Unit,
    onPlayNext: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        NavigationTitle(title = "Top Picks for You")
        BoxWithConstraints {
            // A share of the row with a ceiling, so the next card always peeks in — and a tablet
            // doesn't get one card the size of a poster.
            val cardWidth = if (maxWidth >= 600.dp) 260.dp else minOf(maxWidth * 0.74f, 320.dp)
            LazyRow(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(picks, key = { _, pick -> pick.song.id }) { _, pick ->
                    HeroPickCard(
                        pick = pick,
                        active = pick.song.id == activeId,
                        playing = isPlaying,
                        onPlay = { onPlay(pick.song) },
                        onPlayNext = { onPlayNext(pick.song) },
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.65f, stiffness = 600f), label = "madeForYouPress")
    return scale
}

/** How much of the card the frosted caption takes, from the bottom. */
private const val CAPTION_FRACTION = 0.30f

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroPickCard(
    pick: Recommendation,
    active: Boolean,
    playing: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = pick.song
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val shape = RoundedCornerShape(20.dp)
    val art = song.thumbnail.resize(720, 720)
    Box(
        modifier
            .aspectRatio(0.86f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(14.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
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
        // The cover is recorded so the caption can be glass over it: the panel bends and frosts the
        // artwork actually behind it, not a blurred copy laid underneath.
        val coverBackdrop = rememberLayerBackdrop()
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(coverBackdrop),
        )
        // the reference dock's floating caption: a pane of glass set in from the card's edges rather than a
        // strip across its foot - the cover runs all the way round it, so the card still reads as
        // the artwork with the words resting on it.
        val panelShape = RoundedCornerShape(24.dp)
        val glassy = remember { isRuntimeShaderSupported() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .then(
                    if (glassy) {
                        Modifier.drawBackdrop(
                            backdrop = coverBackdrop,
                            shape = { panelShape },
                            effects = {
                                colorControls(saturation = 1.4f)
                                blur(10f.dp.toPx())
                                lens(12f.dp.toPx(), 22f.dp.toPx(), depthEffect = true)
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.55f) },
                            onDrawSurface = { drawRect(Color.Black.copy(alpha = 0.22f)) },
                        )
                    } else {
                        Modifier
                            .clip(panelShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                    },
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.22f), panelShape)
                .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 20.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.2).sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artists = song.artists.joinToString { it.name }
                if (artists.isNotBlank()) {
                    Text(
                        artists,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pick.reason,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            // Glass play disc: what the card does, said on the card.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(1.dp, Color.White.copy(alpha = 0.42f), CircleShape)
                    .clickable(onClick = onPlay),
            ) {
                Icon(
                    painterResource(if (active && playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }    }
}
