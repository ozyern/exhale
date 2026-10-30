/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/* ------------------------------------------------------------------------------------------- */
/* The beats, in seconds                                                                        */
/* ------------------------------------------------------------------------------------------- */

/** The digits start to rise. */
private const val V_IN = 0.25f

/** Between one digit and the next. */
private const val V_STAGGER = 0.11f

/** How long one digit takes to come into focus. */
private const val V_RISE = 0.9f

/** When the light starts across the figure, and how long one pass takes. */
private const val V_SWEEP = 1.15f
private const val V_SWEEP_LEN = 1.5f

/** When the figure steps back and dissolves. */
private const val V_OUT = 2.95f
private const val V_OUT_LEN = 0.7f

/** When the hills begin to come up, overlapping the figure leaving. */
private const val H_IN = 3.05f
private const val H_STAGGER = 0.09f
private const val H_RISE = 1.1f

/** When the hand starts, and how long it takes to get through the word. */
private const val W_IN = 3.7f
private const val W_LEN = 1.6f

/** How long the screen takes to clear into the app once "Get started" is tapped, in ms. */
private const val WelcomeExitMillis = 520

private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

/** An ease-out with a long tail, the curve ColorOS moves things into place on. */
private fun easeOut(x: Float): Float = clamp01(x).let { 1f - (1f - it) * (1f - it) * (1f - it) * (1f - it) }

private fun smooth(x: Float): Float = clamp01(x).let { it * it * (3f - 2f * it) }

/**
 * The one-time screen an upgrade lands on.
 *
 * Two acts. First the release itself, the way ColorOS introduces a version: its number, large and
 * heavy, each digit rising out of a blur into focus one after another, a band of light passing
 * through the figure once it is whole — and then it steps back and dissolves. Then the welcome, the
 * way iOS opens setup: a landscape of soft hills, blue sky banding into green, comes up from the
 * bottom of the screen layer by layer, and "welcome" is written across it by hand, in white, with a
 * glass button under it to go on.
 *
 * Shown once, ever, per upgrade — see the gate in MainActivity. A fresh install gets the first-run
 * setup instead: being welcomed *back* to an app you have never opened is worse than not being
 * welcomed at all.
 *
 * @param version the version name to name, e.g. "1.0.405".
 * @param figure the part of it that is shown large: the build number.
 */
