/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.component.OppoSans
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The About card's hidden side: the card goes flat graphite, a burst of confetti tumbles down it
 * and settles into a low, jumbled pile along the bottom edge, and the line the project describes
 * itself with fades in, large and light, behind the falling pieces.
 *
 * Every tap throws another handful; [burst] is bumped by the caller to throw one from outside.
 */
@Composable
internal fun AboutConfettiEgg(
    tagline: String,
    burst: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val unit = density.density
    val world = remember { ConfettiWorld(unit) }
    var frame by remember { mutableLongStateOf(0L) }
    val lastBurst = remember { intArrayOf(-1) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(1f / 30f)
                last = now
                world.step(dt)
                frame = now
            }
        }
    }

    // The words arrive once the first shower is mostly down.
    val words = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(650)
        words.animateTo(1f, tween(900))
    }

    Box(
        modifier
            .graphicsLayer { clip = true },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            world.resize(size.width, size.height)
            if (lastBurst[0] != burst) {
                lastBurst[0] = burst
                world.burst(if (burst == 0) 150 else 36)
            }
            frame // read, so every frame redraws
            drawRect(Color(0xFF2B2F33))
        }
        Text(
            text = tagline,
            fontFamily = OppoSans,
            fontWeight = FontWeight.Light,
            fontSize = 60.sp,
            lineHeight = 58.sp,
            letterSpacing = (-1).sp,
            textAlign = TextAlign.Center,
            color = Color(0xFFE9EAEC),
            modifier = Modifier.graphicsLayer {
                alpha = words.value
                translationY = (1f - words.value) * 18f * unit
            },
        )
        Canvas(Modifier.fillMaxSize()) {
            frame
            world.pieces.forEach { p -> drawPiece(p) }
        }
    }
}

private enum class Shape { Strip, Square, Triangle, Dot }

private class Piece(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    var spin: Float,
    val w: Float,
    val h: Float,
    val shape: Shape,
    val color: Color,
    var resting: Boolean = false,
)

private val ConfettiColors = listOf(
    Color(0xFFE8302E), // red
    Color(0xFF1F6BFF), // blue
    Color(0xFFFFC21A), // yellow
    Color(0xFFF4F4F4), // white
    Color(0xFF111214), // black
    Color(0xFF6A4DF4), // violet
)

/**
 * A small falling-pieces simulation: gravity, a little air, walls, and a floor that rises where
 * pieces come to rest, so they pile rather than sink into each other.
 */
private class ConfettiWorld(private val unit: Float) {
    val pieces = ArrayList<Piece>(400)
    private var width = 0f
    private var height = 0f
    private var floor = FloatArray(0)
    private val column get() = 4f * unit
    private val rnd = Random(System.nanoTime())

    fun resize(w: Float, h: Float) {
        if (w == width && h == height) return
        width = w
        height = h
        floor = FloatArray((w / column).toInt() + 2) { h }
        pieces.forEach { it.resting = false }
    }

    fun burst(count: Int) {
        if (width <= 0f) return
        repeat(count) {
            val shape = when (rnd.nextInt(10)) {
                in 0..4 -> Shape.Strip
                5, 6 -> Shape.Square
                7, 8 -> Shape.Triangle
                else -> Shape.Dot
            }
            val (w, h) = when (shape) {
                Shape.Strip -> (6.5f + rnd.nextFloat() * 2f) * unit to (19f + rnd.nextFloat() * 9f) * unit
                Shape.Square -> (11f + rnd.nextFloat() * 4f) * unit to (11f + rnd.nextFloat() * 4f) * unit
                Shape.Triangle -> (15f + rnd.nextFloat() * 4f) * unit to (13f + rnd.nextFloat() * 4f) * unit
                Shape.Dot -> (10f + rnd.nextFloat() * 4f) * unit to 0f
            }
            pieces += Piece(
                x = width * (0.04f + rnd.nextFloat() * 0.92f),
                y = height * (-0.15f + rnd.nextFloat() * 0.75f),
                vx = (rnd.nextFloat() - 0.5f) * 90f * unit,
                vy = (rnd.nextFloat() * 60f - 20f) * unit,
                angle = rnd.nextFloat() * 360f,
                spin = (rnd.nextFloat() - 0.5f) * 720f,
                w = w,
                h = if (shape == Shape.Dot) w else h,
                shape = shape,
                color = ConfettiColors[rnd.nextInt(ConfettiColors.size)],
            )
        }
        // Keep the card from filling up entirely: the oldest resting pieces make room.
        while (pieces.size > 320) pieces.removeAt(0)
    }

