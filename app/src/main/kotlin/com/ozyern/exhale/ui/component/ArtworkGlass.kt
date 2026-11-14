/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.R

/**
 * The software update page's artwork, edge to edge, recorded into [backdrop] so the cards laid
 * over it can be frosted glass: the picture behind each one really blurred, as ColorOS's update
 * and notes pages do it, rather than a dark film that only hides it.
 */
@Composable
fun UpdateArtwork(backdrop: LayerBackdrop, modifier: Modifier = Modifier, dim: Float = 0f) {
    Box(modifier.fillMaxSize().background(Color.Black).layerBackdrop(backdrop)) {
        Image(
            painter = painterResource(R.drawable.exhale_update_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f + dim),
                        0.4f to Color.Black.copy(alpha = dim),
                        1f to Color.Black.copy(alpha = 0.25f + dim),
                    ),
                ),
        )
    }
}

/**
 * Frosted glass over [backdrop]: what is behind the pane blurred to colour, its saturation
 * lifted, a film of [tint] over it and a bright hairline round the rim.
 *
 * Where the phone cannot run the shader the glass is made with, the film alone, heavier: the
 * card as it was before it was glass.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: LayerBackdrop?,
    shape: Shape,
    tint: Color = Color(0xFF17131D).copy(alpha = 0.46f),
    blur: Dp = 30.dp,
    rim: Float = 0.16f,
): Modifier {
    val live = backdrop != null && remember { com.kyant.backdrop.isRuntimeShaderSupported() }
    val pane = if (live) {
        drawBackdrop(
            backdrop = backdrop!!,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blur.toPx())
            },
            highlight = { Highlight.Default.copy(alpha = 0.45f) },
            shadow = { Shadow(alpha = 0.28f) },
            onDrawSurface = { drawRect(tint) },
        )
    } else {
        clip(shape).background(tint.copy(alpha = (tint.alpha + 0.34f).coerceAtMost(0.92f)))
    }
    return pane.border(0.8.dp, Color.White.copy(alpha = rim), shape)
}
