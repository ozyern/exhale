/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import com.ozyern.exhale.ui.theme.LocalGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Shared [HazeState] for the app's live backdrop blur. Provided by the root scaffold and
 * read by glass surfaces via [GlassSurface] so panels can blur the content behind them
 * without prop-drilling. Null when live blur isn't wired for the current screen.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * True only where the platform supports hardware [android.graphics.RenderEffect] backdrop
 * blur (Android 12 / API 31+). Below this, glass surfaces fall back to a translucent scrim.
 */
val supportsLiveBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The app's one real liquid-glass modifier for **floating chrome** — the bottom dock, the
 * search capsule. Built on Kyant's AndroidLiquidGlass: it blurs the app content *and* bends
 * it at the edges (`lens`), which is the part a plain blur can never fake and the reason
 * Apple's chrome reads as a physical pane rather than a frosted rectangle.
 *
 * **The default backdrop is chrome-only.** [LocalAppBackdrop] is a recording of the NavHost
 * content, so a component that lives *inside* that content and then consumes it is a re-entrant
 * `GraphicsLayer` draw: the layer would have to draw itself, and it throws on the first frame.
 * (That was the old "Settings crashes on click".) The dock and the search bar are Scaffold slots
 * — siblings drawn *over* the NavHost — so they are safe with the default.
 *
 * In-content callers pass [backdrop] explicitly, and must pass one recorded by something drawn
 * *beneath* them; `LocalPageBackdrop` is that. They get the identical material either way, which
 * is the point of routing everything through this one function: the round controls are made of
 * the same glass as the search bar because they are literally calling the same code with the
 * same numbers, not because two recipes were tuned to look alike.
 *
 * @param tintAlpha opacity of the film laid over the refraction. Higher = milkier, more legible.
 * @param blurRadius blur strength of the pane's interior.
 * @param backdrop the pixels to refract. Defaults to the app content, i.e. chrome.
 * @param layerBlock optional transform applied to the pane itself (not its content) — how a
 *   pressable control squashes its glass without moving the label sitting on it.
 * @param quality retained for source compatibility with the previous Haze implementation;
 *   Kyant renders the effect in one shader pass, so there is no resolution lever to pull.
 */
@Composable
fun rememberChromeGlassModifier(
    shape: Shape,
    dark: Boolean,
    tintAlpha: Float = 0.32f,
    blurRadius: Dp = 48.dp,
    @Suppress("UNUSED_PARAMETER") quality: Float = 0.5f,
    backdrop: Backdrop = LocalAppBackdrop.current,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {

    // Flat tint, hairline highlight, symmetric shadow — deliberately.
    //
    // A later pass "upgraded" this to a vertical gradient film, a two-tone ambient bevel and an
    // offset shadow. All three are textbook-correct and all three made it visibly worse here: the
    // gradient ran a light-to-dark ramp down a pane whose whole job is to be an even sheet, and
    // the dark half of the ambient bevel dragged a grey smear round the bottom of the dock. What
    // reads as clean over this app's artwork is an even film with a bright rim the whole way
    // round. Do not re-derive that change from first principles — it was tried and reverted.
    val tint = if (dark) Color.Black.copy(alpha = tintAlpha) else Color.White.copy(alpha = tintAlpha)
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.16f)

    return Modifier
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                // Order matters: saturate first, then soften, then bend. Bending an
                // already-desaturated blur is what looks like plastic.
                vibrancy()
                blur(blurRadius.toPx() * 0.25f)
                // Refraction reaching ~14dp in from the rim. This is the edge highlight you
                // see wrapping around content as it passes under the pane.
                lens(14f.dp.toPx(), 28f.dp.toPx(), true)
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 14f.dp, color = shadowColor) },
            layerBlock = layerBlock,
            onDrawSurface = { drawRect(tint) },
        )
}

/**
 * Clear liquid glass, on Apple-matched defaults.
 *
 * Where [rememberChromeGlassModifier] frosts — a heavy blur and a milky film, so the pane reads as
 * a sheet of ice — this one barely blurs at all (8dp), saturates what is behind it by half again,
 * and bends it hard at the rim, under a 40% film of near-black (or near-white). The page stays
 * legible *through* the control and the rim does the work of saying "glass". This is the material
 * the round chrome and the dock are made of.
 *
 * Same backdrop rule as [rememberChromeGlassModifier]: [backdrop] must be recorded by something
 * drawn beneath this, never by something this is drawn inside.
 */
@Composable
fun Modifier.clearGlass(
    shape: Shape,
    backdrop: Backdrop = LocalAppBackdrop.current,
    dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val film = if (dark) Color(0xFF121212).copy(alpha = ClearGlassFilm) else Color(0xFFFAFAFA).copy(alpha = ClearGlassFilm)
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8f.dp.toPx())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    lens(24f.dp.toPx(), 24f.dp.toPx(), true)
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(radius = 24f.dp, color = Color.Black.copy(alpha = if (dark) 0.22f else 0.10f)) },
            layerBlock = layerBlock,
            onDrawSurface = { drawRect(film) },
        )
        // The hairline every glass surface keeps on top of the glass's own highlight: on a
        // near-black page the film is the page's colour, and this is the edge that remains.
        .border(0.5.dp, Color.White.copy(alpha = if (dark) 0.10f else 0.35f), shape)
}

private const val ClearGlassFilm = 0.40f

/**
 * The dock's glass, in both of its states: almost no frost, so the page reads straight through,
 * with the colour behind it lifted, a deep bend at the rim and a darker film than [clearGlass].
 * The bar sits over everything, so it has to look like a lens over the page, not a panel on it.
 */
