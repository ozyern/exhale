/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.LocalItem
import com.ozyern.exhale.ui.component.PlayingIndicatorBox
import com.ozyern.exhale.ui.component.liquidGlassSurface

/**
 * The shortcut grid at the top of Home.
 *
 * This is Spotify's, and it is borrowed on purpose: a two-column block of squat tiles, artwork
 * flush against the left edge of each, no section header above them. It is the one piece of
 * Spotify's home that the iOS music app has no equivalent for, and it does something the horizontal
 * shelves below cannot — it puts six destinations on screen at once, in a shape you can hit
 * without aiming, at the exact moment you have opened the app to resume something.
 *
 * Everything below it stays the iOS music app: bold section headers, chevrons, wide horizontal rows.
 *
 * @param items already trimmed by the caller. Six is the number that fits above the fold on a
 *   phone; more and the shelves get pushed off screen, which defeats the point.
 * @param activeId the id of whatever is currently loaded in the player, if any. These tiles are
 *   the first thing on the page you land on *while something is playing*, and they were the only
 *   list of songs in the app that would not tell you which one it was.
 * @param isPlaying whether that thing is actually running, which decides between the moving bars
 *   and a play glyph.
 * @param onItemLongClick the same menu every other row in the app opens on a long press. Six of
 *   the most-tapped targets on the page had no way to reach it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeShortcutsGrid(
    items: List<LocalItem>,
    onItemClick: (LocalItem) -> Unit,
    modifier: Modifier = Modifier,
    activeId: String? = null,
    isPlaying: Boolean = false,
    onItemLongClick: (LocalItem) -> Unit = {},
    /**
     * Per-tile arrival, by position in the grid. Supplied by Home so the six tiles can cascade
     * instead of landing as one block; the grid itself has no opinion about it.
     */
    tileIntro: (Int) -> Modifier = { Modifier },
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(ShortcutGap),
    ) {
        // Laid out by hand rather than with a grid: this sits inside a LazyColumn item, and a
        // lazy grid nested in a lazy list has no bounded height to measure against.
        items.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShortcutGap),
            ) {
                pair.forEachIndexed { column, item ->
                    ShortcutTile(
                        item = item,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        isActive = activeId != null && item.id == activeId,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .weight(1f)
                            .then(tileIntro(rowIndex * 2 + column)),
                    )
                }
                // An odd count leaves a half-width hole rather than stretching the last tile to
                // full width, which would read as a different kind of element.
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private val ShortcutGap = 8.dp
private val ShortcutHeight = 60.dp
private val ShortcutCorner = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    item: LocalItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "shortcutScale",
    )

    // Artists are people, so their artwork is round even here — the same rule the rest of the app
    // follows. Everything else is a square that fills the tile's full height, flush to the edge.
    val isArtist = item is Artist

    Row(
        modifier = modifier
            .height(ShortcutHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // The shared glass plate, not a local copy of one. These tiles sit in the brightest
            // part of the artwork wash at the top of Home, which is the one place on the page
            // where translucency has something real to reveal — a flat ink wash here is a
            // slightly-lighter black, and that is what made the block read as smudges rather
            // than as objects. This was a hand-rolled gradient-and-rim that was already trying
            // to be `liquidGlassSurface`; using the real one means it also gets the diagonal
            // sheen, and cannot drift away from the rest of the app's glass.
            .liquidGlassSurface(RoundedCornerShape(ShortcutCorner))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .then(if (isArtist) Modifier.padding(8.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(if (isArtist) CircleShape else RoundedCornerShape(0.dp)),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }

            // Over the art, not beside it: the tile is 60dp tall and every horizontal pixel of
            // the rest of it is spoken for by a two-line title. The scrim is what makes white
            // bars legible on a cover that happens to be pale, and it only exists while the
            // indicator does.
            if (isActive) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(if (isArtist) CircleShape else RoundedCornerShape(0.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }
            PlayingIndicatorBox(
                isActive = isActive,
                playWhenReady = isPlaying,
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        )
    }
}
