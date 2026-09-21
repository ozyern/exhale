/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.ui.utils.resize

/** The colour of each chart's cards, in order — Apple Music's red first, then its siblings. */
private val ChartPalettes = listOf(
    Color(0xFFFF4F6D) to Color(0xFFE8304D),
    Color(0xFF6E6BFF) to Color(0xFF4B3FE0),
    Color(0xFFFF8A3D) to Color(0xFFE85D1F),
    Color(0xFF1FB8A6) to Color(0xFF0E8C8A),
    Color(0xFFB45CF0) to Color(0xFF8B3BD0),
)

/**
 * One chart as Apple Music shows it: a run of big colour cards swiped sideways, each carrying nine
 * ranks — number, artwork, title, artist and a play button — under the chart's name.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChartCards(
    title: String,
    songs: List<SongItem>,
    paletteIndex: Int,
    activeId: String?,
    isPlaying: Boolean,
    onPlay: (SongItem) -> Unit,
    onMenu: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val (top, bottom) = ChartPalettes[paletteIndex % ChartPalettes.size]
    val pages = songs.chunked(9)
    val listState = rememberLazyListState()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - 40.dp).coerceAtMost(420.dp)
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(pages) { pageIndex, page ->
                Column(
                    Modifier
                        .width(cardWidth)
                        .shadow(10.dp, RoundedCornerShape(26.dp))
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(listOf(top, bottom)))
                        .padding(top = 16.dp, bottom = 10.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(painterResource(R.drawable.music_note), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.padding(top = 6.dp))
                    page.forEachIndexed { i, song ->
                        ChartRow(
                            rank = pageIndex * 9 + i + 1,
                            song = song,
                            active = song.id == activeId,
                            playing = isPlaying && song.id == activeId,
                            onPlay = { onPlay(song) },
                            onMenu = { onMenu(song) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChartRow(rank: Int, song: SongItem, active: Boolean, playing: Boolean, onPlay: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onMenu)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(8.dp))
        AsyncImage(
            model = song.thumbnail.resize(120, 120),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(7.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                song.artists.joinToString { it.name },
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (active && playing) R.drawable.pause else R.drawable.play),
                contentDescription = null,
                tint = Color(0xFFE8304D),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
