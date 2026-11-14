/**
 * Exhale Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.flac

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.openflac.OpenFlac
import com.ozyern.exhale.playback.FlacSongs
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.ScreenPage
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.iosRowPress
import com.ozyern.exhale.ui.utils.bounceClick

const val FlacRoute = "flac"
const val FlacReleaseRoute = "flac/release/{key}"

fun flacReleaseRoute(release: OpenFlac.Release): String = "flac/release/" + Uri.encode(release.key)

private sealed interface Loaded<out T> {
    data object Loading : Loaded<Nothing>
    data class Ready<T>(val value: T) : Loaded<T>
    data class Failed(val message: String) : Loaded<Nothing>
}

/** The FLAC page: shelves of free-to-play lossless releases, each a row of album tiles. */
@Composable
fun FlacScreen(navController: NavController) {
    var shelves by remember { mutableStateOf<Loaded<List<OpenFlac.Shelf>>>(Loaded.Loading) }
    LaunchedEffect(Unit) {
        shelves = OpenFlac.shelves().fold(
            onSuccess = { Loaded.Ready(it.filter { shelf -> shelf.releases.isNotEmpty() }) },
            onFailure = { Loaded.Failed(it.message ?: "") },
        )
    }

    ScreenPage(title = "FLAC", onBack = { navController.navigateUp() }) {
        Text(
            "Lossless music that's free to play: concert tapes, netlabels, archive transfers and Creative Commons albums.",
            color = IosTheme.colors.secondaryLabel,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
        )
        when (val state = shelves) {
            Loaded.Loading -> FlacMessage("Loading…")
            is Loaded.Failed -> FlacMessage("Couldn't reach the FLAC catalogues. Check your connection and try again.")
            is Loaded.Ready -> state.value.forEach { shelf ->
                Text(
                    shelf.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = IosTheme.colors.label,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    shelf.releases.forEach { release ->
                        FlacReleaseTile(release, size = 152.dp) { navController.navigate(flacReleaseRoute(release)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** One release: its sleeve, title and artist, Play and Shuffle, and its FLAC files in order. */
@Composable
fun FlacReleaseScreen(navController: NavController, key: String) {
    val playerConnection = LocalPlayerConnection.current
    val uriHandler = LocalUriHandler.current
    var tracks by remember(key) { mutableStateOf<Loaded<List<OpenFlac.Track>>>(Loaded.Loading) }
    LaunchedEffect(key) {
        tracks = OpenFlac.tracks(key).fold(
            onSuccess = { Loaded.Ready(it) },
            onFailure = { Loaded.Failed(it.message ?: "") },
        )
    }
    val list = (tracks as? Loaded.Ready)?.value.orEmpty()
    val first = list.firstOrNull()
    val title = first?.album ?: first?.title ?: "FLAC"

    fun play(start: Int, shuffle: Boolean = false) {
        if (list.isEmpty()) return
        val ordered = if (shuffle) list.shuffled() else list
        playerConnection?.playQueue(
            ListQueue(
                title = title,
                items = ordered.map { FlacSongs.metadataFor(it).toMediaItem() },
                startIndex = if (shuffle) 0 else start,
            ),
        )
    }

    ScreenPage(title = title, onBack = { navController.navigateUp() }) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlacArtwork(first?.artworkUrl, Modifier.widthIn(max = 280.dp).fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = IosTheme.colors.label, textAlign = TextAlign.Center)
            first?.artist?.let {
                Text(it, fontSize = 20.sp, color = IosTheme.tint, textAlign = TextAlign.Center)
            }
            first?.let { track ->
                Text(
                    listOfNotNull("FLAC", formatOf(track)).joinToString(" · "),
                    fontSize = 13.sp,
                    color = IosTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FlacActionButton("Play", "play.fill", Modifier.weight(1f), enabled = list.isNotEmpty()) { play(0) }
                FlacActionButton("Shuffle", "shuffle", Modifier.weight(1f), enabled = list.isNotEmpty()) { play(0, shuffle = true) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (val state = tracks) {
            Loaded.Loading -> FlacMessage("Loading…")
            is Loaded.Failed -> FlacMessage("Couldn't load this release.")
            is Loaded.Ready -> {
                state.value.forEachIndexed { index, track -> FlacTrackRow(index + 1, track) { play(index) } }
                first?.credit?.let { credit ->
                    Text(
                        listOfNotNull(credit.licence, "View source").joinToString(" · "),
                        fontSize = 13.sp,
                        color = IosTheme.colors.secondaryLabel,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .bounceClick { uriHandler.openUri(credit.pageUrl) },
                    )
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FlacReleaseTile(release: OpenFlac.Release, size: Dp, onClick: () -> Unit) {
    Column(Modifier.width(size).bounceClick(onClick = onClick)) {
        FlacArtwork(release.artworkUrl, Modifier.size(size))
        Text(
            release.title,
            fontSize = 13.sp,
            color = IosTheme.colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            release.artist,
            fontSize = 13.sp,
            color = IosTheme.colors.secondaryLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FlacArtwork(url: String?, modifier: Modifier) {
    val shape = ContinuousRoundedRectangle(10.dp)
    Box(
        modifier.aspectRatio(1f).clip(shape).background(IosTheme.colors.secondarySystemGroupedBackground),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(symbol = sf("waveform"), contentDescription = null, size = 36.dp, tint = IosTheme.colors.tertiaryLabel)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun FlacTrackRow(number: Int, track: OpenFlac.Track, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).iosRowPress(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), fontSize = 15.sp, color = IosTheme.colors.secondaryLabel, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontSize = 17.sp, color = IosTheme.colors.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            formatOf(track)?.let { Text(it, fontSize = 12.sp, color = IosTheme.colors.secondaryLabel) }
        }
        track.durationSeconds?.let {
            Text("%d:%02d".format(it / 60, it % 60), fontSize = 15.sp, color = IosTheme.colors.secondaryLabel)
        }
    }
    Box(
        Modifier.padding(start = 48.dp).fillMaxWidth().height(IosMetrics.SeparatorThickness).background(IosTheme.colors.separator),
    )
}

@Composable
private fun FlacActionButton(label: String, symbol: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    val shape = ContinuousRoundedRectangle(12.dp)
    Row(
        modifier.height(48.dp).clip(shape).background(IosTheme.colors.secondarySystemGroupedBackground)
            .bounceClick(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolIcon(symbol = sf(symbol), contentDescription = null, size = 17.dp, tint = IosTheme.tint)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) IosTheme.tint else IosTheme.colors.tertiaryLabel)
    }
}

@Composable
private fun FlacMessage(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = IosTheme.colors.secondaryLabel,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
    )
}

private fun formatOf(track: OpenFlac.Track): String? {
    val depth = track.bitDepth
    val rate = track.sampleRate
    return when {
        depth != null && rate != null -> "$depth-bit / ${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz"
        rate != null -> "${"%.1f".format(rate / 1000.0).removeSuffix(".0")} kHz"
        else -> null
    }
}
