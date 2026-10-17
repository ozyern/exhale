/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.pages

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
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
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberCascade
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.pages.ArtistPage
import com.ozyern.exhale.innertube.pages.ArtistSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The cards' arrival curve: a hair past the mark and back, as in ColorOS 17. */
private val Overshoot = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)

/**
 * An artist's page, laid out for a Mac-sized window: their banner across the top with the
 * name set big over it, their newest release beside their top songs, then albums, singles, videos
 * and the artists fans also listen to — each in the form that suits it — and an About card at the end.
 * Once the banner has scrolled away a slim bar keeps the name and a play button in reach.
 */
@Composable
fun ArtistScreen(
    browseId: String,
    name: String?,
    thumbnail: String?,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onBack: () -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(browseId) { mutableStateOf<Result<ArtistPage>?>(null) }
    LaunchedEffect(browseId, attempt) {
        result = null
        result = withContext(Dispatchers.IO) { YouTube.artist(browseId) }
    }
    val page = result?.getOrNull()
    val artist = page?.artist
    val title = artist?.title ?: name.orEmpty()
    val image = artist?.thumbnail ?: thumbnail
    val audience = artist?.monthlyListenerCountText ?: artist?.subscriberCountText
    val nav = LocalNavigator.current
    val listState = rememberLazyListState()
    BenchList(listState, home = false)
    val cascade = rememberCascade("artist:$browseId", ready = result != null)
    val sections = remember(page) { page?.sections.orEmpty().map { it.copy(items = it.items.clean()) }.filter { it.items.isNotEmpty() } }
    val topIndex = sections.indexOfFirst { section -> section.items.all { it is SongItem } && !section.isVideos() }
    val latest = remember(sections) { latestRelease(sections) }
    val canPlay = artist != null && (artist.shuffleEndpoint != null || artist.radioEndpoint != null)
    val shuffle: () -> Unit = { artist?.let { player.playItem(if (it.shuffleEndpoint != null) it.copy(radioEndpoint = null) else it) } }
    val radio: () -> Unit = { artist?.let { player.playItem(if (it.radioEndpoint != null) it.copy(shuffleEndpoint = null) else it) } }
    // The big button plays the artist's best songs in their own order. It used to shuffle, which is
    // what the button beside it says it does — two controls, one behaviour, and no way to simply play.
    val topSongs = remember(sections, topIndex) {
        sections.getOrNull(topIndex)?.items?.filterIsInstance<SongItem>().orEmpty()
    }
    val playTop: () -> Unit = { if (topSongs.isNotEmpty()) player.play(topSongs, 0) else shuffle() }
    val start = insets.start + 20.dp

    var heroHeight by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val pastHero by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > heroHeight - 180f }
    }

    ArtworkWash(image) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = insets.bottom + 16.dp), modifier = Modifier.fillMaxSize()) {
                item(key = "hero") {
                    ArtistHero(
                        name = title,
                        image = image,
                        audience = audience,
                        description = page?.description,
                        start = start,
                        listState = listState,
                        canPlay = canPlay || topSongs.isNotEmpty(),
                        onPlay = playTop,
                        onShuffle = shuffle,
                        onRadio = radio,
                        onHeight = { heroHeight = it },
                    )
                }
                when {
                    result?.isFailure == true -> item(key = "failed") { Box(Modifier.padding(start = start)) { PageError { attempt++ } } }
                    result == null -> items(count = 5, key = { "skeleton_$it" }) { Box(Modifier.padding(start = start, end = 40.dp)) { TrackPlaceholder() } }
                }

                val top = sections.getOrNull(topIndex)
                if (top != null || latest != null) {
                    item(key = "spotlight") {
                        Spotlight(
                            top = top,
                            latest = latest,
                            state = state,
                            player = player,
                            cascade = cascade,
                            onSeeAll = top?.moreEndpoint?.let { endpoint -> { nav?.openArtistItems?.invoke(endpoint, "$title · ${top.title}"); Unit } },
                            modifier = Modifier.padding(start = start, end = 40.dp, top = 30.dp),
                        )
                    }
                }

                sections.forEachIndexed { index, section ->
                    if (index == topIndex) return@forEachIndexed
                    val seeAll = section.moreEndpoint?.let { endpoint -> { nav?.openArtistItems?.invoke(endpoint, "$title · ${section.title}"); Unit } }
                    item(key = "h$index") {
                        SectionTitle(section.title, seeAll, Modifier.feedIntro(index + 1f, cascade).padding(start = start, end = 40.dp, top = 38.dp, bottom = 14.dp))
                    }
                    item(key = "r$index") {
                        ArtistShelf(section, state, player, cascade, start, Modifier.feedIntro(index + 1.4f, cascade))
                    }
                }

                page?.description?.takeIf { it.isNotBlank() }?.let { about ->
                    item(key = "about") {
                        AboutArtist(title, image, about, audience, Modifier.feedIntro(sections.size + 1f, cascade).padding(start = start, end = 40.dp, top = 44.dp))
                    }
                }
            }
            PageScrollbar(listState, insets)
            StickyArtistBar(pastHero, title, image, start, canPlay || topSongs.isNotEmpty(), onPlay = playTop)
            GlassIconButton(NavIcons.Back, "Back", onBack, Modifier.padding(start = start, top = 16.dp))
        }
    }
}

