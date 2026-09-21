/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.ozyern.exhale.ui.component.liquid.InteractiveHighlight
import com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop
import kotlin.math.tanh

/**
 * A round control cut from the **same glass as the search bar**. The back arrow, the app-bar
 * search icon and the clear-search "x" are all this one component.
 *
 * ### Same glass, not a matching recipe
 *
 * It calls [rememberChromeGlassModifier] with the search pill's own numbers — `tintAlpha` 0.42
 * dark / 0.38 light, 56dp blur — so the disc and the bar are the identical material by
 * construction. The earlier version had its own thinner, lighter mix, and a control made of
 * *nearly* the same glass as the surface beside it looks worse than one made of something
 * obviously different: the eye reads the near-miss as a cheap copy of the real thing.
 *
 * What that material actually does is refract. `vibrancy` saturates what is behind, `blur`
 * softens it, and `lens` **bends** it in a ring around the rim, so the picture behind the disc
 * visibly warps at its edge and runs straight through the middle. That bending is the whole
 * difference between glass and a frosted circle.
 *
 * ### Which pixels get bent
 *
 * By default the nearest [LocalPageBackdrop] — the ambient field behind the window, or whatever
 * a page has published beneath its own content. Anything composing as *chrome* (the search bar
 * row, the dock) should pass `LocalAppBackdrop.current` for [backdrop] instead: that is the
 * recording of the app content, the same pixels the pill next to it is bending, and it is legal
 * there precisely because chrome is a sibling drawn over the NavHost rather than inside it.
 *
 * Where neither exists there is nothing to bend, so it falls back to [liquidGlassSurface] with a
 * tonal floor.
 *
 * ### The press
 *
 * A liquid-glass control does not tint on touch, it *deforms*. Holding it squeezes the pane down
 * and pulls it toward the finger — `tanh` so that dragging further keeps giving, but with sharply
 * diminishing return, which is what stops it detaching from the touch point. A specular bloom
 * tracks the finger across the surface at the same time ([InteractiveHighlight] draws it with a
 * runtime shader, falling back to a plain wash where one is unavailable). The icon rides along at
 * a shallower scale than the glass, so the pane appears to compress *around* the glyph rather
 * than the two shrinking together as one flat sticker.
 *
 * Everything is a spring, so an interrupted press — tap, drag off, come back — never snaps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    // 44: the size iOS gives every round control in a bar, and the size a thumb lands on first time.
    diameter: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    /** Pixels to refract. Chrome passes `LocalAppBackdrop.current`; content leaves it null. */
    backdrop: Backdrop? = null,
) {
    val dark = isSystemInDarkTheme()
    val source = backdrop ?: LocalPageBackdrop.current
    val animationScope = rememberCoroutineScope()
    val press = remember(animationScope) { InteractiveHighlight(animationScope) }
    val interactionSource = remember { MutableInteractionSource() }

    val glass = if (source != null) {
        rememberChromeGlassModifier(
            shape = CircleShape,
            dark = dark,
            // A light film and a short blur, so the page behind shows *through* the disc and bends
            // at its rim. At 42% and 56dp it was a dark, smeared disc — a button painted grey
            // rather than a piece of glass, which is the one thing this control has to read as.
            tintAlpha = if (dark) 0.16f else 0.20f,
            blurRadius = 20.dp,
            backdrop = source,
            layerBlock = {
                val progress = press.pressProgress
                val squish = lerp(1f, 0.92f, progress)
                scaleX = squish
                scaleY = squish

                // Follow the finger, with diminishing return. A linear follow lets the pane slide
                // out from under the touch on a long drag; tanh keeps the first few pixels honest
                // and then asymptotes, so the glass always stays under the thumb.
                val reach = size.minDimension
                val offset = press.offset
                translationX = reach * 0.18f * tanh(offset.x / reach)
                translationY = reach * 0.18f * tanh(offset.y / reach)
            },
        )
    } else {
        Modifier
            .graphicsLayer {
                val squish = lerp(1f, 0.92f, press.pressProgress)
                scaleX = squish
                scaleY = squish
            }
            .liquidGlassSurface(
                shape = CircleShape,
                base = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
            )
    }

    Box(
        modifier = modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .alpha(if (enabled) 1f else 0.4f)
                .then(glass)
                // Clip AFTER the glass so the shadow it casts is not cut off, but BEFORE the
                // specular so the press bloom is a disc rather than a square flash.
                .clip(CircleShape)
                .then(press.modifier)
                .then(if (enabled) press.gestureModifier else Modifier)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    enabled = enabled,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    // No ripple: the surface already answers the touch by deforming and blooming,
                    // and a Material ripple expanding underneath the specular reads as two
                    // different design languages arguing on the same 40dp of screen.
                    indication = null,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        // Shallower than the pane's 0.92, on purpose. Equal scales look like a
                        // decal being shrunk; a glyph that gives less than the glass around it
                        // looks like something sitting *under* a surface being pressed.
                        val squish = lerp(1f, 0.96f, press.pressProgress)
                        scaleX = squish
                        scaleY = squish
                    },
            )
        }
    }
}
