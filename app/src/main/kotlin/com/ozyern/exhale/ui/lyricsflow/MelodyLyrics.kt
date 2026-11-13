/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The point of light, stamped from one picture made once, follows AETHER's constellation lyrics
 * (github.com/GHOSTxASR/AETHER, GPL-3.0, ui/lyrics/Constellations.kt).
 */

package com.ozyern.exhale.ui.lyricsflow

import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PathMeasure
import android.graphics.Shader
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.ozyern.exhale.ui.component.LocalLyricsGlowColor
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The song, played as a tune: every word a note, the notes joined by one curving line.
 *
 * Each word sits on its own note, a short lit bar at its own pitch, and the line glides up or down
 * from one note to the next, so the song reads as a melody rising and falling word by word. A point of light runs along the curve at the exact moment being sung: inside a
 * word it sweeps across the letters, lighting them as it passes, and between words it travels the
 * space, so the line never stands still. Words to come wait faint ahead of it; words sung stay lit
 * and fade behind it. The view rides along with the point.
 *
 * Nothing that marks time is sprung: where the point stands is worked out from the song's time
 * every frame, so the light is on the word being sung, not on its way there. Only the word's
 * small lift as it starts, and the view following the point, are eased.
 *
 * One canvas and one clock. The curve, the words' places on it and their widths are worked out
 * once for the song; a frame draws only the stretch of curve on screen.
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val playing by rememberUpdatedState(isPlaying)

    val typeface = remember(context) {
        runCatching { ResourcesCompat.getFont(context, R.font.sfprodisplaybold) }.getOrNull()
            ?: android.graphics.Typeface.DEFAULT_BOLD
    }
    val textPaint = remember(typeface, textScale, density) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { 28.sp.toPx() } * textScale
            letterSpacing = -0.01f
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val song = remember(lines, textPaint, w, density) { ribbon(lines, textPaint, w, density) }
        if (song.words.isEmpty()) return@BoxWithConstraints
        val light = remember(density) { glowSprite(density) }
        val tint = remember(glow) { ColorFilter.tint(glow, BlendMode.SrcIn) }

        // The view: where it looks on the curve, and how fast it is moving there.
        val camera = remember(song) {
            val start = song.pointAt(song.words[0].from)
            floatArrayOf(start.x, start.y, 0f, 0f)
        }
        val frame = remember { mutableLongStateOf(0L) }
        LaunchedEffect(song) {
            var last = withFrameNanos { it }
            while (true) {
                com.ozyern.exhale.utils.awaitAppVisible()
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val target = song.pointAt(song.distanceAt(clock.longValue))
                // Critically damped, stepped finely enough that a long frame cannot overshoot. The
                // view follows the point: the point itself is never behind the song.
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
                frame.longValue++
                // Paused and come to rest: no more frames until it plays or the song is moved.
                if (!playing && hypot(camera[2], camera[3]) < 1f) {
                    val heldAt = clock.longValue
                    androidx.compose.runtime.snapshotFlow { playing || clock.longValue != heldAt }.first { it }
                    last = withFrameNanos { it }
                }
            }
        }

        // Drawing state, made once and reused every frame.
        val window = remember { android.graphics.Path() }
        val bar = remember { android.graphics.Path() }
        val barPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
        val linePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
        val litPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND } }
        val fadeIn = remember(glow, w) {
            LinearGradient(0f, 0f, w * 0.85f, 0f, android.graphics.Color.TRANSPARENT, glow.toArgb(), Shader.TileMode.CLAMP)
        }
        val shaderMatrix = remember { Matrix() }
        val tan = remember { FloatArray(2) }
        val pos = remember { FloatArray(2) }

        Canvas(
            Modifier
                .fillMaxSize()
                // The ends of the line melt into the edges of the screen rather than being cut off.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.90f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }
                .pointerInput(song) {
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
                                val current = song.lineAt(clock.longValue)
                                val to = if (up.x < size.width / 2f) max(0, current - 1) else min(song.lineStarts.lastIndex, current + 1)
                                player.seekTo(song.lineStarts[to])
                            }
                        }
                    }
                },
        ) {
            frame.longValue // read: one redraw for each frame of the loop
            val t = clock.longValue
            val d = song.distanceAt(t)
            val focus = Offset(size.width * 0.40f, size.height * 0.5f)
            val ox = focus.x - camera[0]
            val oy = focus.y - camera[1]
            val hair = 1.5.dp.toPx()

            // The stretch of the curve on screen, with room either side.
            val from = (d - w * 1.1f).coerceAtLeast(0f)
            val to = (d + w * 1.3f).coerceAtMost(song.length)
            window.reset()
            song.measure.getSegment(from, to, window, true)
            val point = song.pointAt(d)

            drawIntoCanvas { canvas ->
                val n = canvas.nativeCanvas
                n.save()
                n.translate(ox, oy)

                // ---- The curve: faint ahead, lit behind the point, brightest where it is ----
                linePaint.strokeWidth = hair
                linePaint.color = android.graphics.Color.argb(46, 255, 255, 255)
                n.drawPath(window, linePaint)
                if (d > from) {
                    window.reset()
                    song.measure.getSegment(from, d, window, true)
                    shaderMatrix.setTranslate(point.x - w * 0.85f, 0f)
                    fadeIn.setLocalMatrix(shaderMatrix)
                    litPaint.shader = fadeIn
                    litPaint.strokeWidth = 9.dp.toPx()
                    litPaint.alpha = 50
                    n.drawPath(window, litPaint)
                    litPaint.strokeWidth = 2.4.dp.toPx()
                    litPaint.alpha = 255
                    n.drawPath(window, litPaint)
                    // The window again, for the words.
                    window.reset()
                    song.measure.getSegment(from, to, window, true)
                }

                // ---- The words, on the curve ----
                val lift = 12.dp.toPx()
                val sweepWidth = 3.dp.toPx()
                for (i in song.firstFrom(from) until song.words.size) {
                    val word = song.words[i]
                    if (word.from > to) break
                    if (word.to < from) continue
                    val h0 = word.from - from
                    val behind = d - word.to
                    // The note: a rounded bar under the word, at its pitch. Lit as far as sung.
                    barPaint.strokeWidth = 6.dp.toPx()
                    barPaint.shader = null
                    val inset = 2.dp.toPx()
                    if (d < word.to) {
                        bar.reset()
                        song.measure.getSegment(max(word.from + inset, d), word.to - inset, bar, true)
                        barPaint.color = android.graphics.Color.argb(56, 255, 255, 255)
                        n.drawPath(bar, barPaint)
                    }
                    if (d > word.from) {
                        bar.reset()
                        song.measure.getSegment(word.from + inset, min(d, word.to - inset), bar, true)
                        val fade = if (d >= word.to) (1f - behind / (w * 0.95f)).coerceIn(0.25f, 1f) else 1f
                        barPaint.color = glow.copy(alpha = 0.95f * fade).toArgb()
                        barPaint.setShadowLayer(8.dp.toPx(), 0f, 0f, glow.copy(alpha = 0.6f * fade).toArgb())
                        n.drawPath(bar, barPaint)
                        barPaint.clearShadowLayer()
                    }
                    when {
                        // Sung: lit, fading as the point leaves it behind.
                        d >= word.to -> {
                            val fade = (1f - behind / (w * 0.95f)).coerceIn(0.22f, 1f)
                            textPaint.color = Color.White.copy(alpha = 0.92f * fade).toArgb()
                            n.drawTextOnPath(word.text, window, h0, -lift, textPaint)
                        }
                        // To come: waiting, faint.
                        d <= word.from -> {
                            textPaint.color = Color.White.copy(alpha = 0.30f).toArgb()
                            n.drawTextOnPath(word.text, window, h0, -lift, textPaint)
                        }
                        // Being sung: the faint word, and the lit one over it as far as the point.
                        else -> {
                            val since = (t - word.startMs).coerceAtLeast(0L)
                            val up = 1f - LiftSpring.remaining(since)
                            val v = -lift - 4.dp.toPx() * up
                            textPaint.color = Color.White.copy(alpha = 0.30f).toArgb()
                            n.drawTextOnPath(word.text, window, h0, v, textPaint)
                            // Cut across the curve at the point, square to it.
                            song.measure.getPosTan(d, pos, tan)
                            val angle = atan2(tan[1], tan[0]) * 180f / PI.toFloat()
                            n.save()
                            n.rotate(angle, pos[0], pos[1])
                            n.clipRect(pos[0] - w, pos[1] - h, pos[0] + sweepWidth, pos[1] + h)
                            n.rotate(-angle, pos[0], pos[1])
                            textPaint.color = Color.White.toArgb()
                            textPaint.setShadowLayer(14.dp.toPx(), 0f, 0f, glow.copy(alpha = 0.9f).toArgb())
                            n.drawTextOnPath(word.text, window, h0, v, textPaint)
                            textPaint.clearShadowLayer()
                            n.restore()
                        }
                    }
                }
                n.restore()
            }

            // ---- The point of light, at the moment being sung ----
            val at = Offset(point.x + ox, point.y + oy)
            stamp(light, at, 46.dp.toPx(), 0.8f, tint)
            drawCircle(Color.White, radius = 3.dp.toPx(), center = at)
        }
    }
}