private fun ArtistSection.isVideos(): Boolean = items.all { it is SongItem } && title.contains("video", ignoreCase = true)

/** The newest album, single or EP on the page, by year; YouTube lists each shelf newest first. */
private fun latestRelease(sections: List<ArtistSection>): AlbumItem? {
    val releases = sections.filter { section -> section.items.all { it is AlbumItem } }.mapNotNull { it.items.firstOrNull() as? AlbumItem }
    return releases.maxByOrNull { it.year ?: 0 } ?: releases.firstOrNull()
}

/** YouTube's artist banners are wide; ask for one wide enough for the window without squaring it. */
private fun bannerUrl(url: String): String {
    val match = Regex("=w(\\d+)-h(\\d+)").find(url) ?: return url.resizedThumbnail(1440)
    val (w, h) = match.destructured
    val width = 2048
    val height = (h.toLong() * width / w.toLong().coerceAtLeast(1)).toInt()
    return url.replaceRange(match.range, "=w$width-h$height")
}

private fun wideThumbnail(url: String): String {
    val size = Regex("=w\\d+-h\\d+")
    return if (size.containsMatchIn(url)) url.replace(size, "=w640-h360") else url
}

/** A line of the hero arriving: rises and settles once [settle] passes [at]. */
internal fun Modifier.stage(at: Float, settle: () -> Float): Modifier = graphicsLayer {
    val t = ((settle() - at) / (1f - at)).coerceIn(0f, 1f)
    alpha = FastOutSlowInEasing.transform((t * 1.6f).coerceAtMost(1f))
    translationY = (1f - Overshoot.transform(t)) * 30.dp.toPx()
}

/** A card in a row arriving from the right, a beat after the one before it. */
private fun Modifier.cascadeRow(index: Int, progress: () -> Float): Modifier = graphicsLayer {
    val begin = index.coerceAtMost(8) * 0.05f
    val t = ((progress() - begin) / 0.45f).coerceIn(0f, 1f)
    if (t >= 1f) return@graphicsLayer
    alpha = FastOutSlowInEasing.transform((t * 1.7f).coerceAtMost(1f))
    translationX = (1f - Overshoot.transform(t)) * 56.dp.toPx()
}

/**
 * The banner. On arrival the picture settles back from a slight zoom, the way ColorOS lands a
 * wallpaper, and the eyebrow, name, audience, blurb and buttons rise in one after another. Scrolling
 * drifts the picture slower than the page and fades the text away.
 */
@Composable
private fun ArtistHero(
    name: String,
    image: String?,
    audience: String?,
    description: String?,
    start: Dp,
    listState: LazyListState,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRadio: () -> Unit,
    onHeight: (Float) -> Unit,
) {
    val reduced = LocalSettings.current.reducedMotion
    val settle = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { settle.animateTo(1f, spring(dampingRatio = 1f, stiffness = 55f)) }
    val progress: () -> Float = { settle.value }
    val ground = MaterialTheme.colorScheme.background
    val blurb = remember(description) {
        description?.split(Regex("(?<=[.!?])\\s+"))?.firstOrNull()?.trim()?.takeIf { it.length in 20..220 }
    }

    BoxWithConstraints(Modifier.fillMaxWidth().onSizeChanged { onHeight(it.height.toFloat()) }) {
        val height = (maxWidth * 0.42f).coerceIn(380.dp, 560.dp)
        Box(Modifier.fillMaxWidth().height(height)) {
            AsyncImage(
                model = image?.let(::bannerUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else size.height
                        translationY = scrolled * 0.45f
                        val zoom = 1f + 0.16f * (1f - settle.value)
                        scaleX = zoom
                        scaleY = zoom
                        alpha = FastOutSlowInEasing.transform((settle.value * 2.4f).coerceAtMost(1f))
                    },
            )
            // Floor fades into the page; the left side darkens so white text reads over any photo.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to ground.copy(alpha = 0.30f), 0.40f to Color.Transparent, 1f to ground)))
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.50f), 0.6f to Color.Transparent)))

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = start, end = 40.dp, bottom = 30.dp)
                    .graphicsLayer {
                        val scrolled = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else height.toPx()
                        alpha = 1f - (scrolled / (height.toPx() * 0.55f)).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(0f, 1f)
                    },
            ) {
                Text(
                    "ARTIST",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.16.em),
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.stage(0.12f, progress),
                )
                Text(
                    name,
                    style = (if (name.length > 20) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge).copy(letterSpacing = (-0.04).em),
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.stage(0.2f, progress),
                )
                audience?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.80f), modifier = Modifier.stage(0.3f, progress))
                }
                blurb?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.66f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp).stage(0.36f, progress),
                    )
                }
                Spacer(Modifier.height(20.dp))
                // Three things, three meanings: play them, play them in any order, play on from them.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.stage(0.44f, progress)) {
                    PlayCircle(diameter = 58.dp, enabled = canPlay, description = "Play $name", onClick = onPlay)
                    Spacer(Modifier.width(14.dp))
                    PillButton("Shuffle", TransportIcons.Shuffle, primary = true, enabled = canPlay, onClick = onShuffle)
                    Spacer(Modifier.width(10.dp))
                    PillButton("Radio", NavIcons.Radio, primary = false, enabled = canPlay, onClick = onRadio)
                }
            }
        }
    }
}