@Composable
fun Modifier.dockGlass(
    shape: Shape,
    backdrop: Backdrop = LocalAppBackdrop.current,
    dark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
): Modifier {
    val film = if (dark) Color(0xFF1A1A1A).copy(alpha = 0.5f) else Color(0xFFFAFAFA).copy(alpha = 0.5f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            colorControls(saturation = 1.6f)
            blur(2f.dp.toPx())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(19f.dp.toPx(), 29f.dp.toPx())
            }
        },
        highlight = { Highlight.Default.copy(width = 0.8f.dp, alpha = 0.3f) },
        shadow = { Shadow() },
        onDrawSurface = { drawRect(film) },
    )
}

/**
 * [clearGlass] for controls inside a page. It refracts the page's own recorded ground where one
 * is published ([com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop]); where none is, it is
 * The lightweight glass — the same film, rim and hairline over nothing — which is what
 * their artwork-page circles are.
 */
@Composable
fun Modifier.lightGlass(shape: Shape): Modifier {
    val backdrop = com.ozyern.exhale.ui.component.liquid.LocalPageBackdrop.current
        ?: com.ozyern.exhale.ui.component.liquid.rememberInContentBackdrop()
    return clearGlass(shape, backdrop)
}

/** Icon colour on [clearGlass]: pure white or black, the only tint that holds over any artwork. */
@Composable
fun clearGlassContentColor(): Color =
    if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black

/**
 * Decorative "frosted glass" surface treatment that works on *every* device: soft shadow,
 * translucent fill, and a hairline light-catching border. This does NOT do live backdrop
 * blur — use [GlassSurface] for that. Ideal for cards/grid items over the liquid background.
 */
fun Modifier.glassSurface(
    shape: Shape? = null,
    tint: Color? = null,
    border: Boolean = true,
): Modifier = composed {
    val glass = LocalGlass.current
    val s = shape ?: RoundedCornerShape(glass.cornerRadius)
    this
        .shadow(
            elevation = glass.shadowElevation,
            shape = s,
            clip = false,
            ambientColor = glass.shadowColor,
            spotColor = glass.shadowColor,
        )
        .clip(s)
        .background(tint ?: glass.fallbackScrim)
        .then(if (border) Modifier.border(glass.borderWidth, glass.borderBrush, s) else Modifier)
}

/**
 * A frosted glass panel with *live* backdrop blur of whatever is drawn behind the current
 * [LocalHazeState] source (Android 12+). On older devices, or when no source is available,
 * it degrades gracefully to a translucent tinted scrim — the layout and content are identical.
 *
 * All Haze-specific API is contained here, so a Haze version bump only touches this function.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalGlass.current.cornerRadius),
    hazeState: HazeState? = LocalHazeState.current,
    tint: Color? = null,
    border: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val useLiveBlur = hazeState != null && supportsLiveBlur

    val base = modifier
        .shadow(
            elevation = glass.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = glass.shadowColor,
            spotColor = glass.shadowColor,
        )
        .clip(shape)

    val filled = if (useLiveBlur) {
        base.hazeEffect(state = hazeState!!) {
            blurRadius = glass.blurRadius
            backgroundColor = glass.blurBackground
            noiseFactor = glass.noiseFactor
        }.background(tint ?: glass.tint)
    } else {
        base.background(tint ?: glass.fallbackScrim)
    }

    Box(
        modifier = filled
            .then(if (border) Modifier.border(glass.borderWidth, glass.borderBrush, shape) else Modifier),
    ) {
        // Soft interior sheen — draw behind content so it never dims text.
        Box(Modifier.matchParentSize().background(glass.highlightBrush))
        content()
    }
}

/**
 * Slowly drifting, multi-layered organic gradient "blobs" that simulate a liquid look
 * behind frosted surfaces. Seed [colors] from the theme (which is already album-art
 * reactive) so the ambient background matches the current song.
 *
 * Cheap: a handful of radial gradients on a single [Canvas]; the soft radial falloff
 * reads as blur without an actual (expensive) full-screen blur pass.
 */
@Composable
fun LiquidBackground(
    colors: List<Color>,
    baseColor: Color,
    modifier: Modifier = Modifier,
    blobAlpha: Float = 0.45f,
) {
    val transition = rememberInfiniteTransition(label = "liquid")
    val twoPi = (2.0 * PI).toFloat()
    val p1 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(19_000, easing = LinearEasing)), label = "p1",
    )
    val p2 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(27_000, easing = LinearEasing)), label = "p2",
    )
    val p3 by transition.animateFloat(
        0f, twoPi, infiniteRepeatable(tween(33_000, easing = LinearEasing)), label = "p3",
    )

    val palette = remember(colors, blobAlpha) {
        val safe = if (colors.size >= 3) colors else (colors + colors + colors).take(3)
        safe.map { it.copy(alpha = blobAlpha) }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(baseColor)
        val w = size.width
        val h = size.height
        val r = maxOf(w, h) * 0.85f

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

        blob(palette[0], w * (0.30f + 0.18f * sin(p1)), h * (0.26f + 0.14f * cos(p1 * 0.9f)), r)
        blob(palette[1], w * (0.74f + 0.16f * sin(p2)), h * (0.42f + 0.16f * cos(p2)), r * 0.9f)
        blob(palette[2], w * (0.48f + 0.22f * cos(p3)), h * (0.82f + 0.12f * sin(p3)), r * 1.1f)
    }
}
