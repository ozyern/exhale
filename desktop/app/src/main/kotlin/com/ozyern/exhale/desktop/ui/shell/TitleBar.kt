/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo

/** Height of Exhale's own title bar. Pages start below it; their backgrounds run under it. */
val TitleBarHeight = 40.dp

/** How far content has to stay clear of the title bar — 0 where there isn't one. */
val LocalTitleBarHeight = compositionLocalOf { 0.dp }

/** What the title bar's back button does right now: close the player, or go back a page. Null hides it. */
object ChromeNav {
    var back by mutableStateOf<(() -> Unit)?>(null)

    /** Shows and hides the sidebar, from the button at its top right. Null where there's no sidebar. */
    var sidebarToggle by mutableStateOf<(() -> Unit)?>(null)

    /** Where the sidebar's pane ends, so the back button sits beside it rather than on it. */
    var sidebarEnd by mutableStateOf(0.dp)
}

/**
 * The window's frame. On the JetBrains Runtime the native title bar is replaced by a custom one of
 * our height with the system caption buttons hidden — Windows still handles dragging, Aero Snap,
 * double-click to maximise and edge resizing. On any other JDK the window is undecorated instead.
 */
object WindowChrome {
    val nativeSupported: Boolean by lazy { runCatching { JBR.isWindowDecorationsSupported() }.getOrDefault(false) }

    fun install(frame: Frame, heightPx: Float): WindowDecorations.CustomTitleBar? {
        if (!nativeSupported) return null
        return runCatching {
            val decorations = JBR.getWindowDecorations() ?: return null
            decorations.createCustomTitleBar().apply {
                height = heightPx
                putProperty("controls.visible", false)
            }.also { decorations.setCustomTitleBar(frame, it) }
        }.getOrNull()
    }
}

/**
 * Spotify-style caption row drawn by Exhale: a back button when there's somewhere to go back to,
 * an empty stretch that drags the window, and minimise / maximise / close. Transparent, so the
 * page's colour runs to the top of the window.
 */
@Composable
fun FrameWindowScope.ExhaleTitleBar(nativeBar: WindowDecorations.CustomTitleBar?, windowState: WindowState, onClose: () -> Unit) {
    val focused = LocalWindowInfo.current.isWindowFocused
    val glyph = Color.White.copy(alpha = if (focused) 0.9f else 0.42f)
    val back = ChromeNav.back
    val maximized = windowState.placement == WindowPlacement.Maximized

    fun toggleMaximize() {
        windowState.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            // The shade that keeps the caption glyphs legible stops at the sidebar, whose glass needs none.
            .drawBehind {
                val from = ChromeNav.sidebarEnd.toPx()
                drawRect(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)),
                    topLeft = Offset(from, 0f),
                    size = Size(size.width - from, size.height),
                )
            },
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            @Composable
            fun DragRegion(modifier: Modifier) {
                if (nativeBar != null) {
                    // Every event over the empty stretch is caption: Windows drags, snaps and maximises.
                    Box(
                        modifier.pointerInput(nativeBar) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Main)
                                    nativeBar.forceHitTest(false)
                                }
                            }
                        },
                    )
                } else {
                    WindowDraggableArea(modifier) {
                        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximize() }) })
                    }
                }
            }

            // The Music app's sidebar button: at the sidebar's top right while it's out, at the window's
            // left edge once it's hidden.
            val toggle = ChromeNav.sidebarToggle
            if (toggle != null) {
                val end = ChromeNav.sidebarEnd
                val lead = if (end > 0.dp) (end - 46.dp).coerceAtLeast(10.dp) else 10.dp
                DragRegion(Modifier.width(lead).fillMaxHeight())
                CaptionButton(nativeBar, onClick = toggle, width = 38.dp, glyphColor = glyph, glyphSize = 18.dp, rounded = true) { color -> drawSidebar(color) }
                Box(Modifier.width((end - lead - 38.dp).coerceAtLeast(0.dp) + 6.dp))
            }
            if (back != null) {
                // The Music app's back: a round plate of glass, not a caption block.
                CaptionButton(nativeBar, onClick = back, width = 40.dp, glyphColor = glyph, glyphSize = 12.dp, plate = true) { color -> drawBack(color) }
            }
            DragRegion(Modifier.weight(1f).fillMaxHeight())
            CaptionButton(nativeBar, onClick = { windowState.isMinimized = true }, glyphColor = glyph) { color -> drawMinimize(color) }
            CaptionButton(nativeBar, onClick = ::toggleMaximize, glyphColor = glyph) { color -> if (maximized) drawRestore(color) else drawMaximize(color) }
            CaptionButton(nativeBar, onClick = onClose, danger = true, glyphColor = glyph) { color -> drawClose(color) }
        }
    }
}