/** The accent play button: grows under the pointer with a soft halo, squeezes when pressed. */
@Composable
internal fun PlayCircle(diameter: Dp, enabled: Boolean, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.9f
            hovered -> 1.08f
            else -> 1f
        },
        spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "play_circle",
    )
    val halo by animateFloatAsState(if (hovered && enabled) 1f else 0f, tween(260), label = "play_halo")
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .drawBehind {
                if (halo > 0f) drawCircle(accent.copy(alpha = 0.32f * halo), radius = size.minDimension / 2f * (1f + 0.32f * halo))
            }
            .shadow(16.dp, CircleShape)
            .clip(CircleShape)
            .background(accent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(TransportIcons.Play, contentDescription = description, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(diameter * 0.46f))
    }
}

/** Past the banner, the name and a play button stay at the top of the page. */
@Composable
private fun StickyArtistBar(visible: Boolean, name: String, image: String?, start: Dp, canPlay: Boolean, onPlay: () -> Unit) {
    val shown by animateFloatAsState(if (visible) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 380f), label = "artist_bar")
    if (shown <= 0.01f) return
    val ground = MaterialTheme.colorScheme.background
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = shown
                translationY = -(1f - shown) * 18.dp.toPx()
            }
            .background(Brush.verticalGradient(0f to ground.copy(alpha = 0.97f), 0.72f to ground.copy(alpha = 0.86f), 1f to Color.Transparent))
            .padding(start = start + 62.dp, end = 40.dp, top = 16.dp, bottom = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            image?.resizedThumbnail(160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(14.dp))
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        PlayCircle(diameter = 46.dp, enabled = canPlay, description = "Shuffle $name", onClick = onPlay)
    }
}

/** Newest release on the left, top songs beside it; stacked when the window is narrow. */
@Composable
private fun Spotlight(
    top: ArtistSection?,
    latest: AlbumItem?,
    state: PlayerState,
    player: DesktopPlayer,
    cascade: () -> Float,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (latest != null && top != null && maxWidth >= 1000.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                LatestRelease(latest, state, player, Modifier.width(260.dp).feedIntro(0f, cascade))
                TopSongs(top, onSeeAll, state, player, cascade, Modifier.weight(1f).feedIntro(0.5f, cascade))
            }
        } else {
            Column {
                if (top != null) TopSongs(top, onSeeAll, state, player, cascade, Modifier.feedIntro(0f, cascade))
                if (latest != null) LatestRelease(latest, state, player, Modifier.padding(top = if (top != null) 32.dp else 0.dp).feedIntro(0.5f, cascade))
            }
        }
    }
}

@Composable
private fun LatestRelease(album: AlbumItem, state: PlayerState, player: DesktopPlayer, modifier: Modifier) {
    val open = rememberItemOpener(player)
    Column(modifier) {
        Text("Latest release", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))
        ItemCard(album, isActive = album.id == state.current?.album?.id, onClick = { open(album) }, artSize = 260.dp, onPlay = { player.playNow(album) })
    }
}

@Composable
private fun TopSongs(section: ArtistSection, onSeeAll: (() -> Unit)?, state: PlayerState, player: DesktopPlayer, cascade: () -> Float, modifier: Modifier) {
    Column(modifier) {
        SectionTitle(section.title.ifBlank { "Top songs" }, onSeeAll, Modifier.padding(bottom = 8.dp))
        Box(Modifier.padding(end = 0.dp)) {
            SongColumns(section.items.filterIsInstance<SongItem>().take(10), state, player, cascade, twoColumnsFrom = 700.dp)
        }
    }
}

