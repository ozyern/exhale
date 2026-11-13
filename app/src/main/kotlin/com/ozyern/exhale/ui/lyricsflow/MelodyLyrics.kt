/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Built after AETHER's constellation lyrics (github.com/GHOSTxASR/AETHER, GPL-3.0,
 * ui/lyrics/Constellations.kt): one drawing the camera travels across, a figure to each line, a
 * word blooming at its own mark as it is sung and settling beside it, lights stamped from a
 * picture made once. There the marks are stars in a sky; here they are the notes of the tune.
 */

package com.ozyern.exhale.ui.lyricsflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.ui.component.LocalLyricsGlowColor
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A word held at least this long is written as an open note; shorter than [FlagBelowMs], with a flag. */
private const val OpenNoteFromMs = 700L
private const val FlagBelowMs = 230L

/** Staff positions, counted in half-gaps from the bottom line: 0 is the bottom line, 8 the top. */
private const val TopStep = 8

private val MelodyFont = FontFamily(Font(R.font.sfprodisplaybold, FontWeight.Bold))

/** One word's note: where it sits in the score, when it is sung and for how long. */
private class Note(val at: Offset, val word: String, val start: Long, val end: Long, val step: Int)

/** A line of the song: its notes on their own run of staff, where it is centred, and when it is sung. */
private class Phrase(val centre: Offset, val notes: List<Note>, val start: Long, val end: Long, val half: Float)

/**
 * A spring, by Apple's response and damping, solved exactly at a time: the same motion at 60 or
 * 120 frames a second, because it is never stepped frame by frame.
 */
private class Springy(response: Float, private val damping: Float) {
    private val omega = (2.0 * PI / response).toFloat()

    /** 0 to 1 from rest, [ms] after it began (past 1 and back when under-damped). */
    fun progress(ms: Long): Float {
        if (ms <= 0) return 0f
        val t = ms / 1000f
        return if (damping >= 1f) {
            1f - (1f + omega * t) * exp(-omega * t)
        } else {
            val wd = omega * sqrt(1f - damping * damping)
            1f - exp(-damping * omega * t) * (cos(wd * t) + damping * omega / wd * sin(wd * t))
        }
    }
}

private val PopSpring = Springy(0.5f, 0.6f)
private val BloomSpring = Springy(0.38f, 0.82f)
private val FadeSpring = Springy(0.32f, 1f)
private val DrawSpring = Springy(0.45f, 1f)
private val DimSpring = Springy(0.9f, 1f)

/** The camera leaves for the next line this long before it is sung, so it is there when the line is. */
private const val CameraLeadMs = 320L

