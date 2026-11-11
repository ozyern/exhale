/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The idea of a word arriving as its own mark — blooming large as it is sung, joined to the last by
 * a line that draws itself, then settling — follows AETHER's constellation lyrics
 * (github.com/GHOSTxASR/AETHER, GPL-3.0). Here the marks are notes on a staff.
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.ui.component.LocalLyricsGlowColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** A word held at least this long is written as an open note; shorter than [FlagBelowMs], with a flag. */
private const val OpenNoteFromMs = 700L
private const val FlagBelowMs = 230L

/** How long a word's pop lasts once it is sung. */
private const val PopMs = 360f

/** Staff positions, counted in half-gaps from the bottom line: 0 is the bottom line, 8 the top. */
private const val TopStep = 8

private val MelodyFont = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

/**
 * The lyrics as sheet music.
 *
 * Every line of the song is a system of staff, every word a note on it with the word written
 * underneath, the way a vocal score sets them. As a word is sung its note pops and lights and the
 * word springs up under it; the tune — a line drawn through the notes — runs ahead from one to the
 * next in time with the voice. A word held long is an open note, a quick one carries a flag, and
 * the notes rise and fall across the line on a walk that is the same every time the song plays.
 *
 * The page follows the singing, keeping the line being sung a third of the way down; drag it and
 * it waits, then comes back. Tap a line to go to it.
 */
