/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ozyern.exhale.R
import com.ozyern.exhale.utils.rememberAppIconPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The cold-start boot animation, drawn as the TOP-MOST layer of the root composition.
 *
 * The beats, in order — it is called Exhale, so the whole thing is one breath:
 *
 *  1. **Bloom.** A warm amber glow swells out of pure black behind the mark, and drifts: two
 *     light sources circling slowly, so the black is lit rather than printed. It starts on
 *     frame one — before the logo is even decoded — so the screen is never a dead black slab.
 *  2. **Inhale.** Two thin rings of the mark's gold close in from the edges of the screen and
 *     are drawn into it, while the mark comes into focus: it fades up out of a soft blur and
 *     settles from 0.92x on a near-critically-damped spring. The light goes *in*.
 *  3. **Glint.** Once, a band of warm light crosses the mark's glossy black body — the light it
 *     just took in, crossing it.
 *  4. **Exhale.** The mark draws back a few percent, and a single gold ring leaves it and runs
 *     out across the screen. The app opens behind that ring — the aperture's edge *is* the ring —
 *     while the mark flies to its own place in Home's top bar and lands on the glass disc the
 *     logo lives in there. The light goes *out*, and takes the splash with it.
 *
 * No text. The mark is the name.
 *
 * ### What was taken out, and what came back
 *
 * Two expanding shockwave rings and a 10° entrance tilt are gone for good: together with a sheen
 * running over the mark on the same beat, they were four things competing for attention inside one
 * second, which is what a splash screen looks like when it is trying to impress you.
 *
 * The sheen came back, changed. It no longer arrives with the mark; it crosses it once, after the
 * mark has settled into focus, and it is masked to the mark so it reads as light moving over the
 * gloss of the black body. Things happen one after another — focus, glint, name, opening — which
 * is the difference between a sequence and a pile-up. The aperture is still the moment the whole
 * animation exists for.
 *
 * ### Why it used to be slow
 *
 * Two separate causes, both fixed here:
 *
 *  - **~1.8s of mandatory animation.** The old timeline waited for the *slowest* of three intro
 *    tweens (720ms) to fully settle, then held 480ms, then crossfaded 440ms + 40ms of slack.
 *    That is the whole of it spent staring at a static mark. The budget below is ~1.4s, and
 *    every phase is doing something.
 *  - **A main-thread image decode at the worst possible moment.** `splash_logo.png` is a
 *    1024x1024 / 1.4MB PNG; `painterResource` decodes it *synchronously, on the main thread,
 *    during composition* — 4MB of ARGB_8888 allocated on the exact frame the whole app is also
 *    composing its first screen. That was the stutter. It is now decoded on [Dispatchers.IO] and
 *    the timeline simply starts when it lands (single-digit-to-low-tens of milliseconds later),
 *    with the bloom already on screen covering the gap.
 *
 * Everything animated here is read inside `graphicsLayer` / draw lambdas, so the whole sequence
 * runs in the draw phase — it never triggers a recomposition or a relayout while the app behind
 * it is doing its expensive first composition.
 *
 * The layer is removed from composition entirely once finished, costing nothing afterwards.
 * `rememberSaveable` in the host keeps it a cold-start-only moment; rotations never replay it.
 */

// ---- Timeline (ms) -------------------------------------------------------------------------
/** How long the mark is on screen before the aperture opens. Short: it is a flourish, not a wait. */
private const val ENTRANCE_MS = 760L
/**
 * The aperture opening. Also the fade, the zoom and the hand-off — one motion on one easing,
 * because two eases running at once is how a single gesture stops reading as single.
 *
 * Longer than the 380ms it was. The iris is the moment the whole animation exists for, and at 380
 * it was over before the eye had followed the edge outward — the extra 120ms is the difference
 * between a cut and an opening.
 */
private const val IRIS_MS = 520

/** Apple's emphasized curve: leaves quickly and takes its time arriving. */
private val EmphasizedEasing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Fraction of the shorter viewport edge the square splash artwork occupies. */
private const val SPLASH_ARTWORK_FRACTION = 0.56f

