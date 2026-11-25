/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import kotlin.math.roundToInt

/**
 * Where the open player wants the picture, in the window's own pixels — null when there is no open
 * player to show it in, which is not the same as no longer watching. The surface itself lives at the
 * root of the app ([VideoSurfaceHost]), so folding the player away leaves the picture playing instead
 * of tearing down the window mpv draws into and starting the song again.
 */
object VideoSlot {
    val rect = mutableStateOf<IntRect?>(null)

    /** Where the picture last sat in the open player: where it glides from, into the dock, as the player closes. */
    val lastOpen = mutableStateOf<IntRect?>(null)
}

/**
 * The player, watching rather than listening: a space kept for the picture, which the host fills.
 *
 * mpv paints into an operating-system window of its own, which sits above everything Exhale draws and
 * cannot be clipped, scaled or folded into the morph that collapses the player. So this holds no
 * surface itself. It measures where the picture belongs — cut to the picture's own shape, so it
 * neither letterboxes inside a larger surface nor hangs out of the window — and publishes that.
 */
@Composable
fun VideoStage(state: PlayerState, player: DesktopPlayer, modifier: Modifier = Modifier) {
    state.current ?: return
    val aspect by player.videoAspect.collectAsState()

    // The picture is only placed while the player is all the way open: on the way up or down the morph
    // is mid-flight, and a window that cannot be clipped has no business being in it.
    val open = LocalPlayerReveal.current() > 0.995f

    DisposableEffect(Unit) { onDispose { VideoSlot.rect.value = null } }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        BoxWithConstraints(
            // Barely a margin: the picture is the page here, and every band of black around it is a
            // band of nothing.
            Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = aspect.coerceIn(0.4f, 3.5f)
            // Sized outright rather than by fitting one edge and hoping the other follows: an aspect
            // taken off the width overflows whenever the height is the tighter of the two.
            val room = maxHeight.takeIf { it.value.isFinite() && it.value > 0f } ?: (maxWidth / shape)
            val width = minOf(maxWidth, room * shape)
            Box(
                Modifier
                    .size(width, width / shape)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        val rect = IntRect(
                            bounds.left.roundToInt(),
                            bounds.top.roundToInt(),
                            bounds.right.roundToInt(),
                            bounds.bottom.roundToInt(),
                        )
                        VideoSlot.rect.value = if (open) rect else null
                        if (open) VideoSlot.lastOpen.value = rect
                    },
            )
        }
    }
}
