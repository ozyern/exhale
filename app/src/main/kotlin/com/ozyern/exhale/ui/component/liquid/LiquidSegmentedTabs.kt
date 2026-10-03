/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Built on the LiquidBottomTabs sample from Kyant0/AndroidLiquidGlass
 * (https://github.com/Kyant0/AndroidLiquidGlass), Copyright 2025 Kyant0, Apache License 2.0.
 */

package com.ozyern.exhale.ui.component.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * A segmented control made of liquid glass: equal options on one capsule, with a glass thumb that
 * can be tapped across or picked up and dragged.
 *
 * Held, the thumb swells past the capsule's edges and refracts the label under it, stretching
 * with its speed as it moves; the whole capsule is tugged a few dp in the direction of the drag.
 * Let go and it settles on the nearest option, which is only then reported, so a drag that changes
 * its mind selects nothing.
 *
 * The accent-coloured label seen inside the thumb is a hidden twin of the row read through the
 * glass, so the lens bends the label itself rather than painting a highlight over it.
 */
@Composable
fun LiquidSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    backdrop: Backdrop = LocalPageBackdrop.current ?: rememberInContentBackdrop(),
) {
    val count = labels.size
    if (count == 0) return

    val dark = isSystemInDarkTheme()
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val container = if (dark) Color(0xFF121212).copy(alpha = 0.4f) else Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val shape = remember { RoundedCornerShape(percent = 50) }
    val innerHeight = height * 0.875f
    // Four to a phone's width leaves about 80dp each, which a ten-letter word only fits a size down.
    val labelSize = if (count >= 4) 13.sp else 15.sp
    val tabsBackdrop = rememberLayerBackdrop()
    val onSelectState by rememberUpdatedState(onSelect)

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val tabWidth = with(density) { (widthPx - 8f.dp.toPx()) / count }
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val scope = rememberCoroutineScope()

        val tug = remember { mutableFloatStateOf(0f) }
        val tugRelease = remember { arrayOfNulls<Job>(1) }
        val panelOffset by remember(density, widthPx) {
            derivedStateOf {
                val fraction = (tug.floatValue / widthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val thumb = remember(scope, count) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedIndex.coerceIn(0, count - 1).toFloat(),
                valueRange = 0f..(count - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = { tugRelease[0]?.cancel() },
                onDragStopped = {
                    val landed = targetValue.fastRoundToInt().fastCoerceIn(0, count - 1)
                    animateToValue(landed.toFloat())
                    val tugFrom = tug.floatValue
                    if (tugFrom != 0f) {
                        tugRelease[0] = scope.launch {
                            Animatable(tugFrom).animateTo(0f, spring(1f, 300f, 0.5f)) { tug.floatValue = value }
                        }
                    }
                    onSelectState(landed)
                },
                onDrag = { _, dragAmount ->
                    followTo(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (count - 1).toFloat()),
                    )
                    tug.floatValue += dragAmount.x
                },
                velocityDampingRatio = 1f,
            )
        }

        // A selection made anywhere else — a tap on a label, the page restoring its state — slides
        // the thumb over with the same press and settle a drag gets.
        LaunchedEffect(thumb, selectedIndex) {
            val target = selectedIndex.coerceIn(0, count - 1).toFloat()
            if (abs(thumb.targetValue - target) > 0.001f) thumb.animateToValue(target)
        }

        val highlight = remember(scope) {
            InteractiveHighlight(
                animationScope = scope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (thumb.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (thumb.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        // The capsule and the labels you can see and tap.
        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, thumb.pressProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEachIndexed { index, label ->
                SegmentLabel(
                    text = label,
                    fontSize = labelSize,
                    color = if (index == selectedIndex) accent else ink.copy(alpha = 0.6f),
                    scale = { 1f },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                        ) { onSelectState(index) },
                )
            }
        }

        // The twin the thumb reads: the same labels in the accent, magnified with the press.
        Row(
            Modifier
                .clearAndSetSemantics {}
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        val progress = thumb.pressProgress
                        vibrancy()
                        blur(8f.dp.toPx())
                        lens(24f.dp.toPx() * progress, 24f.dp.toPx() * progress)
                    },
                    highlight = { Highlight.Default.copy(alpha = thumb.pressProgress) },
                    onDrawSurface = { drawRect(container) },
                )
                .then(highlight.modifier)
                .height(innerHeight)
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            labels.forEach { label ->
                SegmentLabel(
                    text = label,
                    fontSize = labelSize,
                    color = accent,
                    scale = { lerp(1f, 1.2f, thumb.pressProgress) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        // The thumb.
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) thumb.value * tabWidth + panelOffset
                        else size.width - (thumb.value + 1f) * tabWidth + panelOffset
                }
                .then(highlight.gestureModifier)
                .then(thumb.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { shape },
                    effects = {
                        val progress = thumb.pressProgress
                        lens(10f.dp.toPx() * progress, 14f.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = thumb.pressProgress) },
                    shadow = { Shadow(alpha = thumb.pressProgress) },
                    innerShadow = {
                        val progress = thumb.pressProgress
                        InnerShadow(radius = 8f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = thumb.scaleX
                        scaleY = thumb.scaleY
                        val velocity = thumb.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = thumb.pressProgress
                        drawRect(
                            if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .height(innerHeight)
                .fillMaxWidth(1f / count),
        )
    }
}

@Composable
private fun SegmentLabel(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .graphicsLayer {
                    val s = scale()
                    scaleX = s
                    scaleY = s
                },
        )
    }
}
