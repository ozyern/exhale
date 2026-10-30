/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.search

import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import com.ozyern.exhale.desktop.ui.pressScale

import com.ozyern.exhale.desktop.ui.library.isAudioTrack
import com.ozyern.exhale.desktop.ui.SongActionsMenu
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.ExperimentalComposeUiApi
import com.ozyern.exhale.innertube.pages.MoodAndGenres
import com.ozyern.exhale.desktop.ui.LocalNavigator
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.LyricSearch
import com.ozyern.exhale.desktop.data.MusicLinks
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.ui.typingField
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.EqualizerBars
import com.ozyern.exhale.desktop.ui.ItemCard
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.SongRow
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.feedIntro
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.playNow
import com.ozyern.exhale.desktop.ui.rememberItemOpener
import com.ozyern.exhale.desktop.ui.glass.LocalPageBackdrop
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.home.ChromeInsets
import com.ozyern.exhale.desktop.ui.home.LargeTitle
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import com.ozyern.exhale.desktop.ui.subtitle
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.audioTrackFor
import com.ozyern.exhale.innertube.models.bestSongFor
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.isMusicVideo
import com.ozyern.exhale.innertube.models.isNamedBy
import com.ozyern.exhale.innertube.models.songsFirst
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.SearchSummary
import com.ozyern.exhale.innertube.pages.SearchSummaryPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** YouTube Music's own result filters, plus All (its summary page). */
enum class SearchTab(val label: String, val filter: YouTube.SearchFilter?) {
    All("All", null),
    Songs("Songs", YouTube.SearchFilter.FILTER_SONG),
    Artists("Artists", YouTube.SearchFilter.FILTER_ARTIST),
    Albums("Albums", YouTube.SearchFilter.FILTER_ALBUM),
    Playlists("Playlists", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
    Videos("Videos", YouTube.SearchFilter.FILTER_VIDEO),
}

/** Where a search looks, as the Music app's scope buttons beside its field: the catalogue or your own library. */
enum class SearchScope(val label: String) {
    Catalog("YouTube Music"),
    Library("Library"),
}

/** State behind the Search tab: recent searches, live suggestions while typing, results per filter on Enter. */
class SearchModel(private val scope: CoroutineScope) {
    var query by mutableStateOf("")
        private set

    /** The catalogue, or your library (favourites and what you've played), searched as you type. */
    var where by mutableStateOf(SearchScope.Catalog)
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    /** The query the results belong to; results are stale once the text moves on from it. */
    var searchedQuery by mutableStateOf<String?>(null)
        private set
    var tab by mutableStateOf(SearchTab.All)
        private set
    var summary by mutableStateOf<SearchSummaryPage?>(null)
        private set
    var items by mutableStateOf<List<YTItem>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var suggestJob: Job? = null
    private var searchJob: Job? = null
    private val summaries = HashMap<String, SearchSummaryPage>()
    private val filtered = HashMap<String, List<YTItem>>()

    /** Results stay up while the field changes, until the next ones replace them. */
    val showingResults: Boolean
        get() = searchedQuery != null && query.isNotBlank()

    /** Still typing past what the results show: suggestions ride above them. */
    val typingAhead: Boolean
        get() = query.isNotBlank() && searchedQuery != query.trim()

    fun updateQuery(value: String) {
        query = value
        suggestJob?.cancel()
        val q = value.trim()
        // The library is searched locally, on every keystroke, with nothing to ask YouTube.
        if (q.isEmpty() || where == SearchScope.Library) {
            suggestions = emptyList()
            return
        }
        suggestJob = scope.launch {
            delay(220)
            withContext(Dispatchers.IO) { YouTube.searchSuggestions(q) }
                .onSuccess { suggestions = it.queries.take(12) }
            // Results as you type, once the typing pauses (Apple's "start search immediately").
            delay(260)
            if (q.length >= 2 && query.trim() == q && searchedQuery != q) {
                searchedQuery = q
                run(q)
            }
        }
    }

    fun search(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = text
        if (where == SearchScope.Library) {
            DesktopPrefs.recordSearch(q)
            return
        }
        suggestJob?.cancel()
        suggestions = emptyList()
        searchedQuery = q
        DesktopPrefs.recordSearch(q)
        run(q)
    }

    fun selectWhere(value: SearchScope) {
        if (value == where) return
        where = value
        // Back to the catalogue with words already typed: look them up there.
        searchedQuery = null
        if (value == SearchScope.Catalog) updateQuery(query)
    }

    fun selectTab(value: SearchTab) {
        if (value == tab) return
        tab = value
        searchedQuery?.let(::run)
    }

