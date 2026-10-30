/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.Pin
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
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
    // The Music app's menu on macOS 27: the song itself at the head, then the actions in groups parted
    // by hairlines, in the Music app's own words. It was one flat list of Material rows with a ripple.
    MacMenu(expanded = expanded, onDismiss = onDismiss) {
        MenuHeader(song)
        MenuDivider()
        MacMenuRow(TransportIcons.Play, "Play") {
            player.playItem(song)
            onDismiss()
        }
        MacMenuRow(TransportIcons.Queue, "Play Next") {
            player.playNext(song)
            onDismiss()
        }
        MacMenuRow(NavIcons.New, "Play Last") {
            player.addToQueue(song)
            onDismiss()
        }
        MenuDivider()
        // The Music app's star: Favorite Songs, and the library when no account is signed in.
        val favorite = remember(song.id, expanded) { DesktopPrefs.isFavorite(song.id) }
        MacMenuRow(NavIcons.Star, if (favorite) "Undo Favorite" else "Favorite") {
            DesktopPrefs.toggleFavorite(song)
            onDismiss()
        }
        // The Music app's Pins: kept at the top of the sidebar's Library.
        val pinned = remember(song.id, expanded) { DesktopPrefs.isPinned(song.id) }
        MacMenuRow(NavIcons.Library, if (pinned) "Unpin" else "Pin") {
            DesktopPrefs.togglePin(Pin(Pin.Kind.Song, song.id, song.title, song.artists.joinToString { it.name }.ifBlank { null }, song.thumbnail))
            onDismiss()
        }
        MacMenuRow(NavIcons.Radio, "Create Station") {
            player.startRadio(song)
            onDismiss()
        }
        val album = song.album
        if (nav != null && (artist != null || album != null)) {
            MenuDivider()
            if (album != null) {
                MacMenuRow(NavIcons.Library, "Go to Album") {
                    nav.openAlbum(album.id, album.name, song.thumbnail)
                    onDismiss()
                }
            }
            if (artist != null) {
                MacMenuRow(NavIcons.Artists, "Go to Artist") {
                    nav.openArtist(artist.id!!, artist.name, null)
                    onDismiss()
                }
            }
        }
        MenuDivider()
        MacMenuRow(NavIcons.Link, "Copy Link") {
            runCatching {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection("https://music.youtube.com/watch?v=${song.id}"), null)
            }
            onDismiss()
        }
    }
}

/**
 * A menu as macOS 27 draws one: a rounded, nearly opaque dark panel with a hairline rim and a soft
 * shadow, rows of 13-point text with their symbols, the row under the pointer filled with the accent.
 */
@Composable
internal fun MacMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        containerColor = androidx.compose.ui.graphics.Color(0xF5232325),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)),
        shadowElevation = 20.dp,
        modifier = Modifier.widthIn(min = 240.dp, max = 300.dp).padding(horizontal = 5.dp),
        content = content,
    )
}

/** The song the menu is for, at its head: the cover, the title, and who it's by. */
@Composable
private fun MenuHeader(song: SongItem) {
    androidx.compose.foundation.layout.Row(
        Modifier.padding(start = 7.dp, end = 7.dp, top = 2.dp, bottom = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        coil3.compose.AsyncImage(
            song.thumbnail.resizedThumbnail(120),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            val by = song.artists.joinToString { it.name }.ifBlank { song.album?.name.orEmpty() }
            if (by.isNotBlank()) {
                Text(
                    by,
                    fontSize = 12.sp,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A hairline between groups of a menu, inset from its edges as macOS insets them. */
@Composable
internal fun MenuDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .padding(horizontal = 7.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.14f)),
    )
}

/** One row of a [MacMenu]: grey symbol and white words at rest, all of it white on the accent under the pointer. */
@Composable
internal fun MacMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val accent = MaterialTheme.colorScheme.primary
    val white = androidx.compose.ui.graphics.Color.White
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) accent else androidx.compose.ui.graphics.Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (hovered) white else white.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.width(9.dp))
        Text(label, fontSize = 13.sp, color = white.copy(alpha = if (hovered) 1f else 0.92f), maxLines = 1)
    }
}

/**
 * The ⋯ on an album or playlist page: the whole of it next or at the end of the queue, Pins, its
 * artist, and its link — the Music app's menu for a collection.
 */
@Composable
fun CollectionActionsMenu(
    songs: List<SongItem>,
    pin: Pin?,
    artist: com.ozyern.exhale.innertube.models.Artist?,
    link: String?,
    player: DesktopPlayer,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    val nav = LocalNavigator.current
    MacMenu(expanded = expanded, onDismiss = onDismiss) {
        if (songs.isNotEmpty()) {
            MacMenuRow(TransportIcons.Queue, "Play Next") {
                player.playNext(songs)
                onDismiss()
            }
            MacMenuRow(NavIcons.New, "Play Last") {
                player.addToQueue(songs)
                onDismiss()
            }
        }
        if (pin != null) {
            val pinned = remember(pin.id, expanded) { DesktopPrefs.isPinned(pin.id) }
            MacMenuRow(NavIcons.Library, if (pinned) "Unpin" else "Pin") {
                DesktopPrefs.togglePin(pin)
                onDismiss()
            }
        }
        val artistId = artist?.id
        if (nav != null && artist != null && artistId != null) {
            MacMenuRow(NavIcons.Artists, "Go to ${artist.name}") {
                nav.openArtist(artistId, artist.name, null)
                onDismiss()
            }
        }
        if (link != null) {
            MacMenuRow(NavIcons.Link, "Copy link") {
                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(link), null) }
                onDismiss()
            }
        }
    }
}
