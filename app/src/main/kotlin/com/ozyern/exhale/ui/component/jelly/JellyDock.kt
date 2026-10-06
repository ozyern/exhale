/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The jelly tab bar is ported from NuvioMobile's Android navigation bar (GPL-3.0,
 * github.com/NuvioMedia/NuvioMobile), itself a port of react-native-jelly-tabs
 * (github.com/felipe-software/react-native-jelly-tabs, revision f93c79c):
 *
 *   The MIT License (MIT)
 *   Copyright (c) 2026 Felipe.Software
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 *   and associated documentation files (the "Software"), to deal in the Software without
 *   restriction, including without limitation the rights to use, copy, modify, merge, publish,
 *   distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 *   Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all copies or
 *   substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 *   BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *   NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *   DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.ozyern.exhale.ui.component.jelly

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One tab of the jelly dock. */
class JellyDockItem(
    val label: String,
    val iconActive: Int,
    val iconInactive: Int,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The jelly dock: a glass track whose selected pill is a soft body. Tapping sends the pill across on
 * a spring that stretches it with its speed; holding swells it and lets you drag it between tabs,
 * the whole track leaning after your finger and a light blooming under it; letting go lands it on
 * the nearest tab and only then changes page.
 *
 * Everything is driven by one [JellyMotion] stepped from the frame clock, and only while something
 * is moving, so a resting dock costs no frames.
 *
 * @param showLabels labels under the icons, or icons alone.
 * @param compact a shorter track with smaller icons.
 * @param glow the light under the finger while the pill is held.
 * @param minimized the scrolled-away dock: icons only, a shorter and narrower track, the same tabs.
 */
@Composable
fun JellyDock(
    items: List<JellyDockItem>,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    compact: Boolean = false,
    glow: Boolean = true,
    minimized: Boolean = false,
) {
    if (items.isEmpty()) return
    // The pill and the lit tab are the ink colour, not the theme's accent: a white pill at 15% on
    // smoked glass in dark theme, a black one in light.
    val dark = isSystemInDarkTheme()
    val accent = if (dark) Color.White else Color.Black
    val glowStrength by animateFloatAsState(
        targetValue = if (glow) 1f else 0f,
        animationSpec = tween(420),
        label = "jellyGlow",
    )
    val labelFraction by animateFloatAsState(
        targetValue = if (showLabels && !minimized) 1f else 0f,
        animationSpec = tween(300),
        label = "jellyLabels",
    )
    // Minimised, the track also draws in from both ends, the way it does in the original when the
    // page scrolls: 30dp a side, on the same curve as the labels going.
    val shrink by animateFloatAsState(
        targetValue = if (minimized) 1f else 0f,
        animationSpec = tween(300),
        label = "jellyShrink",
    )
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedIndex = items.indexOfFirst { it.selected }
    val visualSelectedIndex = visualIndex(selectedIndex, items.size, isRtl)
    val motion = remember(items.size, isRtl) { JellyMotion(visualSelectedIndex, items.size) }
    val currentItems by rememberUpdatedState(items)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val density = LocalDensity.current
    val trackHeight = 48.dp + (if (compact) 8.dp else 16.dp) * labelFraction
    val shape = remember { RoundedCornerShape(percent = 50) }
    val selectedSurface = accent.copy(alpha = 0.15f)
    val glowColor = accent.copy(alpha = accent.alpha * glowStrength)

    LaunchedEffect(visualSelectedIndex, items.size) {
        motion.select(visualSelectedIndex)
    }
    LaunchedEffect(motion.running) {
        if (!motion.running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (motion.running) {
            withFrameNanos { now ->
                motion.advance((now - previous) / 1_000_000_000.0)
                previous = now
            }
        }
    }

    // Glass needs runtime shaders; without them the pill is the original's flat tint.
    val glassy = remember { isRuntimeShaderSupported() }
    val appBackdrop = LocalAppBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val pillBackdrop = rememberCombinedBackdrop(appBackdrop, tabsBackdrop)
    var trackSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp * shrink)
            .height(trackHeight)
            .onSizeChanged {
                trackSize = it
                motion.resize(it.width / density.density, it.height / density.density, items.size)
            }
            .pointerInput(motion, density, items.size, isRtl) {
                detectJellyGestures(motion, density.density, { currentItems }, { currentIsRtl })
            },
    ) {
        // Pressed, the whole track swells a little.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = motion.frame.trackScale
                    scaleY = motion.frame.trackScale
                },
        ) {
            // Dragged up or down, it stretches after the finger and narrows from where you hold it.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val frame = motion.frame
                        transformOrigin = TransformOrigin(
                            if (size.width > 0) frame.originX * density.density / size.width else 0.5f,
                            0.5f,
                        )
                        scaleX = frame.trackScaleX
                        translationY = frame.trackOffsetY * density.density
                    },
            ) {
                // Dragged sideways, it is tugged a few dp the same way.
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { translationX = motion.frame.panelOffset * density.density },
                ) {
                    Box(Modifier.matchParentSize().jellyTrack(shape, dark, glowStrength))
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(shape)
                            .drawWithContent {
                                drawContent()
                                drawJellyGlow(motion.frame, glowColor)
                            },
                    )
                    // The resting tabs, with a hole where the pill is so they never show through it.
                    Box(
                        Modifier
                            .matchParentSize()
                            .drawWithContent {
                                if (selectedIndex >= 0) {
                                    clipPath(jellyPillPath(motion.frame, items.size), ClipOp.Difference) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    drawContent()
                                }
                            },
                    ) {
                        JellyTabRow(items, labelFraction, motion, active = false, compact, accent, Modifier.matchParentSize())
                    }
                    if (glassy && selectedIndex >= 0 && trackSize.width > 0) {
                        GlassPill(
                            items = items,
                            motion = motion,
                            trackSize = trackSize,
                            labelFraction = labelFraction,
                            compact = compact,
                            ink = accent,
                            glowColor = glowColor,
                            appBackdrop = appBackdrop,
                            tabsBackdrop = tabsBackdrop,
                            pillBackdrop = pillBackdrop,
                        )
                    } else if (selectedIndex >= 0) {
                    // The pill, and the tabs again in full colour, seen only through it: a tab under
                    // the pill turns accent exactly as far as the pill covers it.
                        Box(
                            Modifier
                                .matchParentSize()
                                .clearAndSetSemantics {}
                                .drawWithContent {
                                    drawJellyPill(motion.frame, items.size, selectedSurface, glowColor) {
                                        drawContent()
                                    }
                                },
                        ) {
                            JellyTabRow(items, labelFraction, motion, active = true, compact, accent, Modifier.matchParentSize())
                        }
                    }
                    JellyTabTargets(items, motion, Modifier.matchParentSize())
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Track                                                                    */
/* ----------------------------------------------------------------------- */