    private fun run(q: String) {
        searchJob?.cancel()
        error = null
        val current = tab
        val key = "$q|${current.name}"
        val filter = current.filter
        if (filter == null) {
            summaries[key]?.let {
                summary = it
                searching = false
                return
            }
        } else {
            filtered[key]?.let {
                items = it
                searching = false
                return
            }
        }
        searching = true
        searchJob = scope.launch {
            if (filter == null && MusicLinks.isLink(q)) {
                // A Spotify, Apple Music, Deezer, Tidal, SoundCloud or YouTube link: the same song or
                // album here as the top result, and the ordinary results for it underneath — searching
                // the raw address found only pages about the address.
                val found = MusicLinks.resolve(q)
                if (found == null) {
                    error = "Couldn't find the music in that link here."
                } else {
                    val by = when (found) {
                        is SongItem -> found.artists.joinToString(" ") { it.name }
                        is AlbumItem -> found.artists?.joinToString(" ") { it.name }.orEmpty()
                        else -> ""
                    }
                    val more = withContext(Dispatchers.IO) { YouTube.searchSummary("${found.title} $by".trim()) }.getOrNull()
                    val page = SearchSummaryPage(
                        listOf(SearchSummary("Top result", listOf(found))) +
                            more?.summaries.orEmpty().filterNot { it.title.contains("top", ignoreCase = true) },
                    )
                    val cleaned = page.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)
                    summaries[key] = cleaned
                    summary = cleaned
                }
            } else if (filter == null) {
                // A line of a song — "his palms are sweaty" — is asked of Genius alongside YouTube, whose
                // own search reads titles and so turns up nothing much for it.
                val fromLyrics: kotlinx.coroutines.Deferred<List<LyricSearch.Match>>? = if (LyricSearch.looksLikeLyrics(q)) {
                    async { runCatching { LyricSearch.search(q) }.getOrDefault(emptyList()) }
                } else {
                    null
                }
                // The Songs search alongside the summary, as the Android app does: YouTube's summary
                // often leaves the song itself out (a search for "blinding lights" came back with its
                // video, a live take, an extended edit and two covers, and no Blinding Lights), and
                // the song can only be put first when it is among the results.
                val songSearch = async(Dispatchers.IO) { YouTube.search(q, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty() }
                withContext(Dispatchers.IO) { YouTube.searchSummary(q) }
                    .onSuccess { page ->
                        val withSongs = withSongResults(page, songSearch.await().filterIsInstance<SongItem>())
                        val cleaned = smarten(withSongs.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos), q)
                        summaries[key] = cleaned
                        summary = cleaned
                    }
                    .onFailure { if (it is CancellationException) throw it else error = it.message ?: "Search failed" }
                searching = false
                val matches = fromLyrics?.await().orEmpty()
                if (matches.isNotEmpty() && searchedQuery == q && tab == SearchTab.All) {
                    val merged = withLyricMatches(summary, matches.map { it.song })
                    summaries[key] = merged
                    summary = merged
                    error = null
                }
            } else {
                withContext(Dispatchers.IO) { YouTube.search(q, filter) }
                    .onSuccess { result ->
                        val cleaned = result.items.filterExplicit(DesktopPrefs.hideExplicit).filterVideo(DesktopPrefs.hideVideos)
                        filtered[key] = cleaned
                        items = cleaned
                    }
                    .onFailure { if (it is CancellationException) throw it else error = it.message ?: "Search failed" }
            }
            searching = false
        }
    }

    /** The Songs search's results as the page's Songs section, ahead of the summary's own few. */
    private fun withSongResults(page: SearchSummaryPage, songs: List<SongItem>): SearchSummaryPage {
        if (songs.isEmpty()) return page
        val index = page.summaries.indexOfFirst { it.title.equals("Songs", ignoreCase = true) }
        val merged = (songs + page.summaries.getOrNull(index)?.items.orEmpty()).distinctBy { it.id }
        val section = SearchSummary("Songs", merged)
        val summaries = page.summaries.toMutableList()
        if (index >= 0) {
            summaries[index] = section
        } else {
            // Straight after the top section.
            val top = summaries.indexOfFirst { it.title.contains("top", ignoreCase = true) }
            summaries.add((top + 1).coerceIn(0, summaries.size), section)
        }
        return SearchSummaryPage(summaries)
    }

