/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.musicchem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.utils.makeTimeString
import java.text.NumberFormat
import java.util.Locale

/** Side gutter and gap of the capsule deck: the app's 16dp page gutter. */
internal val ChemGutter = 16.dp
internal val ChemGap = 12.dp

/** The app's card corner (continuous, as every card in the new UI). */
private val ChemCorner = 22.dp

internal fun formatCount(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

/** Each card rises a little and fades in once, staggered down the deck. */
@Composable
internal fun Modifier.chemEntrance(delayMillis: Int = 0): Modifier {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        reveal.animateTo(1f, tween(420, delayMillis = delayMillis, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = reveal.value
        translationY = (1f - reveal.value) * 14.dp.toPx()
    }
}

/** The deck's card: a quiet translucent plate in the page's own ink, continuous corners. */
@Composable
internal fun ChemCard(
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = ContinuousRoundedRectangle(ChemCorner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(ink.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Small caps label over a card's value. */
@Composable
internal fun ChemLabel(text: String, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        fontSize = 11.sp,
        letterSpacing = 0.9.sp,
        fontWeight = FontWeight.SemiBold,
        color = ink.copy(alpha = 0.55f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The month, large, with chevrons to step through the months there is history for. */
@Composable
internal fun ChemMonthHeader(
    month: String,
    year: String,
    ink: Color,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = ChemGutter),
    ) {
        Column(Modifier.weight(1f)) {
            Text(month, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
            Text(year, fontSize = 17.sp, color = ink.copy(alpha = 0.55f), maxLines = 1)
        }
        ChemChevron("chevron.left", stringResource(R.string.music_chem_older_month), ink, canGoOlder, onOlder)
        Spacer(Modifier.width(8.dp))
        ChemChevron("chevron.right", stringResource(R.string.music_chem_newer_month), ink, canGoNewer, onNewer)
    }
}

@Composable
private fun ChemChevron(symbol: String, description: String, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = if (enabled) 0.10f else 0.04f))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        SymbolIcon(
            symbol = sf(symbol),
            contentDescription = description,
            size = 17.dp,
            tint = ink.copy(alpha = if (enabled) 0.9f else 0.3f),
        )
    }
}

/** The month at a glance: its most-played covers as a soft mosaic, the minutes set large across them. */
@Composable
internal fun ChemHero(
    covers: List<String>,
    minutes: Long,
    periodText: String,
    modifier: Modifier = Modifier,
) {
    val shape = ContinuousRoundedRectangle(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(shape)
            .background(Color.Black),
    ) {
        val tiles = if (covers.isEmpty()) emptyList() else List(4) { covers[it % covers.size] }
        if (tiles.isNotEmpty()) {
            Column(Modifier.fillMaxSize().blur(2.dp)) {
                listOf(0, 2).forEach { row ->
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        listOf(row, row + 1).forEach { index ->
                            AsyncImage(
                                model = tiles[index],
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.10f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            ChemLabel(periodText, Color.White)
            Text(
                text = formatCount(minutes),
                fontSize = 64.sp,
                lineHeight = 66.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.music_chem_minutes_of_music),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** Top artist or top song: its picture, the label, the name on exactly two lines. */
@Composable
internal fun ChemSpotlightCard(
    label: String,
    title: String,
    imageUrl: String?,
    circular: Boolean,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChemCard(ink = ink, onClick = onClick, padding = 12.dp, modifier = modifier) {
        val artShape = if (circular) CircleShape else ContinuousRoundedRectangle(14.dp)
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(artShape)
                .background(ink.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(10.dp))
        ChemLabel(label, ink)
        Text(
            text = title,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** How much of the month one artist took, with the month shared from here. */
@Composable
internal fun ChemShareCard(
    artistName: String,
    imageUrl: String?,
    percent: Int,
    timeText: String,
    ink: Color,
    onShare: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChemCard(ink = ink, onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(ink.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.music_chem_share_of_listening, percent),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$artistName · $timeText",
                    fontSize = 15.sp,
                    color = ink.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.10f))
                    .clickable(onClick = onShare),
            ) {
                SymbolIcon(
                    symbol = sf("square.and.arrow.up"),
                    contentDescription = stringResource(R.string.music_chem_share_action),
                    size = 18.dp,
                    tint = ink,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        // The share as a bar, so the number has a shape.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.10f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.85f)),
            )
        }
    }
}

/** One number and what it counts. */
@Composable
internal fun ChemTallyCard(value: String, label: String, ink: Color, modifier: Modifier = Modifier, caption: String? = null) {
    ChemCard(ink = ink, padding = 14.dp, modifier = modifier) {
        ChemLabel(label, ink)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Text(caption, fontSize = 13.sp, color = ink.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The month's top artists side by side, each bar as long as its share of the leader's time. */
@Composable
internal fun ChemBreakdownCard(
    artists: List<Artist>,
    ink: Color,
    onArtistClick: (Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val leader = artists.maxOfOrNull { it.timeListened ?: 0 }?.coerceAtLeast(1) ?: 1
    ChemCard(ink = ink, modifier = modifier.fillMaxWidth()) {
        ChemLabel(stringResource(R.string.music_chem_top_artists), ink)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            artists.forEachIndexed { index, artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onArtistClick(artist) },
                ) {
                    ChemRank(index + 1, ink)
                    Spacer(Modifier.width(10.dp))
                    AsyncImage(
                        model = artist.artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(ink.copy(alpha = 0.08f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                artist.artist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                makeTimeString(artist.timeListened?.toLong()),
                                fontSize = 13.sp,
                                color = ink.copy(alpha = 0.55f),
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(5.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(ink.copy(alpha = 0.08f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((artist.timeListened ?: 0).toFloat() / leader).coerceIn(0.02f, 1f))
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(ink.copy(alpha = 0.7f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A place in the month: the top three are lit, the rest are quiet numbers. */
@Composable
internal fun ChemRank(rank: Int, ink: Color, modifier: Modifier = Modifier) {
    val podium = when (rank) {
        1 -> Color(0xFFFFC94D)
        2 -> Color(0xFFD9DEE6)
        3 -> Color(0xFFE0A06A)
        else -> null
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(podium?.copy(alpha = 0.22f) ?: Color.Transparent),
    ) {
        Text(
            rank.toString(),
            fontSize = 13.sp,
            fontWeight = if (podium != null) FontWeight.Black else FontWeight.SemiBold,
            color = podium ?: ink.copy(alpha = 0.55f),
        )
    }
}

/** A month with nothing played in it. */
@Composable
internal fun ChemEmptyState(ink: Color, modifier: Modifier = Modifier) {
    ChemCard(ink = ink, padding = 24.dp, modifier = modifier.fillMaxWidth()) {
        SymbolIcon(symbol = sf("music.note"), contentDescription = null, size = 28.dp, tint = ink.copy(alpha = 0.6f))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.music_chem_empty_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ink)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.music_chem_empty_body), fontSize = 15.sp, color = ink.copy(alpha = 0.6f))
    }
}
