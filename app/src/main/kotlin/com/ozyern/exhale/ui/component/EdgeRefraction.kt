/*
 * Ported from AETHER (github.com/GHOSTxASR/AETHER, GPL-3.0): the glass at the sides of a shelf
 * (its EdgeRefraction and shader) and the step indicator under it. Adapted for Exhale: the theme's
 * ink rather than white, a plain capsule, and the system's reduced-motion setting.
 */

package com.ozyern.exhale.ui.component

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val EDGE_REFRACTION = """
    uniform shader content;
    uniform float2 size;
    uniform float edge;
    uniform float pull;
    uniform float lift;
    uniform float chroma;
    uniform float smear;
    uniform float leftOn;
    uniform float rightOn;

    half4 main(float2 p) {
        float w = size.x;
        float tl = (1.0 - clamp(p.x / edge, 0.0, 1.0)) * leftOn;
        float tr = (1.0 - clamp((w - p.x) / edge, 0.0, 1.0)) * rightOn;
        float t = max(tl, tr);
        if (t <= 0.0) { return content.eval(p); }
        float dir = tl > tr ? 1.0 : -1.0;
        float k = t * t * (3.0 - 2.0 * t);
        float inner = dir > 0.0 ? edge : w - edge;
        float2 q = float2(mix(p.x, inner, k * pull), 0.0);
        float cy = size.y * 0.5;
        q.y = cy + (p.y - cy) / (1.0 + lift * k);
        float ca = chroma * k * dir;
        float bl = smear * k;
        half4 acc = half4(0.0);
        for (int i = -2; i <= 2; i++) {
            acc += content.eval(q + float2(float(i) * bl * 0.5, 0.0));
        }
        acc *= 0.2;
        acc.r = mix(acc.r, content.eval(q + float2(ca, 0.0)).r, 0.2);
        acc.b = mix(acc.b, content.eval(q - float2(ca, 0.0)).b, 0.2);
        acc.rgb += half3(0.05 * k * k) * acc.a;
        return acc;
    }
"""

/** The edge glass for rows of text: the same bending of light, about half as strong. */
@Composable
fun Modifier.gentleEdgeRefraction(state: LazyListState?): Modifier =
    edgeRefraction(state, edgeFraction = 0.13f, pull = 0.38f, liftFraction = 0.08f, chromaDp = 2f, smearDp = 3f)

/**
 * Light bends at the sides of a horizontal list, as through the curved edge of thick glass: what
 * scrolls in or out is magnified, lifted and smeared, with a breath of colour. Only where something
 * is passing: with [state], an edge with nothing beyond it (the start of the list, its end) stays
 * clear, so items at rest are never distorted. Give the list some vertical padding: magnified
 * items grow up to [liftFraction] taller.
 *
 * Nothing on phones without runtime shaders (before Android 13), or with animations turned off.
 */
@Composable
fun Modifier.edgeRefraction(
    state: LazyListState? = null,
    edgeFraction: Float = 0.16f,
    pull: Float = 0.62f,
    liftFraction: Float = 0.16f,
    chromaDp: Float = 3.5f,
    smearDp: Float = 6f,
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    val context = LocalContext.current
    val still = remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    if (still) return this
    val density = LocalDensity.current.density
    val left by animateFloatAsState(if (state == null || state.canScrollBackward) 1f else 0f, tween(260), label = "edgeLeft")
    val right by animateFloatAsState(if (state == null || state.canScrollForward) 1f else 0f, tween(260), label = "edgeRight")
    val shader = remember { RuntimeShader(EDGE_REFRACTION) }
    val cache = remember { arrayOfNulls<Any>(2) }
    return this.graphicsLayer {
        val s = size
        if (s.width <= 0f || s.height <= 0f) return@graphicsLayer
        if (left <= 0.001f && right <= 0.001f) {
            renderEffect = null
            return@graphicsLayer
        }
        // Rebuilt only when the size or an edge's strength changes.
        val key = listOf(s.width, s.height, left, right)
        if (cache[0] != key) {
            shader.setFloatUniform("size", s.width, s.height)
            shader.setFloatUniform("edge", s.width * edgeFraction)
            shader.setFloatUniform("pull", pull)
            shader.setFloatUniform("lift", liftFraction)
            shader.setFloatUniform("chroma", chromaDp * density)
            shader.setFloatUniform("smear", smearDp * density)
            shader.setFloatUniform("leftOn", left)
            shader.setFloatUniform("rightOn", right)
            cache[0] = key
            cache[1] = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        }
        renderEffect = cache[1] as androidx.compose.ui.graphics.RenderEffect
    }
}