@Composable
private fun CaptionButton(
    nativeBar: WindowDecorations.CustomTitleBar?,
    onClick: () -> Unit,
    width: Dp = 46.dp,
    danger: Boolean = false,
    glyphColor: Color,
    glyphSize: Dp = 10.dp,
    rounded: Boolean = false,
    plate: Boolean = false,
    glyph: DrawScope.(Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        when {
            danger && pressed -> Color(0xFF9B2217)
            danger && hovered -> Color(0xFFC42B1C)
            pressed -> Color.White.copy(alpha = 0.06f)
            hovered -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        tween(90),
        label = "caption_button",
    )
    val tint = if (danger && hovered) Color.White else glyphColor
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .then(
                if (nativeBar != null) {
                    // Controls are client area, or Windows would treat a click on them as a drag.
                    Modifier.pointerInput(nativeBar) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                nativeBar.forceHitTest(true)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            )
            // A button inside the sidebar is a rounded plate, like the Music app's, not a caption block.
            .then(if (rounded) Modifier.padding(vertical = 5.dp).clip(RoundedCornerShape(8.dp)) else Modifier)
            .then(
                if (plate) {
                    Modifier
                        .padding(4.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0xFF2A2A2D).copy(alpha = 0.72f))
                        .border(0.75.dp, Color.White.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)
                } else {
                    Modifier
                },
            )
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(glyphSize)) { glyph(tint) }
    }
}

/** SF Symbols' sidebar.left: a rounded window with its left third ruled off. */
private fun DrawScope.drawSidebar(color: Color) {
    val s = 1.4.dp.toPx()
    val h = size.height * 0.78f
    val top = (size.height - h) / 2f
    drawRoundRect(color, topLeft = Offset(s / 2, top), size = Size(size.width - s, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(s))
    val rule = size.width * 0.36f
    drawLine(color, Offset(rule, top), Offset(rule, top + h), strokeWidth = s)
    drawLine(color, Offset(size.width * 0.13f, top + h * 0.25f), Offset(rule - size.width * 0.1f, top + h * 0.25f), strokeWidth = s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.13f, top + h * 0.45f), Offset(rule - size.width * 0.1f, top + h * 0.45f), strokeWidth = s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

private fun DrawScope.stroke() = 1.dp.toPx()

private fun DrawScope.drawMinimize(color: Color) {
    drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = stroke())
}

private fun DrawScope.drawMaximize(color: Color) {
    val s = stroke()
    drawRect(color, topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s), style = Stroke(s))
}

private fun DrawScope.drawRestore(color: Color) {
    val s = stroke()
    val gap = 2.dp.toPx()
    drawRect(color, topLeft = Offset(s / 2, gap + s / 2), size = Size(size.width - gap - s, size.height - gap - s), style = Stroke(s))
    drawLine(color, Offset(gap, gap), Offset(gap, s / 2), strokeWidth = s)
    drawLine(color, Offset(gap, s / 2), Offset(size.width - s / 2, s / 2), strokeWidth = s)
    drawLine(color, Offset(size.width - s / 2, s / 2), Offset(size.width - s / 2, size.height - gap), strokeWidth = s)
    drawLine(color, Offset(size.width - s / 2, size.height - gap), Offset(size.width - gap, size.height - gap), strokeWidth = s)
}

private fun DrawScope.drawClose(color: Color) {
    val s = stroke()
    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = s)
    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = s)
}

private fun DrawScope.drawBack(color: Color) {
    val s = stroke() * 1.4f
    drawLine(color, Offset(size.width * 0.72f, 0f), Offset(size.width * 0.22f, size.height / 2f), strokeWidth = s)
    drawLine(color, Offset(size.width * 0.22f, size.height / 2f), Offset(size.width * 0.72f, size.height), strokeWidth = s)
}

/**
 * Edges for resizing an undecorated window (only when not on the JetBrains Runtime): the right and
 * bottom edges and the bottom-right corner. Sizes from the pointer's screen position, so the window
 * moving under the pointer never feeds back into the drag.
 */
@Composable
fun ResizeHandles(window: java.awt.Window) {
    Box(Modifier.fillMaxSize()) {
        ResizeEdge(window, Modifier.align(Alignment.CenterEnd).width(5.dp).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, horizontal = true, vertical = false)
        ResizeEdge(window, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp), Cursor.S_RESIZE_CURSOR, horizontal = false, vertical = true)
        ResizeEdge(window, Modifier.align(Alignment.BottomEnd).size(12.dp), Cursor.SE_RESIZE_CURSOR, horizontal = true, vertical = true)
    }
}

@Composable
private fun ResizeEdge(window: java.awt.Window, modifier: Modifier, cursor: Int, horizontal: Boolean, vertical: Boolean) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                    val startWidth = window.width
                    val startHeight = window.height
                    while (true) {
                        val event = awaitPointerEvent()
                        val now = MouseInfo.getPointerInfo()?.location ?: break
                        val w = if (horizontal) (startWidth + now.x - start.x).coerceAtLeast(window.minimumSize.width) else startWidth
                        val h = if (vertical) (startHeight + now.y - start.y).coerceAtLeast(window.minimumSize.height) else startHeight
                        window.setSize(w, h)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    )
}