private const val CameraStiffness = 26f
private val CameraFriction = 2f * sqrt(CameraStiffness)

/** The word's small lift as it starts: quick, so it is up while the word is still being sung. */
private object LiftSpring {
    private const val OMEGA = (2.0 * PI / 0.18).toFloat()

    /** What is left of the motion [ms] after it began, 1 to 0, critically damped. */
    fun remaining(ms: Long): Float {
        val t = ms / 1000f
        return (1f + OMEGA * t) * exp(-OMEGA * t)
    }
}

/** A word on the curve: the stretch of it the word's letters take, and when it is sung. */
private class Word(val text: String, val from: Float, val to: Float, val startMs: Long, val endMs: Long)

/**
 * The whole song on one curve: the curve itself, every word's place along it in reading order,
 * and when each line begins.
 */
private class Ribbon(
    val path: android.graphics.Path,
    val words: List<Word>,
    val lineStarts: List<Long>,
) {
    val measure = PathMeasure(path, false)
    val length = measure.length
    private val xy = FloatArray(2)

    fun pointAt(distance: Float): Offset {
        measure.getPosTan(distance.coerceIn(0f, length), xy, null)
        return Offset(xy[0], xy[1])
    }

    /** The last word to have begun at [t], or -1 before the first. */
    private fun wordAt(t: Long): Int {
        var lo = 0
        var hi = words.size - 1
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].startMs <= t) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /**
     * How far along the curve the song is at [t]: across a word as it is sung, from its first
     * letter to its last, and across the space after it until the next word begins.
     */
    fun distanceAt(t: Long): Float {
        val i = wordAt(t)
        if (i < 0) return words[0].from
        val word = words[i]
        if (t < word.endMs) {
            val f = (t - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L)
            return word.from + (word.to - word.from) * f.coerceIn(0f, 1f)
        }
        val next = words.getOrNull(i + 1) ?: return word.to
        val f = (t - word.endMs).toFloat() / (next.startMs - word.endMs).coerceAtLeast(1L)
        return word.to + (next.from - word.to) * f.coerceIn(0f, 1f)
    }

    /** The line being sung at [t]: the last to have started, or 0 before the first. */
    fun lineAt(t: Long): Int {
        var i = lineStarts.size - 1
        while (i > 0 && lineStarts[i] > t) i--
        return i
    }

    /** The first word that may reach [distance] or beyond. */
    fun firstFrom(distance: Float): Int {
        var lo = 0
        var hi = words.size - 1
        var found = words.size
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].to >= distance) {
                found = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        return found
    }
}