/**
 * The lyrics as the song's tune.
 *
 * The whole song is one score the view travels across. Each line is a short run of staff with a
 * note to every word, rising and falling on a walk that is the same every time the song plays; a
 * word held long is an open note, a quick one carries a flag. The notes of the line being sung
 * wait faintly on their staff. As a word is sung its note lights, the word blooms large above the
 * staff and the tune (a line through the notes) draws itself on to it, a point of light running
 * ahead along the tune between one word and the next, so something is always moving with the
 * voice. Once sung, the word settles small under its note, the way a vocal score sets its words.
 * When the line ends the view glides on to where the next one begins, drawing back a little while
 * it travels.
 *
 * It is one canvas driven by one clock: nothing is laid out or measured while it plays (a line's
 * words are measured a line ahead, between frames), its lights are one picture stamped, and its
 * springs are solved from the song's time rather than stepped, so it moves the same at any refresh
 * rate. It plays; tap either side of the screen to go back or on a line.
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

    val position = rememberFlowPosition(player, sliderPositionProvider)
    val clock = rememberLyricClock(trackKey, rememberLyricPlayhead(position), isPlaying)
    val glow = LocalLyricsGlowColor.current.takeIf { it != Color.Unspecified } ?: Color(0xFF8FD8FF)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val bigStyle = remember(textScale) { TextStyle(fontFamily = MelodyFont, fontSize = 34.sp * textScale, lineHeight = 38.sp * textScale) }
    val smallStyle = remember(textScale) { TextStyle(fontFamily = MelodyFont, fontSize = 12.sp * textScale, lineHeight = 14.sp * textScale) }
    val playing by rememberUpdatedState(isPlaying)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val gap = with(density) { 9.dp.toPx() } * textScale.coerceIn(0.8f, 1.3f)
        val phrases = remember(lines, w, h, gap) { score(lines, w, h, gap) }
        if (phrases.isEmpty()) return@BoxWithConstraints
        val labels = remember(lines, bigStyle, smallStyle) { HashMap<String, TextLayoutResult>() }
        val light = remember(density) { glowSprite(density) }
        val tint = remember(glow) { ColorFilter.tint(glow, BlendMode.SrcIn) }
        val tunes = remember(phrases) { phrases.map(::tuneOf) }

        // The camera: where the view is, and how far it has drawn back to travel. One loop steps
        // it, a few small steps a frame, toward the line being sung.
        val camera = remember(phrases) { floatArrayOf(phrases[0].centre.x, phrases[0].centre.y, 0f, 0f, 1f) }
        val frame = remember { mutableLongStateOf(0L) }
        LaunchedEffect(phrases) {
            var measuredUpTo = -1
            var last = withFrameNanos { it }
            while (true) {
                com.ozyern.exhale.utils.awaitAppVisible()
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val t = clock.longValue
                val current = phraseAt(phrases, t + CameraLeadMs).coerceAtLeast(0)
                // This line's words and the next's are measured here, between frames, so a word
                // never waits on its own layout as it appears.
                if (current != measuredUpTo) {
                    measuredUpTo = current
                    for (i in current..min(current + 1, phrases.lastIndex)) phrases[i].notes.forEach { note ->
                        label(measurer, labels, note.word, bigStyle, true, density)
                        label(measurer, labels, note.word, smallStyle, false, density)
                    }
                }
                val target = phrases[current].centre
                // A spring, stepped finely enough that a long frame cannot make it overshoot.
                val steps = 4
                val h4 = dt / steps
                repeat(steps) {
                    val ax = -CameraStiffness * (camera[0] - target.x) - CameraFriction * camera[2]
                    val ay = -CameraStiffness * (camera[1] - target.y) - CameraFriction * camera[3]
                    camera[2] += ax * h4
                    camera[3] += ay * h4
                    camera[0] += camera[2] * h4
                    camera[1] += camera[3] * h4
                }
                // Travelling, the view draws back a little, as a camera pulls out to pan.
                val speed = hypot(camera[2], camera[3])
                val wanted = 1f - (speed / (w * 3.2f)).coerceIn(0f, 0.14f)
                camera[4] += (wanted - camera[4]) * (1f - exp(-dt / 0.16f))
                frame.longValue++
                // Paused and come to rest: no more frames until it plays or the song is moved.
                if (!playing && speed < 1f) {
                    val heldAt = clock.longValue
                    androidx.compose.runtime.snapshotFlow { playing || clock.longValue != heldAt }.first { it }
                    last = withFrameNanos { it }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(phrases) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                            if (!down.pressed) continue
                            val start = down.position
                            var moved = 0f
                            var up = start
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull() ?: break
                                moved = max(moved, (change.position - start).getDistance())
                                up = change.position
                                if (!change.pressed) break
                            }
                            if (moved < viewConfiguration.touchSlop) {
                                val current = phraseAt(phrases, clock.longValue)
                                val to = if (up.x < size.width / 2f) max(0, current - 1) else min(phrases.lastIndex, current + 1)
                                player.seekTo(phrases[to].start)
                            }
                        }
                    }
                },
        ) {
            frame.longValue // read: one redraw for each frame of the loop
            val t = clock.longValue
            val focus = Offset(size.width / 2f, size.height * 0.46f)
            val origin = focus - Offset(camera[0], camera[1])
            val k = camera[4]
            scale(k, k, pivot = focus) {
                val current = phraseAt(phrases, t)
                val from = max(0, current - 2)
                val to = min(phrases.lastIndex, max(current, 0) + 1)
                for (i in from..to) {
                    val alpha = when {
                        i == current -> 1f
                        // The line just sung dims, on a slow spring, as the next one forms.
                        i == current - 1 -> 1f - 0.7f * DimSpring.progress(t - phrases[current].start)
                        i < current -> 0.16f
                        // The line to come waits, faint, where the view is about to go.
                        else -> 0.34f
                    }
                    drawPhrase(phrases[i], tunes[i], i == current, i > current, t, alpha, origin, gap, glow, tint, light, measurer, labels, bigStyle, smallStyle)
                }
                // The point of light on its way across the gap to the next line's first note.
                if (current >= 0 && current < phrases.lastIndex) {
                    val here = phrases[current]
                    val next = phrases[current + 1]
                    if (t >= here.end && next.start > here.end) {
                        val f = ((t - here.end).toFloat() / (next.start - here.end)).coerceIn(0f, 1f)
                        val e = f * f * (3f - 2f * f)
                        val a = origin + here.notes.last().at
                        val b = origin + next.notes.first().at
                        // An arc rather than a ruled line: it lifts off one note and lands on the next.
                        val lift = Offset(0f, -sin(PI.toFloat() * e) * gap * 5f)
                        stamp(light, a + (b - a) * e + lift, gap * 5.5f, 0.85f, tint)
                    }
                }
            }
        }
    }
}

private const val CameraStiffness = 30f
private const val CameraFriction = 8.6f

/** The line being sung at [t]: the last to have started, or -1 before the first. */
private fun phraseAt(phrases: List<Phrase>, t: Long): Int {
    var i = phrases.size - 1
    while (i >= 0) {
        if (phrases[i].start <= t) return i
        i--
    }
    return -1
}

