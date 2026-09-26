/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop

/**
 * Play and pause as a drop of glass — the same material as the dock's selection capsule, not a
 * tinted chip with a gradient on it.
 *
 * What makes the dock's capsule read as glass is that it *refracts*: what is behind it bends at the
 * rim and fringes into colour when it moves. So this is a lens over the live backdrop — the moving
 * artwork, the page — with a specular rim, an inner shadow that gives it thickness, and a cast shadow
 * that lifts it off the bar. The accent is a faint stain in the glass, deeper while playing.
 *
 * It answers a press the way liquid glass does: it swells and brightens under the finger, the bend
 * deepens and the colour fringe appears, and it settles back with a little overshoot. (Shrinking on
 * press is what a flat Material button does; a droplet does the opposite.)
 */
@Composable
fun LiquidPlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    accent: Color = MaterialTheme.colorScheme.primary,
    backdrop: Backdrop = LocalAppBackdrop.current,
) {
    val dark = isSystemInDarkTheme()
    var pressed by remember { mutableStateOf(false) }

    // 0 at rest, 1 held — every part of the material reads this, so they all move together.
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = 520f),
        label = "liquidPlayPress",
    )
    val stain by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(320),
        label = "liquidPlayStain",
    )
    val glass = isRuntimeShaderSupported()

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // Swell, not sink.
                val swell = 1f + 0.12f * press
                scaleX = swell
                scaleY = swell
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(0.5f.dp.toPx() + 1.5f.dp.toPx() * press)
                    if (glass) {
                        // Bending most of the way in from the rim: a convex drop, not a flat pane.
                        lens(
                            (size.toPx() * 0.24f) * (0.85f + 0.15f * press),
                            (size.toPx() * 0.50f) * (0.85f + 0.15f * press),
                            true,
                            press > 0.02f,
                        )
                    }
                },
                highlight = {
                    Highlight.Default.copy(alpha = 0.75f + 0.25f * press)
                },
                innerShadow = {
                    InnerShadow(
                        radius = 3f.dp + 4f.dp * press,
                        color = Color.Black.copy(alpha = if (dark) 0.18f else 0.10f),
                        alpha = 0.5f + 0.2f * press,
                    )
                },
                shadow = {
                    Shadow(
                        radius = 10f.dp + 6f.dp * press,
                        color = Color.Black.copy(alpha = if (dark) 0.40f else 0.18f),
                    )
                },
                onDrawSurface = {
                    // The accent, as a stain in the glass: faint at rest, deeper while playing.
                    drawRect(accent.copy(alpha = 0.06f + 0.14f * stain))
                        // Held, it brightens — light gathering in the drop — rather than darkening.
                        drawRect(Color.White.copy(alpha = 0.10f * press))
                    // Light pooling in the top of the drop, the way it does in a real bead of water.
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.14f + 0.16f * press), Color.Transparent),
                            center = Offset(this.size.width * 0.5f, this.size.height * 0.08f),
                            radius = this.size.width * 0.50f,
                        ),
                    )
                    // And a little caught at the bottom, bounced back up through it.
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(this.size.width * 0.5f, this.size.height * 1.02f),
                            radius = this.size.width * 0.45f,
                        ),
                    )
                },
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    pressed = false
                }
            }
            .clickable(
                enabled = !isLoading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        val ink = if (dark) Color.White else Color(0xFF121216)
        if (isLoading) {
            LoadingRing(modifier = Modifier.size(size * 0.40f), color = ink, stroke = 2.dp)
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (
                        fadeIn(tween(120)) + scaleIn(
                            initialScale = 0.5f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        )
                        ).togetherWith(fadeOut(tween(90)) + scaleOut(targetScale = 1.3f, animationSpec = tween(90)))
                },
                label = "liquidPlayIcon",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                    tint = ink,
                    modifier = Modifier
                        .size(size * 0.46f)
                        .graphicsLayer {
                            // The glyph sits in the glass, so it bends a touch with the press too.
                            val give = 1f + 0.06f * press
                            scaleX = give
                            scaleY = give
                        },
                )
            }
        }
    }
}