/**
 * Lays the song out as a tune. Every word is a note: a short level bar at its own pitch, as long
 * as the word is wide, with the word written on it. Between one note and the next the line glides
 * up or down in a smooth S, so the whole song is one curving line that rises and falls word by
 * word, the way a melody does. Pitches walk mostly by step, sometimes by a leap, and each line
 * starts afresh a little higher or lower. The same song always makes the same tune.
 */
private fun ribbon(lines: List<LyricLine>, paint: Paint, width: Float, density: Density): Ribbon {
    val space = paint.measureText(" ")
    val breath = with(density) { 64.dp.toPx() }
    val pitchStep = with(density) { 15.dp.toPx() }
    val glideRoom = with(density) { 10.dp.toPx() }
    val words = ArrayList<Word>()
    val lineStarts = ArrayList<Long>()
    val path = android.graphics.Path()
    val piece = android.graphics.Path()
    val measure = PathMeasure()
    var x = 0f
    var arc = 0f
    var pitch = 0
    var y = 0f
    path.moveTo(0f, 0f)

    /** Adds a glide from where the line is to ([toX], [toY]), and how long it is along the curve. */
    fun glide(toX: Float, toY: Float) {
        val mid = (x + toX) / 2f
        piece.reset()
        piece.moveTo(x, y)
        piece.cubicTo(mid, y, mid, toY, toX, toY)
        measure.setPath(piece, false)
        arc += measure.length
        path.cubicTo(mid, y, mid, toY, toX, toY)
        x = toX
        y = toY
    }

    lines.forEachIndexed { i, line ->
        val nextMs = lines.getOrNull(i + 1)?.timeMs
        val timed: List<Triple<String, Long, Long>> = if (line.words.isNotEmpty()) {
            line.words.filter { it.text.isNotBlank() }.map { Triple(it.text, it.startMs, it.endMs) }
        } else {
            val tokens = line.text.split(' ').filter { it.isNotBlank() }
            val end = nextMs?.takeIf { it > line.timeMs } ?: (line.timeMs + 4_000L)
            val span = (end - line.timeMs) / tokens.size.coerceAtLeast(1)
            tokens.mapIndexed { j, token -> Triple(token, line.timeMs + span * j, line.timeMs + span * (j + 1)) }
        }
        if (timed.isEmpty()) return@forEachIndexed
        val rnd = Random(i * 7919 + 17 + line.text.hashCode())
        lineStarts += min(line.timeMs, timed.first().second)
        timed.forEachIndexed { j, (text, start, rawEnd) ->
            // A word's end is when the next begins at the latest, and never before its own start:
            // sources disagree on both, and the point must never run backwards.
            val nextStart = timed.getOrNull(j + 1)?.second ?: nextMs
            var end = if (rawEnd > start) rawEnd else start + 300L
            if (nextStart != null && nextStart > start) end = min(end, nextStart)

            // This note's pitch: a fresh start for a line, otherwise a step or two from the last.
            val before = pitch
            pitch = if (j == 0) {
                if (words.isEmpty()) 0 else (pitch + rnd.nextInt(-2, 3)).coerceIn(-2, 2)
            } else {
                val move = when (rnd.nextInt(10)) {
                    0 -> 3
                    1 -> -3
                    2, 3 -> 2
                    4, 5 -> -2
                    6, 7 -> 1
                    else -> -1
                }
                (pitch + move).let { if (it < -3 || it > 3) pitch - move else it }.coerceIn(-3, 3)
            }
            val noteY = -pitch * pitchStep
            if (words.isEmpty()) {
                path.moveTo(0f, noteY)
                y = noteY
            } else {
                // Room for the glide: a space between words, a breath between lines, and more
                // the further it has to climb or fall, so it never turns steep.
                val room = if (j == 0) breath else space + glideRoom + kotlin.math.abs(pitch - before) * pitchStep * 0.55f
                glide(x + room, noteY)
            }
            val wide = paint.measureText(text)
            words += Word(text, arc, arc + wide, start, max(end, start + 1))
            // The note itself: level, as long as the word.
            path.lineTo(x + wide, y)
            x += wide
            arc += wide
        }
    }
    // A last stretch past the end, so the final note has somewhere to lead.
    if (words.isNotEmpty()) glide(x + width, y)
    // Words must be in time order for the search; sources occasionally are not.
    val ordered = words.withIndex().all { (k, wd) -> k == 0 || words[k - 1].startMs <= wd.startMs }
    val sorted = if (ordered) words else words.sortedBy { it.startMs }
    return Ribbon(path, sorted, lineStarts)
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
