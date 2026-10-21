/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The page's shape — the title, a glass capsule switching Explore, Suggestion and Album, and the
 * three pages under it — follows Convx 1.5.2's search (github.com/cosmictaserdev-creator/Convx,
 * GPL-3.0).
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PureBlackKey
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.ui.component.NavigationTitle
import com.ozyern.exhale.ui.component.YouTubeGridItem
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.viewmodels.ExploreViewModel
import com.ozyern.exhale.viewmodels.MoodAndGenresViewModel
import kotlinx.coroutines.launch
import java.net.URLEncoder

private val SearchTabs = listOf("Explore", "Suggestion", "Album")

/**
 * The Search tab: "Search" and the account disc, a glass capsule switching Explore, Suggestion and
 * Album, and the three pages side by side under it. Swiping the pages slides the capsule's thumb
 * with the finger; tapping a label glides there. Typing happens in the dock's own field.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    navController: NavController,
) {
    val (pureBlack) = rememberPreference(PureBlackKey, defaultValue = false)
    val layoutDirection = LocalLayoutDirection.current
    val basePadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val chrome = LocalSearchChrome.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val start = basePadding.calculateStartPadding(layoutDirection)
    val end = basePadding.calculateEndPadding(layoutDirection)
    val bottom = basePadding.calculateBottomPadding() + 16.dp
    val ink = if (pureBlack) Color.White else MaterialTheme.colorScheme.onBackground

    val pager = rememberPagerState { SearchTabs.size }
    val openSearch: (String) -> Unit = { query ->
        navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
            .padding(start = start, end = end, top = statusTop + 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.search),
                fontSize = 34.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Bold,
                color = ink,
                modifier = Modifier.weight(1f),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (pureBlack) Color.White.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { chrome?.openAccount?.invoke() ?: navController.navigate("settings") },
            ) {
                Icon(
                    painter = painterResource(R.drawable.person),
                    contentDescription = "Profile",
                    tint = if (pureBlack) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        if (chrome?.fieldAtTop == true) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                SearchFieldPill(onClick = chrome.openSearch)
            }
        }
        SearchTabSwitch(
            pager = pager,
            ink = ink,
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 16.dp),
        )
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val padding = PaddingValues(bottom = bottom)
            when (page) {
                0 -> ExplorePage(navController, padding)
                1 -> SuggestionPage(openSearch, ink, padding)
                else -> AlbumPage(navController, padding)
            }
        }
    }
}

/**
 * The glass capsule over the pages. Its thumb sits wherever the pager is — between two labels
 * mid-swipe — so the switch and the pages move as one piece; a tap glides the pager there.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchTabSwitch(
    pager: PagerState,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val thumbColor = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.07f)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            // A plain capsule, not sampled glass: it sits on the page the app's backdrop records,
            // so glass here drew itself inside itself until the render thread's stack ran out.
            .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f))
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f), CircleShape),
    ) {
        val inset = 4.dp
        val segment = (maxWidth - inset * 2) / SearchTabs.size
        Box(
            Modifier
                .padding(inset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    val position = (pager.currentPage + pager.currentPageOffsetFraction)
                        .coerceIn(0f, (SearchTabs.size - 1).toFloat())
                    translationX = position * segment.toPx()
                }
                .clip(CircleShape)
                .background(thumbColor),
        )
        Row(Modifier.fillMaxSize().padding(inset)) {
            SearchTabs.forEachIndexed { index, label ->
                // How much of this label the thumb covers, so its colour follows the thumb across.
                val near = (1f - kotlin.math.abs(pager.currentPage + pager.currentPageOffsetFraction - index))
                    .coerceIn(0f, 1f)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { scope.launch { pager.animateScrollToPage(index) } },
                ) {
                    Text(
                        text = label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = androidx.compose.ui.graphics.lerp(ink.copy(alpha = 0.6f), accent, near),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Explore                                                             */
/* ------------------------------------------------------------------ */

