/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.models.Recommendation
import com.ozyern.exhale.ui.component.PlayingIndicatorBox
import com.ozyern.exhale.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The page margin the title "Home" sits on; both shelves line up with it, as Apple's do. */
internal val HomeShelfMargin = 20.dp

/**
 * Apple Music's "Top Picks for You": tall 3:4 cards three fifths of the phone wide, so the next one
 * peeks in. Each card is washed in its cover's own background colour, the cover sits square at its
 * top, and three lines run along its foot — why it is here in grey, the title, the artist in grey.
 * Tap plays, long-press queues it next.
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

/** A Home shelf's heading: Apple's Title 2, 22pt bold, on the page margin. */
@Composable
internal fun HomeShelfTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
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
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "pickCardPress",
    )
    val art = song.thumbnail.resize(720, 720)
    val tint = rememberCoverBackground(song.thumbnail.resize(48, 48))
    val ground by animateColorAsState(tint ?: Color(0xFF1C1C1E), tween(350), label = "pickCardGround")
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier
            .aspectRatio(0.75f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(ground)
            // A hairline, as Apple rims the cards whose colour is the page's own.
            .border(0.5.dp, Color.White.copy(alpha = 0.16f), shape)
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
        // The reference's proportions, as shares of the card: the cover inset by a ninth of the card's
        // width on three sides, the words inset by a thirteenth.
        val coverInset = maxWidth * 0.11f
        val textInset = maxWidth * 0.075f
        Box(
            Modifier
                .padding(start = coverInset, end = coverInset, top = coverInset)
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = art,
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
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                song.title,
                fontSize = 17.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium,
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
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Card colours already worked out, by cover address. */
private val CoverBackgroundCache = object : LinkedHashMap<String, Color>(32, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 64
}

/**
 * The colour a cover's own background is — the average of its outer edge — kept dark enough that
 * white words read on it. Null until known. Read from a 48-pixel copy, once per cover.
 */
@Composable
private fun rememberCoverBackground(url: String?): Color? {
    val context = LocalContext.current
    val cached = url?.let { synchronized(CoverBackgroundCache) { CoverBackgroundCache[it] } }
    val color by produceState(cached, url) {
        if (url == null || cached != null) return@produceState
        val request = ImageRequest.Builder(context).data(url).size(48, 48).allowHardware(false).build()
        val bitmap = runCatching { context.imageLoader.execute(request).image?.toBitmap() }.getOrNull()
            ?: return@produceState
        value = withContext(Dispatchers.Default) { edgeColor(bitmap) }
            .also { synchronized(CoverBackgroundCache) { CoverBackgroundCache[url] = it } }
    }
    return color
}

private fun edgeColor(bitmap: Bitmap): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return Color(0xFF1C1C1E)
    val band = maxOf(1, minOf(w, h) / 12)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0
    for (y in 0 until h) for (x in 0 until w) {
        if (x >= band && x < w - band && y >= band && y < h - band) continue
        val p = bitmap.getPixel(x, y)
        r += (p shr 16) and 0xFF
        g += (p shr 8) and 0xFF
        b += p and 0xFF
        n++
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt()), hsl)
    // White words must read on it: a pale sleeve gives a deep version of its own colour.
    hsl[2] = hsl[2].coerceIn(0.06f, 0.36f)
    return Color(ColorUtils.HSLToColor(hsl))
}
