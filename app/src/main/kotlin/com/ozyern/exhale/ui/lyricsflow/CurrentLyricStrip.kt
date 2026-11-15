/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The strip follows BitChord's CurrentLyricStrip (github.com/kushagrasinghx/BitChord, GPL-3.0).
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf

/**
 * The line being sung, one row of small type directly above the scrubber, lit word by word as it
 * is sung with the rest of it held back. The next line slides up in its place when it starts. A tap
 * opens the lyrics. Nothing at all for a song with no lyrics, so the scrubber keeps its place.
 *
 * Like the lyrics page, the fill is worked out from the song's clock in the draw phase: a line being
 * sung redraws, nothing recomposes.
 */
@Composable
fun CurrentLyricStrip(
    sliderPositionProvider: () -> Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val trackKey = mediaMetadata?.id ?: return
    val lyrics = currentLyrics?.lyrics
    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList() else toFlowLines(lyrics)
    }
    if (lines.none { !it.isGap }) return

    val synced = remember(lines) { lines.any { it.timeMs > 0L } }
    val style = remember {
        TextStyle(
            fontFamily = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold)),
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(Modifier.weight(1f, fill = false)) {
            if (!synced) {
                StripText(text = lines.first { !it.isGap }.text, style = style, color = color, line = null, clock = null)
            } else {
                val position = rememberFlowPosition(playerConnection.player, sliderPositionProvider)
                val clock = rememberLyricClock(trackKey, rememberLyricPlayhead(position), isPlaying)
                val index by remember(lines) { derivedStateOf { latestLyricRow(lines, clock.longValue) } }
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (slideInVertically(tween(STRIP_MOTION_MS)) { it / 2 } + fadeIn(tween(STRIP_MOTION_MS))) togetherWith
                            (slideOutVertically(tween(STRIP_MOTION_MS)) { -it / 2 } + fadeOut(tween(STRIP_MOTION_MS / 2)))
                    },
                    label = "lyricStrip",
                ) { row ->
                    val line = lines.getOrNull(row)
                    if (line == null || line.isGap) {
                        // Before the first line and through instrumental breaks: a note, not a blank.
                        SymbolIcon(
                            symbol = sf("music.note"),
                            contentDescription = null,
                            tint = color.copy(alpha = UNSUNG_STRIP_ALPHA),
                            size = 15.dp,
                        )
                    } else {
                        StripText(text = line.text, style = style, color = color, line = line, clock = clock)
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        SymbolIcon(
            symbol = sf("chevron.right"),
            contentDescription = null,
            tint = color.copy(alpha = 0.5f),
            size = 12.dp,
        )
    }
}

@Composable
private fun StripText(
    text: String,
    style: TextStyle,
    color: Color,
    line: LyricLine?,
    clock: MutableLongState?,
) {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    // Measured against all the room there is, then laid out as wide as the words, so the chevron
    // follows the end of a short line rather than waiting at the far edge.
    BoxWithConstraints(Modifier.semantics { contentDescription = text }) {
        val width = constraints.maxWidth
        val layout: TextLayoutResult? = remember(text, style, width) {
            if (width <= 0 || width == Constraints.Infinity) null else measurer.measure(
                text,
                style,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = width),
            )
        }
        val density = LocalDensity.current
        val textWidth = with(density) { (layout?.size?.width ?: 0).toDp() }
        Box(
            Modifier
                .width(textWidth)
                .height(STRIP_HEIGHT)
                .drawWithContent {
                    val measured = layout ?: return@drawWithContent
                    val top = (size.height - measured.size.height) / 2f
                    val sung = if (line != null && clock != null) sungChars(line, clock.longValue) else text.length.toFloat()
                    translate(top = top) {
                    drawText(measured, color = color.copy(alpha = UNSUNG_STRIP_ALPHA))
                    if (sung > 0f) {
                        val end = measured.getLineEnd(0, visibleEnd = true)
                        val whole = sung.toInt().coerceIn(0, end)
                        val x0 = measured.getHorizontalPosition(whole, usePrimaryDirection = true)
                        val x1 = if (whole < end) measured.getHorizontalPosition(whole + 1, usePrimaryDirection = true) else x0
                        val x = if (sung >= end) measured.size.width.toFloat() else x0 + (x1 - x0) * (sung - whole)
                        clipRect(right = x) { drawText(measured, color = color) }
                        val feather = FEATHER.toPx()
                        if (sung < end) {
                            clipRect(left = x, right = x + feather) {
                                drawText(
                                    measured,
                                    brush = Brush.horizontalGradient(
                                        0f to color.copy(alpha = 1f - UNSUNG_STRIP_ALPHA),
                                        1f to Color.Transparent,
                                        startX = x,
                                        endX = x + feather,
                                    ),
                                )
                            }
                        }
                    }
                    }
                },
        )
    }
}

private val STRIP_HEIGHT = 28.dp
private val FEATHER = 14.dp
private const val STRIP_MOTION_MS = 320

/** The strip is one line of small type with nothing beside it: held back less than the page. */
private const val UNSUNG_STRIP_ALPHA = 0.55f
