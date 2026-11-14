/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The look follows BitChord's lyrics view (github.com/kushagrasinghx/BitChord, GPL-3.0): big bold
 * lines down the left, the one being sung filled word by word as it is sung.
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import kotlin.math.abs

/**
 * BitChord's lyrics: every line in big bold type down the left of the screen, all of them dim but
 * the one being sung. That one fills with white word by word, the fill sweeping across each word
 * over the time it is sung behind a soft edge, so the line lights up with the voice rather than all
 * at once. It stays lit until the next line takes over. The view keeps the line being sung a
 * little below the top and glides to the next one; lines further from it fade, and a tap on any
 * line goes to it.
 *
 * The fill is worked out from the song's time on every frame, in the draw phase: a line being sung
 * redraws, nothing recomposes.
 */
@Composable
fun ExhaleBitChordLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    fontFamily: FontFamily? = null,
    /** The type size, in sp. */
    textSize: Float = 30f,
    /** Line height as a multiple of the type size. */
    lineSpacing: Float = 1.18f,
    onSeek: ((Long) -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val currentLyrics by playerConnection.currentLyrics.collectAsState(initial = null)
    val lyrics = currentLyrics?.lyrics
    val trackKey = mediaMetadata?.id ?: return

    val lines = remember(lyrics) {
        if (lyrics == null || lyrics == LYRICS_NOT_FOUND) emptyList() else toFlowLines(lyrics).filter { !it.isGap }
    }
    if (lines.isEmpty()) return
    val synced = lines.any { it.timeMs > 0L }

    val position = rememberFlowPosition(player, sliderPositionProvider)
    val clock = rememberLyricClock(trackKey, rememberLyricPlayhead(position), isPlaying)
    val active by remember(lines) {
        derivedStateOf { if (synced) latestLyricRow(lines, clock.longValue).coerceAtLeast(0) else -1 }
    }

    val family = fontFamily ?: remember { FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold)) }
    val style = remember(family, textSize, lineSpacing) {
        TextStyle(
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = textSize.sp,
            lineHeight = (textSize * lineSpacing).sp,
            letterSpacing = (-0.01).sp,
        )
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val anchor = maxHeight * ANCHOR_FRACTION
        val listState = rememberLazyListState()
        // The view glides to the line being sung; a reader scrolling it holds it where they put it
        // until the next line starts.
        LaunchedEffect(active) {
            if (active >= 0 && !listState.isScrollInProgress) {
                listState.animateScrollToItem(active)
            }
        }
        LazyColumn(
            state = listState,
            userScrollEnabled = true,
            contentPadding = PaddingValues(top = anchor, bottom = maxHeight * 0.7f),
            modifier = Modifier
                .fillMaxSize()
                // The ends of the column melt into the page rather than being cut off.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.06f to Color.Black,
                            0.78f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
                BitChordLine(
                    line = line,
                    style = style,
                    clock = clock,
                    isActive = index == active,
                    distance = if (active < 0) 0 else abs(index - active),
                    onClick = onSeek?.takeIf { synced }?.let { seek -> { seek(line.timeMs) } },
                )
            }
        }
    }
}

@Composable
private fun BitChordLine(
    line: LyricLine,
    style: TextStyle,
    clock: MutableLongState,
    isActive: Boolean,
    distance: Int,
    onClick: (() -> Unit)?,
) {
    val measurer = rememberTextMeasurer(cacheSize = 4)
    var width by remember { mutableStateOf(0) }
    val layout: TextLayoutResult? = remember(line.text, style, width) {
        if (width <= 0) null else measurer.measure(line.text, style, constraints = Constraints(maxWidth = width))
    }
    // Lines away from the one being sung recede: a little smaller and fainter the further they are.
    val focus by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f),
        label = "bitchordFocus",
    )
    val far by animateFloatAsState(
        targetValue = (distance.coerceAtMost(4) / 4f),
        animationSpec = tween(420),
        label = "bitchordFar",
    )
    val density = LocalDensity.current
    val heightDp = with(density) { (layout?.size?.height ?: 0).toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 11.dp)
            .onSizeChanged { width = it.width }
            .height(heightDp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .graphicsLayer {
                val s = 0.97f + 0.03f * focus
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0f, 0.5f)
                alpha = 1f - 0.55f * far * (1f - focus)
            }
            .drawWithContent {
                val text = layout ?: return@drawWithContent
                // Every line in the dim ink first; the one being sung is lit over it.
                drawText(text, color = Color.White.copy(alpha = DIM_ALPHA))
                if (!isActive) return@drawWithContent
                val sung = sungChars(line, clock.longValue)
                if (sung <= 0f) return@drawWithContent
                val feather = FEATHER.toPx()
                for (row in 0 until text.lineCount) {
                    val rowStart = text.getLineStart(row)
                    val rowEnd = text.getLineEnd(row, visibleEnd = true)
                    val top = text.getLineTop(row)
                    val bottom = text.getLineBottom(row)
                    val left = text.getLineLeft(row)
                    val right = text.getLineRight(row)
                    if (sung >= rowEnd) {
                        clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
                            drawText(text, color = Color.White)
                        }
                        continue
                    }
                    if (sung <= rowStart) break
                    // Where the fill has got to on this row, between the two characters it is between.
                    val whole = sung.toInt().coerceIn(rowStart, rowEnd)
                    val part = sung - whole
                    val x0 = text.getHorizontalPosition(whole, usePrimaryDirection = true)
                    val x1 = if (whole < rowEnd) text.getHorizontalPosition(whole + 1, usePrimaryDirection = true) else right
                    val x = (x0 + (x1 - x0) * part).coerceIn(left, right)
                    clipRect(left = 0f, top = top, right = x, bottom = bottom) {
                        drawText(text, color = Color.White)
                    }
                    // The soft edge: the fill fades out across a few letters ahead of itself.
                    clipRect(left = x, top = top, right = x + feather, bottom = bottom) {
                        drawText(
                            text,
                            brush = Brush.horizontalGradient(
                                0f to Color.White.copy(alpha = 1f - DIM_ALPHA),
                                1f to Color.Transparent,
                                startX = x,
                                endX = x + feather,
                            ),
                        )
                    }
                    break
                }
            },
    )
}

/** How many characters of [line] have been sung at [positionMs], as a fraction. */
private fun sungChars(line: LyricLine, positionMs: Long): Float {
    if (line.words.isEmpty()) return if (positionMs >= line.timeMs) line.text.length.toFloat() else 0f
    val spans = line.wordSpans
    var sung = 0f
    for (i in line.words.indices) {
        val word = line.words[i]
        val span = spans.getOrNull(i) ?: continue
        if (positionMs >= word.endMs) {
            sung = (span.last + 1).toFloat()
            continue
        }
        if (positionMs > word.startMs) sung = span.first + word.charsSungAt(positionMs)
        break
    }
    return sung
}

/** Where the line being sung sits, as a share of the view's height from its top. */
private const val ANCHOR_FRACTION = 0.16f

/** The ink of everything not yet sung, and of the lines not being sung. */
private const val DIM_ALPHA = 0.32f

/** The width of the fill's soft leading edge. */
private val FEATHER = 22.dp