/**
 * Writes the score: each sung line a run of staff a screen's width from the last, in a heading
 * that wanders; its words left to right, each on a step of the staff a walk away from the one
 * before. Deterministic: the same song always makes the same score.
 */
private fun score(lines: List<LyricLine>, w: Float, h: Float, gap: Float): List<Phrase> {
    val phrases = ArrayList<Phrase>()
    var centre = Offset.Zero
    var heading = 0f
    lines.forEachIndexed { i, line ->
        val nextMs = lines.getOrNull(i + 1)?.timeMs
        val words: List<Triple<String, Long, Long>> = if (line.words.isNotEmpty()) {
            line.words.map { Triple(it.text, it.startMs, it.endMs) }
        } else {
            val tokens = line.text.split(' ').filter { it.isNotBlank() }
            val end = nextMs?.takeIf { it > line.timeMs } ?: (line.timeMs + 4_000L)
            val span = (end - line.timeMs) / tokens.size.coerceAtLeast(1)
            tokens.mapIndexed { j, token -> Triple(token, line.timeMs + span * j, line.timeMs + span * (j + 1)) }
        }
        if (words.isEmpty()) return@forEachIndexed
        val rnd = Random(i * 7919 + 17 + line.text.hashCode())
        if (phrases.isNotEmpty()) {
            heading = (heading + (rnd.nextFloat() - 0.5f) * 1.7f).coerceIn(-1.0f, 1.0f)
            centre += Offset(cos(heading), sin(heading) * 0.8f) * (w * 0.98f)
        }
        val k = words.size
        val spread = min(w * 0.8f, k * w * 0.17f)
        var step = 2 + rnd.nextInt(5)
        val notes = words.mapIndexed { j, (text, start, end) ->
            val x = if (k == 1) 0f else -spread / 2f + spread * j / (k - 1)
            // Mostly by step, sometimes a leap, and never off the staff.
            if (j > 0) {
                val move = when (rnd.nextInt(8)) {
                    0 -> 3
                    1 -> -3
                    2, 3 -> 2
                    4, 5 -> -2
                    6 -> 1
                    else -> -1
                }
                step = (step + move).let { if (it < 0 || it > TopStep) step - move else it }.coerceIn(0, TopStep)
            }
            Note(centre + Offset(x, (4 - step) * gap / 2f), text, start, end, step)
        }
        val lineEnd = max(words.last().third, words.last().second)
        phrases += Phrase(centre, notes, line.timeMs.coerceAtMost(notes.first().start), lineEnd, spread / 2f + gap * 3.2f)
    }
    return phrases
}

/** The tune through a line's notes, relative to nothing but the score: made once. */
private fun tuneOf(phrase: Phrase): Path = Path().apply {
    phrase.notes.forEachIndexed { i, note ->
        if (i == 0) {
            moveTo(note.at.x, note.at.y)
        } else {
            val before = phrase.notes[i - 1].at
            val mid = (before.x + note.at.x) / 2f
            cubicTo(mid, before.y, mid, note.at.y, note.at.x, note.at.y)
        }
    }
}

