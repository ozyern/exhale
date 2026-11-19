/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ozyern.exhale.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val MoteCount = 150
private const val RippleLifeMillis = 1_600f
private const val DismissDragPx = 140f

/** How far the gravity well reaches, as a fraction of the screen's short side. */
private const val WellReach = 0.55f

/**
 * The hidden thing behind seven taps on the About hero.
 *
 * The app is called Exhale, so the egg is a breathing pacer — but a pacer you can only watch is a
 * progress bar with better manners, so this one is a field you are inside of and can put your hand
 * into. A hundred and fifty motes orbit a core; the inhale draws them in and tightens their orbit,
 * the exhale throws them back out, and **holding a finger down gathers them toward it** like a
 * gravity well. Three counter-rotating arcs cross the field at different rates, a phase ring
 * closes once per phase, and each tap pushes a ripple out from where it landed.
 *
 * Three patterns, because one ratio does not fit one purpose: [BreathPatterns] carries the calm-
 * down 4–6, the even-keeled box breath, and 4–7–8 for getting to sleep. Tap the pattern's name to
 * cycle them. A hold phase is a real phase here, not a pause in the animation — the ring keeps
 * closing through it, which is the only thing that makes a four-count hold followable.
 *
 * **Everything is one Canvas and one clock.** A single frame loop writes elapsed milliseconds into
 * a float state that is read *inside* the draw lambda, so a frame costs one draw pass — no
 * recomposition, no layout, no per-mote composable. That is what keeps a hundred and fifty
 * particles plus five gradients at full frame rate instead of a slideshow with good taste. The
 * phase label and the breath count are the only things allowed to recompose, and they change once
 * every few seconds.
 *
 * Tap to ripple, hold to gather, swipe down or press back to leave.
 */
