/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.pages

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.BenchList
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.cascade
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.GlassIconButton
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.rememberCascade
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.desktop.ui.sharedArtwork
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.AlbumReleaseType
import com.ozyern.exhale.innertube.models.Artist
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.BrowseEndpoint
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.AlbumPage
import com.ozyern.exhale.innertube.pages.ArtistPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The Content settings, applied to what a page lists. */
internal fun <T : YTItem> List<T>.clean(): List<T> = filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)

/** What a page knows about itself before it has loaded: enough to draw its header straight away. */
private class CollectionHeader(
    val kind: String,
    val title: String,
    val artwork: String?,
    val artists: List<Artist>,
    val facts: List<String>,
    val description: String?,
)

/** How long a list of songs runs, said plainly: "43 min", "1 hr 12 min". */
private fun runningTime(songs: List<SongItem>): String? {
    val seconds = songs.sumOf { it.duration ?: 0 }
    if (seconds <= 0) return null
    val minutes = (seconds + 30) / 60
    return if (minutes >= 60) "${minutes / 60} hr ${minutes % 60} min" else "$minutes min"
}

private fun songCount(count: Int): String = if (count == 1) "1 song" else "$count songs"

// ── Album ──

@Composable
fun AlbumScreen(
    browseId: String,
    title: String?,
    thumbnail: String?,
    sharedKey: String?,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(browseId) { mutableStateOf<Result<AlbumPage>?>(null) }
    LaunchedEffect(browseId, attempt) {
        result = null
        result = withContext(Dispatchers.IO) { YouTube.album(browseId) }
    }
    val page = result?.getOrNull()
    val album = page?.album
    val songs = remember(page) { page?.songs.orEmpty().clean() }
    val header = CollectionHeader(
        kind = when (album?.releaseType) {
            AlbumReleaseType.SINGLE -> "Single"
            AlbumReleaseType.EP -> "EP"
            else -> "Album"
        },
        title = album?.title ?: title.orEmpty(),
        artwork = album?.thumbnail ?: thumbnail,
        artists = album?.artists.orEmpty().ifEmpty { songs.firstOrNull()?.artists.orEmpty() },
        facts = listOfNotNull(album?.year?.toString(), songs.takeIf { it.isNotEmpty() }?.let { songCount(it.size) }, runningTime(songs)),
        description = null,
    )
    // "More by …": the lead artist's other albums and singles, fetched once the album itself is known.
    val leadArtist = header.artists.firstOrNull { it.id != null }
    val moreBy by produceState<List<YTItem>?>(null, leadArtist?.id, browseId) {
        val id = leadArtist?.id ?: return@produceState
        value = withContext(Dispatchers.IO) { YouTube.artist(id).getOrNull() }
            ?.sections.orEmpty()
            .filter { section -> section.items.all { it is AlbumItem } }
            .flatMap { it.items }
            .filterNot { it.id == browseId }
            .distinctBy { it.id }
            .clean()
            .take(16)
    }
    CollectionPage(
        pageKey = "album:$browseId",
        header = header,
        sharedKey = sharedKey,
        songs = songs,
        loading = result == null,
        failed = result?.isFailure == true,
        onRetry = { attempt++ },
        showArtwork = false,
        player = player,
        state = state,
        insets = insets,
        onBack = onBack,
        onPlay = { shuffled -> if (songs.isNotEmpty()) player.play(if (shuffled) songs.shuffled() else songs, 0) },
        footer = if (page == null) null else {
            {
                Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    // When it came out and how long it runs, set quietly under the last song.
                    Text(
                        listOfNotNull(album?.year?.let { "Released $it" }, songCount(songs.size), runningTime(songs)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    moreBy?.takeIf { it.isNotEmpty() }?.let { ShelfSection("More by ${leadArtist?.name.orEmpty()}", it, state, player) }
                    page.otherVersions.takeIf { it.isNotEmpty() }?.let { ShelfSection("Other versions", it, state, player) }
                }
            }
        },
    )
}

// ── Playlist ──

@Composable
fun PlaylistScreen(
    playlistId: String,
    title: String?,
    thumbnail: String?,
    sharedKey: String?,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var loaded by remember(playlistId) { mutableStateOf<com.ozyern.exhale.innertube.pages.PlaylistPage?>(null) }
    var failed by remember(playlistId) { mutableStateOf(false) }
    var songs by remember(playlistId) { mutableStateOf<List<SongItem>>(emptyList()) }
    var continuation by remember(playlistId) { mutableStateOf<String?>(null) }
    var loadingMore by remember(playlistId) { mutableStateOf(false) }
    LaunchedEffect(playlistId, attempt) {
        failed = false
        withContext(Dispatchers.IO) { YouTube.playlist(playlistId) }
            .onSuccess { page ->
                loaded = page
                songs = page.songs.clean().distinctBy { it.id }
                continuation = page.songsContinuation ?: page.continuation
            }
            .onFailure { if (it is CancellationException) throw it else failed = true }
    }
    // Long playlists arrive a page at a time as the list nears its end.
    var wantMore by remember(playlistId) { mutableIntStateOf(0) }
    LaunchedEffect(wantMore) {
        val next = continuation ?: return@LaunchedEffect
        if (wantMore == 0 || loadingMore) return@LaunchedEffect
        loadingMore = true
        withContext(Dispatchers.IO) { YouTube.playlistContinuation(next) }
            .onSuccess { more ->
                songs = (songs + more.songs.clean()).distinctBy { it.id }
                continuation = more.continuation?.takeIf { it != next && more.songs.isNotEmpty() }
            }
            .onFailure { if (it is CancellationException) throw it else continuation = null }
        loadingMore = false
    }

    val playlist = loaded?.playlist
    val header = CollectionHeader(
        kind = "Playlist",
        title = playlist?.title ?: title.orEmpty(),
        artwork = playlist?.thumbnail ?: thumbnail,
        artists = listOfNotNull(playlist?.author),
        facts = listOfNotNull(playlist?.songCountText ?: songs.takeIf { it.isNotEmpty() }?.let { songCount(it.size) }, runningTime(songs)),
        description = playlist?.description,
    )
    CollectionPage(
        pageKey = "playlist:$playlistId",
        header = header,
        sharedKey = sharedKey,
        songs = songs,
        loading = loaded == null && !failed,
        failed = failed,
        onRetry = { attempt++ },
        showArtwork = true,
        player = player,
        state = state,
        insets = insets,
        onBack = onBack,
        onPlay = { shuffled -> if (songs.isNotEmpty()) player.play(if (shuffled) songs.shuffled() else songs, 0) },
        onNearEnd = { if (continuation != null && !loadingMore) wantMore++ },
        loadingMore = loadingMore,
    )
}

/**
 * An album or a playlist, laid out for a wide window: the cover
 * and what it is on the left, standing still, and the songs scrolling beside it. Narrow windows stack
 * the two. The cover arrives from the card that was clicked (see sharedArtwork), the page is washed in
 * the cover's colour, and the songs cascade in once they've loaded.
 */
@Composable
private fun CollectionPage(
    pageKey: String,
    header: CollectionHeader,
    sharedKey: String?,
    songs: List<SongItem>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    showArtwork: Boolean,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
    onPlay: (shuffled: Boolean) -> Unit,
    footer: (@Composable () -> Unit)? = null,
    onNearEnd: () -> Unit = {},
    loadingMore: Boolean = false,
) {
    val cascade = rememberCascade(pageKey, ready = !loading)
    val listState = rememberLazyListState()
    LaunchedEffect(listState, songs.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 6) onNearEnd() }
    }

    ArtworkWash(header.artwork) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val start = insets.start + 20.dp
            val tracks: LazyListScope.() -> Unit = {
                when {
                    failed -> item(key = "failed") { PageError(onRetry) }
                    loading -> items(count = 8, key = { "skeleton_$it" }) { TrackPlaceholder() }
                    songs.isEmpty() -> item(key = "empty") { Hint("Nothing to play here.") }
                    else -> trackItems(songs, state, player, cascade, showArtwork)
                }
                if (loadingMore) items(count = 3, key = { "more_$it" }) { TrackPlaceholder() }
                if (footer != null) item(key = "footer") { Box(Modifier.feedIntro(3f, cascade).padding(top = 28.dp)) { footer() } }
            }

            if (maxWidth >= 1080.dp) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .width(380.dp)
                            .fillMaxHeight()
                            .padding(start = start, top = 16.dp, end = 12.dp, bottom = insets.bottom),
                    ) {
                        GlassIconButton(NavIcons.Back, "Back", onBack)
                        Spacer(Modifier.height(22.dp))
                        Cover(header.artwork, 320.dp, sharedKey, RoundedCornerShape(22.dp))
                        Spacer(Modifier.height(26.dp))
                        CollectionInfo(header, canPlay = songs.isNotEmpty(), onPlay = onPlay, titleStyle = MaterialTheme.typography.headlineMedium)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(start = 12.dp, end = 40.dp, top = 80.dp, bottom = insets.bottom + 16.dp),
                            modifier = Modifier.fillMaxSize(),
                            content = tracks,
                        )
                        PageScrollbar(listState, insets)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = start, end = 40.dp, top = 16.dp, bottom = insets.bottom + 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "back") { GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(bottom = 20.dp)) }
                        item(key = "header") {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 28.dp)) {
                                Cover(header.artwork, 230.dp, sharedKey, RoundedCornerShape(18.dp))
                                Spacer(Modifier.width(28.dp))
                                CollectionInfo(header, canPlay = songs.isNotEmpty(), onPlay = onPlay, titleStyle = MaterialTheme.typography.headlineSmall)
                            }
                        }
                        tracks()
                    }
                    PageScrollbar(listState, insets)
                }
            }
        }
    }
}

