/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import com.ozyern.exhale.ui.theme.MotionTokens
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape

/**
 * "the iOS music app" tap physics — scale down on press, spring back on release.
 *
 * Everything runs inside [graphicsLayer], so the animation is GPU-only: it skips the
 * composition and layout phases (never animate size/padding for this).
 *
 * Two entry points depending on whether the caller already owns the click:
 *  - [pressScale]: couple the scale to an existing clickable's [InteractionSource].
 *  - [pressScaleContainer]: self-contained observer for containers whose click/scroll is
 *    handled by a child (e.g. list/grid rows that receive a clickable via their modifier).
 *  - [bounceClick]: all-in-one clickable + scale for new call sites.
 *
 * INSTANT TOUCH: [pressScaleContainer] (and therefore [bounceClick]) drive the scale from a
 * pointer observer on the Initial pass, so it fires on ACTION_DOWN with ZERO delay. This is
 * deliberately NOT tied to `clickable`'s [InteractionSource], because Compose delays the
 * press interaction by ~100 ms inside scrollables to disambiguate tap-vs-scroll — which is
 * exactly the "doesn't respond to every rapid tap" lag we want to eliminate.
 */

/** Couples a press-scale to an existing [interactionSource] (from a clickable/toggleable). */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = MotionTokens.PressedScale,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MotionTokens.PressSpring,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Self-contained press-scale for a container whose actual click/scroll is owned by a child
 * or by a clickable supplied through its modifier.
 *
 * It observes pointer events on the [PointerEventPass.Initial] pass WITHOUT consuming them,
 * so the child's click and any enclosing list scroll keep working. The scale is released on
 * up, on cancel, or as soon as the pointer drags past touch slop — so scrolling a list never
 * leaves a row stuck in the pressed state.
 */
fun Modifier.pressScaleContainer(
    pressedScale: Float = MotionTokens.PressedScale,
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MotionTokens.PressSpring,
        label = "pressScaleContainer",
    )
    this
        .pointerInput(Unit) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    // Released or gesture ended.
                    if (change == null || !change.pressed) break
                    // Treat movement past slop as a scroll/drag — let it take over.
                    if (change.positionChangeIgnoreConsumed().getDistance() > slop) break
                }
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/**
 * All-in-one: INSTANT press physics (scale on ACTION_DOWN) + [combinedClickable] ripple/click.
 * The scale comes from [pressScaleContainer] (Initial-pass observer, no tap-slop delay), while
 * the clickable still provides ripple indication and the click/long-click semantics.
 *
 * ### Always pass [shape] when the target is not a rectangle
 *
 * A ripple is drawn by the indication node and clipped by whatever clip is **above** it in the
 * chain. Every glass surface in this app clips itself, and the idiom at all six call sites was
 *
 * ```
 * Modifier.size(38.dp).bounceClick(onClick).liquidGlassSurface(CircleShape)
 * ```
 *
 * which puts the clip *below* the clickable — so the ripple was never clipped at all. Tapping the
 * account disc washed a hard-edged **square** across a circular button, and the same square landed
 * on every rounded card in the account sheet, including the one whose comment says a stray ripple
 * is the cheapest-looking thing in a glass UI.
 *
 * Clipping inside `bounceClick`, before the indication, fixes it wherever the caller says what
 * shape it is. The default stays null so nothing rectangular pays for a graphics layer it does
 * not need.
 */
@Composable
fun Modifier.bounceClick(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pressedScale: Float = MotionTokens.PressedScale,
    shape: Shape? = null,
): Modifier {
    val source = remember { MutableInteractionSource() }
    return this
        .pressScaleContainer(pressedScale)
        .then(if (shape != null) Modifier.clip(shape) else Modifier)
        .combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
}
