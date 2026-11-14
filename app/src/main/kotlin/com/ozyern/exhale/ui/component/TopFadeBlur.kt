/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.takeOrElse
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.constants.TopBarBlurStyle
import com.ozyern.exhale.constants.TopBarBlurStyleKey
import com.ozyern.exhale.ui.component.backdrop.Backdrop
import com.ozyern.exhale.ui.component.backdrop.BackdropEffectScope
import com.ozyern.exhale.ui.component.backdrop.drawPlainBackdrop
import com.ozyern.exhale.ui.component.backdrop.effects.runtimeShaderEffect
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.rememberEnumPreference
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Distance below the bar over which the blur fades to clear. */
private val FADE_RUN = 64.dp

/** Start inset reserved for the floating side bar; display cutout insets are too small to count. */
@Composable
private fun sideBarStartInset(): Dp =
    com.ozyern.exhale.LocalPlayerAwareWindowInsets.current.asPaddingValues()
        .calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        .let { if (it >= SideBarCollapsedWidth) it else 0.dp }

/** Half-width of the horizontal fade centred on the side bar's edge. */
private val SIDE_FADE_RUN = 28.dp

/** Peak opacity of the readability scrim at the top of the strip. */
private const val SCRIM_PEAK = 0.42f

private const val SCRIM_STOPS = 12

private const val MASK_STOPS = 16

/** Blurs the top bar and fades the blur to clear below it, with a readability scrim. */
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalHazeApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
    scrimColor: Color = pageColor,
    // Use this for strips that do not include the status bar.
    heightOverride: Dp? = null,
    /** Optional app backdrop. Use only when this strip is outside its recorded subtree. */
    backdrop: Backdrop? = null,
    /** Width of the side bar docked at the start edge; the strip fades out before it instead of ending abruptly. */
    startFade: Dp = sideBarStartInset(),
) {
    val reduceDynamicBlur by rememberPreference(ReduceDynamicBlurKey, false)
    // The caller uses a solid bar when dynamic blur is disabled.
    if (reduceDynamicBlur) return

    val blurStyle by rememberEnumPreference(TopBarBlurStyleKey, TopBarBlurStyle.HAZE_PROGRESSIVE)
    val barHeight = if (heightOverride != null) 0.dp else topBarHeight()
    val height = heightOverride ?: (barHeight + FADE_RUN)
    val solid = (barHeight / height).coerceIn(0f, 0.9f)

    val mask = remember(solid) {
        Brush.verticalGradient(
            colorStops = Array(MASK_STOPS) { i ->
                val t = i / (MASK_STOPS - 1f)
                val u = ((t - solid) / (1f - solid)).coerceIn(0f, 1f)
                t to Color.Black.copy(alpha = 1f - u * u * (3f - 2f * u))
            },
        )
    }

    val density = LocalDensity.current
    val inputScale = remember(blurStyle) {
        HazeInputScale.Fixed(if (blurStyle == TopBarBlurStyle.HAZE_PROGRESSIVE) 0.5f else 0.25f)
    }
    val progressiveBlur = remember(height, solid, density) {
        HazeProgressive.verticalGradient(
            startIntensity = 1f,
            endIntensity = 0f,
            easing = EaseOutCubic,
            // API 31–32 use a mask instead of Haze's expensive stacked blur layers.
            preferPerformance = true,
        )
    }
    val scrim = remember(scrimColor) { scrimBrush(scrimColor) }
    val sideFade = if (startFade > 0.dp) {
        Modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val end = startFade.toPx()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        ((end - SIDE_FADE_RUN.toPx()) / size.width).coerceAtLeast(0f) to Color.Transparent,
                        ((end + SIDE_FADE_RUN.toPx()) / size.width).coerceAtMost(1f) to Color.Black,
                        1f to Color.Black,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
    } else {
        Modifier
    }
    Box(modifier.fillMaxWidth().height(height).then(sideFade)) {
        if (blurStyle == TopBarBlurStyle.SINGLE_PASS && backdrop != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            DirectTopFadeBlur(backdrop, pageColor, scrimColor, height, solid, Modifier.graphicsLayer())
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer()
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin(pageColor)) {
                        // Haze takes a progressive ramp or a mask, not both: setting both draws nothing.
                        if (blurStyle == TopBarBlurStyle.HAZE_PROGRESSIVE) {
                            progressive = progressiveBlur
                        } else {
                            this.mask = mask
                        }
                        noiseFactor = 0f
                        this.inputScale = inputScale
                    },
            )
            Box(Modifier.matchParentSize().background(scrim))
        }
    }
}