@Composable
internal fun PageScrollbar(state: LazyListState, insets: ChromeInsets) {
    Box(Modifier.fillMaxSize()) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 16.dp, bottom = insets.bottom, end = 6.dp),
        )
    }
}

/**
 * The page's ground: the cover's colour pooled at the top and draining away. A gradient rather than a
 * blurred copy of the cover, so it costs nothing to scroll over.
 */
@Composable
internal fun ArtworkWash(artwork: String?, content: @Composable () -> Unit) {
    val colors by rememberArtworkColors(artwork?.resizedThumbnail(120))
    val tint by animateColorAsState(colors.firstOrNull() ?: Color.Transparent, tween(900), label = "page_wash")
    val second by animateColorAsState(colors.getOrNull(1) ?: colors.firstOrNull() ?: Color.Transparent, tween(900), label = "page_wash_2")
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxWidth().height(620.dp).background(
                Brush.verticalGradient(listOf(tint.copy(alpha = tint.alpha * 0.34f), second.copy(alpha = second.alpha * 0.10f), Color.Transparent)),
            ),
        )
        content()
    }
}

/**
 * The cover: the card's own thumbnail first — already in memory, so the flight never shows an empty
 * frame — and a sharp copy fading in over it once that has loaded.
 */
@Composable
private fun Cover(artwork: String?, size: Dp, sharedKey: String?, shape: Shape) {
    val colors by rememberArtworkColors(artwork?.resizedThumbnail(120))
    val glow by animateColorAsState(colors.firstOrNull() ?: Color.Transparent, tween(800), label = "cover_glow")
    val tiltOn = !LocalSettings.current.reducedMotion
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val motion = rememberCoroutineScope()
    Box(Modifier.size(size)) {
        // The cover's own colour pooled under it, like light through a record sleeve.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = 1.22f
                    scaleY = 1.14f
                    translationY = 22.dp.toPx()
                }
                .background(Brush.radialGradient(listOf(glow.copy(alpha = glow.alpha * 0.55f), Color.Transparent))),
        )
        Box(
            Modifier
                .sharedArtwork(sharedKey, shape)
                .matchParentSize()
                .pointerInput(tiltOn) {
                    if (!tiltOn) return@pointerInput
                    val area = this.size
                    // Leans toward the pointer, a little — the same as the cards it came from.
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val at = event.changes.firstOrNull()?.position ?: continue
                            val inside = event.type != PointerEventType.Exit
                            val x = if (inside) (0.5f - at.y / area.height.coerceAtLeast(1)) * 9f else 0f
                            val y = if (inside) (at.x / area.width.coerceAtLeast(1) - 0.5f) * 9f else 0f
                            motion.launch { tiltX.animateTo(x, spring(dampingRatio = 0.7f, stiffness = 220f)) }
                            motion.launch { tiltY.animateTo(y, spring(dampingRatio = 0.7f, stiffness = 220f)) }
                        }
                    }
                }
                .graphicsLayer {
                    rotationX = tiltX.value
                    rotationY = tiltY.value
                    cameraDistance = 16f * density
                }
                .shadow(28.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(artwork?.resizedThumbnail(544), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            AsyncImage(artwork?.resizedThumbnail(1080), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CollectionInfo(header: CollectionHeader, canPlay: Boolean, onPlay: (Boolean) -> Unit, titleStyle: TextStyle) {
    // The header's lines rise in one after another, as on an artist's page.
    val reduced = LocalSettings.current.reducedMotion
    val settle = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, spring(dampingRatio = 1f, stiffness = 70f)) }
    val progress: () -> Float = { settle.value }
    Column {
        Text(
            header.kind.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.12.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.stage(0.05f, progress),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            header.title,
            style = titleStyle.copy(letterSpacing = (-0.02).em),
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.stage(0.12f, progress),
        )
        if (header.artists.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ArtistLinks(header.artists, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), MaterialTheme.colorScheme.primary, Modifier.stage(0.2f, progress))
        }
        if (header.facts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                header.facts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.stage(0.26f, progress),
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.stage(0.34f, progress)) {
            PlayCircle(diameter = 54.dp, enabled = canPlay, description = "Play ${header.title}") { onPlay(false) }
            Spacer(Modifier.width(12.dp))
            PillButton("Shuffle", TransportIcons.Shuffle, primary = false, enabled = canPlay) { onPlay(true) }
        }
        header.description?.takeIf { it.isNotBlank() }?.let { text ->
            Spacer(Modifier.height(18.dp))
            Box(Modifier.stage(0.42f, progress)) { ExpandableText(text, collapsedLines = 4) }
        }
    }
}

