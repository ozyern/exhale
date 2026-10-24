/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.ui.component.liquidGlassSurface
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

/*
 * The Sound Chem visual language.
 *
 * Structurally this is Spotify's Sound Capsule — a stacked deck of poster cards, each one holding a
 * single stat stated as large as it will go, with a one-line label above it and the artwork it
 * belongs to underneath. What it is *not* is Spotify's material: those cards are flat opaque grey
 * rectangles. These are the app's liquid glass, so the radial colour wash the screen already paints
 * behind them shows through, and each card carries the bright top rim that sells it as a pane
 * rather than a panel.
 *
 * Everything here is deliberately dumb — no view model, no data access, no navigation decisions.
 * StatsScreen computes the numbers and hands them down.
 */

/** Corner radius of a capsule card. Much deeper than a settings group: these are posters, not rows. */
private val CapsuleCorner = 28.dp

/** Inner padding of a capsule card. */
private val CapsulePadding = 20.dp

/** Gap between cards, horizontally and vertically. One value so the deck reads as a grid. */
internal val CapsuleGap = 12.dp

/** Page gutter. Cards are inset from the screen edge by this much. */
internal val CapsuleGutter = 16.dp

private val IntegerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.getDefault())

// ─── Material ─────────────────────────────────────────────────────────────────

/**
 * The staggered entrance every card gets: rise, scale up and fade in on the app's shared spring.
 *
 * Runs entirely in `graphicsLayer`, so a deck of eight cards animating at once costs no layout
 * passes. The delay is what makes it read as a deck being dealt rather than the whole page
 * flickering on at once.
 */
@Composable
internal fun Modifier.capsuleEntrance(delayMillis: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "capsuleEntrance",
    )
    return this.graphicsLayer {
        alpha = progress
        val scale = 0.94f + 0.06f * progress
        scaleX = scale
        scaleY = scale
        translationY = (1f - progress) * 26.dp.toPx()
    }
}

/**
 * Counts a number up from zero instead of printing it.
 *
 * A stat this large is the point of the card, and a number that tallies into place asks to be read
 * in a way a number that is simply *there* does not. Re-runs whenever the period changes, so
 * switching from "1 week" to "1 year" visibly rolls the total up rather than swapping it.
 */
@Composable
internal fun rememberCountUp(target: Long): Long {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(target) {
        animatable.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        )
    }
    return animatable.value.toLong()
}

internal fun formatCount(value: Long): String = IntegerFormat.format(value)

// ─── Card shell ───────────────────────────────────────────────────────────────

@Composable
internal fun SoundChemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(CapsuleCorner),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .liquidGlassSurface(shape, tint = accent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(CapsulePadding),
        content = content,
    )
}

/**
 * A soft coloured halo laid behind artwork.
 *
 * Artwork dropped straight onto glass sits on it; artwork with its own glow underneath sits *in*
 * it. This is the single cheapest thing that separates a capsule card from a stock list item, and
 * it costs one extra radial gradient with no blur pass.
 */
@Composable
private fun ArtworkGlow(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.42f),
                    color.copy(alpha = 0.16f),
                    Color.Transparent,
                ),
            ),
        ),
    )
}

/** The label strip at the top of a card: quiet caption on the left, chevron on the right. */
@Composable
private fun CapsuleLabel(
    text: String,
    showChevron: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showChevron) {
            Icon(
                painter = painterResource(R.drawable.chevron_right),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

/** Section heading between decks of cards. Bold and large — the the iOS music app list header. */
@Composable
internal fun SoundChemSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = CapsuleGutter + 4.dp, vertical = 14.dp),
    )
}

// ─── Cards ────────────────────────────────────────────────────────────────────

/**
 * The headline card: total listening time, stated in minutes because minutes are the unit people
 * compare with each other.
 */
