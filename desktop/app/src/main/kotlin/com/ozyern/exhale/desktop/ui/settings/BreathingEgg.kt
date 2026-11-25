/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private const val MoteCount = 180
private const val RippleLifeMillis = 1_600f
private const val DismissDragPx = 140f
private const val WellReach = 0.55f
private const val TwoPi = (2.0 * PI).toFloat()

/**
 * The hidden thing behind seven taps on About's statement card, ported from the Android app: a
 * breathing pacer you're inside of. Motes orbit a core — drawn in on the inhale, flung out on the
 * exhale — while arcs turn and a ring closes once per phase. Click to ripple, hold the mouse down to
 * gather the motes toward it, click the rhythm to change it. Esc or drag down to leave.
 *
 * One canvas and one clock: the frame loop writes elapsed time into a float read inside the draw
 * lambda, so a frame is one draw pass however many motes there are.
 */
@Composable
internal fun ExhaleBreathingEgg(onDismiss: () -> Unit) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        val elapsed = remember { mutableFloatStateOf(0f) }
        val ripples = remember { mutableStateListOf<Ripple>() }
        var patternIndex by remember { mutableIntStateOf(0) }
        val pattern = BreathPatterns[patternIndex]
        var phase by remember { mutableStateOf(BreathPhase.IN) }
        var breaths by remember { mutableIntStateOf(0) }
        val wellPosition = remember { mutableStateOf(Offset.Unspecified) }
        val wellStrength = remember { mutableFloatStateOf(0f) }
        val wellPressed = remember { mutableStateOf(false) }

        // Uneven orbits on purpose: an evenly spaced ring reads as a machine part.
        val motes = remember {
            val random = Random(0x3AB1E)
            List(MoteCount) {
                Mote(
                    angle = random.nextFloat() * TwoPi,
                    orbit = random.nextFloat().let { it * it },
                    radius = 1.1f + random.nextFloat() * 2.6f,
                    speed = (0.10f + random.nextFloat() * 0.32f) * if (random.nextBoolean()) 1f else -1f,
                    wobble = random.nextFloat() * TwoPi,
                )
            }
        }

        // A new rhythm always starts on a fresh inhale.
        LaunchedEffect(patternIndex) {
            elapsed.floatValue = 0f
            breaths = 0
            val origin = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val millis = (now - origin) / 1_000_000f
                    elapsed.floatValue = millis
                    val state = pattern.at(millis)
                    phase = state.phase
                    breaths = state.cycles
                    val target = if (wellPressed.value) 1f else 0f
                    wellStrength.floatValue += (target - wellStrength.floatValue) * 0.14f
                    if (ripples.isNotEmpty()) ripples.removeAll { millis - it.bornMillis > RippleLifeMillis }
                }
            }
        }

        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val tertiary = MaterialTheme.colorScheme.tertiary

        Box(Modifier.fillMaxSize().background(Color(0xF5080609)), contentAlignment = Alignment.Center) {
            Aurora(primary, tertiary)

            Canvas(
                Modifier
                    .fillMaxSize()
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
                    .pointerInput(Unit) { detectTapGestures { offset -> ripples += Ripple(offset, elapsed.floatValue) } }
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
                val core = size.minDimension * 0.15f * (0.60f + 0.40f * breath)
                val tone = when (state.phase) {
                    BreathPhase.IN -> primary
                    BreathPhase.OUT -> secondary
                    else -> tertiary
                }
                drawBloom(middle, core, breath, primary, tertiary)
                drawMotes(motes, middle, core, breath, seconds, listOf(primary, secondary, tertiary), wellPosition.value, wellStrength.floatValue)
                drawArcs(middle, core, breath, seconds, primary, tertiary)
                drawPhaseRing(middle, core, state.phaseProgress, tone)
                drawCore(middle, core, breath, primary)
                drawRipples(ripples, millis, primary)
            }

            Column(
                Modifier.fillMaxSize().padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Crossfade(targetState = phase, label = "breath_prompt") { current ->
                    Text(
                        when (current) {
                            BreathPhase.IN -> "Breathe in"
                            BreathPhase.OUT -> "Breathe out"
                            else -> "Hold"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    pattern.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable { patternIndex = (patternIndex + 1) % BreathPatterns.size }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (breaths == 0) "Follow the ring. Click the rhythm to change it." else "$breaths breaths",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Click to ripple · Hold to gather · Esc to close",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

/** Two slow colour washes drifting behind the field. */
@Composable
private fun Aurora(primary: Color, tertiary: Color) {
    val drift by rememberInfiniteTransition(label = "aurora").animateFloat(
        initialValue = 0f,
        targetValue = TwoPi,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "aurora_drift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val reach = size.maxDimension * 0.6f
        val a = Offset(size.width * (0.3f + 0.1f * sin(drift)), size.height * (0.35f + 0.08f * cos(drift)))
        val b = Offset(size.width * (0.7f + 0.1f * cos(drift * 0.8f)), size.height * (0.65f + 0.08f * sin(drift * 0.8f)))
        drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = 0.22f), Color.Transparent), center = a, radius = reach), radius = reach, center = a)
        drawCircle(Brush.radialGradient(listOf(tertiary.copy(alpha = 0.18f), Color.Transparent), center = b, radius = reach), radius = reach, center = b)
    }
}

// ─── Field ────────────────────────────────────────────────────────────────────

private enum class BreathPhase { IN, HOLD_FULL, OUT, HOLD_EMPTY }

private class BreathStep(val phase: BreathPhase, val millis: Float)

private class BreathPattern(val name: String, val steps: List<BreathStep>) {
    val cycleMillis: Float = steps.fold(0f) { total, step -> total + step.millis }
}

/** A long exhale to calm down, an even square to hold steady, and 4–7–8 to fall asleep. */
private val BreathPatterns = listOf(
    BreathPattern("Relax · 4–6", listOf(BreathStep(BreathPhase.IN, 4_000f), BreathStep(BreathPhase.OUT, 6_000f))),
    BreathPattern(
        "Box · 4–4–4–4",
        listOf(
            BreathStep(BreathPhase.IN, 4_000f),
            BreathStep(BreathPhase.HOLD_FULL, 4_000f),
            BreathStep(BreathPhase.OUT, 4_000f),
            BreathStep(BreathPhase.HOLD_EMPTY, 4_000f),
        ),
    ),
    BreathPattern(
        "Sleep · 4–7–8",
        listOf(BreathStep(BreathPhase.IN, 4_000f), BreathStep(BreathPhase.HOLD_FULL, 7_000f), BreathStep(BreathPhase.OUT, 8_000f)),
    ),
)

private class BreathState(val phase: BreathPhase, val phaseProgress: Float, val breath: Float, val cycles: Int)

private fun BreathPattern.at(millis: Float): BreathState {
    val cycles = (millis / cycleMillis).toInt()
    var into = millis % cycleMillis
    steps.forEach { step ->
        if (into < step.millis) {
            val progress = (into / step.millis).coerceIn(0f, 1f)
            return BreathState(
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
    val last = steps.last()
    return BreathState(last.phase, 1f, if (last.phase == BreathPhase.IN || last.phase == BreathPhase.HOLD_FULL) 1f else 0f, cycles)
}

private class Mote(val angle: Float, val orbit: Float, val radius: Float, val speed: Float, val wobble: Float)

private class Ripple(val origin: Offset, val bornMillis: Float)

/** Cosine ease. Breath has no straight lines in it. */
private fun eased(t: Float): Float = (1f - cos(t.coerceIn(0f, 1f) * PI.toFloat())) / 2f

private fun DrawScope.drawBloom(middle: Offset, core: Float, breath: Float, primary: Color, tertiary: Color) {
    val reach = core * (3.4f + 1.1f * breath)
    drawCircle(
        Brush.radialGradient(
            listOf(primary.copy(alpha = 0.34f + 0.20f * breath), tertiary.copy(alpha = 0.14f + 0.10f * breath), Color.Transparent),
            center = middle,
            radius = reach,
        ),
        radius = reach,
        center = middle,
    )
}

private fun DrawScope.drawMotes(
    motes: List<Mote>,
    middle: Offset,
    core: Float,
    breath: Float,
    seconds: Float,
    palette: List<Color>,
    well: Offset,
    wellStrength: Float,
) {
    val spread = core * (1.5f + 3.2f * (1f - breath))
    val pulling = wellStrength > 0.01f && well.isSpecified
    val reach = size.minDimension * WellReach
    motes.forEachIndexed { index, mote ->
        val angle = mote.angle + seconds * mote.speed
        val breathing = sin(seconds * 0.9f + mote.wobble) * 0.08f
        val distance = core * 1.15f + spread * (mote.orbit + breathing)
        var position = Offset(middle.x + distance * cos(angle), middle.y + distance * sin(angle))
        var gathered = 0f
        if (pulling) {
            // Quadratic falloff as a fraction of the gap: crowd hard near the pointer, only lean at the rim.
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
            color = palette[index % palette.size].copy(alpha = ((0.16f + 0.42f * breath) * twinkle + 0.34f * gathered).coerceAtMost(1f)),
            radius = mote.radius * density * 0.8f * (0.75f + 0.45f * breath) * (1f + 0.6f * gathered),
            center = position,
        )
    }
}

private fun DrawScope.drawArcs(middle: Offset, core: Float, breath: Float, seconds: Float, primary: Color, tertiary: Color) {
    listOf(Triple(1.75f, 11f, primary), Triple(2.25f, -7f, tertiary), Triple(2.85f, 4.5f, primary)).forEachIndexed { index, (scale, degreesPerSecond, tone) ->
        val sweep = Brush.sweepGradient(
            listOf(Color.Transparent, tone.copy(alpha = 0.05f + 0.30f * breath), Color.Transparent, tone.copy(alpha = 0.02f + 0.12f * breath), Color.Transparent),
            center = middle,
        )
        rotate(degrees = seconds * degreesPerSecond + index * 40f, pivot = middle) {
            drawCircle(sweep, radius = core * scale, center = middle, style = Stroke(width = (1.2f + 2.6f * breath) * (3f - index) * density * 0.5f))
        }
    }
}

private fun DrawScope.drawPhaseRing(middle: Offset, core: Float, phase: Float, tone: Color) {
    val radius = core * 3.25f
    val stroke = Stroke(width = 2.5f * density)
    val topLeft = Offset(middle.x - radius, middle.y - radius)
    val box = Size(radius * 2, radius * 2)
    drawArc(tone.copy(alpha = 0.10f), -90f, 360f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
    drawArc(tone.copy(alpha = 0.55f), -90f, 360f * phase.coerceIn(0f, 1f), useCenter = false, topLeft = topLeft, size = box, style = stroke)
}

private fun DrawScope.drawCore(middle: Offset, core: Float, breath: Float, primary: Color) {
    drawCircle(
        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.60f + 0.30f * breath), primary.copy(alpha = 0.72f), primary.copy(alpha = 0.18f)), center = middle, radius = core),
        radius = core,
        center = middle,
    )
    drawCircle(Color.White.copy(alpha = 0.12f + 0.16f * breath), radius = core, center = middle, style = Stroke(width = 1.4f * density))
}

private fun DrawScope.drawRipples(ripples: List<Ripple>, millis: Float, primary: Color) {
    ripples.forEach { ripple ->
        val life = ((millis - ripple.bornMillis) / RippleLifeMillis).coerceIn(0f, 1f)
        val radius = size.minDimension * 0.55f * eased(life)
        val fade = 1f - life
        drawCircle(primary.copy(alpha = 0.28f * fade), radius = radius, center = ripple.origin, style = Stroke(width = (5f * fade + 1f) * density))
        drawCircle(
            Brush.radialGradient(listOf(Color.Transparent, primary.copy(alpha = 0.10f * fade)), center = ripple.origin, radius = radius.coerceAtLeast(1f)),
            radius = radius,
            center = ripple.origin,
        )
    }
}