@Composable
fun WelcomeScreen(
    version: String,
    figure: String,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    val wordPath = remember { welcomePath() }
    val wordMeasure = remember(wordPath) { PathMeasure().apply { setPath(wordPath, false) } }
    val wordScratch = remember { Path() }

    // With animations switched off in the system, the screen opens on its last frame: the hills up
    // and the word written, nothing moving in to get there.
    val context = androidx.compose.ui.platform.LocalContext.current
    val reducedMotion = remember {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    // One clock for the whole screen. Written from the frame callback and read only in draw and
    // layer lambdas, so a frame costs an invalidation, not a recomposition.
    val clock = remember { mutableFloatStateOf(if (reducedMotion) W_IN + W_LEN else 0f) }
    var arrived by remember { mutableStateOf(false) }
    val digitCount = figure.ifBlank { version }.length
    LaunchedEffect(Unit) {
        // Each digit is felt as it lands, a light tick a beat apart, and the word with one firmer
        // one as the pen lifts: the sequence is meant to be felt as much as seen.
        var ticked = 0
        var signed = false
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.floatValue += ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            val c = clock.floatValue
            while (ticked < digitCount && c < V_OUT && c >= V_IN + 0.12f + ticked * V_STAGGER + V_RISE * 0.5f) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                ticked++
            }
            if (!signed && c >= W_IN + W_LEN) {
                signed = true
                if (!reducedMotion) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            if (!arrived && c >= W_IN + W_LEN * 0.85f) arrived = true
        }
    }
    val buttonIn by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 170f),
        label = "welcomeButton",
    )

    // Going on: the landscape opens up into the app — a slight push toward the viewer while it
    // clears — rather than the screen vanishing on the frame the button is tapped.
    var leaving by remember { mutableStateOf(false) }
    val exit by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (reducedMotion) 1 else WelcomeExitMillis,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "welcomeExit",
        finishedListener = { if (leaving) onDismiss() },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Alpha and scale only: a full-screen blur here cost enough per frame that the exit was
            // mostly dropped frames, and the push already says "into the app".
            .graphicsLayer {
                alpha = 1f - exit
                val s = 1f + 0.06f * exit
                scaleX = s
                scaleY = s
            }
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                // A tap during the number skips to the welcome rather than out of the screen.
                if (clock.floatValue < V_OUT) clock.floatValue = V_OUT
            }
            .drawBehind { drawHills(clock.floatValue) },
    ) {
        // ---- Act one: the version ----
        VersionFigure(figure = figure, version = version, clock = { clock.floatValue })

        // ---- Act two: the word ----
        val spoken = stringResource(R.string.welcome_title)
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-40).dp)
                .fillMaxWidth()
                .padding(horizontal = 42.dp)
                .aspectRatio(3.23f)
                .semantics { contentDescription = spoken },
        ) {
            drawWelcomeWord(
                path = wordPath,
                measure = wordMeasure,
                scratch = wordScratch,
                progress = clamp01((clock.floatValue - W_IN) / W_LEN),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 26.dp)
                .graphicsLayer {
                    alpha = buttonIn
                    translationY = (1f - buttonIn) * 34.dp.toPx()
                },
        ) {
            Text(
                text = stringResource(R.string.welcome_version, version),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            Text(
                text = stringResource(R.string.welcome_get_started),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlassSurface(RoundedCornerShape(percent = 50))
                    .clickable(enabled = arrived && !leaving) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        leaving = true
                    }
                    .padding(vertical = 18.dp),
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------- */
/* Act one                                                                                      */
/* ------------------------------------------------------------------------------------------- */

/**
 * The build number, ColorOS-style: heavy rounded digits on black, each one rising out of a blur
 * into focus a beat after the last, filled with a soft luminous gradient that a band of light
 * crosses once the figure is whole. Then the whole figure steps back, softens and is gone.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.VersionFigure(
    figure: String,
    version: String,
    clock: () -> Float,
) {
    val digits = figure.ifBlank { version }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                val out = easeOut((clock() - V_OUT) / V_OUT_LEN)
                alpha = 1f - out
                val s = 1f - 0.08f * out
                scaleX = s
                scaleY = s
                translationY = -40.dp.toPx() * out
                val blur = 28.dp.toPx() * out
                renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
            },
    ) {
        Text(
            text = "Exhale",
            style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp),
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.graphicsLayer {
                alpha = smooth((clock() - V_IN) / 0.6f)
                translationY = (1f - easeOut((clock() - V_IN) / 0.9f)) * 10.dp.toPx()
            },
        )
        Row(horizontalArrangement = Arrangement.Center) {
            digits.forEachIndexed { i, ch ->
                val start = V_IN + 0.12f + i * V_STAGGER
                Text(
                    text = ch.toString(),
                    style = TextStyle(
                        fontSize = 132.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-4).sp,
                        brush = luminousBrush(clock, i, digits.length),
                    ),
                    modifier = Modifier.graphicsLayer {
                        val p = clamp01((clock() - start) / V_RISE)
                        val e = easeOut(p)
                        alpha = smooth(p * 1.6f)
                        translationY = (1f - e) * 46.dp.toPx()
                        val s = 1.18f - 0.18f * e
                        scaleX = s
                        scaleY = s
                        val blur = 22.dp.toPx() * (1f - e)
                        renderEffect = if (blur > 0.5f) BlurEffect(blur, blur, TileMode.Decal) else null
                    },
                )
            }
        }
        Text(
            text = "Version $version",
            style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.graphicsLayer {
                val t = V_IN + 0.12f + digits.length * V_STAGGER + 0.25f
                alpha = smooth((clock() - t) / 0.6f)
                translationY = (1f - easeOut((clock() - t) / 0.9f)) * 10.dp.toPx()
            },
        )
    }
}

/**
 * Pale blue into white into gold, with a bright band crossing it: ColorOS's luminous numerals. The
 * gradient is laid across the whole figure, offset per digit, so it reads as one light over all of
 * them rather than a separate sheen on each.
 */
private fun luminousBrush(clock: () -> Float, index: Int, count: Int): Brush {
    val digitW = 300f
    val total = digitW * count
    val t = clamp01((clock() - V_SWEEP) / V_SWEEP_LEN)
    val band = -0.3f + 1.6f * smooth(t)
    val start = -index * digitW
    val stops = arrayOf(
        0f to Color(0xFFB9D4FF),
        (band - 0.12f).coerceIn(0.001f, 0.997f) to Color(0xFFDDE8FF),
        band.coerceIn(0.002f, 0.998f) to Color.White,
        (band + 0.12f).coerceIn(0.003f, 0.999f) to Color(0xFFFFE6B8),
        1f to Color(0xFF9FB8FF),
    ).sortedBy { it.first }.toTypedArray()
    return Brush.linearGradient(
        colorStops = stops,
        start = Offset(start, 0f),
        end = Offset(start + total, 160f),
    )
}

/* ------------------------------------------------------------------------------------------- */
/* Act two: the hills                                                                           */
/* ------------------------------------------------------------------------------------------- */

/** A band of the landscape: where it sits, how it rolls, its colour top and bottom. */
private class Hill(
    val base: Float,
    val tilt: Float,
    val amp: Float,
    val freq: Float,
    val phase: Float,
    val drift: Float,
    val top: Color,
    val bottom: Color,
)

/**
 * Sky banding into meadow, back to front. The upper bands are blues stepping lighter toward the
 * horizon, the lower ones greens stepping brighter toward the viewer; each falls from upper left to
 * lower right as the reference does, and each rolls a little at its own pace.
 */
private val HILLS = listOf(
    Hill(0.22f, 0.10f, 0.035f, 1.2f, 0.4f, 0.05f, Color(0xFF7CC4F5), Color(0xFF4FA6EC)),
    Hill(0.34f, 0.12f, 0.040f, 1.0f, 1.9f, 0.04f, Color(0xFFA9DAF8), Color(0xFF64B4EE)),
    Hill(0.44f, 0.14f, 0.045f, 0.9f, 3.1f, 0.05f, Color(0xFF3F97E6), Color(0xFF2C7FD8)),
    Hill(0.55f, 0.16f, 0.050f, 0.8f, 0.9f, 0.035f, Color(0xFFB9E58C), Color(0xFF86CF55)),
    Hill(0.64f, 0.16f, 0.055f, 0.75f, 2.4f, 0.045f, Color(0xFF7DCB4C), Color(0xFF4FAE34)),
    Hill(0.74f, 0.17f, 0.060f, 0.7f, 4.0f, 0.03f, Color(0xFF5DBB3E), Color(0xFF34952A)),
    Hill(0.85f, 0.18f, 0.050f, 0.65f, 5.2f, 0.04f, Color(0xFF3FA737), Color(0xFF237C22)),
)

private val hillPath = Path()

private fun DrawScope.drawHills(clock: Float) {
    val first = clamp01((clock - H_IN) / H_RISE)
    if (first <= 0f) return
    val w = size.width
    val h = size.height

    // The sky behind them all.
    val skyIn = smooth((clock - H_IN) / 0.8f)
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFF2F86E0), Color(0xFF8CCBF6), Color(0xFFCFEAFB))),
        alpha = skyIn,
    )

    val t = clock * 0.18f
    HILLS.forEachIndexed { i, hill ->
        // Each band comes up from below the screen, a moment after the one behind it.
        val rise = easeOut((clock - H_IN - i * H_STAGGER) / H_RISE)
        if (rise <= 0f) return@forEachIndexed
        val lift = (1f - rise) * h * (0.55f + i * 0.04f)
        hillPath.reset()
        val steps = 28
        for (s in 0..steps) {
            val fx = s / steps.toFloat()
            val y = h * (
                hill.base + hill.tilt * (fx - 0.5f) +
                    hill.amp * sin(fx * hill.freq * 2f * PI.toFloat() + hill.phase + t * hill.drift * 10f)
                ) + lift
            if (s == 0) hillPath.moveTo(0f, y) else hillPath.lineTo(fx * w, y)
        }
        hillPath.lineTo(w, h + lift)
        hillPath.lineTo(0f, h + lift)
        hillPath.close()
        val crest = h * (hill.base - hill.amp - hill.tilt / 2f) + lift
        drawPath(
            path = hillPath,
            brush = Brush.verticalGradient(listOf(hill.top, hill.bottom), startY = crest, endY = crest + h * 0.3f),
        )
        // A soft light along each crest, the sheen the reference's bands carry.
        drawPath(
            path = hillPath,
            brush = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                startY = crest,
                endY = crest + h * 0.06f,
            ),
        )
    }
}