    /**
     * The results as someone searching means them, the same rules as the Android app:
     *
     *  - **The song that was typed leads.** YouTube's own first result is often the song's music
     *    video, a remix, an edit, a cover or a lyric video. The plain track whose title and artist
     *    were typed is found among everything returned ([bestSongFor]) and put first; an artist,
     *    album or playlist stays first only when its exact name was typed ([isNamedBy]).
     *  - **Each thing once.** The summary's sections are separate searches that overlap, so the
     *    same song turned up in Top Results, Songs and Videos. It now appears in the first place
     *    it would show and nowhere after.
     *  - **Songs before videos** inside each section.
     */
    private fun smarten(page: SearchSummaryPage, q: String): SearchSummaryPage {
        val sections = page.summaries
        if (sections.isEmpty()) return page
        val topIndex = sections.indexOfFirst { it.title.contains("top", ignoreCase = true) }.coerceAtLeast(0)
        val top = sections[topIndex]
        val lead = top.items.firstOrNull()
        val everything = sections.flatMap { it.items }
        val best: YTItem? = when {
            lead == null -> null
            lead.isNamedBy(q) -> lead
            else -> everything.bestSongFor(q) ?: if (lead.isMusicVideo) everything.audioTrackFor(lead) else null
        }
        val topItems = if (best != null && best.id != lead?.id) listOf(best) + top.items.filter { it.id != best.id } else top.items
        val seen = HashSet<String>()
        val rebuilt = sections.mapIndexed { i, section ->
            val items = if (i == topIndex) topItems else section.items.songsFirst()
            SearchSummary(section.title, items.filter { seen.add(it.id) })
        }
        return SearchSummaryPage(rebuilt.filter { it.items.isNotEmpty() })
    }

    /**
     * The song the line came from takes the top spot, unless YouTube's top result already is one of
     * them; the other matches sit in their own shelf, first, so they are seen before the rest.
     */
    private fun withLyricMatches(page: SearchSummaryPage?, songs: List<SongItem>): SearchSummaryPage {
        val summaries = page?.summaries.orEmpty()
        val top = summaries.firstOrNull { it.title.contains("top", ignoreCase = true) }
        val topIsMatch = top?.items?.firstOrNull()?.id in songs.map { it.id }
        val lead = if (topIsMatch) top!! else SearchSummary("Top result", listOf(songs.first()))
        val others = songs.filterNot { it.id == lead.items.firstOrNull()?.id }
        return SearchSummaryPage(
            listOf(lead) +
                (if (others.isNotEmpty()) listOf(SearchSummary("From the lyrics", others)) else emptyList()) +
                summaries.filterNot { it === top },
        )
    }

    fun clear() {
        suggestJob?.cancel()
        searchJob?.cancel()
        query = ""
        suggestions = emptyList()
        searchedQuery = null
        summary = null
        items = emptyList()
        searching = false
        error = null
    }
}

private enum class SearchMode { Recent, Suggest, Results, Library }

/**
 * The Search tab, laid out across the whole width: recent searches as pills, suggestions in columns,
 * and results the way Spotify sets them — a big top result beside the best songs, then a shelf for
 * each kind of thing found — with YouTube Music's filters along the top.
 */
@Composable
fun SearchScreen(model: SearchModel, player: DesktopPlayer, state: PlayerState, insets: ChromeInsets, showField: Boolean = true) {
    val history by DesktopPrefs.searchHistory.collectAsState()
    val focus = remember { FocusRequester() }
    var fieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(showField) { if (showField) runCatching { focus.requestFocus() } }
    val start = insets.start + 20.dp
    val library = model.where == SearchScope.Library

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(start = start, end = 28.dp, top = 6.dp)) {
        // The Music app's toolbar: the field in the middle of the page, its scope buttons beside it.
        if (showField) {
            SearchToolbar(model, focus, onFocus = { fieldFocused = it })
            Spacer(Modifier.height(22.dp))
        }

        val mode = when {
            library -> SearchMode.Library
            model.showingResults -> SearchMode.Results
            model.query.isNotBlank() -> SearchMode.Suggest
            else -> SearchMode.Recent
        }
        if (mode == SearchMode.Results) {
            com.ozyern.exhale.desktop.ui.glass.LiquidSegmented(
                labels = SearchTab.entries.map { it.label },
                selected = model.tab.ordinal,
                onSelect = { model.selectTab(SearchTab.entries[it]) },
                accent = MaterialTheme.colorScheme.primary,
                height = 34.dp,
            )
            if (model.typingAhead && model.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SuggestionPills(model.suggestions.take(6)) { model.search(it) }
            }
        }
        Spacer(Modifier.height(14.dp))

        AnimatedContent(
            targetState = if (mode == SearchMode.Results) "results:${model.tab.name}" else mode.name,
            transitionSpec = { (fadeIn(tween(200)) + slideInVertically(spring(dampingRatio = 1f, stiffness = 500f)) { it / 40 }) togetherWith fadeOut(tween(110)) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "search_mode",
        ) { target ->
            val bottom = PaddingValues(bottom = insets.bottom + 16.dp)
            when {
                target == SearchMode.Library.name -> LibraryResults(model.query, player, state, bottom)
                target == SearchMode.Recent.name -> BrowseCategories(bottom)
                target == SearchMode.Suggest.name -> Suggestions(model.suggestions, model.query, bottom, onPick = { model.search(it) })
                // Live results keep the last ones up while the next arrive; a spinner only when there are none.
                model.searching && model.summary == null && model.items.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) { CircularProgressIndicator() }
                model.error != null -> Hint(model.error!!)
                model.tab == SearchTab.All -> AllResults(model.summary, model.searchedQuery.orEmpty(), player, state, bottom, onScope = model::selectTab)
                else -> FilteredResults(model.tab, model.items, model.searchedQuery.orEmpty(), player, state, bottom)
            }
        }
    }

    // Clicking into the empty field drops your recent searches under it, as the Music app does.
    androidx.compose.animation.AnimatedVisibility(
        visible = showField && fieldFocused && model.query.isEmpty() && history.isNotEmpty(),
        enter = fadeIn(tween(140)) + androidx.compose.animation.expandVertically(spring(1f, 700f), expandFrom = Alignment.Top),
        exit = fadeOut(tween(100)),
        modifier = Modifier.align(Alignment.TopCenter).padding(start = insets.start, top = 6.dp + ToolbarHeight + 6.dp),
    ) {
        RecentDropdown(history, onPick = { model.search(it) })
    }
    }
}