@Composable
fun ExhaleBreathingEgg(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val haptics = LocalHapticFeedback.current
        val elapsed = remember { mutableFloatStateOf(0f) }
        val ripples = remember { mutableStateListOf<Ripple>() }
        var patternIndex by remember { mutableIntStateOf(0) }
        val pattern = BreathPatterns[patternIndex]
        var phase by remember { mutableStateOf(BreathPhase.IN) }
        var breaths by remember { mutableIntStateOf(0) }

        // The gravity well. Position and strength are floats read in the draw lambda; only the
        // pointer callback writes them, so putting a finger on the field costs nothing but frames.
        val wellPosition = remember { mutableStateOf(Offset.Unspecified) }
        val wellStrength = remember { mutableFloatStateOf(0f) }
        val wellPressed = remember { mutableStateOf(false) }

        // Motes are laid out once. Their orbits are deliberately uneven: an evenly spaced ring
        // reads as a machine part, and the point is breath, not clockwork.
        val motes = remember {
            val random = Random(0x3AB1E)
            List(MoteCount) {
                Mote(
                    angle = random.nextFloat() * TwoPi,
                    orbit = random.nextFloat().let { it * it },
                    radius = 1.1f + random.nextFloat() * 2.6f,
                    speed = (0.10f + random.nextFloat() * 0.32f) *
                        if (random.nextBoolean()) 1f else -1f,
                    wobble = random.nextFloat() * TwoPi,
                )
            }
        }

        // Restarted on every pattern change so a switch always begins on a fresh inhale. Landing
        // mid-exhale in a rhythm you did not choose is the one thing that breaks a pacer.
        LaunchedEffect(patternIndex) {
            elapsed.floatValue = 0f
            breaths = 0
            var lastStep = -1
            val origin = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val millis = (now - origin) / 1_000_000f
                    elapsed.floatValue = millis

                    val state = pattern.at(millis)
                    phase = state.phase
                    breaths = state.cycles
                    if (state.stepIndex != lastStep) {
                        lastStep = state.stepIndex
                        // One pulse per phase boundary, so the pattern can be followed with the
                        // screen off-axis or the eyes closed — which is how it is actually used.
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }

                    // Eased in and out rather than switched: motes that snap toward the finger
                    // read as a glitch, motes that lean toward it read as weight.
                    val target = if (wellPressed.value) 1f else 0f
                    wellStrength.floatValue += (target - wellStrength.floatValue) * 0.14f

                    if (ripples.isNotEmpty()) {
                        ripples.removeAll { millis - it.bornMillis > RippleLifeMillis }
                    }
                }
            }
        }

        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val tertiary = MaterialTheme.colorScheme.tertiary

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            AuroraBackdrop()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Watches the pointer without consuming anything, so the tap and the
                    // swipe-to-dismiss below still see every event they need.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val down = event.changes.firstOrNull { it.pressed }
                                wellPressed.value = down != null
                                if (down != null) wellPosition.value = down.position
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ripples += Ripple(offset, elapsed.floatValue)
                        }
                    }
                    .pointerInput(Unit) {
                        var travelled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f },
                            onDragEnd = { if (travelled > DismissDragPx) onDismiss() },
                        ) { _, delta -> travelled += delta }
                    },
            ) {
                val millis = elapsed.floatValue
                val state = pattern.at(millis)
                val breath = state.breath
                val seconds = millis / 1_000f

                val middle = center
                val core = size.minDimension * 0.17f * (0.60f + 0.40f * breath)
                val tone = when (state.phase) {
                    BreathPhase.IN -> primary
                    BreathPhase.OUT -> secondary
                    else -> tertiary
                }

                drawBloom(middle, core, breath, primary, tertiary)
                drawMotes(
                    motes = motes,
                    middle = middle,
                    core = core,
                    breath = breath,
                    seconds = seconds,
                    primary = primary,
                    secondary = secondary,
                    tertiary = tertiary,
                    well = wellPosition.value,
                    wellStrength = wellStrength.floatValue,
                )
                drawArcs(middle, core, breath, seconds, primary, tertiary)
                drawPhaseRing(middle, core, state.phaseProgress, tone)
                drawCore(middle, core, breath, primary)
                drawRipples(ripples, millis, primary)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
            ) {
                Crossfade(targetState = phase, label = "breathPrompt") { current ->
                    Text(
                        text = stringResource(
                            when (current) {
                                BreathPhase.IN -> R.string.egg_breathe_in
                                BreathPhase.OUT -> R.string.egg_breathe_out
                                else -> R.string.egg_hold
                            }
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // The pattern picker. A chip rather than a settings row: it is one word and a
                // rhythm, and reading it *is* choosing it.
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        patternIndex = (patternIndex + 1) % BreathPatterns.size
                    },
                ) {
                    Text(
                        text = stringResource(pattern.nameRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    text = if (breaths == 0) {
                        stringResource(R.string.egg_hint)
                    } else {
                        stringResource(R.string.egg_breath_count, breaths.toString())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = stringResource(R.string.egg_close_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ─── Field ────────────────────────────────────────────────────────────────────

private const val TwoPi = (2.0 * PI).toFloat()

/**
 * One leg of a breathing pattern. A hold is a phase in its own right, not the absence of one —
 * the ring keeps closing through it and the haptic still fires at its ends, which is the whole
 * difference between "hold for four" and "the animation froze".
 */
private enum class BreathPhase { IN, HOLD_FULL, OUT, HOLD_EMPTY }

private class BreathStep(val phase: BreathPhase, val millis: Float)

private class BreathPattern(val nameRes: Int, val steps: List<BreathStep>) {
    val cycleMillis: Float = steps.fold(0f) { total, step -> total + step.millis }
}

/**
 * Three ratios for three jobs: a long exhale to calm down, an even square to hold steady, and
 * 4–7–8 to fall asleep. Nothing here is invented — they are the three people actually use.
 */
private val BreathPatterns = listOf(
    BreathPattern(
        R.string.egg_pattern_relax,
        listOf(
            BreathStep(BreathPhase.IN, 4_000f),
            BreathStep(BreathPhase.OUT, 6_000f),
        ),
    ),
    BreathPattern(
        R.string.egg_pattern_box,
        listOf(
            BreathStep(BreathPhase.IN, 4_000f),
            BreathStep(BreathPhase.HOLD_FULL, 4_000f),
            BreathStep(BreathPhase.OUT, 4_000f),
            BreathStep(BreathPhase.HOLD_EMPTY, 4_000f),
        ),
    ),
    BreathPattern(
        R.string.egg_pattern_478,
        listOf(
            BreathStep(BreathPhase.IN, 4_000f),
            BreathStep(BreathPhase.HOLD_FULL, 7_000f),
            BreathStep(BreathPhase.OUT, 8_000f),
        ),
    ),
)

/** Where a pattern is at [millis]: which leg, how far into it, and how full the lungs are. */
private class BreathState(
    val stepIndex: Int,
    val phase: BreathPhase,
    val phaseProgress: Float,
    val breath: Float,
    val cycles: Int,
)

/**
 * Resolved on every frame, inside the draw lambda. It walks at most four steps and allocates one
 * small object, which is cheaper than any of the ~160 draw calls that follow it.
 */
private fun BreathPattern.at(millis: Float): BreathState {
    val cycles = (millis / cycleMillis).toInt()
    var into = millis % cycleMillis
    steps.forEachIndexed { index, step ->
        if (into < step.millis) {
            val progress = (into / step.millis).coerceIn(0f, 1f)
            return BreathState(
                stepIndex = index,
                phase = step.phase,
                phaseProgress = progress,
                breath = when (step.phase) {
                    BreathPhase.IN -> eased(progress)
                    BreathPhase.HOLD_FULL -> 1f
                    BreathPhase.OUT -> 1f - eased(progress)
                    BreathPhase.HOLD_EMPTY -> 0f
                },
                cycles = cycles,
            )
        }
        into -= step.millis
    }
    // Only reachable on floating-point crumbs at the very end of a cycle.
    val last = steps.last()
    return BreathState(
        stepIndex = steps.lastIndex,
        phase = last.phase,
        phaseProgress = 1f,
        breath = if (last.phase == BreathPhase.IN || last.phase == BreathPhase.HOLD_FULL) 1f else 0f,
        cycles = cycles,
    )
}

/**
 * One orbiting speck. [orbit] is squared at construction so most motes sit close to the core and
 * a few stray far out, which is what makes the field look like dust rather than a dial.
 */
private class Mote(
    val angle: Float,
    val orbit: Float,
    val radius: Float,
    val speed: Float,
    val wobble: Float,
)

private class Ripple(val origin: Offset, val bornMillis: Float)

/** Cosine ease. Breath has no straight lines in it. */
private fun eased(t: Float): Float = (1f - cos(t.coerceIn(0f, 1f) * PI.toFloat())) / 2f

private fun DrawScope.drawBloom(
    middle: Offset,
    core: Float,
    breath: Float,
    primary: Color,
    tertiary: Color,
) {
    val reach = core * (3.4f + 1.1f * breath)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = 0.34f + 0.20f * breath),
                tertiary.copy(alpha = 0.14f + 0.10f * breath),
                Color.Transparent,
            ),
            center = middle,
            radius = reach,
        ),
        radius = reach,
        center = middle,
    )
}

/**
 * The orbit collapses on the inhale and flings open on the exhale, so the field itself breathes
 * rather than merely surrounding something that does.
 */
private fun DrawScope.drawMotes(
    motes: List<Mote>,
    middle: Offset,
    core: Float,
    breath: Float,
    seconds: Float,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    well: Offset,
    wellStrength: Float,
) {
    val palette = listOf(primary, secondary, tertiary)
    val spread = core * (1.5f + 3.2f * (1f - breath))

    val pulling = wellStrength > 0.01f && well.isSpecified
    val reach = size.minDimension * WellReach

    motes.forEachIndexed { index, mote ->
        val angle = mote.angle + seconds * mote.speed
        val breathing = sin(seconds * 0.9f + mote.wobble) * 0.08f
        val distance = core * 1.15f + spread * (mote.orbit + breathing)

        var position = Offset(
            x = middle.x + distance * cos(angle),
            y = middle.y + distance * sin(angle),
        )
        var gathered = 0f

        if (pulling) {
            // Quadratic falloff, and a *fraction of the gap* rather than a fixed push: near the
            // finger the motes crowd hard, at the rim they only lean. A linear pull applied
            // uniformly turns the whole field into one blob the instant you touch it.
            val dx = well.x - position.x
            val dy = well.y - position.y
            val gap = hypot(dx, dy).coerceAtLeast(1f)
            val falloff = (1f - gap / reach).coerceIn(0f, 1f)
            gathered = falloff * falloff * wellStrength
            val pull = gathered * 0.62f
            position = Offset(position.x + dx * pull, position.y + dy * pull)
        }

        val twinkle = 0.45f + 0.55f * ((sin(seconds * 2.1f + mote.wobble) + 1f) / 2f)

        drawCircle(
            color = palette[index % palette.size].copy(
                // Gathered motes brighten, so the well reads as attention and not as a smudge.
                alpha = ((0.16f + 0.42f * breath) * twinkle + 0.34f * gathered).coerceAtMost(1f),
            ),
            radius = mote.radius * (0.75f + 0.45f * breath) * (1f + 0.6f * gathered),
            center = position,
        )
    }
}

/** Three counter-rotating sweeps. Their stroke thickens with the inhale. */
private fun DrawScope.drawArcs(
    middle: Offset,
    core: Float,
    breath: Float,
    seconds: Float,
    primary: Color,
    tertiary: Color,
) {
    val bands = listOf(
        Triple(1.75f, 11f, primary),
        Triple(2.25f, -7f, tertiary),
        Triple(2.85f, 4.5f, primary),
    )

    bands.forEachIndexed { index, (scale, degreesPerSecond, tone) ->
        val radius = core * scale
        val sweep = Brush.sweepGradient(
            colors = listOf(
                Color.Transparent,
                tone.copy(alpha = 0.05f + 0.30f * breath),
                Color.Transparent,
                tone.copy(alpha = 0.02f + 0.12f * breath),
                Color.Transparent,
            ),
            center = middle,
        )

        rotate(degrees = seconds * degreesPerSecond + index * 40f, pivot = middle) {
            drawCircle(
                brush = sweep,
                radius = radius,
                center = middle,
                style = Stroke(width = (1.2f + 2.6f * breath) * (3f - index) * density * 0.5f),
            )
        }
    }
}

/**
 * A ring that closes exactly once per phase — the only element that says how far through the
 * current leg you are, so the pacer is followable without a number on screen, and the only reason
 * a seven-count hold is bearable.
 *
 * [tone] is chosen by the caller from the phase, which is what distinguishes a hold from the
 * breath either side of it at a glance.
 */
private fun DrawScope.drawPhaseRing(
    middle: Offset,
    core: Float,
    phase: Float,
    tone: Color,
) {
    val radius = core * 3.25f
    val stroke = Stroke(width = 2.5f * density)

    drawArc(
        color = tone.copy(alpha = 0.10f),
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(middle.x - radius, middle.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = stroke,
    )
    drawArc(
        color = tone.copy(alpha = 0.55f),
        startAngle = -90f,
        sweepAngle = 360f * phase.coerceIn(0f, 1f),
        useCenter = false,
        topLeft = Offset(middle.x - radius, middle.y - radius),
        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
        style = stroke,
    )
}

private fun DrawScope.drawCore(middle: Offset, core: Float, breath: Float, primary: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.60f + 0.30f * breath),
                primary.copy(alpha = 0.72f),
                primary.copy(alpha = 0.18f),
            ),
            center = middle,
            radius = core,
        ),
        radius = core,
        center = middle,
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.12f + 0.16f * breath),
        radius = core,
        center = middle,
        style = Stroke(width = 1.4f * density),
    )
}

/** Touch ripples: a ring that widens and thins out, plus a soft wash that fades with it. */
private fun DrawScope.drawRipples(ripples: List<Ripple>, millis: Float, primary: Color) {
    ripples.forEach { ripple ->
        val life = ((millis - ripple.bornMillis) / RippleLifeMillis).coerceIn(0f, 1f)
        val radius = size.minDimension * 0.55f * eased(life)
        val fade = (1f - life)

        drawCircle(
            color = primary.copy(alpha = 0.28f * fade),
            radius = radius,
            center = ripple.origin,
            style = Stroke(width = (5f * fade + 1f) * density),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, primary.copy(alpha = 0.10f * fade)),
                center = ripple.origin,
                radius = radius.coerceAtLeast(1f),
            ),
            radius = radius,
            center = ripple.origin,
        )
    }
}