    fun step(dt: Float) {
        if (dt <= 0f || width <= 0f) return
        val g = 1150f * unit
        for (p in pieces) {
            if (p.resting) continue
            p.vy += g * dt
            // Air: flat pieces flutter, so they fall slower and wobble sideways.
            val drag = if (p.shape == Shape.Strip) 1.3f else 0.9f
            p.vx -= p.vx * drag * dt
            p.vy -= p.vy * drag * 0.55f * dt
            p.vx += sin((p.angle + p.y * 0.02f) * 0.0174f) * 12f * unit * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.angle += p.spin * dt
            p.spin -= p.spin * 0.6f * dt

            val r = max(p.w, p.h) / 2f
            if (p.x < r) { p.x = r; p.vx = abs(p.vx) * 0.3f }
            if (p.x > width - r) { p.x = width - r; p.vx = -abs(p.vx) * 0.3f }

            // The floor under the piece's footprint.
            val half = (abs(p.w * cos(p.angle * 0.0174f)) + abs(p.h * sin(p.angle * 0.0174f))) / 2f * 0.8f
            val from = ((p.x - half) / column).toInt().coerceIn(0, floor.lastIndex)
            val to = ((p.x + half) / column).toInt().coerceIn(0, floor.lastIndex)
            var top = height
            var low = 0f
            var sum = 0f
            for (c in from..to) {
                top = min(top, floor[c])
                low = max(low, floor[c])
                sum += floor[c]
            }
            val avg = sum / (to - from + 1)
            val thickness = min(p.w, p.h)
            if (p.vy > 0 && p.y + thickness / 2f >= top) {
                if (low - top > thickness * 1.2f && from > 0 && to < floor.lastIndex) {
                    // Landed on a peak: it slides off toward the lower side instead of balancing.
                    p.x += if (floor[from - 1] > floor[to + 1]) -column else column
                    p.vy = min(p.vy, 120f * unit)
                    continue
                }
                if (p.vy < 300f * unit) {
                    // Down. It lies roughly flat — strips along or across — at a careless angle,
                    // and becomes part of the floor for whatever lands next.
                    p.resting = true
                    p.vx = 0f; p.vy = 0f; p.spin = 0f
                    if (p.shape == Shape.Strip || p.shape == Shape.Triangle) {
                        val flat = if (p.shape == Shape.Strip && rnd.nextFloat() >= 0.6f) 90f else 0f
                        p.angle = flat + (rnd.nextFloat() - 0.5f) * 50f
                    }
                    p.y = avg - thickness / 2f
                    for (c in from..to) floor[c] = min(floor[c], avg - thickness * 0.55f)
                } else {
                    p.y = top - thickness / 2f
                    p.vy = -p.vy * 0.25f
                    p.vx *= 0.6f
                    p.spin *= 0.5f
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPiece(p: Piece) {
    translate(p.x, p.y) {
        rotate(p.angle, pivot = Offset.Zero) {
            when (p.shape) {
                Shape.Strip, Shape.Square -> drawRect(
                    p.color,
                    topLeft = Offset(-p.w / 2f, -p.h / 2f),
                    size = Size(p.w, p.h),
                )
                Shape.Dot -> drawCircle(p.color, radius = p.w / 2f, center = Offset.Zero)
                Shape.Triangle -> {
                    val path = Path().apply {
                        moveTo(0f, -p.h / 2f)
                        lineTo(p.w / 2f, p.h / 2f)
                        lineTo(-p.w / 2f, p.h / 2f)
                        close()
                    }
                    drawPath(path, p.color)
                }
            }
        }
    }
}
