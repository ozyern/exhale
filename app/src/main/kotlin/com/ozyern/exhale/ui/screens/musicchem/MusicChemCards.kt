/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The card follows BitChord's ReplayCreditCard (github.com/kushagrasinghx/BitChord, GPL-3.0):
 * its proportions, embossed lines and layout. The data is Music Chem's.
 */

package com.ozyern.exhale.ui.screens.musicchem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.player.rememberNowPlayingGradient
import com.ozyern.exhale.ui.screens.OptionStats
import com.ozyern.exhale.viewmodels.StatsViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/** One card's headline fact. */
private data class ChemFact(
    val value: String,
    val label: String,
    val detail: String?,
    val artworkUrl: String?,
    /** Shown beside the fact: the cover, or the artist's round portrait. Off for the minutes card. */
    val showArtwork: Boolean = true,
    val roundArtwork: Boolean = false,
    /** A count, drawn large with its unit after it. */
    val unit: String? = null,
)

/**
 * Your Music Chem, pinned at the head of the Library: this month's headline facts (minutes, top
 * song, top artist, top album) as a row of cards, each lit by the artwork it is about. Tapping
 * one opens Music Chem. Nothing is drawn until there is something to say.
 */
@Composable
fun MusicChemCards(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = koinViewModel(key = "music_chem_cards"),
) {
    remember(viewModel) {
        if (viewModel.selectedOption.value != OptionStats.MONTHS) {
            viewModel.selectedOption.value = OptionStats.MONTHS
            viewModel.indexChips.value = 0
        }
    }
    val songs by viewModel.mostPlayedSongsStats.collectAsStateWithLifecycle()
    val artists by viewModel.mostPlayedArtists.collectAsStateWithLifecycle()
    val albums by viewModel.mostPlayedAlbums.collectAsStateWithLifecycle()
    val firstEvent by viewModel.firstEvent.collectAsStateWithLifecycle()

    val facts = remember(songs, artists, albums) {
        buildList {
            val millis = songs.sumOf { it.timeListened ?: 0L }
            val plays = songs.sumOf { it.songCountListened }
            if (millis > 0L) {
                add(
                    ChemFact(
                        String.format(Locale.getDefault(), "%,d", millis / 60_000L), "Minutes listened",
                        "${plays(plays)} this month", songs.firstOrNull()?.thumbnailUrl, showArtwork = false, unit = "min",
                    ),
                )
            }
            songs.firstOrNull()?.let { song ->
                add(ChemFact(song.title, "Top song", listOfNotNull(song.artistName, plays(song.songCountListened)).joinToString(" · "), song.thumbnailUrl))
            }
            artists.firstOrNull()?.let { artist ->
                add(ChemFact(artist.artist.name, "Top artist", minutes(artist.timeListened?.toLong()), artist.artist.thumbnailUrl, roundArtwork = true))
            }
            albums.firstOrNull()?.let { album ->
                val by = album.artists.firstOrNull()?.name
                add(ChemFact(album.album.title, "Top album", listOfNotNull(by, minutes(album.timeListened)).joinToString(" · "), album.album.thumbnailUrl))
            }
        }
    }
    if (facts.isEmpty()) return

    val memberSince = remember(firstEvent) {
        firstEvent?.event?.timestamp?.format(DateTimeFormatter.ofPattern("MM/yy", Locale.ROOT))
    }
    val month = remember { LocalDate.now().month.getDisplayName(DateTextStyle.FULL, Locale.getDefault()) }
    // A little narrower than the screen, so the next card shows at the edge and says "swipe".
    val cardWidth = (LocalConfiguration.current.screenWidthDp.dp - 48.dp).coerceAtMost(360.dp)
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        // One card per swipe, each coming to rest at the start edge.
        flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
    ) {
        items(facts, key = { it.label }) { fact ->
            MusicChemCard(
                fact = fact,
                month = month,
                memberSince = memberSince,
                onClick = { navController.navigate("music_chem") },
                modifier = Modifier.width(cardWidth),
            )
        }
    }
}

private fun plays(count: Int) = if (count == 1) "1 play" else "$count plays"

private fun minutes(millis: Long?): String? =
    millis?.takeIf { it > 0L }?.let { "${(it / 60_000L).coerceAtLeast(1)} min" }

