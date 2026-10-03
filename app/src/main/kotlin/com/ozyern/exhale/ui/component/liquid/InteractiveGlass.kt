/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Interactive liquid glass from LastWave-Native (github.com/Clash-Projects/LastWave-native,
 * GPL-3.0): the press that follows the finger, the spring that swells the pane, the glow where
 * it was touched and the lens that deepens under it. Adapted to take Exhale's tint and backdrop.
 */

package com.ozyern.exhale.ui.component.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One pane's press: 0 at rest, springing to 1 under a finger, and where that finger is. */
class GlassInteraction(private val scope: CoroutineScope) {
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val press = Animatable(0f, 0.001f)

    val pressProgress: Float get() = press.value

    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) = with(pointer) {
        inspectDragGestures(
            onDragStart = { down ->
                touchPosition = down.position
                scope.launch { press.animateTo(1f, pressSpec) }
            },
            onDragEnd = { scope.launch { press.animateTo(0f, pressSpec) } },
            onDragCancel = { scope.launch { press.animateTo(0f, pressSpec) } },
        ) { change, _ -> touchPosition = change.position }
    }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * The interactive glass: vibrancy, a blur that thickens a little under the finger, a lens
 * across a quarter of the pane, the [tint] film over it, and — pressed — a white glow centred on
 * the touch while the whole pane swells by [pressedScale]. Observe-only: a button inside keeps its
 * own tap. [backdrop] must be recorded by something drawn beneath this pane.
 */
fun Modifier.interactiveGlass(
    backdrop: Backdrop,
    shape: Shape,
    tint: Color,
    interaction: GlassInteraction?,
    pressedScale: Float = 1.12f,
    highlight: Highlight = Highlight.Default,
): Modifier =
    this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            highlight = { highlight },
            effects = {
                val press = interaction?.pressProgress ?: 0f
                vibrancy()
                blur(8f.dp.toPx() + 2f.dp.toPx() * press)
                lens(size.minDimension / 4f + 2f.dp.toPx() * press, size.minDimension / 2f, false)
            },
            onDrawSurface = {
                drawRect(tint)
                val press = interaction?.pressProgress ?: 0f
                if (press > 0f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.18f * press), Color.Transparent),
                            center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension * 1.2f,
                        ),
                        blendMode = BlendMode.Plus,
                    )
                }
            },
            layerBlock = interaction?.let {
                {
                    val scale = lerp(1f, pressedScale, it.pressProgress)
                    scaleX = scale
                    scaleY = scale
                }
            },
        )
        .then(
            if (interaction != null) Modifier.pointerInput(interaction) { interaction.detectPress(this) } else Modifier,
        )
