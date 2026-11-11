/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One entry of a [PopOutMenu]. */
@Immutable
data class PopOutItem(
    val title: String,
    val icon: Int? = null,
    val description: String? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A menu that grows out of the button that opened it, iOS 26's way: a small blob at the tap point
 * stretches into the panel, width leading height on two springs, and closing runs it back faster.
 *
 * A finger can press anywhere on the panel and slide across the rows; the row under it lights,
 * and lifting picks it — a tap is just a slide of no length.
 *
 * Drawn in-window over [content]'s host, not in a popup window, so its glass can bend what is
 * actually behind it ([backdrop]).
 */
@Composable
fun PopOutMenu(
    anchor: Offset,
    quickActions: List<PopOutItem>,
    sections: List<List<PopOutItem>>,
    backdrop: Backdrop?,
    onDismiss: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val scaleX = remember { Animatable(0.12f) }
    val scaleY = remember { Animatable(0.12f) }
    val alpha = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val measured = panelSize != IntSize.Zero && hostSize != IntSize.Zero

    // Opens once the panel has a size, so it grows from the right point.
    LaunchedEffect(measured) {
        if (!measured) return@LaunchedEffect
        launch { alpha.animateTo(1f, tween(90)) }
        launch { scaleX.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 700f)) }
        scaleY.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
    }
    val close: (after: (() -> Unit)?) -> Unit = { after ->
        if (!closing) {
            closing = true
            scope.launch {
                launch { scaleX.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
                launch { scaleY.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
                alpha.animateTo(0f, tween(140, delayMillis = 40))
                onDismiss()
                after?.invoke()
            }
        }
    }
    BackHandler { close(null) }

    // Above the tap with its right edge on it; below when there is no room above.
    fun place(touch: Offset): IntOffset = with(density) {
        val m = 12.dp.toPx()
        val x = (touch.x + 14.dp.toPx() - panelSize.width)
            .coerceIn(m, (hostSize.width - panelSize.width - m).coerceAtLeast(m))
        val above = touch.y - 22.dp.toPx() - panelSize.height
        val y = (if (above >= m) above else touch.y + 22.dp.toPx())
            .coerceIn(m, (hostSize.height - panelSize.height - m).coerceAtLeast(m))
        IntOffset(x.roundToInt(), y.roundToInt())
    }

    val entries = remember(quickActions, sections) { quickActions + sections.flatten() }
    val coords = remember(entries) { arrayOfNulls<LayoutCoordinates>(entries.size) }
    val panelCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var hovered by remember(entries) { mutableIntStateOf(-1) }
    fun hit(position: Offset): Int {
        val panel = panelCoords[0]?.takeIf { it.isAttached } ?: return -1
        return coords.indexOfFirst { c ->
            c != null && c.isAttached && panel.localBoundingBoxOf(c, clipBounds = false).contains(position)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot(); hostSize = it.size }
            .pointerInput(Unit) { detectTapGestures { close(null) } },
    ) {
        val touch = anchor - origin
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .offset { place(touch) }
                .onSizeChanged { panelSize = it }
                .graphicsLayer {
                    val at = place(touch)
                    transformOrigin = if (panelSize.width > 0 && panelSize.height > 0) {
                        TransformOrigin(
                            ((touch.x - at.x) / panelSize.width).coerceIn(0f, 1f),
                            ((touch.y - at.y) / panelSize.height).coerceIn(0f, 1f),
                        )
                    } else {
                        TransformOrigin.Center
                    }
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                    this.alpha = if (measured) alpha.value else 0f
                }
                .width(250.dp)
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(18f.dp.toPx())
                                lens(16f.dp.toPx(), 24f.dp.toPx())
                            },
                            highlight = { Highlight.Default.copy(alpha = 0.6f) },
                            shadow = { Shadow(alpha = 0.5f) },
                            onDrawSurface = { drawRect(Color(0xFF151515).copy(alpha = 0.5f)) },
                        )
                    } else {
                        Modifier.clip(shape).background(Color(0xF0202022))
                    },
                )
                .onGloballyPositioned { panelCoords[0] = it }
                .pointerInput(entries) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hovered = hit(down.position)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val now = hit(change.position)
                            if (now != hovered) {
                                if (now >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hovered = now
                            }
                        }
                        val picked = hovered
                        hovered = -1
                        if (picked >= 0) close(entries[picked].onClick)
                    }
                }
                .padding(vertical = 10.dp),
        ) {
            var index = 0
            if (quickActions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 10.dp),
                ) {
                    quickActions.forEach { item ->
                        val i = index++
                        Column(
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords[i] = it }
                                .clip(RoundedCornerShape(14.dp))
                                .drawBehind { if (hovered == i) drawRect(Color.White.copy(alpha = 0.12f)) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        ) {
                            item.icon?.let {
                                Icon(painterResource(it), null, tint = Color.White, modifier = Modifier.size(21.dp))
                            }
                            Text(
                                item.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            sections.forEachIndexed { s, section ->
                if (s > 0 || quickActions.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(17.dp)
                            .padding(horizontal = 22.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.12f)))
                    }
                }
                section.forEach { item ->
                    val i = index++
                    val ink = if (item.destructive) Color(0xFFFF453A) else Color.White
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .onGloballyPositioned { coords[i] = it }
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind { if (hovered == i) drawRect(Color.White.copy(alpha = 0.12f)) }
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(start = 12.dp, end = 14.dp, top = 11.dp, bottom = 11.dp),
                    ) {
                        item.icon?.let {
                            Box(Modifier.width(26.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(painterResource(it), null, tint = ink, modifier = Modifier.size(19.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = ink, fontSize = 17.sp, letterSpacing = (-0.4).sp)
                            item.description?.let {
                                Text(it, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}


/** The glass a pop-out bends when the player is what is on screen; set by the player while shown. */
object PopOutBackdrops {
    var player: Backdrop? by mutableStateOf(null)
}

/**
 * [PopOutMenu]'s panel around any content: the same blob growing out of the touch into a pane of
 * glass that bends what is behind it, for the app's own menus.
 *
 * Those opened as a sheet from the bottom edge on a tap, and as a dimmed, blurred window on a
 * hold, while the player's "…" opened this; three answers to "more options" in one app. Every menu
 * opened by a touch now opens here, out of the thing that was touched.
 *
 * [anchor] is the touch, on screen. Stays composed through its own closing: [visible] going false
 * runs it back into the touch and then calls [onClosed].
 */
@Composable
fun PopOutPanel(
    anchor: Offset,
    visible: Boolean,
    backdrop: Backdrop?,
    onDismissRequest: () -> Unit,
    onClosed: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val scaleX = remember { Animatable(0.12f) }
    val scaleY = remember { Animatable(0.12f) }
    val alpha = remember { Animatable(0f) }
    val measured = panelSize != IntSize.Zero && hostSize != IntSize.Zero

    LaunchedEffect(measured, visible) {
        if (!measured) {
            // Dismissed before it was ever laid out: nothing to run back, and left composed it
            // would sit over the page, unseen, taking its touches.
            if (!visible) onClosed()
            return@LaunchedEffect
        }
        if (visible) {
            launch { alpha.animateTo(1f, tween(90)) }
            launch { scaleX.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 700f)) }
            scaleY.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f))
        } else {
            launch { scaleX.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
            launch { scaleY.animateTo(0.12f, spring(dampingRatio = 1f, stiffness = 900f)) }
            alpha.animateTo(0f, tween(140, delayMillis = 40))
            onClosed()
        }
    }
    val dismiss by androidx.compose.runtime.rememberUpdatedState(onDismissRequest)
    BackHandler(enabled = visible) { dismiss() }

    // Beside the touch and wholly on screen: above it when there is room, else below, its far
    // edge on the touch so the panel opens away from the finger.
    fun place(touch: Offset): IntOffset = with(density) {
        val m = 12.dp.toPx()
        val leftHalf = touch.x < hostSize.width / 2f
        val x = (if (leftHalf) touch.x - 14.dp.toPx() else touch.x + 14.dp.toPx() - panelSize.width)
            .coerceIn(m, (hostSize.width - panelSize.width - m).coerceAtLeast(m))
        val above = touch.y - 22.dp.toPx() - panelSize.height
        val y = (if (above >= m * 3f) above else touch.y + 22.dp.toPx())
            .coerceIn(m * 3f, (hostSize.height - panelSize.height - m * 3f).coerceAtLeast(m * 3f))
        IntOffset(x.roundToInt(), y.roundToInt())
    }

    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val dark = remember(scheme.surface) {
        0.299f * scheme.surface.red + 0.587f * scheme.surface.green + 0.114f * scheme.surface.blue < 0.5f
    }
    val glass = backdrop != null && remember { com.kyant.backdrop.isRuntimeShaderSupported() }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.localToScreen(Offset.Zero); hostSize = it.size }
            .pointerInput(Unit) { detectTapGestures { dismiss() } },
    ) {
        val touch = anchor - origin
        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .offset { place(touch) }
                .onSizeChanged { panelSize = it }
                .graphicsLayer {
                    val at = place(touch)
                    transformOrigin = if (panelSize.width > 0 && panelSize.height > 0) {
                        TransformOrigin(
                            ((touch.x - at.x) / panelSize.width).coerceIn(0f, 1f),
                            ((touch.y - at.y) / panelSize.height).coerceIn(0f, 1f),
                        )
                    } else {
                        TransformOrigin.Center
                    }
                    this.scaleX = scaleX.value
                    this.scaleY = scaleY.value
                    this.alpha = if (measured) alpha.value else 0f
                }
                // A menu beside a finger: most of a phone's width, never a sheet's.
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.92f)
                .then(
                    if (hostSize.height > 0) {
                        Modifier.heightIn(max = with(density) { (hostSize.height * 0.72f).toDp() })
                    } else {
                        Modifier
                    },
                )
                .then(
                    if (glass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop!!,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur(18f.dp.toPx())
                                lens(16f.dp.toPx(), 24f.dp.toPx())
                            },
                            highlight = { Highlight.Default.copy(alpha = if (dark) 0.6f else 0.9f) },
                            shadow = { Shadow(alpha = if (dark) 0.5f else 0.22f) },
                            onDrawSurface = {
                                // The player's dark pane; in the light theme the same pane in white,
                                // heavy enough that page text under it stays colour, not letters.
                                drawRect(if (dark) Color(0xFF151515).copy(alpha = 0.58f) else Color.White.copy(alpha = 0.72f))
                            },
                        )
                    } else {
                        Modifier.clip(shape).background(scheme.surfaceContainerHigh.copy(alpha = 0.97f))
                    },
                )
                // Touches on the panel are the panel's: never a tap on the page beside it.
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 10.dp),
            content = content,
        )
    }
}