private val TrackFilmDark = Color(0xFF1C1C1E)
private val TrackFilmLight = Color(0xFFF2F2F7)

/**
 * The track: smoked glass. The page behind is blurred hard and laid under a dark film, then lit at
 * the rim — a warm light that falls off a few dp in and is strongest along the top, and a thin
 * bright line on the very edge — so it reads as a slab of tinted glass catching light rather than
 * a flat bar. [strength] fades the rim with the touch-glow setting, as the original does.
 */
@Composable
private fun Modifier.jellyTrack(shape: Shape, dark: Boolean, strength: Float): Modifier {
    val film = if (dark) TrackFilmDark.copy(alpha = 0.55f) else TrackFilmLight.copy(alpha = 0.62f)
    val rim = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { RuntimeShader(JellyRimShader) }.getOrNull()
        } else {
            null
        }
    }
    return this
        .drawBackdrop(
            backdrop = LocalAppBackdrop.current,
            shape = { shape },
            effects = { blur(24f.dp.toPx()) },
            highlight = { null },
            shadow = { null },
            onDrawSurface = { drawRect(film) },
        )
        .drawWithCache {
            val edge = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.27f), Color.White.copy(alpha = 0.02f)),
            )
            val stroke = 0.75.dp.toPx()
            val brush = if (rim != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rim.setFloatUniform("resolution", size.width, size.height)
                rim.setFloatUniform("density", density)
                rim.setFloatUniform("warm", if (dark) 1f else 0.35f)
                ShaderBrush(rim)
            } else {
                null
            }
            onDrawBehind {
                if (strength <= 0f) return@onDrawBehind
                if (brush != null) {
                    drawRect(brush, alpha = strength, blendMode = BlendMode.Plus)
                } else {
                    drawRoundRect(
                        brush = edge,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius((size.height - stroke) / 2),
                        style = Stroke(stroke),
                        alpha = strength,
                    )
                }
            }
        }
}

