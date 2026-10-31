/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.home

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
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
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.AccountSession
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.ContentLanguageFilter
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.PreferredArtist
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.Motion
import com.ozyern.exhale.desktop.ui.PlayDisc
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.LocalNavigator
import com.ozyern.exhale.desktop.ui.StationCard
import com.ozyern.exhale.desktop.ui.BenchList
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.HomePage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The Content settings: hide explicit items and music videos. */
private fun <T : YTItem> List<T>.clean(): List<T> =
    filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)

/** Where the floating chrome sits, so content can clear it. */
data class ChromeInsets(val start: Dp, val bottom: Dp)

/** A station built around one of the listener's artists. */
data class TopPick(val eyebrow: String, val title: String, val image: String?, val item: YTItem)

/** A song for the big cards at the top of Home, and why it's there. */
data class Recommendation(val song: SongItem, val reason: String)

data class Shelf(
    val key: String,
    val label: String?,
    val title: String,
    val items: List<YTItem>,
    val thumbnail: String? = null,
    val roundThumbnail: Boolean = false,
)

/** Alternates two lists — a personal pick, then one of YouTube's, and so on. */
private fun <T> interleave(a: List<T>, b: List<T>): List<T> = buildList {
    for (i in 0 until maxOf(a.size, b.size)) {
        a.getOrNull(i)?.let(::add)
        b.getOrNull(i)?.let(::add)
    }
}

/**
 * Home, built around the listener's own picks first — their artists, artists like them, new
 * releases — and YouTube Music's feed after that.
 */
class HomeFeed(private val scope: CoroutineScope) {
    var recommended by mutableStateOf<List<Recommendation>>(emptyList())
        private set
    var topPicks by mutableStateOf<List<TopPick>>(emptyList())
        private set
    var tasteShelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var similarArtists by mutableStateOf<List<ArtistItem>>(emptyList())
        private set
    var releases by mutableStateOf<List<AlbumItem>>(emptyList())
        private set
    var page by mutableStateOf<HomePage?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null

    val isEmpty: Boolean
        get() = recommended.isEmpty() && topPicks.isEmpty() && tasteShelves.isEmpty() && releases.isEmpty() && page?.sections.isNullOrEmpty()

