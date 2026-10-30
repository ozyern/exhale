/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.ozyern.exhale.desktop.player.DesktopPlayer
import java.awt.Rectangle
import kotlin.math.roundToInt

/** The dock's artwork corners, which the picture takes on in the dock. */
private val DockArtCorner = 5.dp

/**
 * The window mpv paints into, kept for as long as there is something to watch.
 *
 * It lives at the root of the app rather than inside the player screen, and that is the whole point:
 * a surface inside the player is destroyed the moment the player folds away, and mpv cannot be handed
 * a new window without being rebuilt — which means re-resolving the stream and starting the song over.
 * Here it only moves: in the open player it fills the stage; as the player closes it glides down with
 * it into the dock and carries on playing there in place of the artwork, as the Android app's dock
 * does; and where there's no dock to show it in, it shrinks to a speck. mpv follows the resize on its
 * own; nothing reloads, and the song never stops.
 *
 * [dockArt] is the dock's artwork in the window's pixels (null when there's no dock), [morph] how far
 * open the player is, 0 to 1, and [onOpen] what a click on the picture in the dock does.
 *
 * The controls go over the picture in a window of their own ([VideoControlsOverlay]), and only while
 * the player is open around it.
 */
@Composable
fun VideoSurfaceHost(player: DesktopPlayer, dockArt: () -> Rect?, morph: () -> Float, onOpen: () -> Unit) {
    val open by VideoSlot.rect
    val lastOpen by VideoSlot.lastOpen
    val state by player.state.collectAsState()
    val density = LocalDensity.current
    val surfaceBounds = remember { mutableStateOf<Rectangle?>(null) }
    val dockCorner = with(density) { DockArtCorner.toPx() }

    val art = dockArt()?.let { IntRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
    // How far toward the open player the picture is: 1 in it, 0 in the dock.
    val toward = if (open != null) 1f else FastOutSlowInEasing.transform(morph().coerceIn(0f, 1f))
    val from = lastOpen
    val place: IntRect? = when {
        open != null -> open
        art == null -> null
        from == null || toward <= 0.001f -> art
        else -> IntRect(
            lerp(art.left, from.left, toward),
            lerp(art.top, from.top, toward),
            lerp(art.right, from.right, toward),
            lerp(art.bottom, from.bottom, toward),
        )
    }
    val docked = open == null && place != null

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset { IntOffset(place?.left ?: 0, place?.top ?: 0) }
                .size(
                    // A speck rather than nothing: the window has to stay alive for mpv to keep
                    // drawing into it, and one pixel in the corner of the title bar is the price.
                    width = with(density) { (place?.width ?: 1).toDp() },
                    height = with(density) { (place?.height ?: 1).toDp() },
                ),
        ) {
            VideoSurface(
                Modifier.fillMaxSize(),
                onBounds = { surfaceBounds.value = it },
                onGone = { player.detachVideoSurface(it) },
                // Square in the player, the artwork's corners in the dock, and between the two as it goes.
                cornerPx = if (docked) (dockCorner * (1f - toward)).roundToInt() else 0,
                onClick = if (docked) onOpen else null,
                onSurface = { player.attachVideoSurface(it) },
            )
        }

        if (open != null) {
            VideoControlsOverlay(surfaceBounds, state, player)
        }
    }
}