private fun LazyListScope.trackItems(songs: List<SongItem>, state: PlayerState, player: DesktopPlayer, cascade: () -> Float, showArtwork: Boolean) {
    itemsIndexed(songs, key = { i, song -> "$i:${song.id}" }) { index, song ->
        val active = song.id == state.current?.id
        TrackRow(
            number = index + 1,
            song = song,
            active = active,
            playing = state.isPlaying,
            showArtwork = showArtwork,
            onClick = { if (active) player.togglePlayPause() else player.play(songs, index) },
            modifier = Modifier.cascade(index, cascade),
        )
    }
}

// ── "See all" ──

@Composable
fun ArtistItemsScreen(
    browseId: String,
    params: String?,
    title: String,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var items by remember(browseId, params) { mutableStateOf<List<YTItem>?>(null) }
    var failed by remember(browseId, params) { mutableStateOf(false) }
    var continuation by remember(browseId, params) { mutableStateOf<String?>(null) }
    var loadingMore by remember(browseId, params) { mutableStateOf(false) }
    var wantMore by remember(browseId, params) { mutableIntStateOf(0) }
    LaunchedEffect(browseId, params, attempt) {
        failed = false
        withContext(Dispatchers.IO) { YouTube.artistItems(BrowseEndpoint(browseId, params)) }
            .onSuccess { page ->
                items = page.items.clean()
                continuation = page.continuation
            }
            .onFailure { if (it is CancellationException) throw it else failed = true }
    }
    LaunchedEffect(wantMore) {
        val next = continuation ?: return@LaunchedEffect
        if (wantMore == 0 || loadingMore) return@LaunchedEffect
        loadingMore = true
        withContext(Dispatchers.IO) { YouTube.artistItemsContinuation(next) }
            .onSuccess { more ->
                items = (items.orEmpty() + more.items.clean()).distinctBy { it.id }
                continuation = more.continuation?.takeIf { it != next && more.items.isNotEmpty() }
            }
            .onFailure { if (it is CancellationException) throw it else continuation = null }
        loadingMore = false
    }

    val loaded = items
    val cascade = rememberCascade("items:$browseId:$params", ready = loaded != null)
    val open = rememberItemOpener(player)
    val start = insets.start + 20.dp
    val songs = loaded?.filterIsInstance<SongItem>().orEmpty()
    val allSongs = loaded != null && loaded.isNotEmpty() && songs.size == loaded.size
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to gridState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && total > 0 && last >= total - 8 && continuation != null && !loadingMore) wantMore++ }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = if (allSongs) GridCells.Adaptive(520.dp) else GridCells.Adaptive(190.dp),
        contentPadding = PaddingValues(start = start, end = 40.dp, top = 16.dp, bottom = insets.bottom + 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (allSongs) 16.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (allSongs) 2.dp else 22.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(bottom = 16.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) { LargeTitle(title, Modifier.padding(bottom = 14.dp)) }
        when {
            failed -> item(span = { GridItemSpan(maxLineSpan) }) { PageError { attempt++ } }
            loaded == null -> items(count = 8, span = { GridItemSpan(maxLineSpan) }) { TrackPlaceholder() }
            loaded.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) { Hint("Nothing here yet.") }
            allSongs -> itemsIndexed(songs, key = { i, s -> "$i:${s.id}" }) { index, song ->
                val active = song.id == state.current?.id
                TrackRow(
                    number = null,
                    song = song,
                    active = active,
                    playing = state.isPlaying,
                    showArtwork = true,
                    onClick = { if (active) player.togglePlayPause() else player.play(songs, index) },
                    modifier = Modifier.cascade(index, cascade),
                )
            }
            else -> itemsIndexed(loaded, key = { i, item -> "$i:${item.id}" }) { index, item ->
                Box(Modifier.cascade(index, cascade)) {
                    ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = 176.dp, onPlay = { player.playNow(item) })
                }
            }
        }
    }
}

