/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * The cold-start intro, drawn over the already-composed app.
 *
 * On black, a gold disc springs in carrying the black Exhale mark, which turns into place. The disc
 * beats twice — a breath in, a breath out — with a ripple of gold and a haptic tick on each, then
 * the camera rushes through it: the mark outgrows the disc so its strokes sweep past, and a hole
 * opens from the centre onto the live app underneath.
 *
 * One clock drives every layer. It advances per frame and at most [MAX_STEP_MS] at a time: the
 * intro runs while the app is still starting, and a wall-clock animation would skip straight past
 * a startup stall, where this one pauses for it. Skipped when the system animator scale is 0.
 *
 * The mark is one decoded bitmap drawn at whatever size the frame needs: it grows about
 * seventeen-fold in the zoom, and a painter re-rendering at every size is what stutters.
 */
@Composable
fun BootSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) {
            var last = withFrameNanos { it }
            while (clock.floatValue < TOTAL_MS) {
                val now = withFrameNanos { it }
                val before = clock.floatValue
                val after = (before + ((now - last) / 1e6f).coerceAtMost(MAX_STEP_MS)).coerceAtMost(TOTAL_MS)
                last = now
                clock.floatValue = after
                if (BEATS_MS.any { it in before..after && it != before }) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
            }
        }
        onFinished()
    }
    if (reducedMotion) return

    // The plain black "E))" on a transparent ground — the launcher's glyph layer, at 62% of its
    // image. Decoded once; the canvas scales the one bitmap, so nothing is re-rendered as it grows.
    val mark = remember {
        runCatching {
            android.graphics.BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_foreground)
                ?.asImageBitmap()
        }.getOrNull()
    }
    val hole = remember { Path() }
    Canvas(
        modifier
            .fillMaxSize()
            // Nothing underneath answers a touch until the app is revealed.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        val t = clock.floatValue
        val c = center
        val r0 = 52.dp.toPx()
        val cover = hypot(size.width, size.height) / 2f * 1.05f

        // The reveal is a hole clipped out of everything below, so the app shows through it
        // without a full-screen offscreen layer.
        val holeR = cover * outCubic(phase(t, REVEAL_START, TOTAL_MS))
        hole.reset()
        if (holeR > 0f) hole.addOval(Rect(c, holeR))
        clipPath(hole, ClipOp.Difference) {
            drawRect(Color.Black)

            // The ripples, under the disc.
            BEATS_MS.forEach { beat ->
                val x = (t - beat) / RIPPLE_MS
                if (x in 0f..1f) {
                    drawCircle(
                        color = RingGold.copy(alpha = 0.55f * (1f - x)),
                        radius = r0 * (1f + 2.4f * outCubic(x)),
                        center = c,
                        style = Stroke(width = 2.dp.toPx() * (1f - 0.6f * x)),
                    )
                }
            }

            val beat = BEATS_MS.sumOf { pulse(t - it).toDouble() }.toFloat()
            val zoom = inCubic(phase(t, ZOOM_START, ZOOM_END))
            val discIn = outBack(phase(t, 0f, 450f))
            val discR = (r0 * discIn * (1f + 0.09f * beat) + (cover - r0) * zoom).coerceAtLeast(0f)
            if (discR > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(GoldLight, GoldDeep),
                        center = c - Offset(discR * 0.35f, discR * 0.45f),
                        radius = discR * 1.6f,
                    ),
                    radius = discR,
                    center = c,
                )
            }

            val markIn = phase(t, 100f, 550f)
            // Outgrows the disc in the zoom, so the black strokes sweep past the camera.
            val markSize = r0 * 2f * 0.64f * outBack(markIn) * (1f + 0.09f * beat) *
                (1f + 16f * zoom.pow(1.6f))
            val spin = -140f * (1f - outCubic(markIn)) + 35f * zoom
            if (markSize > 0f && mark != null) {
                // The glyph fills most but not all of its image, so the image is drawn that much larger.
                val layer = (markSize / MARK_FILL).toInt().coerceAtLeast(1)
                rotate(spin, pivot = c) {
                    drawImage(
                        image = mark,
                        dstOffset = androidx.compose.ui.unit.IntOffset(
                            (c.x - layer / 2f).toInt(),
                            (c.y - layer / 2f).toInt(),
                        ),
                        dstSize = androidx.compose.ui.unit.IntSize(layer, layer),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                    )
                }
            }
        }
    }
}

/** How much of its image the glyph itself fills. */
private const val MARK_FILL = 0.62f

private val GoldLight = Color(0xFFFFE07A)
private val GoldDeep = Color(0xFFDEB41A)
private val RingGold = Color(0xFFFFE07A)

private const val TOTAL_MS = 1700f
private const val ZOOM_START = 1000f
private const val ZOOM_END = 1480f
private const val REVEAL_START = 1400f
private const val RIPPLE_MS = 600f
private const val PULSE_MS = 220f

/** The most the clock advances in one frame (about two at 60 Hz): a stall pauses, never skips. */
private const val MAX_STEP_MS = 34f

/** The two beats: a breath in and a breath out. */
private val BEATS_MS = listOf(600f, 850f)

private fun phase(t: Float, start: Float, end: Float) = ((t - start) / (end - start)).coerceIn(0f, 1f)

private fun pulse(dt: Float) = if (dt in 0f..PULSE_MS) sin(PI.toFloat() * dt / PULSE_MS) else 0f

private fun outCubic(x: Float) = 1f - (1f - x).pow(3)

private fun inCubic(x: Float) = x * x * x

private fun outBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}