private fun scrimBrush(scrimColor: Color) = Brush.verticalGradient(
    colorStops = Array(SCRIM_STOPS) { i ->
        val t = i / (SCRIM_STOPS - 1f)
        t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
    },
)

/** Direct-backdrop variant: blur and fade in one shader pass, then draw tint and scrim gradients. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun DirectTopFadeBlur(
    backdrop: Backdrop,
    pageColor: Color,
    scrimColor: Color,
    height: Dp,
    solid: Float,
    modifier: Modifier,
) {
    val material = HazeMaterials.ultraThin(pageColor)
    val blurFullPx = with(LocalDensity.current) { material.blurRadius.takeOrElse { 24.dp }.toPx() }
    // Quarter-resolution capture keeps the lightly blurred lower edge from looking blocky.
    val scale = TopFadeScale
    val sigmaPx = blurFullPx * scale
    val effects: BackdropEffectScope.() -> Unit = remember(sigmaPx, solid) {
        {
            runtimeShaderEffect("TopFadeVariableBlur", TopFadeVariableBlurShader, "content") {
                setFloatUniform("size", size.width, size.height)
                setFloatUniform("sigmaMax", sigmaPx)
                setFloatUniform("solid", solid)
            }
        }
    }
    val shapeBlock = remember { { RectangleShape } }
    val tint = material.tints.firstOrNull()?.color ?: Color.Unspecified
    val tintBrush = remember(tint, solid) {
        if (tint == Color.Unspecified) null else Brush.verticalGradient(
            colorStops = Array(MASK_STOPS) { i ->
                val t = i / (MASK_STOPS - 1f)
                t to tint.copy(alpha = tint.alpha * fadeMask(t, solid))
            },
        )
    }
    val scrim = remember(scrimColor) { scrimBrush(scrimColor) }
    // Vary the blur radius by vertical position, then fade to the sharp page at the bottom.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = shapeBlock,
                effects = effects,
                backdropScale = scale,
            )
            .then(if (tintBrush != null) Modifier.background(tintBrush) else Modifier)
            .background(scrim),
    )
}

/** Smoothstep fade: 1 through the solid band, easing to 0 at the strip's foot. */
private fun fadeMask(t: Float, solid: Float): Float {
    val u = ((t - solid) / (1f - solid)).coerceIn(0f, 1f)
    return 1f - u * u * (3f - 2f * u)
}

/** Capture scale of [DirectTopFadeBlur]; the shader's sigma is in these scaled pixels. */
private const val TopFadeScale = 0.25f

/** Single-pass variable Gaussian blur with 40 noise-rotated samples and dithering to reduce banding. */
private const val TopFadeVariableBlurShader = """
uniform shader content;
uniform float2 size;
uniform float sigmaMax;
uniform float solid;

float ign(float2 p) {
    return fract(52.9829189 * fract(dot(p, float2(0.06711056, 0.00583715))));
}

half4 main(float2 c) {
    float t = clamp(c.y / size.y, 0.0, 1.0);
    float u = clamp((t - solid) / (1.0 - solid), 0.0, 1.0);
    float e = u * u * (3.0 - 2.0 * u);
    float s = sigmaMax * (1.0 - e);
    // Opacity holds until halfway down the ramp, then fades into the sharp page.
    float v = clamp((u - 0.5) / 0.5, 0.0, 1.0);
    float a = 1.0 - v * v * (3.0 - 2.0 * v);
    if (a <= 0.0) return half4(0.0);
    half4 col;
    if (s < 0.3) {
        col = content.eval(c);
    } else {
        float rot = ign(c) * 6.2831853;
        float4 acc = float4(0.0);
        for (int i = 0; i < 40; i++) {
            float k = (float(i) + 0.5) / 40.0 * 0.985;
            float r = s * sqrt(-2.0 * log(1.0 - k));
            float ang = rot + float(i) * 2.3999632;
            float2 q = clamp(c + r * float2(cos(ang), sin(ang)), float2(0.5), size - 0.5);
            acc += float4(content.eval(q));
        }
        col = half4(acc / 40.0);
    }
    col *= half(a);
    col.rgb += half((ign(c + 17.0) - 0.5) / 255.0) * col.a;
    return col;
}"""