private val ToolbarHeight = 36.dp
private val FieldWidth = 340.dp
private val MusicAccent = com.ozyern.exhale.desktop.ui.AppleColors.Accent

/**
 * The toolbar over Search: the field centred on the page, a capsule of glass with the scope as its
 * placeholder, ringed in red while you type; and to its right the scope buttons that pick where it looks.
 */
@Composable
private fun SearchToolbar(model: SearchModel, focus: FocusRequester, onFocus: (Boolean) -> Unit) {
    Box(Modifier.fillMaxWidth().height(ToolbarHeight)) {
        SearchField(
            query = model.query,
            placeholder = model.where.label,
            onChange = model::updateQuery,
            onSearch = { model.search() },
            onClear = model::clear,
            focus = focus,
            onFocus = onFocus,
            modifier = Modifier.align(Alignment.Center).width(FieldWidth),
        )
        ScopeButtons(model.where, onSelect = model::selectWhere, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** The scope buttons: small capsules, the chosen one on a grey plate, the rest plain text. */
@Composable
private fun ScopeButtons(selected: SearchScope, onSelect: (SearchScope) -> Unit, modifier: Modifier = Modifier) {
    val ink = Color.White
    Row(
        modifier
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.06f))
            .border(0.75.dp, ink.copy(alpha = 0.08f), CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SearchScope.entries.forEach { scope ->
            val on = scope == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateFloatAsState(if (on) 0.16f else if (hovered) 0.07f else 0f, tween(120), label = "scope_fill")
            Text(
                scope.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ink.copy(alpha = if (on) 1f else 0.72f),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = fill))
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onSelect(scope) }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            )
        }
    }
}

/** Recent searches under the field: a frosted card of rows, newest first, with Clear at its foot. */
@Composable
private fun RecentDropdown(history: List<String>, onPick: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .width(FieldWidth)
            .shadow(24.dp, shape)
            .clip(shape)
            .background(Color(0xF2262628))
            .border(0.75.dp, Color.White.copy(alpha = 0.1f), shape)
            .padding(6.dp),
    ) {
        Text(
            "Recently Searched",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.48f),
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
        )
        history.take(8).forEach { q -> RecentRow(q, onClick = { onPick(q) }, onRemove = { DesktopPrefs.removeSearch(q) }) }
        Text(
            "Clear Recent Searches",
            fontSize = 12.sp,
            color = MusicAccent,
            modifier = Modifier
                .padding(top = 2.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable { DesktopPrefs.clearSearchHistory() }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )
    }
}

/**
 * Searching your library: favourites and what you've played, matched on title, artist and album as
 * you type. Nothing leaves the PC.
 */
@Composable
private fun LibraryResults(query: String, player: DesktopPlayer, state: PlayerState, padding: PaddingValues) {
    val favorites by DesktopPrefs.favorites.collectAsState()
    val history by DesktopPrefs.history.collectAsState()
    val q = query.trim()
    val songs = remember(q, favorites, history) {
        val all = (favorites + history).distinctBy { it.id }
        if (q.isEmpty()) {
            all
        } else {
            all.filter { song ->
                song.title.contains(q, ignoreCase = true) ||
                    song.artists.any { it.name.contains(q, ignoreCase = true) } ||
                    song.album?.name?.contains(q, ignoreCase = true) == true
            }
        }
    }
    if (songs.isEmpty()) {
        Hint(if (q.isEmpty()) "Songs you favourite or play show up here." else "Nothing in your library matches “$q”.")
        return
    }
    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item(key = "library-h") { ResultsHeader(if (q.isEmpty()) "Your Library" else "Songs") }
        itemsIndexed(songs, key = { _, song -> "library:${song.id}" }) { i, song ->
            SongRow(song, isActive = song.id == state.current?.id, onClick = { player.play(songs, i) }, player = player)
        }
    }
}

