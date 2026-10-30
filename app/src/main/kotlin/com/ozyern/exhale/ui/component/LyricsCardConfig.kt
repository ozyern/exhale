/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Layout styles: each value is a different design for the card.
// A new style is an entry here plus a composable in LyricsCardLayouts.
// ─────────────────────────────────────────────────────────────────────────────

enum class LyricsLayoutStyle(
    val displayName: String,
    val description: String,
) {
    Vivid(
        displayName = "Vivid",
        description = "Artwork colours, lines up top",
    ),
    GlassCard(
        displayName = "Glass Card",
        description = "Liquid glass panel",
    ),
    Minimal(
        displayName = "Minimal",
        description = "Clean, nothing else",
    ),
    CoverFocused(
        displayName = "Cover Focus",
        description = "Featured album cover",
    ),
    Centered(
        displayName = "Centered",
        description = "The lyric takes the stage",
    ),
    BlurWash(
        displayName = "Blur Wash",
        description = "Heavily blurred artwork",
    ),
    StreamingModern(
        displayName = "Streaming",
        description = "Modern music app style",
    ),
}

// ─────────────────────────────────────────────────────────────────────────────
// The background, for the layouts that offer a choice of one
// ─────────────────────────────────────────────────────────────────────────────

enum class LyricsBackgroundType(val displayName: String) {
    AlbumArt("Artwork"),
    SolidDark("Dark"),
    SolidLight("Light"),
    Gradient("Gradient"),
}

// ─────────────────────────────────────────────────────────────────────────────
// LyricsCardConfig: the listener's choices, immutable.
// Se pasa a LyricsCardByLayout y a LyricsShareCarouselSheet.
// Change it with .copy(...).
// ─────────────────────────────────────────────────────────────────────────────

data class LyricsCardConfig(

    /** Which design the card is drawn in */
    val layoutStyle: LyricsLayoutStyle = LyricsLayoutStyle.Vivid,

    /** Glass, colours and blur; only the layouts drawn with cloudy/liquidGlass read it */
    val glassStyle: LyricsGlassStyle = LyricsGlassStyle.FrostedDark,

    /**
     * A multiplier on the automatically fitted text size.
     * Rango recomendado: 0.6f – 1.5f
     */
    val textSizeMultiplier: Float = 1f,

    /** How the lyric lines are aligned */
    val textAlign: TextAlign = TextAlign.Start,

    /** What the card shows besides the lyrics */
    val showTitle: Boolean = true,
    val showArtist: Boolean = true,
    val showCoverArt: Boolean = true,
    val showBranding: Boolean = true,

    /** The background (read by Minimal and StreamingModern) */
    val backgroundType: LyricsBackgroundType = LyricsBackgroundType.AlbumArt,

    /**
     * The card's inner padding.
     * Rango recomendado: 12.dp – 36.dp
     */
    val cardPadding: Dp = 24.dp,
)