    fun load(artists: List<PreferredArtist>, force: Boolean = false) {
        if (isLoading && !force) return
        loadJob?.cancel()
        isLoading = true
        error = null
        loadJob = scope.launch {
            try {
                val (pages, home, newReleases) = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val artistPages = artists.take(6).map { artist -> async { artist to YouTube.artist(artist.id).getOrNull() } }
                        val homePage = async { YouTube.home().getOrNull() }
                        val albums = async { YouTube.newReleaseAlbums().getOrNull().orEmpty() }
                        Triple(artistPages.awaitAll(), homePage.await(), albums.await())
                    }
                }

                topPicks = pages.mapNotNull { (artist, artistPage) ->
                    val station = artistPage?.artist ?: return@mapNotNull null
                    TopPick("Featuring ${artist.name}", "${artist.name} Radio", station.thumbnail ?: artist.thumbnail, station)
                }

                val artistShelves = pages.take(4).mapNotNull { (artist, artistPage) ->
                    val items = artistPage?.sections.orEmpty().flatMap { it.items }
                        .filter { it is SongItem || it is AlbumItem || it is ArtistItem }
                        .distinctBy { it.id }
                        .clean()
                        .take(18)
                    if (items.isEmpty()) null
                    else Shelf("artist_${artist.id}", "Similar to", artist.name, items, artist.thumbnail, roundThumbnail = true)
                }
                // Shown now: everything the first round of requests gave. Home used to wait for the
                // mix's radios below as well, a second round of requests one after the first, before
                // it drew anything at all, which was seconds of an empty page at every launch.
                tasteShelves = artistShelves

                val languages = DesktopPrefs.languages.toSet()
                val pickedIds = artists.mapTo(HashSet()) { it.id }
                similarArtists = ContentLanguageFilter.filterItems(
                    pages.flatMap { (_, artistPage) -> artistPage?.sections.orEmpty().flatMap { it.items }.filterIsInstance<ArtistItem>() }
                        .filterNot { it.id in pickedIds }.distinctBy { it.id },
                    languages,
                ).shuffled().take(18)

                releases = ContentLanguageFilter.filterItems(newReleases.clean(), languages)
                page = home?.let { homePage ->
                    val cleaned = homePage.sections.map { it.copy(items = it.items.clean()) }.filter { it.items.isNotEmpty() }
                    homePage.copy(sections = ContentLanguageFilter.filterHomeSections(cleaned, languages))
                }

                val homeSongs = page?.sections.orEmpty().flatMap { section ->
                    section.items.filterIsInstance<SongItem>().map { Recommendation(it, section.title) }
                }
                // Drawn from here: every section shows as soon as it has something. The big cards,
                // built from the mix, keep their placeholder (isLoading is still on) until it arrives,
                // rather than showing one set of songs and swapping it a second later.

                // A mix from the listener's artists' own radios. Radio follows the seed, not the IP.
                val mix = withContext(Dispatchers.IO) {
                    coroutineScope {
                        pages.take(3).mapNotNull { it.second?.artist?.radioEndpoint }
                            .map { endpoint -> async { YouTube.next(endpoint).getOrNull()?.items.orEmpty() } }
                            .awaitAll()
                            .flatten()
                    }
                }.distinctBy { it.id }.clean().shuffled().take(30)
                // The first songs of the mix become the big cards, so the shelf starts where they end.
                tasteShelves = listOfNotNull(mix.drop(6).takeIf { it.isNotEmpty() }?.let { Shelf("made_for_you", "Made for you", "Your Mix", it) }) + artistShelves
                recommended = interleave(mix.take(6).map { Recommendation(it, "From your mix") }, homeSongs)
                    .distinctBy { it.song.id }
                    .take(10)

                if (isEmpty) error = "Couldn't reach YouTube Music. Check your connection and try again."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load Home"
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMore() {
        val current = page ?: return
        val continuation = current.continuation ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        scope.launch {
            withContext(Dispatchers.IO) { YouTube.home(continuation = continuation) }
                .onSuccess { more ->
                    val kept = ContentLanguageFilter.filterHomeSections(
                        more.sections.map { it.copy(items = it.items.clean()) }.filter { it.items.isNotEmpty() },
                        DesktopPrefs.languages.toSet(),
                    )
                    page = current.copy(sections = current.sections + kept, continuation = more.continuation)
                }
            isLoadingMore = false
        }
    }
}

private data class Shortcut(val key: String, val title: String, val image: String?, val round: Boolean, val activeId: String?, val onClick: () -> Unit)

private fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

/**
 * The Android Home, made welcoming for a big screen: logo and account up top, a greeting by the
 * time of day (and your name, signed in), big cards of songs picked for you, the shortcut grid,
 * then "Similar to …" shelves — arriving shelf by shelf on one clock.
 */
@Composable
fun HomeScreen(
    feed: HomeFeed,
    player: DesktopPlayer,
    state: PlayerState,
    insets: ChromeInsets,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignIn: () -> Unit,
    /** Beside the sidebar: the Music app's Home — its title, feature cards and song lists. */
    sidebar: Boolean = false,
) {
    val history by DesktopPrefs.history.collectAsState()
    val artists by DesktopPrefs.artists.collectAsState()
    val account by AccountSession.account.collectAsState()
    val expired by AccountSession.expired.collectAsState()
    val listState = rememberLazyListState()
    BenchList(listState, home = true)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 3) feed.loadMore() }
    }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(feed.isEmpty) {
        if (!feed.isEmpty && intro.value == 0f) {
            if (AppSettings.current.reducedMotion) intro.snapTo(1f)
            else intro.animateTo(1f, tween(Motion.FeedIntroDurationMs + 280, easing = LinearOutSlowInEasing))
        }
    }
    val introProgress: () -> Float = { intro.value }

    // The greeting doesn't wait for the feed: it's the first thing to arrive.
    val hello = remember { Animatable(if (AppSettings.current.reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { hello.animateTo(1f, tween(760, easing = LinearOutSlowInEasing)) }

    val ytSections = feed.page?.sections.orEmpty()
    val ytKeys = remember(ytSections) {
        val seen = HashMap<String, Int>()
        ytSections.mapIndexed { index, section ->
            val base = section.endpoint?.browseId ?: "title_${section.title}_$index"
            val occurrence = (seen[base] ?: 0) + 1
            seen[base] = occurrence
            if (occurrence == 1) base else "${base}__$occurrence"
        }
    }
    val start = insets.start + 20.dp
    val rowPadding = PaddingValues(start = start, end = 40.dp)
    val isActive: (YTItem) -> Boolean = { item -> item.id == state.current?.id || (item is AlbumItem && item.id == state.current?.album?.id) }
    val open = rememberItemOpener(player)
    val navigator = LocalNavigator.current
    val playNow: (YTItem) -> Unit = { player.playNow(it) }

    val shortcuts = remember(history, artists) {
        (history.take(4).map { song -> Shortcut("s:${song.id}", song.title, song.thumbnail, false, song.id) { player.playItem(song) } } +
            // An artist's tile opens their profile; it used to start their radio, which is not what a face promises.
            artists.take(4).map { artist ->
                Shortcut("a:${artist.id}", artist.name, artist.thumbnail, true, null) {
                    navigator?.openArtist?.invoke(artist.id, artist.name, artist.thumbnail) ?: player.playArtist(artist.id)
                }
            })
            .take(8)
    }

    val greeting = remember { greetingFor(LocalTime.now().hour) }
    val firstName = account?.name?.substringBefore(' ')?.takeIf { it.isNotBlank() }
    val today = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()).format(LocalDate.now()) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 20.dp, bottom = insets.bottom + 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            var order = 2f
            fun nextIntro(): Modifier = Modifier.feedIntro(order.also { order += 1f }, introProgress)

            // The sidebar carries the account and Settings; the header would only repeat them.
            if (!sidebar) {
                item(key = "header") {
                    HomeHeader(onOpenSettings, onSignIn, Modifier.padding(start = start, end = 40.dp, bottom = 20.dp))
                }
            }
            item(key = "large_title") {
                LargeTitle(
                    when {
                        sidebar -> "Home"
                        firstName != null -> "$greeting, $firstName"
                        else -> greeting
                    },
                    eyebrow = if (sidebar) null else today,
                    modifier = Modifier
                        .padding(start = start, end = 40.dp, bottom = 4.dp)
                        .graphicsLayer {
                            // Rises in on arrival; then drifts slower than the list, dissolves, and
                            // shrinks toward its bottom-left corner as you scroll away.
                            val height = size.height.coerceAtLeast(1f)
                            val scrolled = if (listState.firstVisibleItemIndex <= 1) {
                                if (listState.firstVisibleItemIndex == 1) listState.firstVisibleItemScrollOffset.toFloat() else 0f
                            } else {
                                height
                            }
                            val t = (scrolled / height).coerceIn(0f, 1f)
                            val arrive = FastOutSlowInEasing.transform(hello.value)
                            alpha = (1f - t) * arrive
                            translationY = scrolled * 0.35f + (1f - arrive) * 22.dp.toPx()
                            val shrink = 1f - 0.16f * t
                            scaleX = shrink
                            scaleY = shrink
                            transformOrigin = TransformOrigin(0f, 1f)
                        },
                )
            }
            if (expired) item(key = "sign_in_expired") {
                SignInExpiredNotice(onSignIn, Modifier.padding(start = start, end = 40.dp, top = 12.dp, bottom = 8.dp))
            }
            if (!sidebar) item(key = "subtitle") {
                Text(
                    if (state.current != null) "Pick up where you left off, or try something new." else "Here's something to exhale to.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = start, end = 40.dp, bottom = 22.dp)
                        .graphicsLayer {
                            val arrive = FastOutSlowInEasing.transform(((hello.value - 0.25f) / 0.75f).coerceIn(0f, 1f))
                            alpha = arrive
                            translationY = (1f - arrive) * 14.dp.toPx()
                        },
                )
            }

            when {
                feed.recommended.isNotEmpty() && sidebar -> item(key = "recommended") {
                    Column(Modifier.padding(top = 18.dp).feedIntro(0f, introProgress)) {
                        // The Music app's Home opens on this shelf, titled like every other one.
                        MusicShelfTitle("Top Picks for You", modifier = Modifier.padding(start = start, end = 40.dp, bottom = 12.dp))
                        FeatureRow(feed.recommended, state, player, start)
                    }
                }
                feed.recommended.isNotEmpty() -> item(key = "recommended") {
                    RecommendedRow(feed.recommended, state, player, start, introProgress)
                }
                feed.isLoading -> item(key = "recommended_placeholder") { RecommendedPlaceholder(start) }
            }

            if (sidebar && history.isNotEmpty()) {
                item(key = "recent_songs") {
                    Column(Modifier.feedIntro(1f, introProgress)) {
                        MusicShelfTitle("Recently Played", modifier = Modifier.padding(start = start, end = 40.dp, top = 34.dp, bottom = 10.dp))
                        // A row of square covers, as the Music app shows what you played last — not a song list.
                        val recent = history.take(24)
                        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(recent, key = { i, song -> "$i:${song.id}" }) { i, song ->
                                ItemCard(song, isActive(song), onClick = { if (isActive(song)) player.togglePlayPause() else player.play(recent, i) })
                            }
                        }
                    }
                }
            } else if (shortcuts.isNotEmpty()) {
                item(key = "shortcuts") {
                    Column(Modifier.feedIntro(1f, introProgress)) {
                        Box(Modifier.padding(start = start, end = 40.dp, top = 30.dp, bottom = 10.dp)) { SectionHeader("Jump back in", "Recently played") }
                        ShortcutGrid(shortcuts, state, Modifier.padding(start = start, end = 40.dp, bottom = 4.dp), introProgress)
                    }
                }
            }

            if (feed.isLoading && feed.isEmpty) {
                items(count = 2, key = { "skeleton_$it" }) { ShelfPlaceholder(rowPadding, start) }
            }
            if (!feed.isLoading && feed.isEmpty && feed.error != null) {
                item(key = "empty") { EmptyState(feed.error!!, onRetry, Modifier.padding(start = start)) }
            }

            feed.tasteShelves.forEach { shelf ->
                val sectionIntro = nextIntro()
                header(shelf.key, start, sectionIntro) {
                    if (sidebar) MusicShelfTitle(shelf.title, shelf.label) else SectionHeader(shelf.label, shelf.title, shelf.thumbnail, shelf.roundThumbnail)
                }
                shelfRow("${shelf.key}_row", shelf.items, rowPadding, sectionIntro, isActive, playNow, open)
            }

            if (feed.topPicks.isNotEmpty()) {
                val sectionIntro = nextIntro()
                header("stations", start, sectionIntro) { if (sidebar) MusicShelfTitle("Stations for You") else SectionHeader("Made for you", "Stations") }
                item(key = "stations_row") {
                    LazyRow(sectionIntro, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(feed.topPicks, key = { i, pick -> "$i:${pick.item.id}" }) { _, pick ->
                            StationCard(pick.title, pick.eyebrow, pick.image, onClick = { player.playItem(pick.item) })
                        }
                    }
                }
            }

            if (feed.similarArtists.isNotEmpty()) {
                val sectionIntro = nextIntro()
                header("similar", start, sectionIntro) { if (sidebar) MusicShelfTitle("Artists You Might Like") else SectionHeader("Discover", "Artists you might like") }
                shelfRow("similar_row", feed.similarArtists, rowPadding, sectionIntro, isActive, playNow, open)
            }

            if (feed.releases.isNotEmpty()) {
                val sectionIntro = nextIntro()
                header("releases", start, sectionIntro) { if (sidebar) MusicShelfTitle("New Releases") else SectionHeader(null, "New releases") }
                shelfRow("releases_row", feed.releases.take(24), rowPadding, sectionIntro, isActive, playNow, open)
            }

            ytSections.forEachIndexed { index, section ->
                val key = "yt_${ytKeys[index]}"
                val sectionIntro = nextIntro()
                header(key, start, sectionIntro) {
                    if (sidebar) MusicShelfTitle(section.title, section.label)
                    else SectionHeader(section.label, section.title, section.thumbnail, section.endpoint?.browseId?.startsWith("UC") == true)
                }
                shelfRow("${key}_row", section.items, rowPadding, sectionIntro, isActive, playNow, open)
            }

            if (feed.isLoadingMore) item(key = "loading_more") { ShelfPlaceholder(rowPadding, start) }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 16.dp, bottom = insets.bottom, end = 6.dp),
        )
    }
}