/**
 * Search before anything is typed, as the Music app on the Mac lays it out: Browse Categories — every
 * mood and genre as a wide tile of a photo washed in its colour, five across, FLAC first.
 */
@Composable
private fun BrowseCategories(padding: PaddingValues) {
    val navigator = LocalNavigator.current
    val groups by produceState(CategoryArt.groups) {
        if (value == null) value = withContext(Dispatchers.IO) { YouTube.moodAndGenres().getOrNull() }.orEmpty().also { CategoryArt.groups = it }
    }
    val categories = remember(groups) { groups.orEmpty().flatMap { it.items }.distinctBy { it.title } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(176.dp),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Browse Categories", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
        }
        item(key = "category:flac") {
            CategoryTile("FLAC", Color(0xFF2F6FE0), art = null) { navigator?.openFlac?.invoke() }
        }
        if (groups == null) {
            items(14) { Box(Modifier.fillMaxWidth().aspectRatio(CategoryAspect).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.06f))) }
        } else {
            items(categories, key = { "category:${it.title}:${it.endpoint.browseId}:${it.endpoint.params}" }) { category ->
                val art by produceState(CategoryArt.cached(category)) { if (value == null) value = CategoryArt.load(category) }
                CategoryTile(category.title, Color(category.stripeColor), art) {
                    navigator?.openBrowse?.invoke(category.endpoint.browseId, category.endpoint.params, category.title)
                }
            }
        }
    }
}

private const val CategoryAspect = 1.62f

/**
 * A photo for each category tile: the cover of the first playlist on the category's own page, asked
 * for only when its tile comes on screen, a few at a time, and kept for the session.
 */
private object CategoryArt {
    @Volatile var groups: List<MoodAndGenres>? = null
    private val art = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val gate = kotlinx.coroutines.sync.Semaphore(4)

    private fun key(item: MoodAndGenres.Item) = "${item.endpoint.browseId}|${item.endpoint.params}"

    fun cached(item: MoodAndGenres.Item): String? = art[key(item)]

    suspend fun load(item: MoodAndGenres.Item): String? {
        cached(item)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) { YouTube.browse(item.endpoint.browseId, item.endpoint.params).getOrNull() }
                ?.items?.flatMap { it.items }?.firstNotNullOfOrNull { it.thumbnail }
                ?.resizedThumbnail(360)
                ?.also { art[key(item)] = it }
        }
    }
}

/**
 * The Music app's duotone: the photo's light and shade remapped onto two tones of one colour, so a
 * tile reads as its colour first and its picture second. Black lands on [shade], white on [light].
 */
private fun duotone(shade: Color, light: Color): androidx.compose.ui.graphics.ColorFilter {
    fun row(s: Float, l: Float) = floatArrayOf(0.299f * (l - s), 0.587f * (l - s), 0.114f * (l - s), 0f, s * 255f)
    val r = row(shade.red, light.red)
    val g = row(shade.green, light.green)
    val b = row(shade.blue, light.blue)
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(
        androidx.compose.ui.graphics.ColorMatrix(r + g + b + floatArrayOf(0f, 0f, 0f, 1f, 0f)),
    )
}

/** [color]'s hue at a saturation and brightness white text reads on; greys stay grey. */
private fun richer(color: Color): Color {
    val hsb = java.awt.Color.RGBtoHSB((color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt(), null)
    val grey = hsb[1] < 0.08f
    val saturation = if (grey) hsb[1] else hsb[1].coerceAtLeast(0.62f)
    // A grey has no colour to carry white text, so it goes darker than the rest.
    val rgb = java.awt.Color.HSBtoRGB(hsb[0], saturation, hsb[2].coerceIn(0.36f, if (grey) 0.5f else 0.72f))
    return Color(rgb or (0xFF shl 24))
}

/** A past search: magnifier, the words, and an ✕ that shows under the pointer. */
@Composable
private fun RecentRow(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (hovered) 0.06f else 0f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (hovered) {
            Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * A category as the Music app draws one: a wide tile of a photo washed in the category's colour, the
 * name in white at its foot over a soft shade. The pointer brightens it a touch; nothing jumps.
 */
@Composable
private fun CategoryTile(title: String, color: Color, art: String?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, false, pressedScale = 0.97f)
    // YouTube's category colours are pastels, too pale for white lettering: deepen them into the
    // rich, saturated colours the Music app's tiles have, keeping each one's hue.
    val rich = remember(color) { richer(color) }
    val filter = remember(rich) { duotone(lerp(rich, Color.Black, 0.58f), lerp(rich, Color.White, 0.16f)) }
    val glow by animateFloatAsState(if (hovered) 0.08f else 0f, tween(140), label = "category_hover")
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CategoryAspect)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(rich)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (art != null) {
            AsyncImage(art, contentDescription = null, contentScale = ContentScale.Crop, colorFilter = filter, modifier = Modifier.matchParentSize())
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(lerp(rich, Color.White, 0.14f), rich, lerp(rich, Color.Black, 0.3f)), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY))))
        }
        // Enough shade under the name to read it on any photo, and no more.
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.32f))))
        Box(Modifier.matchParentSize().background(Color.White.copy(alpha = glow)))
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp,
            modifier = Modifier.padding(start = 11.dp, end = 11.dp, bottom = 10.dp),
        )
    }
}

