/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.pages

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.OpenFlacSongs
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongRow
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.openflac.OpenFlac

/**
 * The FLAC row at the top of Mood & Genres: a few releases from every shelf under "FLAC ›", whose
 * title opens the whole page. Nothing is drawn while it loads or if it can't.
 */
@Composable
fun FlacShelf() {
    val nav = LocalNavigator.current
    val shelves by produceState<List<OpenFlac.Shelf>?>(null) { value = OpenFlac.shelves().getOrNull() }
    val releases = shelves?.flatMap { it.releases.take(4) }?.takeIf { it.isNotEmpty() } ?: return
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "FLAC ›",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .padding(top = 16.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { nav?.openFlac?.invoke() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(releases, key = { it.key }) { release ->
                FlacCard(release, 168.dp) { nav?.openFlacRelease?.invoke(release.key) }
            }
        }
    }
}

/** Every FLAC shelf: concerts, independent labels, 78s, Creative Commons albums and classical. */
@Composable
fun FlacScreen(insets: ChromeInsets, onBack: (() -> Unit)?) {
    val nav = LocalNavigator.current
    val result by produceState<Result<List<OpenFlac.Shelf>>?>(null) { value = OpenFlac.shelves() }
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 16.dp, bottom = insets.bottom + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (onBack != null) item(key = "back") { GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(bottom = 18.dp)) }
            item(key = "title") {
                LargeTitle("FLAC")
                Text(
                    "Lossless recordings, free to play.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val shelves = result?.getOrNull()
            when {
                result == null -> item(key = "loading") { Box(Modifier.padding(top = 26.dp).fillMaxWidth().height(240.dp).glassPlate(RoundedCornerShape(16.dp))) }
                shelves == null -> item(key = "failed") { FlacNote("FLAC music isn't reachable right now.") }
                else -> shelves.forEach { shelf ->
                    item(key = "shelf:${shelf.title}") {
                        Text(shelf.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(shelf.releases, key = { it.key }) { release ->
                                FlacCard(release, 168.dp) { nav?.openFlacRelease?.invoke(release.key) }
                            }
                        }
                    }
                }
            }
        }
        PageScrollbar(listState, insets)
    }
}

/** One release: artwork, title, artist and format; Play and Shuffle; the tracks; the licence's credit. */
@Composable
fun FlacReleaseScreen(key: String, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets, onBack: () -> Unit) {
    val result by produceState<Result<List<OpenFlac.Track>>?>(null, key) { value = OpenFlac.tracks(key) }
    val uri = LocalUriHandler.current
    val listState = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = insets.start + 20.dp, end = 40.dp, top = 16.dp, bottom = insets.bottom + 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "back") { GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(bottom = 18.dp)) }
            val tracks = result?.getOrNull()
            when {
                result == null -> item(key = "loading") { Box(Modifier.fillMaxWidth().height(240.dp).glassPlate(RoundedCornerShape(16.dp))) }
                tracks == null -> item(key = "failed") { FlacNote("This release isn't reachable right now.") }
                tracks.isEmpty() -> item(key = "empty") { FlacNote("Nothing in this release plays in FLAC.") }
                else -> {
                    val songs = tracks.map(OpenFlacSongs::songFor)
                    val first = tracks.first()
                    val title = first.album ?: key.substringAfter(':')
                    val artists = tracks.map { it.artist }.distinct()
                    item(key = "header") {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 20.dp)) {
                            FlacArtwork(first.artworkUrl, title, Modifier.size(220.dp))
                            Column(Modifier.padding(start = 24.dp).weight(1f)) {
                                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    if (artists.size == 1) artists.single() else "Various Artists",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOfNotNull(
                                        "FLAC",
                                        tracks.mapNotNull { it.bitDepth }.maxOrNull()?.let { "$it-bit" },
                                        "${tracks.size} songs",
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                                    FlacButton("Play") { player.play(songs, 0) }
                                    FlacButton("Shuffle") { player.play(songs.shuffled(), 0) }
                                }
                            }
                        }
                    }
                    itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { i, song ->
                        SongRow(song, isActive = state.current?.id == song.id, onClick = {
                            if (state.current?.id == song.id) player.togglePlayPause() else player.play(songs, i)
                        })
                    }
                    // The licences ask for this much: what the recording is shared under, and where it lives.
                    item(key = "credit") {
                        Column(Modifier.padding(top = 20.dp)) {
                            Text(
                                first.credit.licence?.let { "Shared under $it" } ?: "Shared for free listening",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Recording details",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp).clickable { runCatching { uri.openUri(first.credit.pageUrl) } },
                            )
                        }
                    }
                }
            }
        }
        PageScrollbar(listState, insets)
    }
}

@Composable
private fun FlacCard(release: OpenFlac.Release, size: Dp, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.02f)
    Column(
        modifier = Modifier
            .width(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        FlacArtwork(release.artworkUrl, release.title, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(8.dp))
        Text(release.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(release.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A release's cover, or — for recordings that have none — its name over a quiet wash. */
@Composable
private fun FlacArtwork(url: String?, title: String, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.18f)))),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun FlacButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .width(130.dp)
            .glassPlate(RoundedCornerShape(percent = 50))
            .clickable(onClick = onClick),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FlacNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 26.dp))
}
