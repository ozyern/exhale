/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.player.RepeatMode
import com.ozyern.exhale.desktop.ui.LoadingRing
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongActionsMenu
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.player.GlassTrack
import com.ozyern.exhale.desktop.ui.player.PlayerPanel
import com.ozyern.exhale.desktop.ui.rememberPosition
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import kotlin.math.roundToInt

val PlayerBarHeight = 50.dp

/**
 * The Music app's player bar: one glass capsule floating over the foot of the page. Transport on the
 * left — shuffle and repeat lit when they're on — what's playing in the middle with a hairline of
 * progress under it, and lyrics, up next, the output and the volume on the right. Clicking the song
 * opens the player; scrolling anywhere over the bar changes the volume.
 */
@Composable
fun PlayerBar(
    state: PlayerState,
    player: DesktopPlayer,
    onExpandPlayer: (PlayerPanel) -> Unit,
    onBarBounds: (Rect) -> Unit,
    onArtBounds: (Rect) -> Unit,
    artAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalAppBackdrop.current ?: return
    val shape = RoundedCornerShape(percent = 50)
    val song = state.current
    val hasSong = song != null

    Row(
        modifier
            .height(PlayerBarHeight)
            .onGloballyPositioned { onBarBounds(it.boundsInRoot()) }
            .volumeWheel(player)
            .liquidGlass(shape, backdrop, tintAlpha = 0.34f, blurRadius = 56.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarToggle(TransportIcons.Shuffle, "Shuffle", active = state.shuffle, enabled = hasSong, onClick = player::toggleShuffle)
        BarButton(TransportIcons.Backward, "Previous", enabled = hasSong, iconSize = 20, onClick = player::previous)
        BarButton(
            if (state.isPlaying) TransportIcons.Pause else TransportIcons.Play,
            if (state.isPlaying) "Pause" else "Play",
            enabled = hasSong,
            iconSize = 25,
            onClick = player::togglePlayPause,
        )
        BarButton(TransportIcons.Forward, "Next", enabled = hasSong && state.hasNext, iconSize = 20, onClick = player::next)
        BarToggle(
            if (state.repeat == RepeatMode.ONE) TransportIcons.RepeatOne else TransportIcons.Repeat,
            "Repeat",
            active = state.repeat != RepeatMode.OFF,
            enabled = hasSong,
            onClick = player::cycleRepeat,
        )
        Spacer(Modifier.width(10.dp))

        NowPlayingSlot(
            state = state,
            player = player,
            onOpen = { onExpandPlayer(PlayerPanel.None) },
            onArtBounds = onArtBounds,
            artAlpha = artAlpha,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        Box {
            var menu by remember { mutableStateOf(false) }
            BarButton(NavIcons.More, "More", enabled = hasSong) { menu = true }
            if (song != null) SongActionsMenu(song, player, expanded = menu, onDismiss = { menu = false })
        }
        Spacer(Modifier.width(10.dp))
        BarButton(TransportIcons.Lyrics, "Lyrics", enabled = hasSong) { onExpandPlayer(PlayerPanel.Lyrics) }
        BarButton(TransportIcons.Queue, "Up next", enabled = hasSong) { onExpandPlayer(PlayerPanel.Queue) }
        OutputButton(player)
        VolumeButton(state, player)
    }
}

/** Cover, title and "artist — album", with the song's progress as a hairline along the foot. */
@Composable
private fun NowPlayingSlot(
    state: PlayerState,
    player: DesktopPlayer,
    onOpen: () -> Unit,
    onArtBounds: (Rect) -> Unit,
    artAlpha: () -> Float,
    modifier: Modifier,
) {
    val song = state.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered && song != null) 0.05f else 0f, tween(160), label = "bar_song_wash")
    val duration = state.durationSeconds
    val position = player.rememberPosition(active = song != null)
    Box(
        modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = song != null, onClick = onOpen),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 4.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val artShape = RoundedCornerShape(5.dp)
            Box(
                Modifier
                    .size(34.dp)
                    .onGloballyPositioned { onArtBounds(it.boundsInRoot()) }
                    .graphicsLayer { alpha = artAlpha() }
                    .clip(artShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (song != null) {
                    AsyncImage(song.thumbnail.resizedThumbnail(240), contentDescription = "Open player", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (state.isLoading) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                            LoadingRing(Modifier.size(16.dp), color = Color.White, stroke = 2.dp)
                        }
                    }
                } else {
                    Icon(TransportIcons.Play, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            AnimatedContent(
                targetState = song,
                contentKey = { it?.id },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "bar_song",
                modifier = Modifier.weight(1f),
            ) { current ->
                Column(verticalArrangement = Arrangement.Center) {
                    if (current == null) {
                        Text("Not Playing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(current.title, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val line = listOfNotNull(
                            current.artists.joinToString { it.name }.takeIf { it.isNotBlank() },
                            current.album?.name?.takeIf { it.isNotBlank() },
                        ).joinToString(" — ")
                        Text(
                            state.error ?: line,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (song != null) {
            DockProgress(
                fraction = { if (duration > 0) (position.value / duration).toFloat().coerceIn(0f, 1f) else 0f },
                duration = duration,
                onSeek = { if (duration > 0) player.seekTo(it * duration) },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 10.dp)
                    .offset(y = 3.dp),
            )
        }
    }
}

/** Where the sound goes: every output Windows offers, the one in use ticked. */
@Composable
private fun OutputButton(player: DesktopPlayer) {
    var open by remember { mutableStateOf(false) }
    var outputs by remember { mutableStateOf(emptyList<DesktopPlayer.AudioOutput>()) }
    Box {
        BarButton(NavIcons.Output, "Audio output") {
            outputs = player.audioOutputs()
            open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, offset = DpOffset(0.dp, (-8).dp)) {
            if (outputs.isEmpty()) {
                DropdownMenuItem(text = { Text("No outputs found") }, onClick = { open = false }, enabled = false)
            }
            val current = player.currentAudioOutput()
            outputs.forEach { output ->
                DropdownMenuItem(
                    text = { Text(output.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (output.name == current) Icon(NavIcons.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = {
                        player.setAudioOutput(output.name)
                        open = false
                    },
                )
            }
        }
    }
}

/** The speaker: click to mute, and a slider beside it while the pointer is over it. */
@Composable
private fun VolumeButton(state: PlayerState, player: DesktopPlayer) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val open by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 500f), label = "bar_volume")
    Row(Modifier.hoverable(interaction), verticalAlignment = Alignment.CenterVertically) {
        BarButton(if (state.volume == 0) TransportIcons.VolumeOff else TransportIcons.VolumeUp, "Mute", onClick = player::toggleMute)
        if (open > 0.01f) {
            GlassTrack(
                value = state.volume / 100f,
                onChange = { player.setVolume((it * 100).roundToInt()) },
                onCommit = { player.setVolume((it * 100).roundToInt()) },
                restHeight = 4.dp,
                modifier = Modifier
                    .width(96.dp * open)
                    .padding(end = 8.dp)
                    .graphicsLayer { alpha = open },
            )
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, description: String, enabled: Boolean = true, iconSize: Int = 18, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, pressedScale = 0.86f)
    val wash by animateFloatAsState(if (hovered && enabled) 0.08f else 0f, tween(140), label = "bar_button_wash")
    Box(
        Modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(iconSize.dp))
    }
}

/** Shuffle and repeat: the accent, on a soft disc of it, while they're on. */
@Composable
private fun BarToggle(icon: ImageVector, description: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val lit by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "bar_toggle")
    Box(
        Modifier
            .size(34.dp)
            .padding(3.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f * lit))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.75f else 0.3f)
        Icon(icon, description, tint = androidx.compose.ui.graphics.lerp(rest, accent, lit), modifier = Modifier.size(16.dp))
    }
}
