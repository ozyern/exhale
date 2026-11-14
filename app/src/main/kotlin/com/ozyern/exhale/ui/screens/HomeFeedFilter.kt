/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.ui.unit.dp
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.ChipsRow
import com.ozyern.exhale.ui.theme.rememberArtworkTint
import com.music.innertube.pages.HomePage

/** "All" followed by YouTube Music's own chips; null is the unfiltered Home. */
@Composable
internal fun HomeFeedFilterRow(
    moodChips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onSelected: (HomePage.Chip?) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    sideInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    ChipsRow(
        chips = listOf<Pair<HomePage.Chip?, String>>(null to stringResource(R.string.filter_all)) +
            moodChips.map { it to it.title },
        currentValue = selectedChip,
        onValueUpdate = onSelected,
        modifier = modifier,
        scrollState = scrollState,
        sideInset = sideInset,
    )
}

/**
 * The image YouTube Music pairs with a chip, pinned to the top and fading through its own
 * tone into the page. [scrolledPx] fades it out and drifts it up as the feed scrolls.
 */
@Composable
internal fun HomeChipBackdrop(url: String?, fadeTo: Color, scrolledPx: () -> Float) {
    Crossfade(targetState = url, label = "homeChipBackdrop") { current ->
        if (current == null) return@Crossfade
        val artTone = rememberArtworkTint(current).firstOrNull() ?: fadeTo
        // Light pages keep dark text, so the veil leans toward the page colour to stay readable.
        val tone = if (fadeTo.luminance() < 0.5f) artTone else lerp(artTone, fadeTo, 0.6f)
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .graphicsLayer {
                    val scrolled = scrolledPx()
                    alpha = (1f - scrolled / size.height).coerceIn(0f, 1f)
                    translationY = -scrolled / 2f
                },
        ) {
            AsyncImage(
                model = current,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0f to tone.copy(alpha = 0.2f),
                        0.45f to tone.copy(alpha = 0.55f),
                        0.75f to tone,
                        1f to fadeTo,
                    ),
                ),
            )
        }
    }
}
