/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.runtime.remember
import com.ozyern.exhale.desktop.data.DesktopPrefs
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.innertube.models.SongItem
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * What you can do with a song without opening it: play it now, put it next, add it to the end, start
 * its radio, go to whoever made it, or take its link. Right-click any song, or use the ⋯ that appears
 * under the pointer.
 */
@Composable
fun SongActionsMenu(song: SongItem, player: DesktopPlayer, expanded: Boolean, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val artist = song.artists.firstOrNull { it.id != null }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Action(TransportIcons.Play, "Play now") {
            player.playItem(song)
            onDismiss()
        }
        // The Music app's star: Favorite Songs, and the library when no account is signed in.
        val favorite = remember(song.id, expanded) { DesktopPrefs.isFavorite(song.id) }
        Action(NavIcons.Star, if (favorite) "Undo Favorite" else "Favorite") {
            DesktopPrefs.toggleFavorite(song)
            onDismiss()
        }
        Action(TransportIcons.Queue, "Play next") {
            player.playNext(song)
            onDismiss()
        }
        Action(NavIcons.New, "Add to queue") {
            player.addToQueue(song)
            onDismiss()
        }
        Action(NavIcons.Radio, "Start radio") {
            player.startRadio(song)
            onDismiss()
        }
        if (nav != null && artist != null) {
            Action(NavIcons.Artists, "Go to ${artist.name}") {
                nav.openArtist(artist.id!!, artist.name, null)
                onDismiss()
            }
        }
        if (nav != null) {
            song.album?.let { album ->
                Action(NavIcons.Library, "Go to ${album.name}") {
                    nav.openAlbum(album.id, album.name, song.thumbnail)
                    onDismiss()
                }
            }
        }
        Action(NavIcons.Link, "Copy link") {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("https://music.youtube.com/watch?v=${song.id}"), null)
            }
            onDismiss()
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}
