/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.utils.completed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * What you can do with a song without opening it, laid out as the Android app's ⋯ menu: the song with
 * its heart at the head, Play next / Add / Share across the top, then Start radio and Add to queue,
 * Pin, and the ways to its artist and album. Right-click any song, or use the ⋯ that appears under the
 * pointer.
 */
@Composable
fun SongActionsMenu(song: SongItem, player: DesktopPlayer, expanded: Boolean, onDismiss: () -> Unit) {
    val nav = LocalNavigator.current
    val artist = song.artists.firstOrNull { it.id != null }
    var picking by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) picking = false }
    OptionsMenu(expanded = expanded, onDismiss = onDismiss) {
        var liked by remember(song.id, expanded) { mutableStateOf(DesktopPrefs.isFavorite(song.id)) }
        OptionsHeader(
            image = song.thumbnail,
            title = song.title,
            subtitle = listOfNotNull(
                song.artists.joinToString { it.name }.ifBlank { null },
                song.duration?.let { formatTime(it.toDouble()) },
            ).joinToString(" • "),
            liked = liked,
            onLike = {
                DesktopPrefs.toggleFavorite(song)
                liked = !liked
            },
        )
        if (picking) {
            PlaylistPicker(song, onBack = { picking = false }, onDone = onDismiss)
            return@OptionsMenu
        }
        OptionsQuickActions(
            listOf(
                OptionsAction(OptionsIcons.PlayNext, "Play next") {
                    player.playNext(song)
                    onDismiss()
                },
                // Android's Add puts the song in one of your playlists; that needs an account.
                OptionsAction(OptionsIcons.Add, "Add", enabled = AccountSession.isSignedIn) { picking = true },
                OptionsAction(OptionsIcons.Share, "Share") {
                    copyToClipboard(com.ozyern.exhale.desktop.data.ShareLinks.song(song.id))
                    onDismiss()
                },
            ),
        )
        OptionsGroup(
            listOf(
                OptionsItem(OptionsIcons.Radio, "Start radio", "Create a station based on this item") {
                    player.startRadio(song)
                    onDismiss()
                },
                OptionsItem(OptionsIcons.Queue, "Add to queue", "Add to the bottom of your queue") {
                    player.addToQueue(song)
                    onDismiss()
                },
            ),
        )
        val pinned = remember(song.id, expanded) { DesktopPrefs.isPinned(song.id) }
        OptionsGroup(
            listOf(
                OptionsItem(OptionsIcons.Pin, if (pinned) "Unpin" else "Pin", "Keep it at the top of your Library") {
                    DesktopPrefs.togglePin(Pin(Pin.Kind.Song, song.id, song.title, song.artists.joinToString { it.name }.ifBlank { null }, song.thumbnail))
                    onDismiss()
                },
            ),
        )
        val album = song.album
        if (nav != null) {
            OptionsGroup(
                listOfNotNull(
                    artist?.let {
                        OptionsItem(OptionsIcons.Artist, "View artist", song.artists.joinToString { a -> a.name }) {
                            nav.openArtist(it.id!!, it.name, null)
                            onDismiss()
                        }
                    },
                    album?.let {
                        OptionsItem(OptionsIcons.Album, "View album", it.name) {
                            nav.openAlbum(it.id, it.name, song.thumbnail)
                            onDismiss()
                        }
                    },
                ),
            )
        }
    }
}

/**
 * Add, in place: your own playlists listed in the panel, as Android's playlist chooser lists them,
 * with a way back to the menu at the top.
 */