/** The indicator's height: its dots and the capsule round them. */
private val StepHeight = 21.dp

/** How many screens a row holds, and where it is among them (0 to pages - 1, between them while moving). */
private class RowPages(val pages: Int, val position: Float)

private fun LazyListState.rowPages(maxPages: Int): RowPages {
    val info = layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return RowPages(0, 0f)
    val span = first.size + info.mainAxisItemSpacing
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    val content = info.beforeContentPadding + info.totalItemsCount * span - info.mainAxisItemSpacing + info.afterContentPadding
    val range = (content - viewport).coerceAtLeast(0)
    if (range <= 0) return RowPages(1, 0f)
    val pages = (ceil(range / viewport.toFloat()).toInt() + 1).coerceIn(2, maxPages)
    val scrolled = when {
        !canScrollForward -> range.toFloat()
        else -> (firstVisibleItemIndex * span + firstVisibleItemScrollOffset).toFloat().coerceIn(0f, range.toFloat())
    }
    return RowPages(pages, scrolled / range * (pages - 1))
}

/**
 * Where a shelf is: pages behind are solid dots, pages ahead are faint, and the page you are on is
 * a bar. When the row comes to rest the bar fills, left to right; swipe on and it eases into a dot
 * as the next one stretches into the bar, on a spring. It follows the finger and never moves the
 * row itself. Nothing shows for a row that fits, but its room is kept.
 */
@Composable
fun StepIndicator(state: LazyListState, modifier: Modifier = Modifier, maxPages: Int = 9) {
    val geometry by remember(state) { derivedStateOf { state.rowPages(maxPages) } }
    val pages = geometry.pages
    if (pages < 2) {
        Spacer(modifier.height(StepHeight))
        return
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val current = geometry.position.roundToInt().coerceIn(0, pages - 1)
    val bar by animateFloatAsState(current.toFloat(), spring(dampingRatio = 0.8f, stiffness = 220f), label = "step")
    val fill = remember { Animatable(0f) }
    val moving = state.isScrollInProgress
    LaunchedEffect(current, moving) {
        if (moving) {
            if (fill.targetValue < 1f || fill.value < 1f) fill.snapTo(0f)
        } else {
            fill.animateTo(1f, spring(dampingRatio = 1f, stiffness = 60f))
        }
    }
    val dot = 5.dp
    val wide = 24.dp
    val gap = 6.dp
    val width = dot * (pages - 1) + wide + gap * (pages - 1)
    Canvas(
        modifier
            .semantics { contentDescription = "Page ${current + 1} of $pages" }
            .background(ink.copy(alpha = 0.07f), CircleShape)
            .border(0.6.dp, ink.copy(alpha = 0.14f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 8.dp)
            .size(width, dot),
    ) {
        val d = dot.toPx()
        val w = wide.toPx()
        val g = gap.toPx()
        val r = CornerRadius(d / 2f)
        var x = 0f
        for (j in 0 until pages) {
            val weight = (1f - abs(bar - j)).coerceIn(0f, 1f)
            val size = d + (w - d) * weight
            val done = j < current
            drawRoundRect(if (done) ink else ink.copy(alpha = 0.26f), Offset(x, 0f), Size(size, d), r)
            if (j == current) {
                val f = fill.value.coerceIn(0f, 1f)
                if (f > 0.002f) drawRoundRect(ink, Offset(x, 0f), Size(d + (size - d) * f, d), r)
            }
            x += size + g
        }
    }
}