/* ------------------------------------------------------------------------------------------- */
/* The word                                                                                     */
/* ------------------------------------------------------------------------------------------- */

/**
 * "welcome", as one unbroken cursive stroke.
 *
 * Drawn rather than set: none of the app's faces is a script, and `FontFamily.Cursive` is nothing at
 * all on a good number of OEM builds. A path has no such failure mode, and a stroke can be *drawn
 * on*, so the word is written in front of you instead of fading up.
 *
 * x runs right, y runs **down**, the baseline is y = 0, the x-height is y = -1 and the ascender is
 * y = -2. Every letter enters and leaves on the connector line at y = -0.35, so a letter's exit is
 * the next letter's entry. Each row is `endX, endY, c1x, c1y, c2x, c2y` — one cubic, relative to
 * the letter's own origin.
 */
private val WELCOME_ADVANCE = mapOf(
    'w' to 1.50f, 'e' to 0.74f, 'l' to 0.68f, 'c' to 0.66f, 'o' to 0.70f, 'm' to 1.52f,
)

private val WELCOME_STROKES = mapOf(
    'w' to floatArrayOf(
        0.26f, -1.00f, 0.06f, -0.66f, 0.23f, -0.84f,
        0.46f, -0.02f, 0.29f, -0.84f, 0.44f, -0.32f,
        0.72f, -1.00f, 0.48f, -0.32f, 0.69f, -0.84f,
        0.92f, -0.02f, 0.75f, -0.84f, 0.90f, -0.32f,
        1.18f, -1.00f, 0.94f, -0.32f, 1.15f, -0.84f,
        1.50f, -0.35f, 1.23f, -0.84f, 1.43f, -0.60f,
    ),
    'e' to floatArrayOf(
        0.42f, -0.86f, 0.14f, -0.50f, 0.30f, -0.80f,
        0.10f, -0.60f, 0.46f, -1.02f, 0.16f, -0.98f,
        0.36f, -0.04f, 0.02f, -0.28f, 0.14f, -0.02f,
        0.74f, -0.35f, 0.56f, -0.06f, 0.68f, -0.16f,
    ),
    'l' to floatArrayOf(
        0.30f, -1.82f, 0.10f, -0.76f, 0.22f, -1.44f,
        0.13f, -1.26f, 0.46f, -2.22f, 0.01f, -2.04f,
        0.31f, -0.05f, 0.21f, -0.86f, 0.18f, -0.28f,
        0.68f, -0.35f, 0.44f, 0.07f, 0.60f, -0.09f,
    ),
    'c' to floatArrayOf(
        0.44f, -0.84f, 0.15f, -0.56f, 0.32f, -0.90f,
        0.11f, -0.70f, 0.50f, -1.06f, 0.19f, -1.06f,
        0.40f, -0.05f, 0.01f, -0.40f, 0.15f, -0.03f,
        0.66f, -0.35f, 0.56f, -0.07f, 0.62f, -0.19f,
    ),
    'o' to floatArrayOf(
        0.34f, -0.90f, 0.10f, -0.60f, 0.18f, -0.90f,
        0.32f, -0.05f, 0.53f, -0.90f, 0.53f, -0.20f,
        0.34f, -0.90f, 0.08f, -0.01f, 0.03f, -0.86f,
        0.70f, -0.35f, 0.49f, -0.93f, 0.64f, -0.64f,
    ),
    'm' to floatArrayOf(
        0.22f, -0.95f, 0.06f, -0.60f, 0.13f, -0.95f,
        0.43f, -0.04f, 0.33f, -0.95f, 0.40f, -0.44f,
        0.63f, -0.95f, 0.47f, -0.50f, 0.54f, -0.95f,
        0.84f, -0.04f, 0.74f, -0.95f, 0.81f, -0.44f,
        1.04f, -0.95f, 0.88f, -0.50f, 0.95f, -0.95f,
        1.25f, -0.04f, 1.15f, -0.95f, 1.22f, -0.44f,
        1.52f, -0.35f, 1.33f, 0.03f, 1.46f, -0.16f,
    ),
)