@Composable
private fun PlaylistPicker(song: SongItem, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val playlists by produceState<List<PlaylistItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            YouTube.library("FEmusic_liked_playlists").completed().getOrNull()?.items.orEmpty()
                .filterIsInstance<PlaylistItem>()
                .filter { it.isEditable && it.id.isNotBlank() }
        }
    }
    var adding by remember { mutableStateOf<String?>(null) }
    OptionsGroup(
        buildList {
            add(OptionsItem(NavIcons.Back, "Back", onClick = onBack))
            playlists?.forEach { playlist ->
                add(
                    OptionsItem(OptionsIcons.Queue, playlist.title, if (adding == playlist.id) "Adding…" else playlist.songCountText) {
                        if (adding != null) return@OptionsItem
                        adding = playlist.id
                        scope.launch {
                            withContext(Dispatchers.IO) { YouTube.addToPlaylist(playlist.id, song.id) }
                            adding = null
                            onDone()
                        }
                    },
                )
            }
        },
    )
    when {
        playlists == null -> OptionsNote("Loading your playlists…")
        playlists!!.isEmpty() -> OptionsNote("You don't have any playlists you can add to.")
    }
}

@Composable
private fun OptionsNote(text: String) {
    Text(text, fontSize = 12.sp, color = OptionsMenuStyle.SecondaryLabel, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
}

private fun copyToClipboard(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
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
        shape = OptionsMenuStyle.Shape,
        containerColor = OptionsMenuStyle.PanelFill.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)),
        shadowElevation = 20.dp,
        modifier = Modifier.widthIn(min = 240.dp, max = 300.dp).padding(horizontal = 5.dp),
        content = content,
    )
}

/** A hairline between groups of a menu, inset from its edges as macOS insets them. */
@Composable
internal fun MenuDivider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(OptionsMenuStyle.Separator),
    )
}

/** One row of a menu, as the Android ⋯ menu draws it: symbol and title in the label colour, a soft wash under the pointer. */
@Composable
internal fun MacMenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = OptionsMenuStyle.Label
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) OptionsMenuStyle.Highlight else androidx.compose.ui.graphics.Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(Modifier.width(22.dp), contentAlignment = androidx.compose.ui.Alignment.CenterStart) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(17.dp))
        }
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, color = ink, maxLines = 1)
    }
}

/**
 * The ⋯ on an album or playlist page, in the same Android layout as a song's: the collection at the
 * head, Play next / Shuffle / Share across the top, then Add to queue, Pin, and its artist.
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
    val canPlay = songs.isNotEmpty()
    OptionsMenu(expanded = expanded, onDismiss = onDismiss) {
        if (pin != null) {
            OptionsHeader(
                image = pin.thumbnail,
                title = pin.title,
                subtitle = listOfNotNull(pin.subtitle, if (canPlay) (if (songs.size == 1) "1 song" else "${songs.size} songs") else null).joinToString(" • "),
                roundImage = pin.kind == Pin.Kind.Artist,
            )
        }
        OptionsQuickActions(
            listOf(
                OptionsAction(OptionsIcons.PlayNext, "Play next", enabled = canPlay) {
                    player.playNext(songs)
                    onDismiss()
                },
                OptionsAction(OptionsIcons.Shuffle, "Shuffle", enabled = canPlay) {
                    player.play(songs.shuffled(), 0)
                    onDismiss()
                },
                OptionsAction(OptionsIcons.Share, "Share", enabled = link != null) {
                    link?.let(::copyToClipboard)
                    onDismiss()
                },
            ),
        )
        OptionsGroup(
            listOfNotNull(
                if (canPlay) {
                    OptionsItem(OptionsIcons.Queue, "Add to queue", "Add to the bottom of your queue") {
                        player.addToQueue(songs)
                        onDismiss()
                    }
                } else {
                    null
                },
                pin?.let {
                    val pinned = remember(it.id, expanded) { DesktopPrefs.isPinned(it.id) }
                    OptionsItem(OptionsIcons.Pin, if (pinned) "Unpin" else "Pin", "Keep it at the top of your Library") {
                        DesktopPrefs.togglePin(it)
                        onDismiss()
                    }
                },
            ),
        )
        val artistId = artist?.id
        if (nav != null && artist != null && artistId != null) {
            OptionsGroup(
                listOf(
                    OptionsItem(OptionsIcons.Artist, "View artist", artist.name) {
                        nav.openArtist(artistId, artist.name, null)
                        onDismiss()
                    },
                ),
            )
        }
    }
}