private fun LazyListScope.header(key: String, start: Dp, intro: Modifier, content: @Composable () -> Unit) {
    item(key = "${key}_header") {
        Box(intro.padding(start = start, end = 40.dp, top = 28.dp, bottom = 10.dp)) { content() }
    }
}

private fun LazyListScope.shelfRow(
    key: String,
    items: List<YTItem>,
    rowPadding: PaddingValues,
    intro: Modifier,
    isActive: (YTItem) -> Boolean,
    onPlay: ((YTItem) -> Unit)? = null,
    onClick: (YTItem) -> Unit,
) {
    item(key = key) {
        LazyRow(intro, contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { _, item ->
                ItemCard(item, isActive(item), onClick = { onClick(item) }, onPlay = onPlay?.let { play -> { play(item) } })
            }
        }
    }
}

// ── Top picks: the big cards ──

/** Cards slide in from the right one after another, rather than the row appearing whole. */
private fun Modifier.cardIntro(index: Int, progress: () -> Float): Modifier = graphicsLayer {
    val begin = (index * 0.08f).coerceAtMost(0.5f)
    val raw = ((progress() - begin) / 0.5f).coerceIn(0f, 1f)
    val eased = FastOutSlowInEasing.transform(raw)
    alpha = eased
    translationX = (1f - eased) * 56.dp.toPx()
    val s = 0.94f + 0.06f * eased
    scaleX = s
    scaleY = s
}