/** What YouTube thinks you're typing, as a quiet row over the results; click one to search it. */
@Composable
private fun SuggestionPills(suggestions: List<String>, onPick: (String) -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.07f))
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NavIcons.Search, null, tint = ink.copy(alpha = 0.5f), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(suggestion, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Suggestions(suggestions: List<String>, query: String, padding: PaddingValues, onPick: (String) -> Unit) {
    if (suggestions.isEmpty()) {
        Hint("Press Enter to search for “${query.trim()}”.")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(360.dp),
        contentPadding = padding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(suggestions, key = { "suggest:$it" }) { suggestion ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (hovered) Color.White.copy(alpha = 0.07f) else Color.Transparent)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onPick(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NavIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(suggestion, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun rememberIntro(key: Any?): () -> Float {
    val intro = remember(key) { Animatable(0f) }
    LaunchedEffect(intro) { intro.animateTo(1f, tween(520, easing = LinearOutSlowInEasing)) }
    return { intro.value }
}

@Composable
private fun AllResults(summary: SearchSummaryPage?, query: String, player: DesktopPlayer, state: PlayerState, padding: PaddingValues, onScope: (SearchTab) -> Unit) {
    val summaries = summary?.summaries.orEmpty()
    val topSummary = summaries.firstOrNull { it.title.contains("top", ignoreCase = true) }
    // The likeliest things, whatever they are; the first is the big card, the rest a shelf of their own.
    val top = (topSummary?.items ?: summaries.mapNotNull { it.items.firstOrNull() }).distinctBy { it.id }.take(8)
    val best = top.firstOrNull()
    val songSummary = summaries.firstOrNull { it.title.equals("Songs", ignoreCase = true) }
    // Songs are audio tracks, by YouTube's own tag on each result: not the music videos, interviews,
    // fan uploads and podcast episodes a summary also carries (those have their own shelves).
    val songs = (songSummary?.items ?: summaries.flatMap { it.items })
        .filterIsInstance<SongItem>()
        .filter { it.isAudioTrack && it.id != best?.id }
        .distinctBy { it.id }
        .take(12)
    val alsoTop = top.drop(1).filterNot { item -> songs.take(4).any { it.id == item.id } }
    val shelves = summaries.filter { it !== topSummary && it !== songSummary && it.items.isNotEmpty() }
    val intro = rememberIntro(query)
    val open = rememberItemOpener(player)

    if (top.isEmpty() && shelves.isEmpty() && songs.isEmpty()) {
        Hint("Nothing found for “$query”.")
        return
    }

    LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
        item(key = "lead") {
            // The best match as one big card, its songs beside it; stacked when the window is narrow.
            BoxWithConstraints(Modifier.fillMaxWidth().feedIntro(0f, intro)) {
                val wide = maxWidth >= 760.dp
                val card: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier) {
                        ResultsHeader("Top Result")
                        if (best != null) {
                            TopResultCard(
                                best,
                                active = best.id == state.current?.id,
                                playing = state.isPlaying,
                                onPlay = { player.playNow(best) },
                                onOpen = { open(best) },
                            )
                        }
                    }
                }
                val list: @Composable (Modifier) -> Unit = { modifier ->
                    val lead = songs.take(4)
                    Column(modifier) {
                        ResultsHeader("Songs", onMore = { onScope(SearchTab.Songs) }) {
                            HeaderAction("Play") { player.play(songs, 0) }
                        }
                        lead.forEachIndexed { i, song ->
                            CompactSongRow(song, active = song.id == state.current?.id, player = player, last = i == lead.lastIndex) { player.play(songs, i) }
                        }
                    }
                }
                when {
                    best == null -> list(Modifier.fillMaxWidth())
                    songs.isEmpty() -> card(Modifier.widthIn(max = 460.dp).fillMaxWidth())
                    wide -> Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        card(Modifier.weight(0.42f))
                        list(Modifier.weight(0.58f))
                    }
                    else -> Column {
                        card(Modifier.fillMaxWidth())
                        list(Modifier.fillMaxWidth())
                    }
                }
            }
        }
        if (alsoTop.isNotEmpty()) {
            item(key = "more-h") { ResultsHeader("More Results") }
            item(key = "more") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.feedIntro(1f, intro)) {
                    itemsIndexed(alsoTop, key = { i, item -> "more:$i:${item.id}" }) { _, item ->
                        ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = 164.dp, onPlay = { player.playNow(item) })
                    }
                }
            }
        }
        shelves.forEachIndexed { index, shelf ->
            val scope = SearchTab.entries.firstOrNull { it.filter != null && shelf.title.contains(it.label.removeSuffix("s"), ignoreCase = true) }
            item(key = "shelf-h:$index") {
                ResultsHeader(shelf.title, onMore = scope?.let { { onScope(it) } })
            }
            item(key = "shelf:$index") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.feedIntro(index + 2f, intro)) {
                    itemsIndexed(shelf.items, key = { i, item -> "$index:$i:${item.id}" }) { _, item ->
                        ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = 164.dp, onPlay = { player.playNow(item) })
                    }
                }
            }
        }
    }
}

