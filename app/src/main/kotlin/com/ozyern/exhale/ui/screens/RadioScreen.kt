/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.playback.queues.YouTubeQueue
import com.ozyern.exhale.viewmodels.MoodAndGenresViewModel
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Radio: stations that never end. Yours, made from what you have been playing; one for each of the
 * artists you listen to most; and one for every mood and genre. A tap starts the station, except a
 * mood's, which opens its page of mixes to pick from.
 */
@Composable
fun RadioScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val since = remember { LocalDateTime.now().minusDays(90).toInstant(ZoneOffset.UTC).toEpochMilli() }
    val topSongs by database.mostPlayedSongs(fromTimeStamp = since, limit = 1).collectAsState(initial = emptyList())
    val topArtists by database.mostPlayedArtists(fromTimeStamp = since, limit = 12).collectAsState(initial = emptyList())
    val moods: MoodAndGenresViewModel = hiltViewModel()
    val sections by moods.sections.collectAsState()
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()

    fun startStation(videoId: String) {
        val connection = playerConnection ?: return
        connection.playQueue(YouTubeQueue(WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")))
    }

    LazyColumn(
        contentPadding = PaddingValues(top = insets.calculateTopPadding() + 8.dp, bottom = insets.calculateBottomPadding() + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Text(
                "Radio",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        // ---- Your station ----
        item {
            val seed = topSongs.firstOrNull()
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.9f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFFA2D48), Color(0xFF7B2FF7))))
                    .clickable {
                        if (seed != null) {
                            startStation(seed.id)
                        } else {
                            Toast.makeText(context, "Play a few songs first, and your station will be made from them", Toast.LENGTH_SHORT).show()
                        }
                    },
            ) {
                seed?.song?.thumbnailUrl?.let { art ->
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alpha = 0.35f,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Icon(painterResource(R.drawable.dock_radio), null, tint = Color.White, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Your Station", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(
                        seed?.let { "Made from ${it.song.title} and what you play" } ?: "Made from what you play",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ---- Your artists' stations ----
        if (topArtists.isNotEmpty()) {
            item { RadioHeading("Stations from your artists") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(topArtists, key = { it.id }) { artist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(104.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        val radio = YouTube.artist(artist.id).getOrNull()?.artist?.radioEndpoint
                                        val connection = playerConnection ?: return@launch
                                        if (radio != null) {
                                            connection.playQueue(YouTubeQueue(radio))
                                        } else {
                                            Toast.makeText(context, "No station for ${artist.artist.name} yet", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            AsyncImage(
                                model = artist.artist.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                artist.artist.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Station",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---- Moods and genres ----
        sections.orEmpty().forEach { section ->
            item { RadioHeading(section.title.ifBlank { "Moods & genres" }) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(section.items, key = { "${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { item ->
                        val stripe = Color(item.stripeColor.toInt()).copy(alpha = 1f)
                        Box(
                            Modifier
                                .size(width = 150.dp, height = 96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Brush.linearGradient(listOf(stripe, stripe.copy(alpha = 0.55f))))
                                .clickable {
                                    navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                                }
                                .padding(12.dp),
                        ) {
                            Row(Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.dock_radio), null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
    )
}
