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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * True when glass is drawn as still frosted plates: Liquid glass switched off, Lite graphics (an older
 * PC, see [com.ozyern.exhale.desktop.data.Graphics]), or a bench run that measures without it.
 */
fun glassOff(): Boolean =
    !AppSettings.current.liquidGlass || com.ozyern.exhale.desktop.data.Graphics.lite || com.ozyern.exhale.desktop.ui.Bench.off("glass")

/**
 * Records the page into [backdrop] for glass to bend — unless no glass will read it, in which case the
 * recording, a second full draw of the page every frame, is skipped.
 */
fun Modifier.recordBackdrop(backdrop: LayerBackdrop): Modifier = if (glassOff()) this else layerBackdrop(backdrop)

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
    if (glassOff()) return glassPlate(shape, base = Color(0xE6141014))
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
    if (glassOff()) return background(Color(0xF0141014))
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

/** The sidebar pane's corners: the Music app's floating sidebar, rounded inside the window's own. */
val SidebarShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/**
 * The Music app's sidebar: a pane of glass floating inside the window, inset from its edges, with
 * rounded corners, a hairline rim and a soft shadow lifting it off the page. What's behind is
 * saturated and heavily blurred under a neutral graphite, so the frost reads clean rather than tinted.
 */
fun Modifier.sidebarGlass(backdrop: Backdrop): Modifier {
    val rim = border(1.dp, Color.White.copy(alpha = 0.10f), SidebarShape)
    if (glassOff()) return background(Color(0xF5222224), SidebarShape).then(rim)
    val tint = Color(0xFF2A2A2D).copy(alpha = 0.46f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { SidebarShape },
        effects = {
            vibrancy()
            blur(40f.dp.toPx())
        },
        highlight = null,
        shadow = { Shadow(radius = 18.dp, color = Color.Black.copy(alpha = 0.32f)) },
        onDrawSurface = { drawRect(tint) },
    ).then(rim)
}

/**
 * The blur at the foot of the sidebar, under you: the rows scrolling beneath are recorded in
 * [backdrop] and drawn back here blurred. Feathered at the top by [featherHeight], so the blur
 * comes on gradually instead of starting at a hard line.
 */
fun Modifier.progressiveBlur(backdrop: Backdrop, featherHeight: Dp, blurRadius: Dp = 10.dp): Modifier = if (glassOff()) this else
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val feather = featherHeight.toPx().coerceAtMost(size.height)
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, (feather / size.height) to Color.Black, 1f to Color.Black),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
        .drawBackdrop(
            backdrop = backdrop,
            shape = { FlatShape },
            effects = { blur(blurRadius.toPx()) },
            highlight = null,
            shadow = null,
        )

/**
 * Fades what's drawn out toward the bottom: fully there down to [fadeEnd] minus [fadeLength], gone
 * by [fadeEnd] above the bottom edge. The sharp copy of the sidebar's rows, under the blurred one.
 */
fun Modifier.fadeOutBottom(fadeLength: Dp, fadeEnd: Dp): Modifier =
    graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val end = (size.height - fadeEnd.toPx()).coerceAtLeast(1f)
            val start = (end - fadeLength.toPx()).coerceAtLeast(0f)
            drawRect(
                Brush.verticalGradient(0f to Color.Black, (start / size.height) to Color.Black, (end / size.height) to Color.Transparent, 1f to Color.Transparent),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }

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
    backdrop: LayerBackdrop = rememberLayerBackdrop(),
    background: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier) {
        Box(Modifier.matchParentSize().recordBackdrop(backdrop), content = background)
        CompositionLocalProvider(LocalPageBackdrop provides backdrop) {
            Box(Modifier.fillMaxSize(), content = content)
        }
    }
}
