/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A slow-drifting colour wash for pages built out of glass.
 *
 * Glass needs something behind it. The obvious way to paint this is one `drawWithCache` that
 * composes four radial gradients into a single layer — which is what the Sound Chem screen used to
 * do, and it is fine while it is *still*. The moment you want it to move, that approach has to
 * rebuild every `Brush` and re-run the whole draw on each frame.
 *
 * So each blob is its own composable instead, holding a static radial gradient, and the drift is
 * `translationX` / `translationY` / `scale` inside a `graphicsLayer` lambda. The lambda reads the
 * animation state in the layer phase, so a frame of drift costs no recomposition and no layout —
 * the render thread just moves three already-rasterised layers. That is what makes a full-screen
 * ambient animation affordable at all.
 *
 * @param animated when false the blobs sit at their rest positions. Wired to the app's
 *   "disable blur" preference, which is the switch people reach for on weak hardware.
 */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme

    // One phase drives all three blobs; each reads it at its own offset and its own rate, which is
    // what stops the drift from looking like a single rigid object sliding around.
    val phase: State<Float> = rememberAmbientPhase(cycleMs = 26_000, running = animated)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        val span = maxWidth

        AuroraBlob(
            color = scheme.primary,
            size = span * 1.15f,
            bias = BiasAlignment(-0.75f, -0.95f),
            phase = phase,
            phaseOffset = 0f,
            peakAlpha = 0.40f,
        )
        AuroraBlob(
            color = scheme.tertiary,
            size = span * 1.05f,
            bias = BiasAlignment(0.9f, -0.6f),
            phase = phase,
            phaseOffset = 0.37f,
            peakAlpha = 0.32f,
        )
        AuroraBlob(
            color = scheme.secondary,
            size = span * 1.25f,
            bias = BiasAlignment(-0.2f, -0.05f),
            phase = phase,
            phaseOffset = 0.71f,
            peakAlpha = 0.26f,
        )

        // Ground the lower half. Without this the blobs keep tinting content all the way down the
        // page and long lists start to look washed out rather than lit.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            scheme.surface.copy(alpha = 0.55f),
                            scheme.surface,
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    ),
                ),
        )
    }
}

/** One soft blob: a static radial gradient on a layer that drifts. */
@Composable
private fun BoxScope.AuroraBlob(
    color: Color,
    size: Dp,
    bias: BiasAlignment,
    phase: State<Float>,
    phaseOffset: Float,
    peakAlpha: Float,
) {
    val brush = Brush.radialGradient(
        colors = listOf(
            color.copy(alpha = peakAlpha),
            color.copy(alpha = peakAlpha * 0.55f),
            color.copy(alpha = peakAlpha * 0.22f),
            Color.Transparent,
        ),
    )

    Box(
        modifier = Modifier
            .align(bias)
            .size(size)
            .graphicsLayer {
                val t = (phase.value + phaseOffset) * 2f * PI.toFloat()
                val amplitude = 44.dp.toPx()
                translationX = sin(t) * amplitude
                translationY = cos(t * 0.7f) * amplitude * 0.65f
                val breathe = 1f + 0.07f * sin(t * 1.3f)
                scaleX = breathe
                scaleY = breathe
            }
            .background(brush),
    )
}
