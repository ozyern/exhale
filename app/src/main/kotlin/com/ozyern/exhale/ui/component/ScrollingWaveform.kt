/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx 1.5 (ui/component/WaveformSeekBar.kt, github.com/cosmictaserdev-creator/Convx),
 * GPL-3.0.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.floor
import kotlin.random.Random

/**
 * Bar heights in 0..1 for a song, the same every time it plays (seeded by its id). Not the audio's
 * real loudness, a shape that reads like it: a random walk, so neighbouring bars relate.
 */
internal fun waveformBars(seed: Int, count: Int): FloatArray {
    val random = Random(seed)
    var previous = 0.5f
    return FloatArray(count) {
        previous = (previous + (random.nextFloat() - 0.5f) * 0.6f).coerceIn(0.12f, 1f)
        previous
    }
}

/**
 * The mini player's waveform: a short window of the song's bars, centred on where it is playing,
 * gliding left as it plays and fading out at both ends. The bars already played are drawn in
 * [playedColor]. A tap seeks to the bar under the finger.
 *
 * [progress] is read only while drawing, so the waveform moving never recomposes anything.
 */
@Composable
fun ScrollingWaveform(
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    playedColor: Color,
    trackColor: Color,
    seed: Int,
    modifier: Modifier = Modifier,
    totalBars: Int = 80,
    visibleBars: Int = 14,
    edgeFade: Float = 0.22f,
) {
    val heights = remember(seed, totalBars) { waveformBars(seed, totalBars) }
    Canvas(
        modifier
            // Its own layer, so the fade can cut the bars' ends away without touching the glass.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        edgeFade to Color.Black,
                        1f - edgeFade to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(visibleBars, totalBars) {
                detectTapGestures { tap ->
                    val shift = ((tap.x / size.width).coerceIn(0f, 1f) - 0.5f) * visibleBars / totalBars
                    onSeek((progress() + shift).coerceIn(0f, 1f))
                }
            },
    ) {
        val count = heights.size
        if (count == 0 || size.width <= 0f || size.height <= 0f) return@Canvas

        val midY = size.height / 2f
        val minHeight = size.height * 0.15f
        // One slot per visible bar, placed from a fractional first index so the window glides
        // rather than stepping a whole bar at a time.
        val slot = size.width / visibleBars
        val barWidth = slot * 0.55f
        val centre = progress().coerceIn(0f, 1f) * (count - 1)
        val first = centre - visibleBars / 2f
        val start = floor(first).toInt()

        for (i in start..(start + visibleBars + 1)) {
            if (i !in 0 until count) continue
            val x = (i - first) * slot + slot / 2f
            val h = (heights[i] * size.height * 0.9f).coerceAtLeast(minHeight)
            drawRoundRect(
                color = if (i <= centre) playedColor else trackColor,
                topLeft = Offset(x - barWidth / 2f, midY - h / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}