/** An artist's songs in two columns when there's room, numbered down the first and on down the second. */
@Composable
private fun SongColumns(songs: List<SongItem>, state: PlayerState, player: DesktopPlayer, cascade: () -> Float, twoColumnsFrom: Dp = 980.dp) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= twoColumnsFrom) 2 else 1
        val perColumn = ((songs.size + columns - 1) / columns).coerceAtLeast(1)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            songs.chunked(perColumn).forEachIndexed { column, chunk ->
                Column(Modifier.weight(1f)) {
                    chunk.forEachIndexed { i, song ->
                        val index = column * perColumn + i
                        val active = song.id == state.current?.id
                        TrackRow(
                            number = index + 1,
                            song = song,
                            active = active,
                            playing = state.isPlaying,
                            showArtwork = true,
                            onClick = { if (active) player.togglePlayPause() else player.play(songs, index) },
                            modifier = Modifier.cascade(i + column, cascade),
                        )
                    }
                }
            }
        }
    }
}

/** One shelf, in the form that suits what's on it: videos wide, artists round, songs as a list, the rest as cards. */
@Composable
private fun ArtistShelf(section: ArtistSection, state: PlayerState, player: DesktopPlayer, cascade: () -> Float, start: Dp, modifier: Modifier) {
    val open = rememberItemOpener(player)
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    val items = section.items
    when {
        section.isVideos() -> LazyRow(modifier, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            itemsIndexed(items.filterIsInstance<SongItem>(), key = { i, song -> "$i:${song.id}" }) { i, song ->
                val active = song.id == state.current?.id
                VideoCard(song, active, state.isPlaying, onClick = { if (active) player.togglePlayPause() else player.playItem(song) }, modifier = Modifier.cascadeRow(i, cascade))
            }
        }
        items.all { it is SongItem } -> Box(modifier.padding(start = start - 12.dp, end = 28.dp)) {
            SongColumns(items.filterIsInstance<SongItem>().take(10), state, player, cascade)
        }
        items.all { it is ArtistItem } -> LazyRow(modifier, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(items.filterIsInstance<ArtistItem>(), key = { i, artist -> "$i:${artist.id}" }) { i, artist ->
                ArtistCircle(artist, onClick = { open(artist) }, modifier = Modifier.cascadeRow(i, cascade))
            }
        }
        else -> LazyRow(modifier, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { i, item ->
                Box(Modifier.cascadeRow(i, cascade)) {
                    // Albums a size up from everything else on the page: they're what an artist page is for.
                    ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = if (item is AlbumItem) 212.dp else 184.dp, onPlay = { player.playNow(item) })
                }
            }
        }
    }
}

/** A music video: wide thumbnail that zooms a little under the pointer, the running time in its corner. */
@Composable
private fun VideoCard(song: SongItem, active: Boolean, playing: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "video_press")
    val zoom by animateFloatAsState(if (hovered) 1.07f else 1f, spring(dampingRatio = 0.9f, stiffness = 220f), label = "video_zoom")
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .width(304.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shadow(if (hovered) 18.dp else 8.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                wideThumbnail(song.thumbnail),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                },
            )
            if (hovered || active) {
                Box(Modifier.align(Alignment.Center).size(54.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    if (active && playing) EqualizerBars(Color.White, Modifier.size(20.dp))
                    else Icon(TransportIcons.Play, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            song.duration?.let {
                Text(
                    formatTime(it.toDouble()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(song.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (song.artists.isNotEmpty()) ArtistLinks(song.artists, MaterialTheme.typography.bodySmall, MaterialTheme.colorScheme.secondary)
    }
}

/** Another artist: a round photo that lifts and rings with the accent under the pointer, name centred beneath. */
@Composable
private fun ArtistCircle(artist: ArtistItem, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            pressed -> 0.95f
            hovered -> 1.04f
            else -> 1f
        },
        spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "artist_circle",
    )
    val ring by animateFloatAsState(if (hovered) 1f else 0f, tween(220), label = "artist_ring")
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .width(168.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            artist.thumbnail?.resizedThumbnail(360),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .shadow(if (hovered) 20.dp else 8.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.5.dp, accent.copy(alpha = ring), CircleShape),
        )
        Spacer(Modifier.height(12.dp))
        Text(artist.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        artist.subscriberCountText?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AboutArtist(name: String, image: String?, about: String, audience: String?, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glassPlate(RoundedCornerShape(28.dp))
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        AsyncImage(
            image?.resizedThumbnail(544),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(196.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f)) {
            Text("About $name", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            audience?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            ExpandableText(about, collapsedLines = 6)
        }
    }
}