/** YouTube's moods and genres, in its own sections, two tiles a row. */
@Composable
private fun ExplorePage(
    navController: NavController,
    padding: PaddingValues,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    if (sections == null) {
        PageLoading()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        sections.orEmpty().forEach { section ->
            if (section.title.isNotBlank()) {
                item(key = "title:${section.title}", contentType = "title") {
                    NavigationTitle(title = section.title)
                }
            }
            val rows = section.items.chunked(2)
            items(rows, key = { row -> section.title + row.joinToString("|") { it.endpoint.browseId + it.title } }, contentType = { "row" }) { row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    row.forEach { item ->
                        ExploreTile(
                            title = item.title,
                            stripe = Color(item.stripeColor),
                            onClick = {
                                navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ExploreTile(
    title: String,
    stripe: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "exploreTilePress",
    )
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .padding(6.dp)
            .height(72.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.09f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        // YouTube's own colour for the mood, as a thin bar at the leading edge.
        Box(
            Modifier
                .padding(start = 10.dp)
                .width(4.dp)
                .height(28.dp)
                .clip(CircleShape)
                .background(stripe.copy(alpha = 1f)),
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Suggestion                                                          */
/* ------------------------------------------------------------------ */

/** What is being played right now: Apple's most-played charts for this country. */
@Composable
private fun SuggestionPage(
    openSearch: (String) -> Unit,
    ink: Color,
    padding: PaddingValues,
) {
    var page by remember { mutableStateOf<ChartPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val loaded = AppleMusicCharts.load()
        page = loaded
        failed = loaded == null
    }
    val charts = page
    if (charts == null) {
        if (failed) PageMessage("No suggestions right now.") else PageLoading()
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item(key = "songsTitle") { NavigationTitle(title = "Top Songs") }
        itemsIndexed(charts.songs.take(15), key = { i, s -> "song$i${s.name}" }) { index, song ->
            ChartSongRow(rank = index + 1, entry = song, ink = ink, onClick = { openSearch("${song.name} ${song.artist}") })
        }
        if (charts.artists.isNotEmpty()) {
            item(key = "artistsTitle") { NavigationTitle(title = "Top Artists") }
            item(key = "artists") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(charts.artists, key = { it.name }) { artist ->
                        ChartTile(entry = artist, size = 96.dp, round = true, showArtist = false, onClick = { openSearch(artist.name) })
                    }
                }
            }
        }
        if (charts.albums.isNotEmpty()) {
            item(key = "albumsTitle") { NavigationTitle(title = "Top Albums") }
            item(key = "albums") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(charts.albums, key = { it.name + it.artist }) { album ->
                        ChartTile(entry = album, size = 150.dp, onClick = { openSearch("${album.name} ${album.artist}") })
                    }
                }
            }
        }
        if (charts.videos.isNotEmpty()) {
            item(key = "videosTitle") { NavigationTitle(title = "Top Music Videos") }
            item(key = "videos") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(charts.videos, key = { it.name + it.artist }) { video ->
                        ChartTile(entry = video, size = 220.dp, ratio = 16f / 9f, onClick = { openSearch("${video.name} ${video.artist}") })
                    }
                }
            }
        }
        item(key = "credit") {
            Text(
                text = "Charts from Apple Music",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun ChartSongRow(rank: Int, entry: ChartEntry, ink: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(ink.copy(alpha = 0.08f)),
        )
        Text(
            text = rank.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink.copy(alpha = 0.55f),
            modifier = Modifier.width(36.dp).padding(start = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(entry.name, fontSize = 16.sp, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.artist, fontSize = 13.sp, color = ink.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChartTile(
    entry: ChartEntry,
    size: Dp,
    onClick: () -> Unit,
    round: Boolean = false,
    ratio: Float = 1f,
    showArtist: Boolean = true,
) {
    Column(
        modifier = Modifier
            .width(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = entry.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(if (round) CircleShape else RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (round) Modifier.fillMaxWidth() else Modifier,
            textAlign = if (round) androidx.compose.ui.text.style.TextAlign.Center else null,
        )
        if (showArtist) {
            Text(
                text = entry.artist,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Album                                                               */
/* ------------------------------------------------------------------ */

/** New releases, two to a row. */
@Composable
private fun AlbumPage(
    navController: NavController,
    padding: PaddingValues,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val explore by viewModel.explorePage.collectAsStateWithLifecycle()
    val albums = explore?.newReleaseAlbums?.distinctBy { it.id }
    if (albums == null) {
        PageLoading()
        return
    }
    if (albums.isEmpty()) {
        PageMessage("No new releases right now.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, bottom = padding.calculateBottomPadding()),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            YouTubeGridItem(
                item = album,
                fillMaxWidth = true,
                modifier = Modifier.clickable { navController.navigate("album/${album.id}") },
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Shared                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun PageLoading() {
    Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        LoadingRing(Modifier.size(28.dp))
    }
}

@Composable
private fun PageMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(top = 48.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
    }
}

/**
 * the iOS music app's field under the title: a capsule with the glass, the prompt and a microphone at the
 * end. Tapping anywhere on it starts typing in the app's search field.
 */
@Composable
private fun SearchFieldPill(onClick: () -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fill = if (dark) Color(0xFF1C1C1E) else Color(0xFFE9E9EE)
    val ink = if (dark) Color.White else Color.Black
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Artists, Songs, Lyrics and More",
            fontSize = 17.sp,
            color = ink.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.mic),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
