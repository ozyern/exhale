/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.delay
import java.awt.Canvas
import java.awt.Rectangle

/**
 * Where a video is drawn. mpv paints straight into this window rather than through Compose: it is the
 * one thing on screen the app doesn't draw itself, so the glass can't bend it and nothing composites
 * over it.
 *
 * Two things are reported back out. The native handle, because a window only has one once it's really
 * on screen and the player is waiting for it before it can start mpv here. And where the surface sits
 * on the screen, so the controls — which live in a window of their own, the only thing that can be
 * above this one — can be laid exactly over it.
 */
@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    onBounds: (Rectangle?) -> Unit = {},
    onGone: (Long) -> Unit = {},
    cornerPx: Int = 0,
    onClick: (() -> Unit)? = null,
    onSurface: (Long) -> Unit,
) {
    val latest by rememberUpdatedState(onSurface)
    val reportBounds by rememberUpdatedState(onBounds)
    val reportGone by rememberUpdatedState(onGone)
    val click by rememberUpdatedState(onClick)
    val corner by rememberUpdatedState(cornerPx)
    var handed by remember { mutableLongStateOf(0L) }
    val canvas = remember {
        Canvas().apply {
            background = java.awt.Color.BLACK
            isFocusable = false
        }
    }
    // A native window draws over everything, so in the dock it sits where the artwork is and takes its
    // clicks: they open the player, as the artwork does.
    DisposableEffect(canvas) {
        val listener = object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                click?.let { java.awt.EventQueue.invokeLater(it) }
            }
        }
        canvas.addMouseListener(listener)
        onDispose { canvas.removeMouseListener(listener) }
    }
    // Rounded as the artwork it stands in for. Nothing in the app can clip a native window, but Windows
    // can: a window region, set again whenever the size or the corner changes.
    androidx.compose.runtime.LaunchedEffect(canvas, cornerPx, handed) {
        if (handed != 0L) roundCorners(handed, canvas, cornerPx)
    }
    DisposableEffect(canvas) {
        val listener = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                if (handed != 0L) roundCorners(handed, canvas, corner)
            }
        }
        canvas.addComponentListener(listener)
        onDispose { canvas.removeComponentListener(listener) }
    }
    // A window only has a handle once it's really on screen, and how long that takes is not ours to
    // decide — so it's asked for until it's there rather than hooked to one moment.
    LaunchedEffect(canvas) {
        repeat(60) {
            if (canvas.isDisplayable) {
                val handle = runCatching { Native.getComponentID(canvas) }.getOrElse {
                    println("Exhale video: no handle for the surface — ${it.message}")
                    0L
                }
                if (handle != 0L) {
                    handed = handle
                    latest(handle)
                    return@LaunchedEffect
                }
            }
            delay(50)
        }
        println("Exhale video: the surface never became displayable")
    }

    // Watched rather than listened for: a window moving doesn't tell its children, and the overlay
    // being a frame behind while the window is dragged is better than it being in the wrong place.
    LaunchedEffect(canvas) {
        var last: Rectangle? = null
        while (true) {
            val now = if (canvas.isShowing) {
                runCatching { Rectangle(canvas.locationOnScreen, canvas.size) }.getOrNull()
            } else {
                null
            }
            if (now != last) {
                last = now
                reportBounds(now)
            }
            delay(60)
        }
    }
    // Going away is news. The window this reports is about to be destroyed by the toolkit, and mpv
    // is still drawing into it until it's told otherwise.
    DisposableEffect(canvas) {
        onDispose {
            reportBounds(null)
            if (handed != 0L) reportGone(handed)
        }
    }
    SwingPanel(background = Color.Black, factory = { canvas }, modifier = modifier)
}

@Suppress("FunctionName")
private interface Gdi32 : Library {
    fun CreateRoundRectRgn(left: Int, top: Int, right: Int, bottom: Int, widthEllipse: Int, heightEllipse: Int): Pointer?
}

@Suppress("FunctionName")
private interface User32 : Library {
    fun SetWindowRgn(window: Pointer, region: Pointer?, redraw: Boolean): Int
}

private val gdi32 by lazy { runCatching { Native.load("gdi32", Gdi32::class.java) }.getOrNull() }
private val user32 by lazy { runCatching { Native.load("user32", User32::class.java) }.getOrNull() }

/**
 * Clips the window to a rounded rectangle, [cornerPx] in screen pixels; no corner clears the region,
 * back to the plain window. AWT gives the size in scaled units and Windows wants pixels.
 */
private fun roundCorners(handle: Long, canvas: Canvas, cornerPx: Int) {
    val scale = canvas.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
    val width = (canvas.width * scale).toInt()
    val height = (canvas.height * scale).toInt()
    val users = user32 ?: return
    val window = Pointer(handle)
    if (cornerPx <= 0 || width <= 0 || height <= 0) {
        users.SetWindowRgn(window, null, true)
        return
    }
    // The window takes ownership of the region; it is not ours to free.
    val region = gdi32?.CreateRoundRectRgn(0, 0, width + 1, height + 1, cornerPx * 2, cornerPx * 2) ?: return
    users.SetWindowRgn(window, region, true)
}