@Composable
internal fun SoundChemTimeCard(
    minutes: Long,
    modifier: Modifier = Modifier,
) {
    val displayed = rememberCountUp(minutes)

    SoundChemCard(
        modifier = modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.primary,
    ) {
        CapsuleLabel(
            text = stringResource(R.string.sound_chem_time_listened),
            showChevron = false,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sound_chem_minutes_value, formatCount(displayed)),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Minutes are the headline because minutes are the number people compare. Hours are the
        // number they can actually picture, so they get the quiet second line rather than a card
        // of their own.
        if (minutes >= 60L) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(
                    R.string.sound_chem_hours_value,
                    formatCount(minutes / 60L),
                    (minutes % 60L).toString(),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * One half of the two-up row: a label, the name in its own accent, and the artwork below it.
 *
 * The accent is passed in rather than fixed because the whole trick of the reference layout is that
 * adjacent cards are tinted differently — it stops the deck reading as a form.
 */
@Composable
internal fun SoundChemSpotlightCard(
    label: String,
    title: String,
    accent: Color,
    imageUrl: String?,
    circular: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val artShape = if (circular) CircleShape else RoundedCornerShape(14.dp)

    SoundChemCard(modifier = modifier, onClick = onClick, accent = accent) {
        CapsuleLabel(text = label, showChevron = onClick != null)
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            // Pinned to two lines in both cards so the pair keeps a shared baseline no matter how
            // long either name runs.
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkGlow(color = accent, modifier = Modifier.fillMaxSize())
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize(0.92f)
                    .clip(artShape)
                    .border(1.5.dp, accent.copy(alpha = 0.35f), artShape),
            )
        }
    }
}

/**
 * The share-of-listening card — the one the reference builds its whole screen around.
 *
 * A single artist's slice of the total, as a percentage, with the artist's face at poster size
 * above it. Only shown when there is a meaningful slice to show; StatsScreen decides that.
 */
@Composable
internal fun SoundChemShareCard(
    artistName: String,
    imageUrl: String?,
    percent: Int,
    timeText: String,
    periodText: String,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    SoundChemCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        accent = MaterialTheme.colorScheme.primary,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkGlow(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxSize(),
            )
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        shape = CircleShape,
                    ),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.sound_chem_share_headline, percent),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(6.dp))

        // The artist's name is tinted inside the sentence rather than being pulled out onto its
        // own line — the reference does this and it keeps the card to three text blocks.
        val sentence = stringResource(R.string.sound_chem_share_body, timeText, artistName)
        val nameStyle = SpanStyle(color = MaterialTheme.colorScheme.primary)
        Text(
            text = remember(sentence, artistName, nameStyle) {
                buildAnnotatedString {
                    val start = sentence.lastIndexOf(artistName)
                    if (start < 0 || artistName.isEmpty()) {
                        append(sentence)
                    } else {
                        append(sentence.substring(0, start))
                        withStyle(nameStyle) { append(artistName) }
                        append(sentence.substring(start + artistName.length))
                    }
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = periodText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (onShare != null) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = stringResource(R.string.sound_chem_share_action),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onShare)
                        .padding(6.dp)
                        .size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A compact number-over-label tile. Three of these fill one row. */
@Composable
internal fun SoundChemTallyCard(
    value: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val displayed = rememberCountUp(value.toLong())

    SoundChemCard(modifier = modifier, accent = accent) {
        Text(
            text = formatCount(displayed),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The breakdown card: the top few artists as proportional bars.
 *
 * This replaces the pie chart of cropped artist photographs that used to sit here. A pie made of
 * face-slices is unreadable at any size — you cannot compare two wedges, and the images are
 * mangled. Bars sorted by length are the comparison the card is actually for, and they animate,
 * which a pie of images never could.
 */
@Composable
internal fun SoundChemBreakdownCard(
    artists: List<Artist>,
    modifier: Modifier = Modifier,
    onArtistClick: (Artist) -> Unit = {},
) {
    val total = artists.sumOf { it.timeListened?.toLong() ?: 0L }
    if (total <= 0L) return

    val accents = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
    )

    SoundChemCard(modifier = modifier.fillMaxWidth()) {
        CapsuleLabel(
            text = stringResource(R.string.sound_chem_breakdown),
            showChevron = false,
        )
        Spacer(Modifier.height(16.dp))

        artists.forEachIndexed { index, artist ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            BreakdownRow(
                artist = artist,
                fraction = ((artist.timeListened?.toLong() ?: 0L).toFloat() / total)
                    .coerceIn(0f, 1f),
                accent = accents[index % accents.size],
                onClick = { onArtistClick(artist) },
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    artist: Artist,
    fraction: Float,
    accent: Color,
    onClick: () -> Unit,
) {
    // Held in state so the bar grows from empty on first composition rather than appearing full.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(fraction) { shown = true }
    val animatedFraction by animateFloatAsState(
        targetValue = if (shown) fraction else 0f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "breakdownBar",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artist.artist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

/** Shown in place of the whole deck when the selected period has no plays in it. */
@Composable
internal fun SoundChemEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CapsuleGutter, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.stats),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.sound_chem_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.sound_chem_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