// Brand palette, sampled from the artwork itself rather than guessed. The default mark
// (splash_logo.png) is a black glyph wearing a gold rim light: 72% of the mark is near-black body, its rim averages #DEB41A,
// and it ranges from a #000100 shadow to a #FFF07F highlight. It is a GOLD logo with a dark core.
//
// That split is why the bloom matters more than it looks. On the black canvas the body is
// invisible on its own and only the rim reads — the warm glow behind the mark is what gives the
// body an edge to sit against, so the bloom is doing structural work, not decoration.
//
// (The bloom was once crimson-magenta, #B01E45 over #3A0A1E, a palette belonging to no part of
// this artwork. A gold mark floating in a pink glow is the mismatch; these are its own colours.)
//
// The Gold pack's mark (splash_logo_gold.png) is the same glyph rendered in that same gold rather
// than wearing it as a rim, so the palette holds for both and the bloom needs no per-pack case.
private val SplashBase = Color.Black
private val BloomDeep = Color(0xFFE0A020)   // warm amber core, the mark's mid-gold pushed brighter
private val BloomDark = Color(0xFF33200A)   // deep brown-amber mid-tone, the mark's own shadow

/** The mark's rim gold, highlight to mid: what the breath rings are drawn in. */
private val RingGold = Color(0xFFFFE07A)
private val RingGoldDeep = Color(0xFFDEB41A)

