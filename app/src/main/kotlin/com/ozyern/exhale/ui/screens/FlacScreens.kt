/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.openflac.OpenFlac
import com.ozyern.exhale.playback.queues.ListQueue
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.utils.OpenFlacSongs

/** Where a release opens: its key is "ia:…", "jm:…" or "wm:…", so it travels encoded. */
fun flacReleaseRoute(release: OpenFlac.Release): String =
    "flac_release/${android.net.Uri.encode(release.key)}"

/** Every FLAC shelf: concerts, independent labels, 78s, Creative Commons albums and classical. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlacScreen(navController: NavController) {
    var shelves by remember { mutableStateOf<List<OpenFlac.Shelf>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val result = OpenFlac.shelves()
        shelves = result.getOrNull()
        failed = result.isFailure
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val loaded = shelves
        when {
            loaded != null -> LazyColumn(
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "top") { Spacer(Modifier.height(statusTop + 48.dp)) }
                item(key = "title") {
                    Text(
                        text = "FLAC",
                        fontSize = 34.sp,
                        lineHeight = 41.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        text = "Lossless recordings, free to play.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    )
                }
                loaded.forEach { shelf ->
                    item(key = "shelf:${shelf.title}") {
                        NavigationTitle(title = shelf.title)
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(shelf.releases, key = { it.key }) { release ->
                                FlacReleaseTile(release, size = 150.dp, onClick = { navController.navigate(flacReleaseRoute(release)) })
                            }
                        }
                    }
                }
            }
            failed -> FlacMessage("FLAC music isn't reachable right now.")
            else -> FlacLoading()
        }
        TopAppBar(
            colors = com.ozyern.exhale.ui.component.transparentTopBarColors(),
            title = {},
            navigationIcon = { LiquidBackButton(onClick = navController::navigateUp) },
        )
    }
}

/** One release: its artwork, title and artist, Play and Shuffle, the tracks, and the licence's credit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlacReleaseScreen(navController: NavController, key: String) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<OpenFlac.Track>?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        val result = OpenFlac.tracks(key)
        tracks = result.getOrNull()
        failed = result.isFailure
    }
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val ink = MaterialTheme.colorScheme.onBackground
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    fun play(list: List<OpenFlac.Track>, start: Int, shuffle: Boolean = false) {
        val items = list.map { OpenFlacSongs.metadataFor(it).toMediaItem() }
        val ordered = if (shuffle) items.shuffled() else items
        playerConnection.playQueue(ListQueue(title = list.first().album ?: "FLAC", items = ordered, startIndex = if (shuffle) 0 else start))
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val loaded = tracks
        when {
            loaded.isNullOrEmpty() && failed -> FlacMessage("This release isn't reachable right now.")
            loaded == null -> FlacLoading()
            loaded.isEmpty() -> FlacMessage("Nothing in this release plays in FLAC.")
            else -> {
                val first = loaded.first()
                val title = first.album ?: key.substringAfter(':')
                val artists = loaded.map { it.artist }.distinct()
                val artist = if (artists.size == 1) artists.single() else "Various Artists"
                val depth = loaded.mapNotNull { it.bitDepth }.maxOrNull()
                val rate = loaded.mapNotNull { it.sampleRate }.maxOrNull()
                LazyColumn(
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "header") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth().padding(top = statusTop + 56.dp, start = 16.dp, end = 16.dp),
                        ) {
                            FlacArtwork(first.artworkUrl, title, Modifier.size(240.dp))
                            Spacer(Modifier.height(16.dp))
                            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(artist, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = listOfNotNull(
                                    "FLAC",
                                    depth?.let { "$it-bit" },
                                    rate?.let { "${it / 1000.0}".removeSuffix(".0") + " kHz" },
                                ).joinToString(" · "),
                                fontSize = 13.sp,
                                color = grey,
                            )
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                FlacActionButton("Play", R.drawable.play, Modifier.weight(1f)) { play(loaded, 0) }
                                FlacActionButton("Shuffle", R.drawable.shuffle, Modifier.weight(1f)) { play(loaded, 0, shuffle = true) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    itemsIndexed(loaded, key = { i, t -> "$i${t.url}" }) { index, track ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { play(loaded, index) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text("${index + 1}", fontSize = 15.sp, color = grey, modifier = Modifier.width(32.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 17.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (artists.size > 1) {
                                    Text(track.artist, fontSize = 13.sp, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            track.durationSeconds?.let { seconds ->
                                Text("%d:%02d".format(seconds / 60, seconds % 60), fontSize = 15.sp, color = grey, modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    // The licences ask for this much: what the recording is shared under, and where it lives.
                    item(key = "credit") {
                        val credit = first.credit
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = credit.licence?.let { "Shared under $it" } ?: "Shared for free listening",
                                fontSize = 13.sp,
                                color = grey,
                            )
                            Text(
                                text = "Recording details",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, credit.pageUrl.toUri())) }
                                    },
                            )
                        }
                    }
                }
            }
        }
        TopAppBar(
            colors = com.ozyern.exhale.ui.component.transparentTopBarColors(),
            title = {},
            navigationIcon = { LiquidBackButton(onClick = navController::navigateUp) },
        )
    }
}

@Composable
private fun FlacReleaseTile(release: OpenFlac.Release, size: Dp, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(size)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        FlacArtwork(release.artworkUrl, release.title, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(6.dp))
        Text(release.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(release.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A release's cover, or — for recordings that have none — its name over a quiet wash. */
@Composable
private fun FlacArtwork(url: String?, title: String, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.BottomStart,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                ),
            ),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun FlacActionButton(label: String, icon: Int, modifier: Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FlacLoading() {
    Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
        LoadingRing(Modifier.size(28.dp))
    }
}

@Composable
private fun FlacMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(top = 120.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