/**
 * A song beside the top result: small cover, title over artist, its length at the end, a hairline under
 * the text (none under the [last]) and the ⋯ only under the pointer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CompactSongRow(song: SongItem, active: Boolean, player: DesktopPlayer, last: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ink.copy(alpha = if (hovered) 0.05f else 0f))
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Press) { if (it.buttons.isSecondaryPressed) menuOpen = true }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 4.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                song.thumbnail.resizedThumbnail(120),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(ink.copy(alpha = 0.08f)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary else ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Some results come without their artists; the album stands in, or the title sits alone.
                val by = song.artists.joinToString { it.name }.ifBlank { song.album?.name.orEmpty() }
                if (by.isNotBlank()) Text(by, fontSize = 13.sp, color = ink.copy(alpha = 0.55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            song.duration?.let {
                Text(formatTime(it.toDouble()), fontSize = 13.sp, color = ink.copy(alpha = 0.55f), modifier = Modifier.padding(horizontal = 8.dp))
            }
            Box {
                Box(
                    Modifier
                        .size(30.dp)
                        .graphicsLayer { alpha = if (hovered || menuOpen) 1f else 0f }
                        .clip(CircleShape)
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(NavIcons.More, "More", tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                }
                SongActionsMenu(song, player, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
        // The hairline starts where the text does, as Apple's list rows do.
        if (!last) Box(Modifier.align(Alignment.BottomStart).padding(start = 60.dp).fillMaxWidth().height(0.5.dp).background(ink.copy(alpha = 0.12f)))
    }
}

@Composable
private fun FilteredResults(tab: SearchTab, items: List<YTItem>, query: String, player: DesktopPlayer, state: PlayerState, padding: PaddingValues) {
    val intro = rememberIntro("$query|$tab")
    val open = rememberItemOpener(player)
    if (items.isEmpty()) {
        Hint("No ${tab.label.lowercase()} found for “$query”.")
        return
    }
    if (tab == SearchTab.Songs || tab == SearchTab.Videos) {
        val songs = items.filterIsInstance<SongItem>()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(520.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ResultsHeader("${songs.size} ${tab.label.lowercase()}") }
            itemsIndexed(songs, key = { i, song -> "$i:${song.id}" }) { i, song ->
                Box(Modifier.feedIntro(i.coerceAtMost(14) * 0.5f, intro)) {
                    SongRow(song, isActive = song.id == state.current?.id, onClick = { player.playItem(song) }, player = player)
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(190.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { ResultsHeader("${items.size} ${tab.label.lowercase()}") }
            itemsIndexed(items, key = { i, item -> "$i:${item.id}" }) { i, item ->
                Box(Modifier.feedIntro(i.coerceAtMost(18) * 0.4f, intro)) {
                    ItemCard(item, isActive = item.id == state.current?.id, onClick = { open(item) }, artSize = 176.dp, onPlay = { player.playNow(item) })
                }
            }
        }
    }
}

@Composable
private fun ResultsHeader(text: String, onMore: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    // One height whether or not there's an action beside it, so headers side by side line up.
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).then(if (onMore != null) Modifier.clickable(onClick = onMore) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (onMore != null) Text(" ›", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** A quiet word beside a section heading: play the lot, or put the lot in the queue. */
@Composable
private fun HeaderAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun typeLabel(item: YTItem): String = when (item) {
    is SongItem -> if (item.isMusicVideo) "Music Video" else "Song"
    is ArtistItem -> "Artist"
    is AlbumItem -> "Album"
    is PlaylistItem -> "Playlist"
}

/**
 * The best match as one big card, as tall as the four songs beside it: artwork top left (round for an
 * artist), the name large under it, what it is and who by in grey, and a play button at the corner.
 * The card takes a deep shade of the artwork's own colour and lifts a little under the pointer.
 */