@Composable
fun BootSplash(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Sized off the SHORTER viewport edge so the square artwork is generous on a tablet, fully
    // un-cropped on a small phone, and safe in landscape — with margin left over for the spring's
    // overshoot and the exit zoom.
    val configuration = LocalConfiguration.current
    val artworkSize = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        (minOf(configuration.screenWidthDp, configuration.screenHeightDp) * SPLASH_ARTWORK_FRACTION).dp
    }
    var screenWidthPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableStateOf(0f) }

    val context = LocalContext.current
    // Whichever mark the user picked in Settings -> Appearance -> App icon. Read from
    // PackageManager, so it is right on the first frame — a splash that opened on the wrong logo
    // and corrected itself would be worse than not following the setting at all.
    val markRes = rememberAppIconPack().splashLogoRes
    var logo by remember { mutableStateOf<ImageBitmap?>(null) }
    var decodeFailed by remember { mutableStateOf(false) }

    val bloom = remember { Animatable(0f) }
    // 0.90, not 0.70. A mark arriving from two thirds of its size has visibly *travelled*, which
    // needs a bounce to land and then reads as a bounce. From 0.90 it simply settles.
    val logoScale = remember { Animatable(0.92f) }
    val logoAlpha = remember { Animatable(0f) }
    // The focus pull: blur radius in dp, from soft to sharp.
    val logoBlur = remember { Animatable(14f) }
    // -0.3 to 1.3 across the mark, so the band enters and leaves entirely off it.
    val glint = remember { Animatable(-0.3f) }
    // The inhale: rings closing on the mark. The exhale ring rides the aperture's edge (iris).
    val inhale = remember { Animatable(0f) }
    // The bloom's two light sources, circling. Runs from frame one to the last.
    val drift = remember { Animatable(0f) }
    val iris = remember { Animatable(0f) }
    // The mark's flight to the top bar.
    val flight = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)

    // Frame one: the bloom is already breathing in while the artwork is still being decoded on a
    // background thread. The user never sees an empty black hold.
    LaunchedEffect(markRes) {
        launch {
            bloom.animateTo(1f, tween(durationMillis = 380, easing = LinearOutSlowInEasing))
        }
        launch {
            drift.animateTo(1f, tween(durationMillis = 2_400, easing = LinearEasing))
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeResource(context.resources, markRes)
                    ?.asImageBitmap()
            }.getOrNull()
        }
        if (decoded != null) logo = decoded else decodeFailed = true
    }

    val ready = logo != null || decodeFailed

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect

        // --- Arrival, then the breath ---
        launch { logoAlpha.animateTo(1f, tween(durationMillis = 300, easing = LinearOutSlowInEasing)) }
        launch {
            logoBlur.animateTo(0f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
            // The one tactile beat: the lens clicking into focus.
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
        launch { inhale.animateTo(1f, tween(durationMillis = ENTRANCE_MS.toInt() - 60, easing = FastOutSlowInEasing)) }
        launch {
            delay(260)
            glint.animateTo(1.3f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
        launch {
            // Damping 0.88: it settles rather than bounces. The old 0.62 gave a visible rebound,
            // which is a *toy* gesture — right for a game splash, wrong for the screen that opens
            // in front of a music library every morning.
            logoScale.animateTo(1f, spring(dampingRatio = 0.88f, stiffness = 300f))
            // Linear on purpose. Any easing has an acceleration you can perceive, and a breath you
            // can perceive is a zoom. You should only notice this one by comparing the first frame
            // of the hold against the last.
            logoScale.animateTo(
                1.035f,
                tween(durationMillis = ENTRANCE_MS.toInt(), easing = LinearEasing),
            )
        }

        delay(ENTRANCE_MS)

        // A breath in before it leaves: a few percent smaller for a beat, so the flight reads as
        // the mark pushing off rather than being pulled away.
        logoScale.animateTo(0.97f, tween(durationMillis = 110, easing = FastOutSlowInEasing))

        // --- Exit: the aperture opens and the mark flies past the camera ---
        // The breath is still running here; `animateTo` on the same Animatable cancels it and
        // carries on from wherever it had reached, so the hand-off has no seam in it.
        // The mark goes home: to the logo's disc in the top bar, on the aperture's own curve.
        launch { flight.animateTo(1f, tween(durationMillis = IRIS_MS, easing = EmphasizedEasing)) }
        // The breath out: felt as well as seen.
        haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
        // The glow leaves with the mark. Left up, it is a warm haze lying over the first frames of
        // a fully drawn app, which is the one thing that can make an otherwise clean hand-off look
        // like a rendering fault.
        launch { bloom.animateTo(0f, tween(durationMillis = 340, easing = FastOutSlowInEasing)) }
        iris.animateTo(1f, tween(durationMillis = IRIS_MS, easing = EmphasizedEasing))

        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                screenWidthPx = it.width.toFloat()
                screenHeightPx = it.height.toFloat()
            },
        contentAlignment = Alignment.Center,
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Offscreen compositing is what makes the aperture possible: BlendMode.Clear can only
            // punch a true hole through pixels that live in their own layer. Without this, Clear
            // would blend against the window and paint black instead of revealing the app. The
            // mark is drawn above this layer, not in it, so the aperture never erases it mid-flight.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val progress = iris.value
                if (progress > 0f) {
                    // Grows from the mark's own radius out past the far corners, so the last frame
                    // of the splash is genuinely empty and the hand-off has nothing left to hide.
                    val start = artworkSize.toPx() * 0.30f
                    val end = hypot(size.width, size.height) * 0.52f
                    val radius = lerp(start, end, progress)
                    drawCircle(
                        color = Color.Black,
                        radius = radius,
                        center = center,
                        blendMode = BlendMode.Clear,
                    )
                    // The exhale: a gold ring on the aperture's edge, bright as it leaves the mark
                    // and spent by the time it reaches the corners. A wide faint stroke under a
                    // fine bright one, so it glows without a blur pass.
                    val fade = (1f - progress) * (1f - progress)
                    if (fade > 0.01f) {
                        drawCircle(
                            color = RingGoldDeep.copy(alpha = 0.28f * fade),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx()),
                        )
                        drawCircle(
                            color = RingGold.copy(alpha = 0.9f * fade),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()),
                        )
                    }
                }
            }
            .background(SplashBase),
        contentAlignment = Alignment.Center,
    ) {
        // ---- Bloom: the mark's own amber, lit from two slowly circling sources ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bloom.value }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(BloomDark.copy(alpha = 0.55f), SplashBase),
                    ),
                )
                .drawWithContent {
                    drawContent()
                    val turn = drift.value * 1.2f
                    val reach = size.minDimension * 0.62f
                    listOf(0f to 0.62f, 2.4f to 0.36f).forEach { (phase, strength) ->
                        val center = Offset(
                            x = size.width / 2f + size.minDimension * 0.10f * cos(turn + phase),
                            y = size.height / 2f + size.minDimension * 0.08f * sin(turn * 0.8f + phase),
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    BloomDeep.copy(alpha = strength),
                                    BloomDark.copy(alpha = strength * 0.45f),
                                    Color.Transparent,
                                ),
                                center = center,
                                radius = reach,
                            ),
                            radius = reach,
                            center = center,
                        )
                    }
                },
        )

        // ---- Inhale: two rings of gold drawn in from the edges, into the mark ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val t = inhale.value
                    if (t <= 0f || t >= 1f) return@drawWithContent
                    val far = hypot(size.width, size.height) * 0.5f
                    val near = artworkSize.toPx() * 0.36f
                    // The second ring follows the first a beat behind, so the light arrives as a
                    // breath drawn in, not as one hoop.
                    listOf(0f, 0.22f).forEach { lag ->
                        val local = ((t - lag) / (1f - lag)).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) return@forEach
                        val eased = FastOutSlowInEasing.transform(local)
                        val radius = lerp(far, near, eased)
                        // Faint far out, brightest on the way in, gone as it reaches the mark.
                        val a = sin(local * Math.PI.toFloat()) * bloom.value * (if (lag == 0f) 1f else 0.6f)
                        drawCircle(
                            color = RingGoldDeep.copy(alpha = 0.22f * a),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx()),
                        )
                        drawCircle(
                            color = RingGold.copy(alpha = 0.75f * a),
                            radius = radius,
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
                        )
                    }
                },
        )
    }

        // ---- The mark ----
        // The transform lives on this Box rather than on the Image inside it, so the whole thing
        // scales as one rasterised object. No offscreen layer: that was only ever needed to mask
        // the sheen sweep to the artwork's alpha, and the sweep is gone.
        Box(
            modifier = Modifier
                .size(artworkSize)
                .graphicsLayer {
                    // Home is the top bar's logo disc: its mark is about 28dp across, 16dp in from
                    // the start edge and centred in the 64dp bar under the status bar.
                    val f = flight.value
                    val markInk = artworkSize.toPx() * 0.78f
                    val homeScale = 28.dp.toPx() / markInk
                    val homeX = (16.dp + 19.dp).toPx() - size.width / 2f
                    val homeY = statusBarTop + 32.dp.toPx() - size.height / 2f
                    // The splash is centred on the window; its own centre is where we start from.
                    val fromCentre = Offset(
                        (screenWidthPx - size.width) / 2f,
                        (screenHeightPx - size.height) / 2f,
                    )
                    translationX = (homeX - fromCentre.x) * f
                    translationY = (homeY - fromCentre.y) * f
                    val scale = logoScale.value * (1f + (homeScale - 1f) * f)
                    scaleX = scale
                    scaleY = scale
                    // Solid for most of the flight, handing over to the real logo as it lands.
                    alpha = logoAlpha.value * (1f - ((f - 0.72f) / 0.28f).coerceIn(0f, 1f))
                    val blurPx = logoBlur.value.dp.toPx()
                    renderEffect = if (blurPx > 0.5f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
                    // The glint is masked to the mark's own pixels, which needs a layer of its own.
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val g = glint.value
                    if (g > -0.3f && g < 1.3f) {
                        // A diagonal band of warm light, drawn only where the mark is (SrcAtop):
                        // a reflection travelling across the gloss, not a stripe across the screen.
                        val band = size.width * 0.22f
                        val x = size.width * g
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFFFFF6D8).copy(alpha = 0.55f),
                                    Color.Transparent,
                                ),
                                start = Offset(x - band, 0f),
                                end = Offset(x + band, size.height * 0.55f),
                            ),
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val markModifier = Modifier.fillMaxSize()

            val bitmap = logo
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = markModifier,
                )
            } else if (decodeFailed) {
                // Belt and braces: if the background decode ever fails, fall back to the
                // synchronous resource path rather than booting into a logo-less splash.
                Image(
                    painter = androidx.compose.ui.res.painterResource(markRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = markModifier,
                )
            }
        }

    }
}