private fun welcomePath(word: String = "welcome"): Path {
    val path = Path()
    path.moveTo(-0.34f, -0.02f)
    path.cubicTo(-0.22f, 0.10f, -0.12f, -0.10f, 0f, -0.35f)
    var x = 0f
    for (ch in word) {
        val strokes = WELCOME_STROKES[ch] ?: continue
        var i = 0
        while (i < strokes.size) {
            path.cubicTo(
                x + strokes[i + 2], strokes[i + 3],
                x + strokes[i + 4], strokes[i + 5],
                x + strokes[i], strokes[i + 1],
            )
            i += 6
        }
        x += WELCOME_ADVANCE[ch] ?: 0f
    }
    path.cubicTo(x + 0.24f, -0.30f, x + 0.44f, -0.52f, x + 0.62f, -0.52f)
    return path
}

/**
 * The word being written, by arc length, in white over the hills: a faint soft shadow under it so
 * it holds against the pale sky, and the stroke itself — no glow, the way the setup screen writes it.
 */
private fun DrawScope.drawWelcomeWord(
    path: Path,
    measure: PathMeasure,
    scratch: Path,
    progress: Float,
) {
    if (progress <= 0.001f) return
    val bounds = path.getBounds()
    if (bounds.width <= 0f || bounds.height <= 0f) return

    scratch.reset()
    if (progress >= 0.999f) scratch.addPath(path) else measure.getSegment(0f, measure.length * progress, scratch, true)

    val scale = min(size.width / bounds.width, size.height / bounds.height)
    val ox = size.width / 2f - (bounds.left + bounds.width / 2f) * scale
    val oy = size.height / 2f - (bounds.top + bounds.height / 2f) * scale
    withTransform({
        translate(ox, oy)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(scratch, Color(0xFF0B3A78).copy(alpha = 0.10f), style = stroke(0.24f))
        drawPath(scratch, Color(0xFF0B3A78).copy(alpha = 0.12f), style = stroke(0.15f))
        drawPath(scratch, Color.White, style = stroke(0.085f))
        // The pen: a soft point of light at the end of the stroke while it is still writing, so
        // the word reads as being written by something rather than uncovered.
        if (progress < 0.999f) {
            val nib = measure.getPosition(measure.length * progress)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)),
                    center = nib,
                    radius = 0.32f,
                ),
                radius = 0.32f,
                center = nib,
            )
        }
    }
}

private fun stroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

