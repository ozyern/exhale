/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.desktop.data.AppSettings

/**
 * Recording of the page content, for chrome that floats *over* it (the rail, the player bar).
 * Content must never sample this — a layer cannot draw itself. Same rule as the Android app.
 */
val LocalAppBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Recording of what is painted *behind* a page's content, for glass inside that content. */
val LocalPageBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/**
 * The Android app's liquid glass (`rememberChromeGlassModifier`), same recipe and numbers:
 * saturate, soften, then bend the rim with a lens. Dark only — the desktop app has no light theme yet.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    backdrop: Backdrop,
    tintAlpha: Float = 0.32f,
    blurRadius: Dp = 48.dp,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    // Appearance → Liquid glass off: a frosted plate with enough body to read over anything.
    if (!AppSettings.current.liquidGlass || com.ozyern.exhale.desktop.ui.Bench.off("glass")) return glassPlate(shape, base = Color(0xE6141014))
    val tint = Color.Black.copy(alpha = tintAlpha)
    val shadowColor = Color.Black.copy(alpha = 0.45f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx() * 0.25f)
            lens(14f.dp.toPx(), 28f.dp.toPx(), true)
        },
        highlight = { Highlight.Default },
        shadow = { Shadow(radius = 14f.dp, color = shadowColor) },
        layerBlock = layerBlock,
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * A flat frosted pane, as macOS draws a sidebar: what's behind is saturated and blurred, then tinted
 * — no lens at the rim, no highlight, no shadow, because the pane is part of the window, not on it.
 */
fun Modifier.frostedPane(backdrop: Backdrop, tintAlpha: Float = 0.5f, blurRadius: Dp = 64.dp): Modifier {
    if (!AppSettings.current.liquidGlass || com.ozyern.exhale.desktop.ui.Bench.off("glass")) return background(Color(0xF0141014))
    val tint = Color.Black.copy(alpha = tintAlpha)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { FlatShape },
        effects = {
            vibrancy()
            blur(blurRadius.toPx() * 0.25f)
        },
        highlight = null,
        shadow = null,
        onDrawSurface = { drawRect(tint) },
    )
}

private val FlatShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp)

/**
 * The tonal glass plate for cards and placeholders, where there is nothing worth refracting:
 * gradient fill, diagonal sheen, bright rim. Port of the Android `liquidGlassSurface` (dark branch).
 */
fun Modifier.glassPlate(shape: Shape, base: Color = Color.Unspecified): Modifier {
    val fill = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.045f)))
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.11f),
        0.42f to Color.Transparent,
        1f to Color.Transparent,
    )
    val rim = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f)))
    return clip(shape)
        .then(if (base != Color.Unspecified) Modifier.background(base) else Modifier)
        .background(fill)
        .background(sheen)
        .border(1.dp, rim, shape)
}

/**
 * Paints [background] into a layer and draws [content] over it as a sibling, publishing that layer
 * as [LocalPageBackdrop] — so glass inside the page refracts the page's own ground.
 */
@Composable
fun PageBackdropHost(
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop), content = background)
        CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