/**
 * The rim light, as light to add over the track. Depth is measured in dp in from the capsule's
 * edge; the warm sheen decays over ~16dp and is weighted to the top, the highlight is a hairline
 * on the edge itself, brightest at the top and faint along the bottom.
 */
private const val JellyRimShader = """
uniform float2 resolution;
uniform float density;
uniform float warm;

half4 main(float2 position) {
    float2 halfSize = resolution * 0.5;
    float radius = halfSize.y;
    float2 local = position - halfSize;
    float2 capsule = float2(max(abs(local.x) - halfSize.x + radius, 0.0), local.y);
    float distanceToCenter = length(capsule);
    float distanceToEdge = distanceToCenter - radius;
    float coverage = 1.0 - smoothstep(-0.5, 0.5, distanceToEdge);
    if (coverage <= 0.0) return half4(0.0);

    float2 normal = float2(capsule.x * sign(local.x), capsule.y) / max(distanceToCenter, 0.001);
    float depth = max(-distanceToEdge, 0.0) / density;

    float rim = exp(-0.0565 * depth - 0.0322 * depth * depth);
    float upperLight = 0.18 + 0.82 * pow(max(-normal.y, 0.0), 0.65);
    half3 sheen = half3(0.1735, 0.0529, 0.0184) * rim * upperLight * warm;

    float highlight = exp(-pow((depth - 0.35) / 0.42, 2.0));
    float highlightLight = 0.12 + 0.88 * sqrt(max((1.0 - normal.y) * 0.5, 0.0));
    half3 color = sheen + half3(0.25) * highlight * highlightLight;
    color *= coverage;
    return half4(color, max(color.r, max(color.g, color.b)));
}
"""

/* ----------------------------------------------------------------------- */
/* The glass pill                                                           */
/* ----------------------------------------------------------------------- */

/**
 * The pill as liquid glass, in place of the original's flat tint.
 *
 * At rest it is the same white 15% pill with the tab crisp on it, frosted a little. Held, it clears
 * to glass and bends what is under it: a hidden twin of the tab row, drawn bright over a live sample
 * of the page, is recorded into [tabsBackdrop], and the pill reads the page and that twin together
 * through a lens that strengthens with the press — so while you drag, the tabs and the page swim
 * under it rather than being covered by it. It swells, stretches and wobbles with the same jelly
 * motion as before; only its material changed.
 */