@Composable
private fun TopResultCard(item: YTItem, active: Boolean, playing: Boolean, onPlay: () -> Unit, onOpen: () -> Unit) {
    val colors by rememberArtworkColors(item.thumbnail?.resizedThumbnail(120))
    val ink = MaterialTheme.colorScheme.onSurface
    val base by animateColorAsState(colors.firstOrNull() ?: ink.copy(alpha = 0.3f), tween(500), label = "top_result_base")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.985f, hoverScale = 1.01f)
    val shape = RoundedCornerShape(18.dp)
    val artShape = if (item is ArtistItem) CircleShape else RoundedCornerShape(10.dp)
    val accent = MaterialTheme.colorScheme.primary
    val byline = listOf(typeLabel(item), item.subtitle().orEmpty()).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    Box(
        Modifier
            .fillMaxWidth()
            .height(232.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(base, Color.Black, 0.45f), lerp(base, Color.Black, 0.8f)), start = Offset.Zero, end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)))
            .border(0.5.dp, Color.White.copy(alpha = if (hovered) 0.16f else 0.08f), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(20.dp),
    ) {
        Column(Modifier.padding(end = 64.dp)) {
            AsyncImage(
                item.thumbnail?.resizedThumbnail(400),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(100.dp).shadow(12.dp, artShape).clip(artShape).background(Color.White.copy(alpha = 0.06f)),
            )
            Spacer(Modifier.weight(1f))
            Text(item.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(byline, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val playScale by animateFloatAsState(if (hovered) 1.06f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "top_result_play")
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(accent)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            if (active && playing) {
                EqualizerBars(MaterialTheme.colorScheme.onPrimary, Modifier.size(20.dp))
            } else {
                Icon(TransportIcons.Play, contentDescription = "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * The field as a capsule of the same glass as the dock and the player, not a form box: a magnifier,
 * the words at body size, and a round clear button. Focus brightens the rim and lifts the capsule a
 * hair instead of drawing a coloured ring; until something is typed, Ctrl K sits where the clear
 * button goes.
 */
@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focus: FocusRequester,
    onFocus: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = Color.White
    val shape = CircleShape
    // macOS's focus ring: the accent around the field while it has the keyboard, nothing otherwise.
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "search_ring")
    val rim by animateFloatAsState(if (hovered) 0.16f else 0.1f, tween(160), label = "search_rim")
    val backdrop = LocalPageBackdrop.current
    Row(
        modifier
            .height(ToolbarHeight - 4.dp)
            .then(if (ring > 0.01f) Modifier.border(2.dp, MusicAccent.copy(alpha = 0.7f * ring), shape) else Modifier)
            .padding(if (ring > 0.01f) 2.dp else 0.dp)
            .then(if (backdrop != null) Modifier.liquidGlass(shape, backdrop, tintAlpha = 0.3f, blurRadius = 32.dp) else Modifier.glassPlate(shape))
            .border(0.75.dp, ink.copy(alpha = rim), shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { runCatching { focus.requestFocus() } }
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NavIcons.Search, contentDescription = null, tint = ink.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, fontSize = 13.sp, color = ink.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, color = ink),
                cursorBrush = SolidColor(MusicAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocus(it.isFocused)
                    }
                    .typingField()
                    .onPreviewKeyEvent {
                        when {
                            it.type != KeyEventType.KeyDown -> false
                            it.key == Key.Enter || it.key == Key.NumPadEnter -> {
                                onSearch()
                                true
                            }
                            it.key == Key.Escape && query.isNotEmpty() -> {
                                onClear()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
        AnimatedContent(
            targetState = query.isNotEmpty(),
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(spring(0.6f, 600f), initialScale = 0.6f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(120), targetScale = 0.6f)) },
            label = "search_trailing",
        ) { typed ->
            if (typed) {
                // Drawn rather than typed, so the cross sits dead centre in its disc.
                val clear = remember { MutableInteractionSource() }
                val clearPressed by clear.collectIsPressedAsState()
                val clearScale by pressScale(clearPressed, pressedScale = 0.85f)
                Box(
                    Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = clearScale
                            scaleY = clearScale
                        }
                        .clip(CircleShape)
                        .clickable(interactionSource = clear, indication = null, onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(15.dp)) {
                        drawCircle(ink.copy(alpha = 0.28f))
                        val arm = size.minDimension * 0.18f
                        val c = center
                        val stroke = 1.6.dp.toPx()
                        drawLine(Color.Black.copy(alpha = 0.75f), Offset(c.x - arm, c.y - arm), Offset(c.x + arm, c.y + arm), stroke, StrokeCap.Round)
                        drawLine(Color.Black.copy(alpha = 0.75f), Offset(c.x + arm, c.y - arm), Offset(c.x - arm, c.y + arm), stroke, StrokeCap.Round)
                    }
                }
            } else if (!focused) {
                Text(
                    "Ctrl K",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ink.copy(alpha = 0.5f),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, ink.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            } else {
                Spacer(Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp, horizontal = 4.dp))
}

@Suppress("unused")
private val UnusedIcon: ImageVector? = null