/** A soft round light, drawn once and stamped wherever one is needed. */
private fun glowSprite(density: Density): ImageBitmap {
    val px = 128
    val image = ImageBitmap(px, px)
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(px.toFloat(), px.toFloat())) {
        drawCircle(
            Brush.radialGradient(0f to Color.White, 0.3f to Color.White.copy(alpha = 0.4f), 1f to Color.Transparent, center = center, radius = size.minDimension / 2f),
            radius = size.minDimension / 2f,
        )
    }
    return image
}

/** [image] centred on [at], [extent] across, at [alpha], in [tint], its light added to what is beneath. */
private fun DrawScope.stamp(image: ImageBitmap, at: Offset, extent: Float, alpha: Float, tint: ColorFilter?) {
    if (alpha <= 0.004f || extent <= 0.5f) return
    val s = extent / image.width
    withTransform({
        translate(at.x - extent / 2f, at.y - extent / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) {
        drawImage(image, alpha = alpha.coerceAtMost(1f), colorFilter = tint, blendMode = BlendMode.Plus)
    }
}

private fun label(measurer: TextMeasurer, cache: HashMap<String, TextLayoutResult>, word: String, style: TextStyle, big: Boolean, density: Density): TextLayoutResult =
    cache.getOrPut((if (big) "B" else "s") + word) { measurer.measure(word, style, maxLines = 1, density = density) }

/** One line of the score at [t]: its staff, the tune drawing itself, its notes, the word being sung. */
private fun DrawScope.drawPhrase(
    phrase: Phrase,
    tune: Path,
    live: Boolean,
    ahead: Boolean,
    t: Long,
    alpha: Float,
    origin: Offset,
    gap: Float,
    glow: Color,
    tint: ColorFilter,
    light: ImageBitmap,
    measurer: TextMeasurer,
    labels: HashMap<String, TextLayoutResult>,
    bigStyle: TextStyle,
    smallStyle: TextStyle,
) {
    val c = origin + phrase.centre
    val hair = 1.dp.toPx()
    // ---- The staff: five lines, fading out at either end rather than cut off ----
    val staff = Brush.horizontalGradient(
        0f to Color.Transparent,
        0.12f to Color.White.copy(alpha = 0.22f * alpha),
        0.88f to Color.White.copy(alpha = 0.22f * alpha),
        1f to Color.Transparent,
        startX = c.x - phrase.half,
        endX = c.x + phrase.half,
    )
    for (l in -2..2) {
        val y = c.y + l * gap
        drawLine(staff, Offset(c.x - phrase.half, y), Offset(c.x + phrase.half, y), strokeWidth = hair)
    }
    val sung = if (live) phrase.notes.count { it.start <= t } else if (ahead) 0 else phrase.notes.size
    // ---- The tune: faint through the whole line, lit as far as the voice has got ----
    withTransform({ translate(origin.x, origin.y) }) {
        drawPath(tune, Color.White.copy(alpha = 0.10f * alpha), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
    }
    for (j in 1 until sung) {
        val a = origin + phrase.notes[j - 1].at
        val b = origin + phrase.notes[j].at
        val reach = if (live) DrawSpring.progress(t - phrase.notes[j].start) else 1f
        val end = a + (b - a) * reach
        drawLine(glow.copy(alpha = 0.10f * alpha), a, end, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(glow.copy(alpha = 0.75f * alpha), a, end, strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round)
    }
    // ---- The point of light running ahead to the next word, so the tune never stands still ----
    if (live && sung in 1 until phrase.notes.size) {
        val here = phrase.notes[sung - 1]
        val next = phrase.notes[sung]
        if (next.start > here.start) {
            val f = ((t - here.start).toFloat() / (next.start - here.start)).coerceIn(0f, 1f)
            val a = origin + here.at
            val b = origin + next.at
            stamp(light, a + (b - a) * f, gap * 4.2f, 0.7f, tint)
        }
    }
    // ---- Notes and words ----
    for (j in phrase.notes.indices) {
        val note = phrase.notes[j]
        val at = origin + note.at
        val isSung = j < sung
        if (!isSung) {
            // Waiting on the staff: the tune can be read ahead of the voice.
            drawNote(note, at, gap, Color.White.copy(alpha = 0.34f * alpha), 1f)
            continue
        }
        // When this word stopped being the one sung: the next word's start, or the line's end.
        val over = if (j + 1 < sung) phrase.notes[j + 1].start else if (live && t >= phrase.end) phrase.end else Long.MAX_VALUE
        val singing = live && t < over
        val small = label(measurer, labels, note.word, smallStyle, false, this)
        // Under the staff, as a score sets its words; every other one a row lower so neighbours never touch.
        val smallTop = Offset(at.x - small.size.width / 2f, c.y + gap * 2f + 8.dp.toPx() + (j % 2) * (small.size.height + 2.dp.toPx()))
        if (singing) {
            val since = t - note.start
            val pop = PopSpring.progress(since)
            val bloom = BloomSpring.progress(since)
            val seen = FadeSpring.progress(since)
            stamp(light, at, gap * (9f - 3f * bloom), 0.5f * seen, tint)
            drawNote(note, at, gap, Color.White, 1f + 0.55f * (1f - bloom).coerceAtLeast(0f) + 0.12f)
            val big = label(measurer, labels, note.word, bigStyle, true, this)
            val grow = 0.72f + 0.28f * pop
            val rise = (1f - pop) * 12.dp.toPx()
            // Above the staff, kept on the screen's width whatever the note's place in the line.
            val half = big.size.width / 2f
            val cx = at.x.coerceIn(half + 14.dp.toPx(), size.width - half - 14.dp.toPx())
            val pivot = Offset(cx, c.y - gap * 2f - 16.dp.toPx())
            scale(grow, grow, pivot = pivot) {
                val topLeft = Offset(cx - half, pivot.y - big.size.height + rise)
                stamp(light, topLeft + Offset(half, big.size.height / 2f), big.size.height * 2.6f, 0.22f * seen, tint)
                drawText(big, color = Color.White, topLeft = topLeft, alpha = seen.coerceIn(0f, 1f))
            }
            drawText(small, color = Color.White, topLeft = smallTop, alpha = 0.5f * seen.coerceIn(0f, 1f))
        } else {
            // Settling: the big word shrinks and fades as the small one under the note comes up.
            val since = if (over == Long.MAX_VALUE) Long.MAX_VALUE else t - over
            val settled = if (since == Long.MAX_VALUE) 1f else FadeSpring.progress(since).coerceIn(0f, 1f)
            drawNote(note, at, gap, lerpColor(Color.White, glow, 0.35f).copy(alpha = alpha), 1f + 0.12f * (1f - settled))
            if (settled < 0.999f && live) {
                val big = label(measurer, labels, note.word, bigStyle, true, this)
                val half = big.size.width / 2f
                val cx = at.x.coerceIn(half + 14.dp.toPx(), size.width - half - 14.dp.toPx())
                val pivot = Offset(cx, c.y - gap * 2f - 16.dp.toPx())
                val shrink = 1f - 0.4f * settled
                scale(shrink, shrink, pivot = pivot) {
                    drawText(big, color = Color.White, topLeft = Offset(cx - half, pivot.y - big.size.height), alpha = (1f - settled) * alpha)
                }
            }
            drawText(small, color = Color.White, topLeft = smallTop, alpha = (0.5f + 0.4f * settled) * alpha)
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float): Color = androidx.compose.ui.graphics.lerp(a, b, f)

/** One note: head, stem, and a flag if it is quick. */
private fun DrawScope.drawNote(note: Note, at: Offset, gap: Float, ink: Color, grow: Float) {
    val held = note.end - note.start
    val open = held >= OpenNoteFromMs
    val quick = held in 1 until FlagBelowMs
    val rx = gap * 0.66f
    val ry = gap * 0.47f
    scale(grow, grow, pivot = at) {
        val up = note.step < 4
        val stemX = if (up) at.x + rx * 0.88f else at.x - rx * 0.88f
        val stemEnd = if (up) at.y - gap * 3.1f else at.y + gap * 3.1f
        drawLine(ink, Offset(stemX, at.y), Offset(stemX, stemEnd), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        if (quick) {
            val dir = if (up) 1f else -1f
            drawLine(ink, Offset(stemX, stemEnd), Offset(stemX + gap * 0.9f, stemEnd + dir * gap * 1.1f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        }
        rotate(-22f, at) {
            drawOval(
                color = ink,
                topLeft = Offset(at.x - rx, at.y - ry),
                size = Size(rx * 2, ry * 2),
                style = if (open) Stroke(1.8.dp.toPx()) else Fill,
            )
        }
    }
}