// ── Pieces ──

/** A shelf of cards under a heading, for the end of a page ("Other versions"). */
@Composable
private fun ShelfSection(title: String, items: List<YTItem>, state: PlayerState, player: DesktopPlayer) {
    val open = rememberItemOpener(player)
    Column {
        SectionTitle(title, onSeeAll = null, modifier = Modifier.padding(start = 12.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, onPlay = { player.playNow(item) })
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, onSeeAll: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Text(
                "See all",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (hovered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (hovered) 0.12f else 0.06f))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onSeeAll)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/**
 * One song in a list: its number (a play glyph under the pointer, bouncing bars while it plays), the
 * cover when the list mixes albums, title and linked artists, and the time.
 */
@Composable
fun TrackRow(
    number: Int?,
    song: SongItem,
    active: Boolean,
    playing: Boolean,
    showArtwork: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val press by pressScale(pressed, pressedScale = 0.985f)
    val shape = RoundedCornerShape(14.dp)
    val accent = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .then(
                when {
                    active -> Modifier.glassPlate(shape, base = accent.copy(alpha = 0.14f))
                    hovered -> Modifier.glassPlate(shape)
                    else -> Modifier.clip(shape)
                },
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (number != null) {
            Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
                when {
                    active && playing -> EqualizerBars(accent, Modifier.size(16.dp))
                    active || hovered -> Icon(TransportIcons.Play, contentDescription = "Play", tint = if (active) accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    else -> Text("$number", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        if (showArtwork) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                AsyncImage(song.thumbnail.resizedThumbnail(160), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (number == null && active) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        if (playing) EqualizerBars(Color.White, Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.explicit) {
                    Spacer(Modifier.width(6.dp))
                    Icon(NavIcons.Explicit, contentDescription = "Explicit", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }
            if (song.artists.isNotEmpty()) {
                ArtistLinks(song.artists, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        song.duration?.let {
            Spacer(Modifier.width(12.dp))
            Text(formatTime(it.toDouble()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Artist names, each one a link to its page when YouTube gave it an id. */
@Composable
fun ArtistLinks(artists: List<Artist>, style: TextStyle, color: Color, modifier: Modifier = Modifier, suffix: String? = null) {
    val nav = LocalNavigator.current
    val hover = MaterialTheme.colorScheme.onSurface
    val text = remember(artists, nav, suffix, hover) {
        buildAnnotatedString {
            artists.forEachIndexed { i, artist ->
                if (i > 0) append(", ")
                val id = artist.id
                if (id != null && nav != null) {
                    val link = LinkAnnotation.Clickable(
                        tag = "artist:$id",
                        styles = TextLinkStyles(hoveredStyle = SpanStyle(textDecoration = TextDecoration.Underline, color = hover)),
                    ) { nav.openArtist(id, artist.name, null) }
                    withLink(link) { append(artist.name) }
                } else {
                    append(artist.name)
                }
            }
            suffix?.let { append(it) }
        }
    }
    Text(text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
internal fun PillButton(text: String, icon: ImageVector, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.93f, hoverScale = 1.04f)
    val background = if (primary) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = if (hovered) 0.17f else 0.10f)
    val content = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .height(46.dp)
            .shadow(if (primary && hovered) 16.dp else 0.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = content)
    }
}

@Composable
internal fun ExpandableText(text: String, collapsedLines: Int) {
    var expanded by remember(text) { mutableStateOf(false) }
    var overflowing by remember(text) { mutableStateOf(false) }
    Column {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
        )
        if (overflowing || expanded) {
            Text(
                if (expanded) "Less" else "More",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
internal fun TrackPlaceholder() {
    val pulse by rememberInfiniteTransition(label = "track_skeleton").animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "track_skeleton_alpha",
    )
    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = pulse }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).glassPlate(RoundedCornerShape(9.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.45f).height(14.dp).glassPlate(RoundedCornerShape(6.dp)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.25f).height(11.dp).glassPlate(RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
internal fun PageError(onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 28.dp)) {
        Text("Couldn't load this page.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text("Check your connection, then try again.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        PillButton("Try again", NavIcons.Reset, primary = true, onClick = onRetry)
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 28.dp))
}