@Composable
private fun GlassPill(
    items: List<JellyDockItem>,
    motion: JellyMotion,
    trackSize: IntSize,
    labelFraction: Float,
    compact: Boolean,
    ink: Color,
    glowColor: Color,
    appBackdrop: com.kyant.backdrop.Backdrop,
    tabsBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    pillBackdrop: com.kyant.backdrop.Backdrop,
) {
    val density = LocalDensity.current
    val shape = remember { RoundedCornerShape(percent = 50) }
    val insetPx = with(density) { 4.dp.toPx() }
    val tabWidthPx = (trackSize.width - insetPx * 2) / items.size
    val pillHeightPx = trackSize.height - insetPx * 2
    val tabWidth = with(density) { tabWidthPx.toDp() }
    val pillHeight = with(density) { pillHeightPx.toDp() }

    Box(Modifier.fillMaxWidth().height(with(density) { trackSize.height.toDp() })) {
        // The twin the pill reads. Never seen directly.
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .drawBackdrop(
                    backdrop = appBackdrop,
                    shape = { shape },
                    effects = {
                        val press = motion.frame.press
                        vibrancy()
                        blur(2f.dp.toPx())
                        lens(15f.dp.toPx() * press, 18f.dp.toPx() * press)
                    },
                    highlight = { null },
                    shadow = { null },
                ),
        ) {
            JellyTabRow(items, labelFraction, motion, active = true, compact, ink, Modifier.matchParentSize())
        }

        // The pill.
        Box(
            Modifier
                .graphicsLayer {
                    translationX = insetPx + motion.frame.position * tabWidthPx
                    translationY = insetPx
                }
                .width(tabWidth)
                .height(pillHeight)
                .drawBackdrop(
                    backdrop = pillBackdrop,
                    shape = { shape },
                    effects = {
                        val press = motion.frame.press
                        // Softly frosted at rest; clear, bending glass while held.
                        blur(3f.dp.toPx() * (1f - press))
                        lens(
                            lerp(0f, 10f.dp.toPx(), press),
                            lerp(0f, 12f.dp.toPx(), press),
                            chromaticAberration = press > 0.01f,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = lerp(0.45f, 1f, motion.frame.press)) },
                    shadow = { Shadow(alpha = lerp(0.2f, 0.9f, motion.frame.press)) },
                    innerShadow = {
                        val press = motion.frame.press
                        InnerShadow(radius = 8f.dp * press, alpha = press)
                    },
                    layerBlock = {
                        scaleX = motion.frame.pillScaleX
                        scaleY = motion.frame.pillScaleY
                    },
                    onDrawSurface = {
                        val press = motion.frame.press
                        drawRect(ink.copy(alpha = 0.15f * (1f - 0.7f * press)))
                        if (glowColor.alpha > 0f && press > 0f) {
                            drawRect(
                                Brush.radialGradient(
                                    listOf(glowColor.copy(alpha = 0.12f * press * glowColor.alpha), Color.Transparent),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.maxDimension,
                                ),
                            )
                        }
                    },
                ),
        )

        // The selected tab, crisp on the pill at rest; it gives way to what the glass shows as
        // soon as the pill is pressed.
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .graphicsLayer { alpha = (1f - motion.frame.press * 3f).coerceIn(0f, 1f) }
                .drawWithContent {
                    clipPath(jellyPillPath(motion.frame, items.size)) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            JellyTabRow(items, labelFraction, motion, active = true, compact, ink, Modifier.matchParentSize())
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Tabs                                                                     */
/* ----------------------------------------------------------------------- */

@Composable
private fun JellyTabRow(
    items: List<JellyDockItem>,
    labelFraction: Float,
    motion: JellyMotion,
    active: Boolean,
    compact: Boolean,
    ink: Color,
    modifier: Modifier,
) {
    val color = if (active) ink else ink.copy(alpha = 0.55f)
    val iconSize = if (compact) 24.dp else 28.dp
    val labelHeight = if (compact) 14.dp else 16.dp
    Row(
        modifier = modifier.padding(4.dp).clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val scale = if (active) motion.frame.contentScale else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(if (active) item.iconActive else item.iconInactive),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer { translationY = 2.dp.toPx() * labelFraction },
                    )
                    Box(
                        Modifier
                            .height(labelHeight * labelFraction)
                            .fillMaxWidth()
                            .clipToBounds()
                            .alpha(labelFraction),
                    ) {
                        Text(
                            text = item.label,
                            color = color,
                            style = TextStyle(
                                fontSize = if (compact) 12.sp else 13.sp,
                                lineHeight = if (compact) 14.sp else 16.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Invisible, accessible tap targets laid over the drawn tabs. */
@Composable
private fun JellyTabTargets(
    items: List<JellyDockItem>,
    motion: JellyMotion,
    modifier: Modifier,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(modifier.padding(horizontal = 4.dp).selectableGroup()) {
        items.forEachIndexed { index, item ->
            val visual = visualIndex(index, items.size, isRtl)
            val onClick = {
                motion.select(visual)
                item.onClick()
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(
                        selected = item.selected,
                        role = Role.Tab,
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                    .clearAndSetSemantics {
                        role = Role.Tab
                        selected = item.selected
                        contentDescription = item.label
                        onClick { onClick(); true }
                    },
            )
        }
    }
}

private fun visualIndex(logical: Int, count: Int, isRtl: Boolean): Int =
    if (logical in 0 until count && isRtl) count - 1 - logical else logical

private suspend fun PointerInputScope.detectJellyGestures(
    motion: JellyMotion,
    density: Float,
    currentItems: () -> List<JellyDockItem>,
    isRtl: () -> Boolean,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        motion.begin(down.position.x / density, down.position.y / density)
        var claimed = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                val delta = change.position - down.position
                if (max(abs(delta.x), abs(delta.y)) > viewConfiguration.touchSlop) claimed = true
                if (claimed) change.consume()
                awaitPointerEvent(PointerEventPass.Main)
                if (!motion.dragging) {
                    finished = true
                    break
                }
                // A plain tap belongs to the tab under it, which selects itself.
                if (change.isConsumed && !claimed) break
                motion.drag(delta.x / density, delta.y / density)
                if (!change.pressed) {
                    val visual = motion.finish()
                    val items = currentItems()
                    val logical = visualIndex(visual, items.size, isRtl())
                    finished = true
                    items.getOrNull(logical)?.onClick?.invoke()
                    change.consume()
                    break
                }
            }
        } finally {
            if (!finished) {
                val items = currentItems()
                motion.cancel(visualIndex(items.indexOfFirst { it.selected }, items.size, isRtl()))
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Drawing                                                                  */
/* ----------------------------------------------------------------------- */

private fun DrawScope.drawJellyGlow(frame: JellyFrame, color: Color) {
    if (frame.glowOpacity <= 0f || color.alpha <= 0f) return
    val alpha = 0.15f * frame.glowOpacity * color.alpha
    drawRect(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.45f to color.copy(alpha = alpha * 0.43f),
            1f to color.copy(alpha = 0f),
            center = Offset(frame.originX.dp.toPx(), frame.glowY.dp.toPx()),
            radius = 300.dp.toPx(),
        ),
        topLeft = Offset(-48.dp.toPx(), -16.dp.toPx()),
        size = Size(size.width + 96.dp.toPx(), size.height + 32.dp.toPx()),
    )
}

private fun DrawScope.jellyPillPath(frame: JellyFrame, count: Int): Path {
    val inset = 4.dp.toPx()
    val tabWidth = (size.width - 2 * inset) / count
    val itemHeight = size.height - 2 * inset
    val centerX = inset + (frame.position + 0.5f) * tabWidth
    val halfWidth = tabWidth * frame.pillScaleX / 2
    val halfHeight = itemHeight * frame.pillScaleY / 2
    val radius = min(tabWidth, itemHeight) / 2
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = centerX - halfWidth,
                top = size.height / 2 - halfHeight,
                right = centerX + halfWidth,
                bottom = size.height / 2 + halfHeight,
                cornerRadius = CornerRadius(radius * frame.pillScaleX, radius * frame.pillScaleY),
            ),
        )
    }
}

private fun DrawScope.drawJellyPill(
    frame: JellyFrame,
    count: Int,
    surface: Color,
    glow: Color,
    content: () -> Unit,
) {
    clipPath(jellyPillPath(frame, count)) {
        drawRect(
            color = surface,
            topLeft = Offset(-48.dp.toPx(), -16.dp.toPx()),
            size = Size(size.width + 96.dp.toPx(), size.height + 32.dp.toPx()),
        )
        drawJellyGlow(frame, glow)
        content()
    }
}

/* ----------------------------------------------------------------------- */
/* Motion                                                                   */
/* ----------------------------------------------------------------------- */

private data class JellyFrame(
    val position: Float,
    val pillScaleX: Float = 1f,
    val pillScaleY: Float = 1f,
    val contentScale: Float = 1f,
    val panelOffset: Float = 0f,
    val trackScale: Float = 1f,
    val trackScaleX: Float = 1f,
    val trackOffsetY: Float = 0f,
    val originX: Float = 0f,
    val glowY: Float = 0f,
    val glowOpacity: Float = 0f,
    /** 0 at rest, 1 fully held. */
    val press: Float = 0f,
)

/** Every spring in the dock, advanced together. Units are dp and seconds. */
@Stable
private class JellyMotion(initialIndex: Int, count: Int) {
    private val position = JellySpring(initialIndex.coerceAtLeast(0).toDouble(), 1000.0, 1.0)
    private val velocity = JellySpring(0.0, 300.0, 0.5)
    private val press = JellySpring(0.0, 1000.0, 1.0)
    private val scaleX = JellySpring(1.0, 250.0, 0.6)
    private val scaleY = JellySpring(1.0, 250.0, 0.7)
    private val panel = JellySpring(0.0, 300.0, 1.0)
    private val distortionDamping = 18.0 / (2 * sqrt(240.0 * 0.9))
    private val trackY = JellySpring(0.0, 240.0 / 0.9, distortionDamping)
    private val trackX = JellySpring(1.0, 240.0 / 0.9, distortionDamping)
    private val trackPress = JellySpring(1.0, 240.0 / 0.9, distortionDamping)
    private val glow = JellySpring(0.0, 240.0 / 0.9, distortionDamping)
    private var target = position.value
    private var pressTarget = 0.0
    private var shapeTarget = 1.0
    private var releasePending = false
    private var downX = 0.0
    private var downY = 0.0
    private var dragStartTarget = target
    private var dragStartPanel = 0.0
    private var dragStartY = 0.0
    private var movedDistance = 0.0
    private var originX = 0.0
    private var width = 0.0
    private var height = 64.0
    private var tabCount = count
    private val maxIndex get() = (tabCount - 1).coerceAtLeast(0)
    private val tabWidth get() = ((width - 8) / tabCount.coerceAtLeast(1)).coerceAtLeast(0.0)

    var dragging = false
        private set
    var running by mutableStateOf(false)
        private set
    var frame by mutableStateOf(JellyFrame(position.value.toFloat()))
        private set

    fun resize(width: Float, height: Float, count: Int) {
        this.width = width.toDouble()
        this.height = height.toDouble()
        tabCount = count
        target = target.coerceIn(0.0, maxIndex.toDouble())
        if (!dragging) originX = this.width / 2
        publish()
    }

    fun select(index: Int) {
        dragging = false
        if (index >= 0) target = index.coerceAtMost(maxIndex).toDouble()
        releasePending = true
        pressTarget = 0.0
        shapeTarget = 1.0
        running = true
    }

    fun begin(x: Float, y: Float) {
        downX = x.toDouble()
        downY = y.toDouble().coerceIn(0.0, height)
        originX = downX.coerceIn(0.0, width)
        dragStartY = trackY.value
        movedDistance = 0.0
        if (tabWidth > 0) target = indexAt(downX).toDouble()
        dragStartTarget = target
        dragStartPanel = panel.value
        dragging = true
        releasePending = false
        pressTarget = 1.0
        shapeTarget = 1.3
        panel.velocity = 0.0
        running = true
    }

    fun drag(x: Float, y: Float) {
        if (!dragging || tabWidth <= 0) return
        val dx = x.toDouble()
        val dy = y.toDouble()
        target = (dragStartTarget + dx / tabWidth).coerceIn(0.0, maxIndex.toDouble())
        panel.snapTo(dragStartPanel + dx)
        trackY.snapTo(dragStartY + rubberBand(dy, height) * 0.25)
        trackX.snapTo(1 - (abs(dy) / 700).coerceAtMost(1.0) * 0.08)
        originX = (downX + dx).coerceIn(0.0, width)
        movedDistance = max(movedDistance, max(abs(dx), abs(dy)))
        publish()
    }

    fun finish(): Int {
        val index = if (movedDistance < 4 && tabWidth > 0) indexAt(downX)
        else floor(target + 0.5).toInt().coerceIn(0, maxIndex)
        dragging = false
        panel.velocity = 0.0
        target = index.toDouble()
        releasePending = true
        running = true
        return index
    }

    fun cancel(selectedIndex: Int) {
        dragging = false
        panel.velocity = 0.0
        select(selectedIndex)
    }

    fun advance(seconds: Double) {
        val delta = seconds.coerceIn(0.0, 0.064)
        target = target.coerceIn(0.0, maxIndex.toDouble())
        position.advance(target, delta)
        velocity.advance(if (dragging && maxIndex > 0) position.velocity / maxIndex else 0.0, delta)
        if (!dragging) panel.advance(0.0, delta)
        if (releasePending && abs(position.value - target) < max(1, maxIndex) * 0.025) {
            releasePending = false
            pressTarget = 0.0
            shapeTarget = 1.0
        }
        press.advance(pressTarget, delta)
        scaleX.advance(shapeTarget, delta)
        scaleY.advance(shapeTarget, delta)
        trackPress.advance(if (dragging) 1.025 else 1.0, delta)
        glow.advance(if (dragging) 1.0 else 0.0, delta)
        if (!dragging) {
            trackY.advance(0.0, delta)
            trackX.advance(1.0, delta)
            if (trackX.isAtRest(1.0)) originX = width / 2
        }
        publish()
        running = dragging || releasePending || !position.isAtRest(target) || !velocity.isAtRest(0.0) ||
            !press.isAtRest(0.0) || !scaleX.isAtRest(1.0) || !scaleY.isAtRest(1.0) ||
            !panel.isAtRest(0.0) || !trackY.isAtRest(0.0) || !trackX.isAtRest(1.0) ||
            !trackPress.isAtRest(1.0) || !glow.isAtRest(0.0)
    }

    private fun indexAt(x: Double): Int = floor((x - 4) / tabWidth).toInt().coerceIn(0, maxIndex)

    private fun publish() {
        val speed = velocity.value / 10
        frame = JellyFrame(
            position = position.value.toFloat(),
            pillScaleX = (scaleX.value / (1 - (speed * 0.75).coerceIn(-0.2, 0.2))).toFloat(),
            pillScaleY = (scaleY.value * (1 - (speed * 0.25).coerceIn(-0.2, 0.2))).toFloat(),
            contentScale = (1 + 0.2 * press.value).toFloat(),
            panelOffset = panelOffset(panel.value, width),
            trackScale = trackPress.value.toFloat(),
            trackScaleX = trackX.value.toFloat(),
            trackOffsetY = trackY.value.toFloat(),
            originX = originX.toFloat(),
            glowY = downY.toFloat(),
            glowOpacity = glow.value.toFloat().coerceIn(0f, 1f),
            press = press.value.toFloat().coerceIn(0f, 1f),
        )
    }
}

/** An analytic damped spring, stepped exactly rather than integrated. */
private class JellySpring(
    var value: Double,
    private val stiffness: Double,
    private val dampingRatio: Double,
) {
    var velocity = 0.0

    fun isAtRest(target: Double): Boolean = abs(value - target) < 0.0001 && abs(velocity) < 0.0001

    fun snapTo(target: Double) {
        value = target
        velocity = 0.0
    }

    fun advance(target: Double, seconds: Double) {
        if (isAtRest(target)) {
            snapTo(target)
            return
        }
        val displacement = value - target
        val frequency = sqrt(stiffness)
        if (dampingRatio == 1.0) {
            val decay = exp(-frequency * seconds)
            val coefficient = velocity + frequency * displacement
            value = target + (displacement + coefficient * seconds) * decay
            velocity = (velocity - frequency * coefficient * seconds) * decay
        } else {
            val damping = dampingRatio * frequency
            val damped = frequency * sqrt(1 - dampingRatio * dampingRatio)
            val decay = exp(-damping * seconds)
            val cosine = cos(damped * seconds)
            val sine = sin(damped * seconds)
            val positionCoefficient = (velocity + damping * displacement) / damped
            val velocityCoefficient = (damping * velocity + stiffness * displacement) / damped
            value = target + decay * (displacement * cosine + positionCoefficient * sine)
            velocity = decay * (velocity * cosine - velocityCoefficient * sine)
        }
    }
}

private fun rubberBand(distance: Double, dimension: Double): Double {
    if (distance == 0.0 || dimension <= 0.0) return 0.0
    val damped = (1 - 1 / (abs(distance) * 0.14 / dimension + 1)) * dimension
    return if (distance < 0) -damped else damped
}

private fun panelOffset(rawOffset: Double, width: Double): Float {
    if (width <= 0 || rawOffset == 0.0) return 0f
    val fraction = (rawOffset / width).coerceIn(-1.0, 1.0)
    val x = abs(fraction)
    var low = 0.0
    var high = 1.0
    var parameter = x
    repeat(10) {
        val bezierX = parameter * parameter * (3 * (1 - parameter) * 0.58 + parameter)
        if (bezierX < x) low = parameter else high = parameter
        parameter = (low + high) / 2
    }
    val eased = parameter * parameter * (3 * (1 - parameter) + parameter)
    return ((if (fraction < 0) -4 else 4) * eased).toFloat()
}
