/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.R

/**
 * A choice as iOS offers one: a pull-down menu that springs out of the row you touched, lists the
 * options with a check against the current one, and closes the moment one is picked.
 *
 * It replaces a centred Material dialog of radio buttons, which asked for the whole screen — a scrim,
 * a card in the middle, a column of circles — to change one setting. This stays next to the thing it
 * changes and takes only the room its options need.
 */
@Composable
fun <T> PullDownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) { visible.targetState = expanded }
    if (!visible.currentState && !visible.targetState) return

    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }
    // The popup covers the whole window — so the page behind can be dimmed and a tap anywhere off
    // the menu closes it — and the panel is placed inside it against the row that opened it.
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val position = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                if (anchor != anchorBounds) anchor = anchorBounds
                return IntOffset.Zero
            }
        }
    }
    var opensUp by remember { mutableStateOf(false) }

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize()) {
            // The page steps back while the choice is made.
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(160)),
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
                )
            }
            Box(
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    val windowW = constraints.maxWidth
                    val windowH = constraints.maxHeight
                    // Right edge on the row's right edge, just below it — or above, when there isn't room.
                    val x = (anchor.right - placeable.width - edge)
                        .coerceIn(edge, (windowW - placeable.width - edge).coerceAtLeast(edge))
                    val below = anchor.bottom + gap
                    val up = below + placeable.height > windowH - edge
                    if (up != opensUp) opensUp = up
                    val y = if (up) (anchor.top - gap - placeable.height).coerceAtLeast(edge) else below
                    layout(windowW, windowH) { placeable.place(x, y) }
                },
            ) {
                AnimatedVisibility(
                    visibleState = visible,
                    enter = fadeIn(tween(140)) + scaleIn(
                        initialScale = 0.6f,
                        transformOrigin = TransformOrigin(1f, if (opensUp) 1f else 0f),
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 560f),
                    ),
                    exit = fadeOut(tween(120)) + scaleOut(
                        targetScale = 0.9f,
                        transformOrigin = TransformOrigin(1f, if (opensUp) 1f else 0f),
                        animationSpec = tween(140),
                    ),
                ) {
                    MenuPanel(options, selected, label) { value ->
                        onSelect(value)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> MenuPanel(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    // The app's own theme, which can differ from the system's.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // iOS menu geometry, not Material's: no rules between items, rows built on 44pt, the panel
    // sized by its longest label rather than by the row that opened it.
    val shape = RoundedCornerShape(MenuCornerRadius)
    // Nearly opaque: a popup gets no backdrop blur, so a translucent panel shows the rows under it.
    val panel = if (dark) Color(0xFC1C1C1F) else Color(0xFCFBFBFD)
    val rim = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)

    val scroll = rememberScrollState()
    // A long list opens on the current choice, not on its first row.
    val selectedIndex = options.indexOf(selected)
    val rowPx = with(LocalDensity.current) { MenuRowHeight.roundToPx() }
    LaunchedEffect(Unit) {
        if (selectedIndex > 2) scroll.scrollTo(((selectedIndex - 2) * rowPx).coerceAtMost(scroll.maxValue))
    }
    // Where there is more to scroll, the edge fades instead of cutting a row in half.
    val fadeTop by animateFloatAsState(if (scroll.value > 0) 1f else 0f, tween(160), label = "menuFadeTop")
    val fadeBottom by animateFloatAsState(if (scroll.value < scroll.maxValue) 1f else 0f, tween(160), label = "menuFadeBottom")

    // The rows arrive one after another, a few ms apart, as the panel opens.
    val cascade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { cascade.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }

    Column(
        Modifier
            .widthIn(min = 210.dp, max = 270.dp)
            .width(IntrinsicSize.Max)
            .shadow(
                elevation = 34.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = Color.Black.copy(alpha = 0.5f),
            )
            .clip(shape)
            .background(panel)
            // One pass of light across the top, so the panel sits above the page rather than on it.
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = if (dark) 0.06f else 0.3f), Color.Transparent),
                ),
            )
            .border(0.7.dp, rim, shape)
            .heightIn(max = 420.dp)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 28.dp.toPx()
                if (fadeTop > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 1f - fadeTop),
                            1f to Color.Black,
                            startY = 0f,
                            endY = fade,
                        ),
                        size = androidx.compose.ui.geometry.Size(size.width, fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
                if (fadeBottom > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            1f to Color.Black.copy(alpha = 1f - fadeBottom),
                            startY = size.height - fade,
                            endY = size.height,
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - fade),
                        size = androidx.compose.ui.geometry.Size(size.width, fade),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
            .verticalScroll(scroll)
            .padding(vertical = 5.dp),
    ) {
        options.forEachIndexed { index, option ->
            val step = (index.coerceAtMost(8)) * 0.07f
            MenuRow(
                text = label(option),
                checked = option == selected,
                appear = { ((cascade.value - step) / 0.44f).coerceIn(0f, 1f) },
            ) { onPick(option) }
        }
    }
}

/** iOS menus round at 13-14pt; a little more here, for the inset rows inside it. */
private val MenuCornerRadius = 16.dp

/** 44pt, the row height every iOS list and menu is built on. */
private val MenuRowHeight = 44.dp

@Composable
private fun MenuRow(text: String, checked: Boolean, appear: () -> Float, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 220), label = "menuRowPress")
    val ink = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val a = appear()
                alpha = a
                translationY = (1f - a) * -6.dp.toPx()
            }
            .padding(horizontal = 5.dp)
            .heightIn(min = MenuRowHeight)
            // Pressed, the row lights as its own rounded tile inside the panel, the way iOS does.
            .clip(RoundedCornerShape(11.dp))
            .background(ink.copy(alpha = 0.11f * press))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check leads, the way UIMenu shows a chosen item, so every label starts in the same
        // place whether or not anything is ticked.
        Box(Modifier.width(20.dp), contentAlignment = Alignment.CenterStart) {
            if (checked) {
                Icon(
                    painterResource(R.drawable.check),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
