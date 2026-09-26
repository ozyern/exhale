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

private val CardWidth = 172.dp

/**
 * Square, because an album cover is square.
 *
 * The card was a 176x220 portrait crop with the song title set 19sp bold across its foot. That is
 * the shape of a video thumbnail with a headline on it - a YouTube shelf - and it made every cover
 * on Home a letterboxed fragment of itself. Apple's shelves show the artwork whole and put the
 * words underneath, where they can be read at a caption size instead of shouting over the picture.
 */
private val CardArtHeight = CardWidth

@Composable
private fun pressScale(source: MutableInteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.65f, stiffness = 600f), label = "madeForYouPress")
    return scale
}

/** The artwork, stretched far past its edges and blurred down to its colour, as a card's ground. */
@Composable
private fun PickCard(pick: Recommendation, active: Boolean, playing: Boolean, onPlay: () -> Unit) {
    val song = pick.song
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(CardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interactionSource = source, indication = null, onClick = onPlay),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(CardArtHeight)
                .clip(shape)
                .background(Color(0xFF1C1C1E)),
        ) {
            AsyncImage(
                model = song.thumbnail.resize(544, 544),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (playing) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            pick.reason,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
