/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.home

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongActionsMenu
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.desktop.ui.thumbnailSizeFor
import com.ozyern.exhale.innertube.models.SongItem
import java.util.Locale

/**
 * The Music app's shelf heading: the title in bold with a chevron hard against it. A label, where a
 * shelf has one ("Similar to"), leads the same line in grey rather than sitting on a line of its own.
 */
@Composable
fun MusicShelfTitle(title: String, label: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Only a label that leads into the title ("Similar to", "More by") belongs on its line;
        // "Made for you" before "Your Mix" just reads as one muddled phrase.
        if (label != null && (label.endsWith(" to", ignoreCase = true) || label.endsWith(" by", ignoreCase = true))) {
            Text("$label ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Icon(
            TransportIcons.Collapse,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 2.dp).size(16.dp).graphicsLayer { rotationZ = -90f },
        )
    }
}

/**
 * The top of the Music app's pages: a row of wide cards, each under three lines of its own — why it's
 * here in small capitals, the song, and who it's by — with the artwork filling the card and a line of
 * context set over its foot.
 */
@Composable
fun FeatureRow(picks: List<Recommendation>, state: PlayerState, player: DesktopPlayer, start: Dp) {
    LazyRow(
        contentPadding = PaddingValues(start = start, end = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        itemsIndexed(picks, key = { _, pick -> pick.song.id }) { _, pick ->
            val song = pick.song
            val active = song.id == state.current?.id
            FeatureTile(
                eyebrow = pick.reason,
                title = song.title,
                subtitle = song.artists.joinToString { it.name },
                image = song.thumbnail,
                caption = song.album?.name ?: song.artists.joinToString { it.name },
                active = active,
                playing = active && state.isPlaying,
                onClick = { if (active) player.togglePlayPause() else player.playItem(song) },
            )
        }
    }
}

/** How wide a feature card is: the Music app's, two and a bit across a typical window. */
private val FeatureWidth = 380.dp

/** One wide card from the top of a Music app page; see [FeatureRow]. */
@Composable
fun FeatureTile(
    eyebrow: String,
    title: String,
    subtitle: String?,
    image: String?,
    caption: String?,
    active: Boolean,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, pressedScale = 0.98f)
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(FeatureWidth)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Text(eyebrow.uppercase(Locale.getDefault()), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle.orEmpty(), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(236.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(if (hovered) 18.dp else 0.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            // Fetched at the card's own pixel width: a 1200 px cover here was four times the pixels to
            // download, decode and upload, all landing in the same frame as the page first scrolled.
            AsyncImage(
                image?.resizedThumbnail(thumbnailSizeFor(FeatureWidth.value * LocalDensity.current.density)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.62f))),
            )
            Row(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    caption.orEmpty(),
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    Box(Modifier.padding(start = 10.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        if (playing) EqualizerBars(Color.White, Modifier.size(14.dp)) else Icon(TransportIcons.Play, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * The Music app's song list ("Latest Songs"): rows in columns across the page, each a small cover,
 * the title over the artist, and a ⋯ for its actions, with hairlines between rows inset to the text.
 */
@Composable
fun SongListGrid(songs: List<SongItem>, state: PlayerState, player: DesktopPlayer, start: Dp, rows: Int = 4) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = start, end = 40.dp)) {
        val columns = when {
            maxWidth >= 880.dp -> 3
            maxWidth >= 560.dp -> 2
            else -> 1
        }
        val shown = songs.take(columns * rows)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (column in 0 until columns) {
                Column(Modifier.weight(1f)) {
                    val slice = shown.drop(column * rows).take(rows)
                    slice.forEachIndexed { index, song ->
                        val active = song.id == state.current?.id
                        SongListRow(
                            song = song,
                            active = active,
                            playing = active && state.isPlaying,
                            divider = index < slice.lastIndex,
                            player = player,
                            onClick = { if (active) player.togglePlayPause() else player.play(shown, shown.indexOf(song)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListRow(song: SongItem, active: Boolean, playing: Boolean, divider: Boolean, player: DesktopPlayer, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val wash by animateFloatAsState(if (hovered) 0.05f else 0f, tween(120), label = "song_row")
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                AsyncImage(song.thumbnail.resizedThumbnail(thumbnailSizeFor(42f * LocalDensity.current.density)), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (active) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                        if (playing) EqualizerBars(Color.White, Modifier.size(16.dp)) else Icon(TransportIcons.Play, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artists.joinToString { it.name }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NavIcons.More, "More", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                SongActionsMenu(song, player, expanded = menu, onDismiss = { menu = false })
            }
        }
        if (divider) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 58.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
        }
    }
}