@Composable
fun ExhaleMelodyLyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    /** Multiple of the style's own type size. */
    textScale: Float = 1f,
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
    val synced = remember(lines) { lines.any { it.timeMs > 0L } }

    val position = rememberFlowPosition(player, sliderPositionProvider)
    val clock = rememberLyricClock(trackKey, rememberLyricPlayhead(position), isPlaying)
    val active by remember(lines, synced) {
        derivedStateOf { if (synced) latestLyricRow(lines, clock.longValue) else -1 }
    }

    val listState = rememberLazyListState()
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    // Follows the singing; a drag holds it still until a moment after the finger has gone.
    LaunchedEffect(active, dragged) {
        if (active < 0) return@LaunchedEffect
        if (dragged) return@LaunchedEffect
        if (listState.isScrollInProgress) kotlinx.coroutines.delay(2_400)
        runCatching { listState.animateScrollToItem(active) }
    }

    val glow = LocalLyricsGlowColor.current.takeIf { it != Color.Unspecified } ?: Color(0xFF7FD6FF)
    val measurer = rememberTextMeasurer()
    val style = remember(textScale) {
        TextStyle(fontFamily = MelodyFont, fontSize = 22.sp * textScale, letterSpacing = (-0.01).em, color = Color.White)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = viewport * 0.26f, bottom = viewport * 0.62f),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
                val distance = if (active < 0) 0 else abs(index - active)
                val rowAlpha by animateFloatAsState(
                    targetValue = when {
                        !synced -> 0.92f
                        distance == 0 -> 1f
                        distance == 1 -> 0.46f
                        else -> 0.26f
                    },
                    animationSpec = tween(360),
                    label = "melodyRow",
                )
                MelodyRow(
                    line = line,
                    nextLineMs = lines.getOrNull(index + 1)?.timeMs,
                    measurer = measurer,
                    style = style,
                    glow = glow,
                    clock = clock,
                    // Only the line being sung follows the clock; the rest are still pictures.
                    state = when {
                        !synced -> RowState.Still
                        index == active -> RowState.Singing
                        index < active -> RowState.Sung
                        else -> RowState.Ahead
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = rowAlpha }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = synced,
                        ) { player.seekTo(line.timeMs) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private enum class RowState { Still, Ahead, Singing, Sung }

/** One word's note: which system of the row it is on, where, how high, and when it is sung. */
private class MelodyNote(
    val layout: TextLayoutResult,
    val system: Int,
    val x: Float,
    val step: Int,
    val startMs: Long,
    val endMs: Long,
)

private class MelodySheet(val notes: List<MelodyNote>, val systems: Int, val systemHeight: Float, val gap: Float, val textHeight: Float) {
    val height: Float get() = systems * systemHeight

    /** The y of the staff's bottom line in [system]. */
    fun staffBottom(system: Int): Float = system * systemHeight + gap * 2.6f + gap * 4f

    fun noteY(note: MelodyNote): Float = staffBottom(note.system) - note.step * gap / 2f
}

/** Sets [line]'s words as notes across as many systems as [width] needs. Deterministic for a line. */
private fun engrave(
    line: LyricLine,
    nextLineMs: Long?,
    width: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
): MelodySheet {
    val gap = with(density) { 8.dp.toPx() } * (style.fontSize.value / 22f).coerceIn(0.8f, 1.4f)
    val between = with(density) { 12.dp.toPx() }
    val inset = with(density) { 10.dp.toPx() }
    val words: List<Triple<String, Long, Long>> = if (line.words.isNotEmpty()) {
        line.words.map { Triple(it.text, it.startMs, it.endMs) }
    } else {
        // No times inside the line: its words share it out evenly, or, with no times at all, none.
        val tokens = line.text.split(' ').filter { it.isNotBlank() }
        val end = nextLineMs?.takeIf { it > line.timeMs } ?: (line.timeMs + 4_000L)
        tokens.mapIndexed { i, token ->
            if (line.timeMs <= 0L && nextLineMs == null) {
                Triple(token, Long.MAX_VALUE, Long.MAX_VALUE)
            } else {
                val span = (end - line.timeMs) / tokens.size.coerceAtLeast(1)
                Triple(token, line.timeMs + span * i, line.timeMs + span * (i + 1))
            }
        }
    }
    // The tune: a walk up and down the staff, seeded by the line so it never changes.
    var seed = (line.text.hashCode().toLong() * 6364136223846793005L + line.timeMs) or 1L
    fun next(bound: Int): Int {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (((seed ushr 33) % bound).toInt() + bound) % bound
    }
    var step = 2 + next(4)
    var cursor = inset
    var system = 0
    var textHeight = 0f
    val notes = ArrayList<MelodyNote>(words.size)
    for ((text, start, end) in words) {
        val layout = measurer.measure(text, style, maxLines = 1)
        val w = maxOf(layout.size.width.toFloat(), gap * 2.2f)
        if (cursor + w > width - inset && cursor > inset) {
            system++
            cursor = inset
        }
        textHeight = maxOf(textHeight, layout.size.height.toFloat())
        notes += MelodyNote(layout, system, cursor + w / 2f, step, start, end)
        cursor += w + between
        // Mostly by step, sometimes a leap, and never off the staff.
        val move = when (next(8)) {
            0 -> 3
            1 -> -3
            2, 3 -> 2
            4, 5 -> -2
            6 -> 1
            else -> -1
        }
        step = (step + move).let { if (it < 0 || it > TopStep) step - move else it }.coerceIn(0, TopStep)
    }
    // Room above the staff for stems, the staff, the words under it, and air before the next system.
    val systemHeight = gap * 2.6f + gap * 4f + gap * 1.5f + textHeight + with(density) { 14.dp.toPx() }
    return MelodySheet(notes, system + 1, systemHeight, gap, textHeight)
}

@Composable
private fun MelodyRow(
    line: LyricLine,
    nextLineMs: Long?,
    measurer: TextMeasurer,
    style: TextStyle,
    glow: Color,
    clock: MutableLongState,
    state: RowState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val sheet = remember(line, nextLineMs, widthPx, style) { engrave(line, nextLineMs, widthPx, measurer, style, density) }
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(with(density) { sheet.height.toDp() })
                .drawWithCache {
                    val gap = sheet.gap
                    val hair = Stroke(1.dp.toPx())
                    // The tune through each system's notes, made once.
                    val tunes = (0 until sheet.systems).map { system ->
                        val on = sheet.notes.filter { it.system == system }
                        Path().apply {
                            on.forEachIndexed { i, note ->
                                val y = sheet.noteY(note)
                                if (i == 0) {
                                    moveTo(note.x, y)
                                } else {
                                    val before = on[i - 1]
                                    val mid = (before.x + note.x) / 2f
                                    cubicTo(mid, sheet.noteY(before), mid, y, note.x, y)
                                }
                            }
                        }
                    }
                    val tuneStroke = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
                    onDrawBehind {
                        val t = when (state) {
                            RowState.Singing -> clock.longValue
                            RowState.Sung -> Long.MAX_VALUE - 1
                            RowState.Still -> Long.MAX_VALUE - 1
                            RowState.Ahead -> Long.MIN_VALUE
                        }
                        val singing = state == RowState.Singing
                        // ---- Staff ----
                        for (system in 0 until sheet.systems) {
                            val bottom = sheet.staffBottom(system)
                            for (l in 0..4) {
                                val y = bottom - l * gap
                                drawLine(Color.White.copy(alpha = 0.20f), Offset(0f, y), Offset(size.width, y), strokeWidth = hair.width)
                            }
                            drawLine(Color.White.copy(alpha = 0.34f), Offset(0f, bottom), Offset(0f, bottom - 4 * gap), strokeWidth = hair.width * 1.4f)
                            drawLine(Color.White.copy(alpha = 0.34f), Offset(size.width, bottom), Offset(size.width, bottom - 4 * gap), strokeWidth = hair.width * 1.4f)
                        }
                        // ---- The tune: faint all the way, lit as far as the voice has got ----
                        val current = sheet.notes.indexOfLast { it.startMs <= t }
                        tunes.forEachIndexed { system, path ->
                            drawPath(path, Color.White.copy(alpha = 0.12f), style = tuneStroke)
                            val litTo = when {
                                current < 0 -> -1f
                                sheet.notes[current].system > system -> size.width
                                sheet.notes[current].system < system -> -1f
                                else -> {
                                    val at = sheet.notes[current]
                                    val after = sheet.notes.getOrNull(current + 1)?.takeIf { it.system == system }
                                    if (after == null || after.startMs <= at.startMs || !singing) {
                                        at.x
                                    } else {
                                        val f = ((t - at.startMs).toFloat() / (after.startMs - at.startMs)).coerceIn(0f, 1f)
                                        at.x + (after.x - at.x) * f
                                    }
                                }
                            }
                            if (litTo > 0f) {
                                clipRect(right = litTo) {
                                    drawPath(path, glow.copy(alpha = 0.9f), style = tuneStroke)
                                }
                            }
                        }
                        // ---- Notes and their words ----
                        for (note in sheet.notes) {
                            val sung = t >= note.startMs
                            val p = if (singing && sung) ((t - note.startMs) / PopMs).coerceIn(0f, 1f) else 1f
                            // Up fast and back: a pop, not a swell.
                            val pop = if (p < 1f) sin(PI.toFloat() * p) * (1f - p) * 1.7f else 0f
                            val ink = if (sung) 1f else 0.42f
                            drawNote(sheet, note, gap, ink, pop, glow)
                            val wordTop = sheet.staffBottom(note.system) + gap * 1.5f - 5.dp.toPx() * pop
                            val topLeft = Offset(note.x - note.layout.size.width / 2f, wordTop)
                            scale(1f + 0.16f * pop, Offset(note.x, wordTop + note.layout.size.height)) {
                                drawText(note.layout, color = Color.White, topLeft = topLeft, alpha = ink)
                            }
                        }
                    }
                },
        )
    }
}

