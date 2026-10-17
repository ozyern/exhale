/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The tray menu: the song, its
 * controls, the way back to the window, and quit.
 */

package com.ozyern.exhale.desktop.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.player.DesktopPlayer
import kotlinx.coroutines.flow.MutableStateFlow

/** Exhale in the notification area: click to bring the window back, right-click for the controls. */
@Composable
fun ApplicationScope.ExhaleTray(player: DesktopPlayer?, onShow: () -> Unit, onQuit: () -> Unit) {
    val idle = remember { MutableStateFlow(null) }
    val state by (player?.state ?: idle).collectAsState()
    val song = state?.current
    val nowPlaying = song?.let { current ->
        listOfNotNull(current.title, current.artists.firstOrNull()?.name).joinToString(" — ")
    }
    Tray(
        icon = appIcon,
        // Windows cuts a tooltip off at 127 characters.
        tooltip = (nowPlaying?.let { "Exhale — $it" } ?: "Exhale").take(127),
        onAction = onShow,
        menu = {
            if (nowPlaying != null) {
                Item(nowPlaying.take(60), enabled = false, onClick = {})
                Separator()
            }
            if (player != null && song != null) {
                Item(if (state?.isPlaying == true) "Pause" else "Play", onClick = player::togglePlayPause)
                Item("Next", onClick = player::next)
                Item("Previous", onClick = player::previous)
                Separator()
            }
            Item("Show Exhale", onClick = onShow)
            Item("Quit", onClick = onQuit)
        },
    )
}
