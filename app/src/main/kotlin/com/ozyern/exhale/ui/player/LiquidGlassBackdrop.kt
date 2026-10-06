/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.ui.utils.highRes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deep blur radius for the artwork plate — heavy enough that no album detail survives. */
private val LiquidBlurRadius = 100.dp

/**
 * The full player's "Liquid Glass" backdrop: the current album art rendered as glowing, moving
 * liquid colour behind the frosted controls.
 *
 * Three layers, cheapest first:
 *
 *  1. **Artwork plate** — the cover, over-scaled and blurred by [LiquidBlurRadius]. Over-scaling
 *     is what lets us use the cheap clamped edge treatment: the real edges sit outside the
 *     viewport, so nothing has to be sampled beyond them. This layer is static per song, so its
 *     RenderEffect result is retained by the render node and the blur is paid for once, not
 *     every frame.
 *  2. **Mesh gradient** — three slowly drifting radial blobs seeded from the palette already
 *     extracted from the cover. Deliberately *not* blurred: a radial gradient's falloff is
 *     already smoother than any blur kernel, so blurring it would cost a full-screen RenderEffect
 *     on an animating layer (the one thing that genuinely drops frames) and change nothing you
 *     can see. Drawn in `drawBehind` so the 20–30s drift never touches composition.
 *  3. **Legibility scrim** — a vertical gradient so the controls keep contrast against whatever
 *     colours the album happens to throw up.
 *
 * When [disableBlur] is set (user preference / low-end devices) the deep plate is skipped and the
 * mesh alone carries the look — still liquid, just without the expensive pass.
 *
 * When [sharpArtwork] is set the cover is the *foreground*, not a backdrop: it is drawn full-bleed
 * and completely untouched — no blur, no over-scan, no mesh tint, no alpha knock-down. Only the
 * legibility scrim sits on top of it. See the note on [sharpArtwork].
 */
@Composable
fun LiquidGlassArtworkBackdrop(
    thumbnailUrl: String?,
    meshColors: List<Color>,
    disableBlur: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    /** Extra scrim darkness at the bottom, where the controls sit. */
    bottomScrimAlpha: Float = 0.86f,
    blurRadius: Dp = LiquidBlurRadius,
    /**
     * Draw the cover crisp and full-bleed instead of as a blurred plate.
     *
     * The blurred plate + drifting mesh is a *background* treatment: it exists to sit behind a
     * separate, sharp thumbnail. Designs that have no separate thumbnail (V8) show the cover
     * itself as the hero image, and running it through [blur] there turns the album art into an
     * unrecognisable smudge. In that case the artwork is foreground and must stay untouched:
     * the plate and the mesh are skipped entirely (they would be fully occluded by a
     * [ContentScale.Crop] image anyway, so this also saves a full-screen RenderEffect per song).
     */
    sharpArtwork: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = thumbnailUrl,
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) },
            label = label,
        ) { artworkUrl ->
            // The high-res rewrite asks YouTube for `maxresdefault.jpg`, which many videos don't
            // have: that request 404s and the player was left with no cover at all, though the
            // mini player beside it showed one. On failure, fall back to the URL as given.
            var highResFailed by remember(artworkUrl) { mutableStateOf(false) }
            val model = artworkUrl?.let { if (highResFailed) it else it.highRes() }
            val onArtState: (coil3.compose.AsyncImagePainter.State) -> Unit = {
                if (it is coil3.compose.AsyncImagePainter.State.Error && !highResFailed) highResFailed = true
            }
            if (artworkUrl == null) {
                Box(Modifier.fillMaxSize().background(Color.Black))
            } else if (sharpArtwork) {
                // FOREGROUND album art. No .blur(), no RenderEffect, no graphicsLayer alpha —
                // deliberately a bare AsyncImage so nothing can soften it. Do not add one here.
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = onArtState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onState = onArtState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // 1.45x: at a 100dp radius the blur reaches ~150px past every edge,
                            // and un-scaled art would smear its own border across the screen.
                            val overscan = if (disableBlur) 1.06f else 1.45f
                            scaleX = overscan
                            scaleY = overscan
                            alpha = if (disableBlur) 0.72f else 0.95f
                        }
                        .then(if (disableBlur) Modifier else Modifier.blur(blurRadius)),
                )
            }
        }

        // Background-only decoration: tinting the sharp cover would wash out exactly the detail
        // we just went to the trouble of preserving.
        if (!sharpArtwork) {
            LiquidMeshGradient(
                colors = meshColors,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black.copy(alpha = 0.20f),
                            0.32f to Color.Transparent,
                            0.62f to Color.Black.copy(alpha = 0.26f),
                            1f to Color.Black.copy(alpha = bottomScrimAlpha),
                        )
                    )
                )
        )
    }
}

/**
 * The drifting colour mesh. Three radial blobs on independent, mutually prime periods so the
 * composite never visibly loops.
 *
 * Everything animated is read inside `drawBehind`, which puts the whole thing in the draw phase:
 * the infinite transition invalidates a draw command list, never a composition or a layout pass.
 * That is the difference between this being free and it costing 3 frames a second forever.
 */
@Composable
private fun LiquidMeshGradient(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    blobAlpha: Float = 0.42f,
) {
    if (colors.isEmpty()) return

    val palette = remember(colors, blobAlpha) {
        val safe = if (colors.size >= 3) colors.take(3) else List(3) { colors[it % colors.size] }
        safe.map { it.copy(alpha = blobAlpha) }
    }

    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "liquidMesh")
    val twoPi = (2.0 * PI).toFloat()
    val p1 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(23_000, easing = LinearEasing)), label = "mesh1",
    )
    val p2 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(31_000, easing = LinearEasing)), label = "mesh2",
    )
    val p3 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(37_000, easing = LinearEasing)), label = "mesh3",
    )

    val phases = rememberUpdatedState(Triple(p1, p2, p3))

    Box(
        modifier = modifier.drawBehind {
            val (a, b, c) = phases.value
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.92f

            fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                val center = Offset(cx, cy)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0f)),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }

            blob(palette[0], w * (0.28f + 0.20f * sin(a)), h * (0.24f + 0.16f * cos(a * 0.9f)), r)
            blob(palette[1], w * (0.76f + 0.18f * sin(b)), h * (0.44f + 0.18f * cos(b)), r * 0.88f)
            blob(palette[2], w * (0.46f + 0.24f * cos(c)), h * (0.80f + 0.14f * sin(c)), r * 1.08f)
        }
    )
}