/** One note: head, stem, a flag if it is quick, and the light it gives off as it pops. */
private fun DrawScope.drawNote(sheet: MelodySheet, note: MelodyNote, gap: Float, ink: Float, pop: Float, glow: Color) {
    val y = sheet.noteY(note)
    val centre = Offset(note.x, y)
    val held = note.endMs - note.startMs
    val open = note.endMs != Long.MAX_VALUE && held >= OpenNoteFromMs
    val quick = held in 1 until FlagBelowMs
    val rx = gap * 0.66f
    val ry = gap * 0.47f
    val white = Color.White.copy(alpha = ink)
    if (pop > 0.01f) {
        val r = gap * (2.4f + 1.6f * pop)
        drawCircle(
            Brush.radialGradient(listOf(glow.copy(alpha = 0.55f * pop.coerceAtMost(1f)), Color.Transparent), center = centre, radius = r),
            radius = r,
            center = centre,
        )
    }
    scale(1f + 0.5f * pop, centre) {
        // A ledger line for a note sitting on the bottom or the top line's neighbours is not needed:
        // the walk stays on the staff.
        val up = note.step < 4
        val stemX = if (up) note.x + rx * 0.88f else note.x - rx * 0.88f
        val stemEnd = if (up) y - gap * 3.1f else y + gap * 3.1f
        drawLine(white, Offset(stemX, y), Offset(stemX, stemEnd), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        if (quick) {
            val dir = if (up) 1f else -1f
            val flag = Path().apply {
                moveTo(stemX, stemEnd)
                cubicTo(stemX + gap * 0.9f, stemEnd + dir * gap * 0.6f, stemX + gap * 1.1f, stemEnd + dir * gap * 1.4f, stemX + gap * 0.5f, stemEnd + dir * gap * 2f)
            }
            drawPath(flag, white, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        }
        rotate(-22f, centre) {
            drawOval(
                color = white,
                topLeft = Offset(note.x - rx, y - ry),
                size = Size(rx * 2, ry * 2),
                style = if (open) Stroke(1.8.dp.toPx()) else Fill,
            )
        }
    }
}
