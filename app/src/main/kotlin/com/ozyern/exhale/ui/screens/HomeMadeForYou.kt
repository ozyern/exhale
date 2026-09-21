/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.Recommendation
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.utils.resize

/**
 * "Made for You", the big cards at the top of Home — the phone's take on the Windows app's Top picks.
 *
 * The first suggestion is a spotlight: its own cover blurred into the whole card, the sharp cover
 * above the words, the reason it was picked, and Play / Play Next. The rest are tall cards whose
 * lower half is the cover's colour. Every card takes its colour from its artwork by drawing that
 * artwork, stretched and blurred — nothing is sampled or guessed.
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
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(picks, key = { _, pick -> pick.song.id }) { index, pick ->
                val active = pick.song.id == activeId
                // Uniform tall cards, two and a bit to a screen, as Apple's Top Picks are.
                PickCard(pick, active, isPlaying, onPlay = { onPlay(pick.song) })
            }
        }
    }
}

private val CardHeight = 268.dp

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.65f, stiffness = 600f), label = "madeForYouPress")
    return scale
}

/** The artwork, stretched far past its edges and blurred down to its colour, as a card's ground. */
@Composable
private fun ArtworkWash(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = url?.resize(120, 120),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 2.4f
                    scaleY = 2.4f
                }
                .blur(60.dp),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    }
}

@Composable
private fun SpotlightCard(pick: Recommendation, active: Boolean, playing: Boolean, onPlay: () -> Unit, onPlayNext: () -> Unit) {
    val song = pick.song
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val shape = RoundedCornerShape(26.dp)
    Box(
        Modifier
            .width(300.dp)
            .height(CardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(14.dp, shape)
            .clip(shape)
            .background(Color(0xFF1C1C1E))
            .clickable(interactionSource = source, indication = null, onClick = onPlay),
    ) {
        ArtworkWash(song.thumbnail, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Box(
                Modifier
                    .size(172.dp)
                    .align(Alignment.CenterHorizontally)
                    .shadow(20.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
            ) {
                AsyncImage(
                    model = song.thumbnail.resize(544, 544),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                pick.reason.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.08.em,
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(song.title, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                song.artists.joinToString { it.name },
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(
                    icon = if (active && playing) R.drawable.pause else R.drawable.play,
                    label = if (active && playing) "Pause" else "Play",
                    filled = true,
                    onClick = onPlay,
                )
                PillButton(icon = R.drawable.playlist_play, label = "Play Next", filled = false, onClick = onPlayNext)
            }
        }
    }
}

@Composable
private fun PillButton(icon: Int, label: String, filled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val content = if (filled) Color.Black else Color.White
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.18f))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content)
    }
}

@Composable
private fun PickCard(pick: Recommendation, active: Boolean, playing: Boolean, onPlay: () -> Unit) {
    val song = pick.song
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val shape = RoundedCornerShape(14.dp)
    val width: Dp = 176.dp
    Column(
        Modifier
            .width(width)
            .height(CardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Color(0xFF1C1C1E))
            .clickable(interactionSource = source, indication = null, onClick = onPlay),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = song.thumbnail.resize(544, 544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            ArtworkWash(song.thumbnail, Modifier.fillMaxSize())
            // Where the cover meets the panel, fade one into the other.
            Box(Modifier.fillMaxWidth().height(28.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent))))
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    pick.reason,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(song.title, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artists.joinToString { it.name },
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
