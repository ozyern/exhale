/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.viewmodels

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ozyern.exhale.innertube.YouTube
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.WatchEndpoint
import com.ozyern.exhale.innertube.models.YTItem
import com.ozyern.exhale.innertube.models.filterExplicit
import com.ozyern.exhale.innertube.models.filterVideo
import com.ozyern.exhale.innertube.pages.ExplorePage
import com.ozyern.exhale.innertube.pages.HomePage
import com.ozyern.exhale.innertube.utils.completed
import com.ozyern.exhale.innertube.utils.parseCookieString
import com.ozyern.exhale.constants.HideExplicitKey
import com.ozyern.exhale.constants.HideVideoKey
import com.ozyern.exhale.constants.InnerTubeCookieKey
import com.ozyern.exhale.constants.ContentLanguageKey
import com.ozyern.exhale.constants.PreferredAudioLanguagesKey
import com.ozyern.exhale.constants.QuickPicks
import com.ozyern.exhale.constants.QuickPicksKey
import com.ozyern.exhale.constants.SpeedDialSongIdsKey
import com.ozyern.exhale.constants.YtmSyncKey
import com.ozyern.exhale.constants.PreferredArtistsKey
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.db.entities.Artist
import com.ozyern.exhale.db.entities.ArtistEntity
import com.ozyern.exhale.db.MusicDatabase
import com.ozyern.exhale.db.entities.*
import com.ozyern.exhale.extensions.toEnum
import com.ozyern.exhale.models.ItemMetadata
import com.ozyern.exhale.models.SimilarRecommendation
import com.ozyern.exhale.playback.DownloadUtil
import com.ozyern.exhale.utils.dataStore
import com.ozyern.exhale.utils.ContentLanguageFilter
import com.ozyern.exhale.constants.PlaylistSortType
import com.ozyern.exhale.utils.get
import com.ozyern.exhale.utils.getAsync
import com.ozyern.exhale.utils.SyncUtils
import com.ozyern.exhale.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
    val downloadUtil: DownloadUtil,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    private val isInitialLoadComplete = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val speedDialSongs = MutableStateFlow<List<Song>>(emptyList())
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)

    /**
     * The big cards at the top of Home, as the Windows app builds them: songs from the radios of the
     * artists this listener actually plays most, alternated with the songs YouTube's own feed picked,
     * each carrying the reason it is there.
     */
    val madeForYou = MutableStateFlow<List<com.ozyern.exhale.models.Recommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = combine(
        quickPicks,
        forgottenFavorites,
        keepListening
    ) { quickPicks, forgottenFavorites, keepListening ->
        (quickPicks.orEmpty() + forgottenFavorites.orEmpty() + keepListening.orEmpty())
            .filter { it is Song || it is Album }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allYtItems = combine(
        similarRecommendations,
        homePage
    ) { similarRecommendations, homePage ->
        similarRecommendations?.flatMap { it.items }.orEmpty() +
                homePage?.sections?.flatMap { it.items }.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val libraryMetadata = combine(
        database.allSongs(),
        database.allArtistsByPlayTime(),
        database.playlists(PlaylistSortType.CREATE_DATE, true),
    ) { songs, artists, playlists ->
        val metadataMap = mutableMapOf<String, ItemMetadata>()

        songs.forEach { song ->
            metadataMap[song.id] = ItemMetadata(
                isLiked = song.song.liked,
                isInLibrary = song.song.inLibrary != null
            )
        }

        artists.forEach { artist ->
            if (artist.artist.bookmarkedAt != null) {
                metadataMap[artist.id] = ItemMetadata(isLiked = true)
            }
        }

        playlists.forEach { playlist ->
            if (playlist.playlist.bookmarkedAt != null) {
                metadataMap[playlist.id] = ItemMetadata(isLiked = true)
            }
        }

        metadataMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allItemsMetadata = combine(
        libraryMetadata,
        downloadUtil.downloads
    ) { libraryMeta, downloads ->
        if (downloads.isEmpty()) return@combine libraryMeta

        val metadataMap = libraryMeta.toMutableMap()
        downloads.forEach { (id, download) ->
            val current = metadataMap[id] ?: ItemMetadata()
            metadataMap[id] = current.copy(downloadState = download.state)
        }
        metadataMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Account display info
    val accountName = MutableStateFlow("Guest")
    val accountImageUrl = MutableStateFlow<String?>(null)
    
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    
    // Track if we're currently processing account data
    private var isProcessingAccountData = false
    private var wasLoggedIn = false

    private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? {
        return chips?.filterNot { it.title.contains("podcasts", ignoreCase = true) }
    }

    /**
     * STRICT language enforcement: the user's onboarding/content language picks are read from
     * DataStore and every Innertube feed batch is passed through [ContentLanguageFilter], so an
     * English-only user never sees Hindi/other-language shelves leak onto Home. The Innertube
     * `hl` locale is already injected globally in App.kt; this closes the gap for mixed-language
     * regional feeds the API returns regardless of locale.
     */
    private suspend fun preferredLanguages(): Set<String> =
        ContentLanguageFilter.resolveLanguages(
            preferredCsv = context.dataStore.getAsync(PreferredAudioLanguagesKey, ""),
            contentLanguage = context.dataStore.getAsync(ContentLanguageKey, "system"),
        )

    private suspend fun getQuickPicks(){
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> quickPicks.value = database.quickPicks().first().shuffled().take(20)
            QuickPicks.LAST_LISTEN -> songLoad()
        }
    }

    private suspend fun loadSpeedDialSongs() {
        val speedDialIds = context.dataStore.getAsync(SpeedDialSongIdsKey, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)
        if (speedDialIds.isEmpty()) {
            speedDialSongs.value = emptyList()
            return
        }
        val songsById = database.getSongsByIds(speedDialIds).associateBy { it.id }
        speedDialSongs.value = speedDialIds.mapNotNull { songsById[it] }
    }

    private suspend fun load() {
        if (isLoading.value) return
        isLoading.value = true
        
        try {
            supervisorScope {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val languages = preferredLanguages()
                val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                launch { getQuickPicks() }
                launch { loadSpeedDialSongs() }
                launch { forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20) }
                
                launch {
                    val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)
                    val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                        .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
                        .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)
                    keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                }

                launch {
                        YouTube.home().onSuccess { page ->
                        homePage.value = page.copy(
                            chips = filterHomeChips(page.chips),
                            sections = ContentLanguageFilter.filterHomeSections(
                                page.sections.map { section ->
                                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                                },
                                languages,
                            )
                        )
                    }.onFailure { reportException(it) }
                }

                launch {
                    YouTube.explore().onSuccess { page ->
                        val artists: MutableMap<Int, String> = mutableMapOf()
                        val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                        database.allArtistsByPlayTime().first().let { list ->
                            var favIndex = 0
                            for ((artistsIndex, artist) in list.withIndex()) {
                                artists[artistsIndex] = artist.id
                                if (artist.artist.bookmarkedAt != null) {
                                    favouriteArtists[favIndex] = artist.id
                                    favIndex++
                                }
                            }
                        }
                        explorePage.value = ContentLanguageFilter.filterExplorePage(
                            page.copy(
                                newReleaseAlbums = page.newReleaseAlbums
                                    .sortedBy { album ->
                                        val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                        val firstArtistKey = artistIds.firstNotNullOfOrNull { artistId ->
                                            if (artistId in favouriteArtists.values) {
                                                favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                            } else {
                                                artists.entries.firstOrNull { it.value == artistId }?.key
                                            }
                                        } ?: Int.MAX_VALUE
                                        firstArtistKey
                                    }.filterExplicit(hideExplicit)
                            ),
                            languages,
                        )
                    }.onFailure { reportException(it) }
                }
            }

            viewModelScope.launch(Dispatchers.IO) {
                loadSimilarRecommendations()
            }
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { loadMadeForYou() }.onFailure { reportException(it) }
            }
                    
            isInitialLoadComplete.value = true
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isLoading.value = false
        }
    }

    /**
     * Onboarding-seeded recommendations. The Song Preferences flow persists the user's chosen
     * artists as a CSV of names ([PreferredArtistsKey]); on a fresh install the play-history the
     * normal recommender leans on is empty, so without this the Home page would ignore the
     * onboarding entirely. Here we resolve each saved name to a real YouTube Music artist
     * (search → artist page) and surface that artist's shelves as a "Because you like …"
     * recommendation, tuning Home to the onboarding picks from the very first launch.
     *
     * Resolved artist ids are cached in-process so a refresh does not re-hit search every time.
     */
    private val seededArtistIdCache = mutableMapOf<String, String?>()

    private suspend fun loadOnboardingSeededRecommendations(
        hideExplicit: Boolean,
        hideVideo: Boolean,
    ): List<SimilarRecommendation> {
        val preferredNames = context.dataStore.getAsync(PreferredArtistsKey, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(4) // keep the Home fetch light; the rest still bias search locale via App.kt
        if (preferredNames.isEmpty()) return emptyList()

        return preferredNames.mapNotNull { name ->
            val artistId = seededArtistIdCache.getOrPut(name) {
                YouTube.search(name, YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    ?.items?.filterIsInstance<ArtistItem>()?.firstOrNull()?.id
            } ?: return@mapNotNull null

            val page = YouTube.artist(artistId).getOrNull() ?: return@mapNotNull null
            val items = buildList {
                addAll(page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty())
                addAll(page.sections.lastOrNull()?.items.orEmpty())
            }.filterExplicit(hideExplicit).filterVideo(hideVideo).shuffled()
                .ifEmpty { return@mapNotNull null }

            SimilarRecommendation(
                title = Artist(
                    artist = ArtistEntity(
                        id = artistId,
                        name = page.artist.title,
                        thumbnailUrl = page.artist.thumbnail,
                        channelId = page.artist.channelId,
                    ),
                    songCount = 0,
                ),
                items = items,
            )
        }
    }

    private suspend fun loadMadeForYou() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideo = context.dataStore.get(HideVideoKey, false)
        val languages = preferredLanguages()
        val month = System.currentTimeMillis() - 86400000L * 30

        // Who they listen to: the last month first, all-time when the month is thin.
        val artists = (database.mostPlayedArtists(month, limit = 12).first() + database.allArtistsByPlayTime().first().take(12))
            .filter { it.artist.isYouTubeArtist }
            .distinctBy { it.id }
            .take(4)

        val mix = coroutineScope {
            artists.map { artist ->
                async {
                    val page = YouTube.artist(artist.id).getOrNull() ?: return@async emptyList()
                    val radio = page.artist.radioEndpoint ?: return@async emptyList()
                    YouTube.next(radio).getOrNull()?.items.orEmpty()
                        .map { com.ozyern.exhale.models.Recommendation(it, "Because you like ${artist.artist.name}") }
                }
            }.awaitAll()
        }
        // Each artist's radio in turn, so four artists read as four artists and not one.
        val fromArtists = buildList {
            val lists = mix.map { it.drop(1).shuffled() }
            for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { list -> list.getOrNull(i)?.let(::add) }
        }

        val page = homePage.value ?: YouTube.home().getOrNull()
        val fromFeed = page?.sections.orEmpty().flatMap { section ->
            section.items.filterIsInstance<com.ozyern.exhale.innertube.models.SongItem>()
                .map { com.ozyern.exhale.models.Recommendation(it, section.title) }
        }

        val picks = buildList {
            for (i in 0 until maxOf(fromArtists.size, fromFeed.size)) {
                fromArtists.getOrNull(i)?.let(::add)
                fromFeed.getOrNull(i)?.let(::add)
            }
        }
        val allowed = ContentLanguageFilter.filterItems(
            picks.map { it.song }.filterExplicit(hideExplicit).filterVideo(hideVideo),
            languages,
        ).mapTo(HashSet()) { it.id }
        madeForYou.value = picks.filter { it.song.id in allowed }.distinctBy { it.song.id }.take(10)
    }

    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideo = context.dataStore.get(HideVideoKey, false)
        val languages = preferredLanguages()
        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

        // Onboarding picks lead the rail so a fresh install reflects the user's choices at once.
        val onboardingRecommendations = loadOnboardingSeededRecommendations(hideExplicit, hideVideo)

        val artistRecommendations = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }
            .shuffled().take(3)
            .mapNotNull {
                val items = mutableListOf<YTItem>()
                YouTube.artist(it.id).onSuccess { page ->
                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                    items += page.sections.lastOrNull()?.items.orEmpty()
                }
                SimilarRecommendation(
                    title = it,
                    items = ContentLanguageFilter.filterItems(
                        items.filterExplicit(hideExplicit).filterVideo(hideVideo),
                        languages,
                    ).shuffled().ifEmpty { return@mapNotNull null }
                )
            }

        val songRecommendations = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
            .filter { it.album != null }
            .shuffled().take(2)
            .mapNotNull { song ->
                val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                    ?: return@mapNotNull null
                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                SimilarRecommendation(
                    title = song,
                    items = ContentLanguageFilter.filterItems(
                        (page.songs.shuffled().take(8) +
                                page.albums.shuffled().take(4) +
                                page.artists.shuffled().take(4) +
                                page.playlists.shuffled().take(4))
                            .filterExplicit(hideExplicit).filterVideo(hideVideo),
                        languages,
                    )
                        .shuffled()
                        .ifEmpty { return@mapNotNull null }
                )
            }

        // Onboarding-seeded rows stay pinned at the top (not shuffled in) so the user immediately
        // sees their picks reflected; de-dupe by artist id against history-derived rows.
        val historyBased = (artistRecommendations + songRecommendations).shuffled()
        val seededIds = onboardingRecommendations.mapTo(HashSet()) { it.title.id }
        similarRecommendations.value =
            onboardingRecommendations + historyBased.filterNot { it.title.id in seededIds }
    }

    private suspend fun songLoad() {
        val song = database.events().first().firstOrNull()?.song
        if (song != null) {
            if (database.hasRelatedSongs(song.id)) {
                val relatedSongs = database.getRelatedSongs(song.id).first().shuffled().take(20)
                quickPicks.value = relatedSongs
            }
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideo = context.dataStore.get(HideVideoKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val languages = preferredLanguages()
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = homePage.value?.sections.orEmpty() +
                    ContentLanguageFilter.filterHomeSections(
                        nextSections.sections.map { section ->
                            section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                        },
                        languages,
                    )
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val languages = preferredLanguages()
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = ContentLanguageFilter.filterHomeSections(
                    nextSections.sections.map { section ->
                        section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                    },
                    languages,
                )
            )
            selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    fun refreshAccountData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isProcessingAccountData) return@launch
            
            isProcessingAccountData = true
            try {
                val cookie = context.dataStore.get(InnerTubeCookieKey, "")
                if (cookie.isNotEmpty()) {
                    YouTube.cookie = cookie
                    
                    YouTube.accountInfo().onSuccess { info ->
                        accountName.value = info.name
                        accountImageUrl.value = info.thumbnailUrl
                    }.onFailure {
                        timber.log.Timber.w(it, "Failed to fetch account info")
                    }

                    launch {
                        YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                            val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                            accountPlaylists.value = lists
                        }.onFailure {
                            timber.log.Timber.w(it, "Failed to fetch playlists")
                        }
                    }
                } else {
                    accountName.value = "Guest"
                    accountImageUrl.value = null
                    accountPlaylists.value = null
                }
            } finally {
                isProcessingAccountData = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[SpeedDialSongIdsKey].orEmpty() }
                .distinctUntilChanged()
                .collect {
                    loadSpeedDialSongs()
                }
        }

        // React to onboarding completion (or Settings re-run): when the preferred-artists CSV
        // changes, drop the resolution cache and rebuild the recommendation rail so Home reflects
        // the new picks immediately, without waiting for a manual pull-to-refresh.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[PreferredArtistsKey].orEmpty() }
                .distinctUntilChanged()
                .drop(1) // skip the initial value; init's load() already covers first paint
                .collect {
                    seededArtistIdCache.clear()
                    loadSimilarRecommendations()
                }
        }

        // Strict-language enforcement is live: if the user changes their language picks
        // (onboarding re-run or Settings → Content), refetch the whole Home feed so already
        // rendered other-language shelves disappear immediately.
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { (it[PreferredAudioLanguagesKey].orEmpty()) + "|" + (it[ContentLanguageKey].orEmpty()) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    load()
                }
        }

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000)
            
            syncUtils.cleanupDuplicatePlaylists()
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect
                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true
                    
                    try {
                        val isLoggedIn = cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
                        val loginTransition = isLoggedIn && !wasLoggedIn
                        wasLoggedIn = isLoggedIn
                        
                        if (isLoggedIn && cookie != null && cookie.isNotEmpty()) {
                            try {
                                YouTube.cookie = cookie
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to set YouTube cookie")
                                return@collect
                            }

                            if (loginTransition) {
                                launch {
                                    try {
                                        if (context.dataStore.get(YtmSyncKey, true)) {
                                            syncUtils.performFullSync()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error during login-triggered sync")
                                        reportException(e)
                                    }
                                }
                            }
                            
                            kotlinx.coroutines.delay(100)
                            
                            try {
                                YouTube.accountInfo().onSuccess { info ->
                                    accountName.value = info.name
                                    accountImageUrl.value = info.thumbnailUrl
                                }.onFailure { e ->
                                    timber.log.Timber.w(e, "Failed to fetch account info")
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Exception fetching account info")
                            }

                            launch {
                                try {
                                    YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                                        val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { it.id == "SE" }
                                        accountPlaylists.value = lists
                                    }.onFailure { e ->
                                        timber.log.Timber.w(e, "Failed to fetch account playlists")
                                    }
                                } catch (e: Exception) {
                                    timber.log.Timber.e(e, "Exception fetching account playlists")
                                }
                            }
                        } else {
                            accountName.value = "Guest"
                            accountImageUrl.value = null
                            accountPlaylists.value = null
                        }
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "Error processing cookie change")
                        accountName.value = "Guest"
                        accountImageUrl.value = null
                        accountPlaylists.value = null
                    } finally {
                        isProcessingAccountData = false
                    }
                }
        }
    }
}