/** One headline fact as a card you could keep in a wallet (after BitChord's ReplayCreditCard). */
@Composable
private fun MusicChemCard(
    fact: ChemFact,
    month: String,
    memberSince: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Lit by the artwork the card is about: the cover's own gradient, as the player draws it.
    val gradient by rememberNowPlayingGradient(fact.artworkUrl, hero = false)
    val top = gradient?.let { Color(it.top) } ?: Color(0xFF4A2A6E)
    val bottom = gradient?.let { Color(it.bottom) } ?: Color(0xFF1E1030)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "chemCardPress",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .aspectRatio(CARD_RATIO)
            .shadow(18.dp, CardShape, clip = false, ambientColor = bottom, spotColor = bottom)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(lift(top), top, bottom)))
            .drawWithCache {
                // Deepened towards the foot, where the embossed lines are.
                val depth = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.06f),
                    0.55f to Color.Black.copy(alpha = 0.20f),
                    1.0f to Color.Black.copy(alpha = 0.46f),
                )
                // The logo's sound waves, engraved faintly across the card from its right edge.
                val waveCenter = Offset(size.width * 1.02f, size.height * 0.58f)
                val waveStroke = Stroke(width = 1.dp.toPx())
                val waveStep = size.height * 0.16f
                // Light falling on the top-left corner, as on a glossy card.
                val sheen = Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.20f),
                    0.35f to Color.White.copy(alpha = 0.04f),
                    0.6f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                onDrawBehind {
                    drawRect(depth)
                    for (i in 1..9) {
                        val r = waveStep * i
                        drawArc(
                            color = Color.White.copy(alpha = 0.075f - i * 0.006f),
                            startAngle = 115f,
                            sweepAngle = 130f,
                            useCenter = false,
                            topLeft = Offset(waveCenter.x - r, waveCenter.y - r),
                            size = Size(r * 2, r * 2),
                            style = waveStroke,
                        )
                    }
                    drawRect(sheen)
                }
            }
            .border(0.75.dp, EdgeLight, CardShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "YOUR MUSIC CHEM",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 1.4.sp,
                        lineHeight = 13.sp,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                    Text(
                        text = month.uppercase(Locale.getDefault()),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 1.4.sp,
                        lineHeight = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.width(10.dp))
                // Exhale's own mark in its gold. The black around it was made transparent, so its
                // glow lies on the card.
                Image(
                    painter = painterResource(R.drawable.music_chem_logo),
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (fact.showArtwork && fact.artworkUrl != null) {
                    val artShape = if (fact.roundArtwork) CircleShape else ArtShape
                    AsyncImage(
                        model = fact.artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(58.dp)
                            .shadow(10.dp, artShape)
                            .clip(artShape)
                            .border(0.75.dp, Color.White.copy(alpha = 0.22f), artShape),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (fact.unit != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = fact.value,
                                style = TextStyle(
                                    fontSize = 40.sp,
                                    lineHeight = 42.sp,
                                    fontWeight = FontWeight.W800,
                                    letterSpacing = (-0.5).sp,
                                    brush = PolishedInk,
                                    shadow = EmbossShadow,
                                ),
                                maxLines = 1,
                            )
                            Text(
                                text = " ${fact.unit}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.W700,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            text = fact.value,
                            style = TextStyle(
                                fontSize = 23.sp,
                                lineHeight = 26.sp,
                                fontWeight = FontWeight.W800,
                                brush = PolishedInk,
                                shadow = EmbossShadow,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = fact.label.uppercase(Locale.ROOT),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 1.8.sp,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            }
            Spacer(Modifier.weight(0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Embossed(text = "EXHALE LISTENER", size = 13.sp)
                    if (fact.detail != null) {
                        Text(
                            text = fact.detail,
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (memberSince != null) {
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "MEMBER\nSINCE",
                            fontSize = 7.sp,
                            lineHeight = 8.sp,
                            letterSpacing = 0.8.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.End,
                        )
                        Embossed(text = memberSince, size = 12.sp)
                    }
                }
            }
        }
    }
}

/** A line pressed into the card: monospaced and tracked, lit from above. */
@Composable
private fun Embossed(text: String, size: TextUnit) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.W600,
            fontSize = size,
            letterSpacing = 1.6.sp,
            brush = PolishedInk,
            shadow = EmbossShadow,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The cover's colour, brightened for the card's lit corner. */
private fun lift(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(android.graphics.Color.argb(255, (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt()), hsv)
    hsv[2] = (hsv[2] * 1.6f + 0.08f).coerceAtMost(0.72f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private val PolishedInk = Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF3F4F8), Color(0xFFC9CCD6)))

private val EmbossShadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 2.5f), blurRadius = 4f)

private val CardShape = RoundedCornerShape(22.dp)

private val ArtShape = RoundedCornerShape(12.dp)

/** The card's edge catching the light: bright along the top, almost gone at the foot. */
private val EdgeLight = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.06f)))

/** 85.6mm × 54mm: what makes the shape read as a card. */
private const val CARD_RATIO = 1.586f
