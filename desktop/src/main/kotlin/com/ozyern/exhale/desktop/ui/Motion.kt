/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.LaunchedEffect
import com.ozyern.exhale.desktop.data.LocalSettings
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.State
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the listener is moving the interface right now — scrolling, or between pages. Background
 * work that redraws the page (the moving artwork behind it) holds off meanwhile: every one of its
 * frames re-records the whole page for the glass over it, and those are the frames a scroll or a
 * page change can't spare.
 */
object UiMotion {
    @Volatile private var busyUntil = 0L

    val busy: Boolean get() = System.nanoTime() < busyUntil

    /** Something just moved; count the interface as busy for [millis] more. */
    fun poke(millis: Long = 500) {
        busyUntil = System.nanoTime() + millis * 1_000_000L
    }
}

/** The Android app's MotionTokens: tap physics and emphasized springs. */
object Motion {
    const val PressedScale = 0.92f
    val PressSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 520f)

    /** Android's Aquamorphic spring (constants/Dimensions.kt) — the dock, the tiles, the morphs. */
    const val AquamorphicDamping = 0.75f
    const val AquamorphicStiffness = 250f

    /** Home's arrival, from the Android HomeScreen. */
    const val FeedIntroDurationMs = 620
    const val FeedIntroStagger = 0.07f
    const val FeedIntroSlice = 0.55f
    val FeedIntroRise = 16.dp
    const val FeedIntroScale = 0.02f
}

/**
 * One shelf of the page arriving: fades up [Motion.FeedIntroRise] and grows the last two percent
 * into place, offset by its [order] on one shared clock — so the page lands as a page rather than
 * as things popping in. Draw phase only.
 */
fun Modifier.feedIntro(order: Float, progress: () -> Float): Modifier = graphicsLayer {
    val start = (order * Motion.FeedIntroStagger).coerceAtMost(1f - Motion.FeedIntroSlice)
    val raw = ((progress() - start) / Motion.FeedIntroSlice).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(raw)
    alpha = eased
    translationY = (1f - eased) * Motion.FeedIntroRise.toPx()
    val grown = 1f - Motion.FeedIntroScale * (1f - eased)
    scaleX = grown
    scaleY = grown
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/** A touch past the target and back: the curve ColorOS 17's cards arrive on. */
private val CascadeOvershoot = CubicBezierEasing(0.34f, 1.32f, 0.64f, 1f)

/**
 * ColorOS 17's notification stack, for lists: each row rises out of a little depth, overshoots its
 * place by a hair and settles, a beat after the one above. Rows past the first screenful don't wait.
 * Draw phase only.
 */
fun Modifier.cascade(index: Int, progress: () -> Float): Modifier = graphicsLayer {
    val begin = index.coerceAtMost(14) * 0.04f
    val t = ((progress() - begin) / 0.42f).coerceIn(0f, 1f)
    if (t >= 1f) return@graphicsLayer
    val settle = CascadeOvershoot.transform(t)
    alpha = LinearOutSlowInEasing.transform((t * 1.8f).coerceAtMost(1f))
    translationY = (1f - settle) * 34.dp.toPx()
    val s = 0.92f + 0.08f * settle
    scaleX = s
    scaleY = s
    transformOrigin = TransformOrigin(0.5f, 0f)
}

/**
 * A clock for [cascade]. Always settled now: the Music app on the Mac lets a page's content simply be
 * there, and the second-long stagger this used to run kept the whole page redrawing at the very moment
 * its artwork was arriving and going up to the GPU — the first-visit stutter on New and the artist pages.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberCascade(key: Any?, ready: Boolean): () -> Float = SettledCascade

private val SettledCascade: () -> Float = { 1f }

/** ColorOS 17's press: the surface gives at once, with nothing to wobble about… */
private val PressIn = spring<Float>(dampingRatio = 1f, stiffness = 1_400f)

/** …and on release springs back a touch past its rest before settling, the rebound its taps are known for. */
private val PressRelease = spring<Float>(dampingRatio = 0.48f, stiffness = 420f)

/** Hover eases in and out without overshoot; it is a pointer resting, not a tap. */
private val HoverEase = spring<Float>(dampingRatio = 0.9f, stiffness = 380f)

/**
 * The scale a pressable surface draws at, the same everywhere in the app: [pressedScale] while held,
 * [hoverScale] under the pointer, 1 at rest — in on [PressIn], back out on [PressRelease]. Read it in
 * a graphicsLayer so the press costs no recomposition.
 */
@Composable
fun pressScale(pressed: Boolean, hovered: Boolean = false, pressedScale: Float = 0.96f, hoverScale: Float = 1f): State<Float> {
    val scale = remember { Animatable(1f) }
    val reduced = LocalSettings.current.reducedMotion
    val target = when {
        pressed -> pressedScale
        hovered -> hoverScale
        else -> 1f
    }
    LaunchedEffect(target) {
        val spec = when {
            reduced -> snap()
            pressed -> PressIn
            scale.value < 1f -> PressRelease
            else -> HoverEase
        }
        scale.animateTo(target, spec)
    }
    return scale.asState()
}

/** A round control that squishes on press and springs back, with no ripple — glass deforms, it doesn't flash. */
@Composable
fun PressableIcon(
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, pressedScale = Motion.PressedScale)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Play and pause morph into each other with a spin-and-scale instead of swapping. */
@Composable
fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = playing,
        transitionSpec = {
            (scaleIn(spring(0.55f, 420f), initialScale = 0.4f) + fadeIn(tween(120))) togetherWith
                (scaleOut(tween(140), targetScale = 0.4f) + fadeOut(tween(120)))
        },
        label = "play_pause",
    ) { isPlaying ->
        Icon(if (isPlaying) TransportIcons.Pause else TransportIcons.Play, if (isPlaying) "Pause" else "Play", tint = tint, modifier = modifier)
    }
}