@Composable
private fun RecommendedRow(picks: List<Recommendation>, state: PlayerState, player: DesktopPlayer, start: Dp, intro: () -> Float) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val step = with(LocalDensity.current) { 560.dp.toPx() }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = start, end = 40.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionHeader("Made for you", "Top picks") }
            CarouselArrow(TransportIcons.Collapse, "Previous", rotation = 90f, enabled = listState.canScrollBackward) {
                scope.launch { listState.animateScrollBy(-step, spring(dampingRatio = 0.9f, stiffness = 160f)) }
            }
            Spacer(Modifier.width(10.dp))
            CarouselArrow(TransportIcons.Collapse, "Next", rotation = -90f, enabled = listState.canScrollForward) {
                scope.launch { listState.animateScrollBy(step, spring(dampingRatio = 0.9f, stiffness = 160f)) }
            }
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(start = start, end = 40.dp, top = 6.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(picks, key = { _, pick -> pick.song.id }) { index, pick ->
                val active = pick.song.id == state.current?.id
                val onPlay: () -> Unit = { if (active) player.togglePlayPause() else player.playItem(pick.song) }
                Box(Modifier.cardIntro(index, intro)) {
                    if (index == 0) {
                        SpotlightCard(pick, active, state.isPlaying, onPlay, onPlayNext = { player.playNext(pick.song) })
                    } else {
                        PickCard(pick, active, state.isPlaying, onPlay)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarouselArrow(icon: ImageVector, description: String, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    val fade by animateFloatAsState(if (enabled) 1f else 0.35f, tween(200), label = "arrow_fade")
    GlassIconButton(
        icon = icon,
        contentDescription = description,
        onClick = { if (enabled) onClick() },
        size = 40.dp,
        modifier = Modifier.graphicsLayer { alpha = fade },
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation })
    }
}

/**
 * The first pick, as a spotlight: the cover blurred into a wide card, the title set large, Play
 * and Play next, and a sharp cover that lifts and leans under the pointer.
 */
@Composable
private fun SpotlightCard(pick: Recommendation, active: Boolean, playing: Boolean, onPlay: () -> Unit, onPlayNext: () -> Unit) {
    val song = pick.song
    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val base by animateColorAsState(colors.firstOrNull() ?: MaterialTheme.colorScheme.primary, tween(600), label = "spotlight_base")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.98f, hoverScale = 1.01f)
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 240f), label = "spotlight_lift")
    val shape = RoundedCornerShape(28.dp)

    Box(
        Modifier
            .width(640.dp)
            .height(372.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (hovered) 30.dp else 0.dp, shape)
            .clip(shape)
            .background(lerp(base, Color.Black, 0.55f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
    ) {
        AsyncImage(
            song.thumbnail.resizedThumbnail(544),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.75f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                }
                .blur(72.dp, BlurredEdgeTreatment.Rectangle),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.12f)))))
        Row(Modifier.fillMaxSize().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    pick.reason.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.12.em),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-0.02).em),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.76f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpotlightButton(
                        icon = if (active && playing) TransportIcons.Pause else TransportIcons.Play,
                        label = if (active && playing) "Pause" else "Play",
                        filled = true,
                        onClick = onPlay,
                    )
                    SpotlightButton(TransportIcons.Queue, "Play next", filled = false, onClick = onPlayNext)
                }
            }
            Spacer(Modifier.width(26.dp))
            Box(
                Modifier
                    .size(272.dp)
                    .graphicsLayer {
                        translationY = -8.dp.toPx() * lift
                        rotationZ = -2.5f * lift
                        val s = 1f + 0.035f * lift
                        scaleX = s
                        scaleY = s
                    }
                    .shadow(26.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(song.thumbnail.resizedThumbnail(720), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (active) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                        if (playing) EqualizerBars(Color.White, Modifier.size(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotlightButton(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.94f, hoverScale = 1.03f)
    val content = if (filled) Color.Black else Color.White
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.16f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content)
    }
}

/** A tall pick: the cover on top, a panel in the cover's own colour under it saying why it's here. */
@Composable
private fun PickCard(pick: Recommendation, active: Boolean, playing: Boolean, onPlay: () -> Unit) {
    val song = pick.song
    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val base by animateColorAsState(colors.firstOrNull() ?: MaterialTheme.colorScheme.surfaceVariant, tween(600), label = "pick_base")
    val panel = lerp(base, Color.Black, 0.4f)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, pressedScale = 0.96f)
    val lift by animateFloatAsState(if (hovered) 1f else 0f, spring(dampingRatio = 0.65f, stiffness = 300f), label = "pick_lift")
    val shape = RoundedCornerShape(24.dp)

    Column(
        Modifier
            .width(248.dp)
            .height(372.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = -8.dp.toPx() * lift
            }
            .shadow(if (hovered) 26.dp else 0.dp, shape)
            .clip(shape)
            .background(panel)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(song.thumbnail.resizedThumbnail(544), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp).background(Brush.verticalGradient(listOf(Color.Transparent, panel))))
            if (hovered || active) {
                PlayDisc(playing = active && playing, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
            }
        }
        Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                pick.reason.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.1.em),
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RecommendedPlaceholder(start: Dp) {
    val pulse by rememberInfiniteTransition(label = "picks_skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "picks_skeleton_alpha",
    )
    Column(Modifier.graphicsLayer { alpha = pulse }) {
        Box(Modifier.padding(start = start, bottom = 14.dp).width(220.dp).height(40.dp).glassPlate(RoundedCornerShape(10.dp)))
        LazyRow(
            contentPadding = PaddingValues(start = start, end = 40.dp, top = 6.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            userScrollEnabled = false,
        ) {
            item { Box(Modifier.width(640.dp).height(372.dp).glassPlate(RoundedCornerShape(28.dp))) }
            items(count = 4) { Box(Modifier.width(248.dp).height(372.dp).glassPlate(RoundedCornerShape(24.dp))) }
        }
    }
}

// ── Shortcuts ──

/** Android's HomeShortcutsGrid: squat glass tiles, artwork flush left, cascading in tile by tile. */
@Composable
private fun ShortcutGrid(shortcuts: List<Shortcut>, state: PlayerState, modifier: Modifier, introProgress: () -> Float) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = (maxWidth / 280.dp).toInt().coerceIn(2, 4)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shortcuts.take(columns * 2).chunked(columns).forEachIndexed { row, tiles ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.forEachIndexed { column, tile ->
                        ShortcutTile(
                            tile,
                            active = tile.activeId != null && tile.activeId == state.current?.id,
                            playing = state.isPlaying,
                            modifier = Modifier.weight(1f).feedIntro((row * columns + column) * 0.34f, introProgress),
                        )
                    }
                    repeat(columns - tiles.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutTile(tile: Shortcut, active: Boolean, playing: Boolean, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.015f)
    Row(
        modifier = modifier
            .height(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .glassPlate(RoundedCornerShape(8.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = tile.onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f).then(if (tile.round) Modifier.padding(8.dp) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                tile.image?.resizedThumbnail(226),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(if (tile.round) CircleShape else RoundedCornerShape(0.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (active) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    if (playing) EqualizerBars(Color.White, Modifier.size(18.dp))
                    else Icon(TransportIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            tile.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 10.dp),
        )
    }
}

/** A large display title, tracked in tight, with an optional quiet eyebrow above it. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    Column(modifier.fillMaxWidth()) {
        if (eyebrow != null) {
            Text(eyebrow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The Mac Music app's page title: bold and large, but a window title, not a phone's 34-point one.
        Text(title, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.02).em, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Android's NavigationTitle: a small label over a bold title with the chevron hard against its
 * last letter, so "Similar to Sabrina Carpenter ›" reads as one phrase.
 */
@Composable
fun SectionHeader(label: String?, title: String, thumbnail: String? = null, roundThumbnail: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        thumbnail?.let { url ->
            AsyncImage(
                model = url.resizedThumbnail(120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 56.dp, height = 40.dp).clip(if (roundThumbnail) RoundedCornerShape(10.dp) else RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column {
            label?.let { Text(it, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(letterSpacing = (-0.02).em),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(
                    TransportIcons.Collapse,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 2.dp).size(20.dp).graphicsLayer { rotationZ = -90f },
                )
            }
        }
    }
}

@Composable
fun ShelfPlaceholder(rowPadding: PaddingValues, start: Dp) {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    Column(Modifier.graphicsLayer { alpha = pulse }.padding(top = 30.dp)) {
        Box(Modifier.padding(start = start, bottom = 14.dp).width(220.dp).height(26.dp).glassPlate(RoundedCornerShape(10.dp)))
        LazyRow(contentPadding = rowPadding, horizontalArrangement = Arrangement.spacedBy(12.dp), userScrollEnabled = false) {
            items(count = 8) {
                Column {
                    Box(Modifier.size(168.dp).glassPlate(RoundedCornerShape(18.dp)))
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.width(120.dp).height(14.dp).glassPlate(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

/** Shown once YouTube has turned the saved session away, so signing out on its own isn't a surprise. */
@Composable
private fun SignInExpiredNotice(onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Your YouTube sign-in expired, so Exhale is playing signed out.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(onClick = onSignIn) { Text("Sign in again") }
    }
}

@Composable
private fun EmptyState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Home didn't load", style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}
