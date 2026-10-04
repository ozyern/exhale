/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */





package com.ozyern.exhale.ui.player

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import androidx.compose.material3.Icon
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.canvas.models.CanvasArtwork
import com.ozyern.exhale.constants.PlayerBackgroundStyle
import com.ozyern.exhale.constants.PlayerBackgroundStyleKey
import com.ozyern.exhale.constants.PlayerDesignStyle
import com.ozyern.exhale.constants.PlayerDesignStyleKey
import com.ozyern.exhale.constants.PlayerHorizontalPadding
import com.ozyern.exhale.constants.SeekExtraSeconds
import com.ozyern.exhale.constants.SwipeThumbnailKey
import com.ozyern.exhale.constants.ExhaleCanvasKey
import com.ozyern.exhale.constants.MaxCanvasCacheSizeKey
import com.ozyern.exhale.constants.ThumbnailCornerRadiusKey
import com.ozyern.exhale.constants.CropThumbnailToSquareKey
import com.ozyern.exhale.constants.HidePlayerThumbnailKey
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.extensions.toMediaItem
import com.ozyern.exhale.ui.component.supportsLiveBlur
import com.ozyern.exhale.ui.component.SabrinaCoverCharms
import com.ozyern.exhale.ui.component.sabrinaDecorEnabled
import com.ozyern.exhale.ui.utils.highRes
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.platform.LocalView
import android.content.Context
import android.view.HapticFeedbackConstants

object CanvasArtworkPlaybackCache {
    private const val defaultMaxSize = 256
    private const val PERSIST_FILE = "canvas_artwork_cache.json"
    private const val PERSIST_DEBOUNCE_MS = 2_000L

    private val map = LinkedHashMap<String, CanvasArtwork>(defaultMaxSize, 0.75f, true)
    @Volatile
    private var maxSize = defaultMaxSize
    @Volatile private var cacheFile: File? = null

    private val persistScope = CoroutineScope(Dispatchers.IO)
    private var persistJob: Job? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val mapSerializer = MapSerializer(String.serializer(), CanvasArtwork.serializer())

    fun init(context: Context) {
        cacheFile = File(context.filesDir, PERSIST_FILE)
        // Disk restore is a file read + JSON decode of up to 256 entries — never worth
        // blocking Application.onCreate() for. Hydrate on IO; get() simply misses until
        // the restore lands (the canvas falls back to a live fetch, same as first run).
        persistScope.launch { loadFromDisk() }
    }

    @Synchronized
    fun get(mediaId: String): CanvasArtwork? {
        if (maxSize <= 0) return null
        return map[mediaId]
    }

    @Synchronized
    fun put(mediaId: String, artwork: CanvasArtwork) {
        val limit = maxSize
        if (limit <= 0) return
        if (mediaId.isBlank()) return
        map[mediaId] = artwork
        while (map.size > limit) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        schedulePersist()
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun clear() {
        map.clear()
        schedulePersist()
    }

    @Synchronized
    fun setMaxSize(value: Int) {
        maxSize = value.coerceAtLeast(0)
        if (maxSize == 0) {
            map.clear()
            schedulePersist()
            return
        }
        var evicted = false
        while (map.size > maxSize) {
            val it = map.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
                evicted = true
            } else {
                break
            }
        }
        if (evicted) schedulePersist()
    }

    @Synchronized
    private fun loadFromDisk() {
        val file = cacheFile ?: return
        if (!file.exists()) return
        try {
            val raw = file.readText()
            if (raw.isBlank()) return
            val restored = json.decodeFromString(mapSerializer, raw)
            map.clear()
            map.putAll(restored)
            while (maxSize > 0 && map.size > maxSize) {
                val it = map.entries.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                } else {
                    break
                }
            }
            Timber.d("Canvas cache restored: ${map.size} entries from disk")
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore canvas cache from disk")
            runCatching { file.delete() }
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = persistScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            writeToDisk()
        }
    }

    private fun writeToDisk() {
        val file = cacheFile ?: return
        try {
            val snapshot: Map<String, CanvasArtwork>
            synchronized(this@CanvasArtworkPlaybackCache) {
                snapshot = LinkedHashMap(map)
            }
            val raw = json.encodeToString(mapSerializer, snapshot)
            file.writeText(raw)
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist canvas cache to disk")
        }
    }
}

private data class ThumbnailPage(
    val slotKey: String,
    val windowIndex: Int,
    val mediaItem: MediaItem,
)

@SuppressLint("LocalContextGetResourceValueCall")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    isPlayerExpanded: Boolean = true, // Add parameter to control swipe based on player state
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current

    // States
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val swipeThumbnail by rememberPreference(SwipeThumbnailKey, true)

    val view = LocalView.current

    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val archiveTuneCanvasEnabled by rememberPreference(ExhaleCanvasKey, true)
    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V8,
    )
    val (maxCanvasCacheSize, _) = rememberPreference(
        key = MaxCanvasCacheSizeKey,
        defaultValue = 256,
    )
    val (thumbnailCornerRadius, _) = rememberPreference(
        key = ThumbnailCornerRadiusKey,
        defaultValue = 16f
    )
    val cropThumbnailToSquare by rememberPreference(CropThumbnailToSquareKey, false)
    val sabrinaCharms = sabrinaDecorEnabled()
    val sabrinaCharmPalette = if (sabrinaCharms) {
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        )
    } else {
        emptyList()
    }
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    // Player background style for consistent theming
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )

    val textBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR -> Color.White
        PlayerBackgroundStyle.GRADIENT -> Color.White
        PlayerBackgroundStyle.COLORING -> Color.White
        PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
        PlayerBackgroundStyle.GLOW -> Color.White
        PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
        PlayerBackgroundStyle.FLUID -> Color.White
        PlayerBackgroundStyle.CUSTOM -> Color.White
    }

    // ---- Liquid Glass plate ----
    // The frosted capsule the cover floats on. Deliberately a plain translucent film + hairline
    // border rather than a blurred copy of the artwork: a translucent film over the already-blurred
    // Liquid Glass backdrop reads as frosted glass, and — unlike an artwork blur — carries zero
    // album detail that could bleed onto the cover.
    val glassIsDark = androidx.compose.foundation.isSystemInDarkTheme()
    val glassPlateBrush = remember(glassIsDark) {
        if (glassIsDark) {
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.03f)),
            )
        } else {
            androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.12f)),
            )
        }
    }
    val glassPlateBorder = remember(glassIsDark) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (glassIsDark) 0.22f else 0.55f),
                Color.White.copy(alpha = 0.04f),
            ),
        )
    }

    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
    }

    // Grid state
    val thumbnailLazyGridState = rememberLazyGridState()

    // Create a playlist using correct shuffle-aware logic
    val timeline = playerConnection.player.currentTimeline
    val currentIndex = playerConnection.player.currentMediaItemIndex
    val shuffleModeEnabled = playerConnection.player.shuffleModeEnabled
    val previousWindowIndex = if (swipeThumbnail && !timeline.isEmpty) {
        timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else {
        C.INDEX_UNSET
    }
    val previousMediaMetadata = if (previousWindowIndex != C.INDEX_UNSET) {
        try {
            playerConnection.player.getMediaItemAt(previousWindowIndex)
        } catch (e: Exception) {
            null
        }
    } else null

    val nextWindowIndex = if (swipeThumbnail && !timeline.isEmpty) {
        timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else {
        C.INDEX_UNSET
    }
    val nextMediaMetadata = if (nextWindowIndex != C.INDEX_UNSET) {
        try {
            playerConnection.player.getMediaItemAt(nextWindowIndex)
        } catch (e: Exception) {
            null
        }
    } else null

    val currentMediaItem = remember(mediaMetadata) {
        // Fallback to player's current item if mediaMetadata is null,
        // but prefer mediaMetadata for immediate updates during crossfade.
        val metadata = mediaMetadata
        if (metadata != null) {
            // Use extension to convert metadata to a proper MediaItem with all fields (uri, artwork, tag)
            metadata.toMediaItem()
        } else {
            try {
                playerConnection.player.currentMediaItem
            } catch (e: Exception) {
                null
            }
        }
    }

    val thumbnailPages = buildList {
        if (previousMediaMetadata != null) {
            add(
                ThumbnailPage(
                    slotKey = "previous",
                    windowIndex = previousWindowIndex,
                    mediaItem = previousMediaMetadata
                )
            )
        }
        if (currentMediaItem != null) {
            add(
                ThumbnailPage(
                    slotKey = "current",
                    windowIndex = currentIndex,
                    mediaItem = currentMediaItem
                )
            )
        }
        if (nextMediaMetadata != null) {
            add(
                ThumbnailPage(
                    slotKey = "next",
                    windowIndex = nextWindowIndex,
                    mediaItem = nextMediaMetadata
                )
            )
        }
    }
    val currentMediaIndex = thumbnailPages.indexOfFirst { it.slotKey == "current" }

    // OuterTune Snap behavior
    val horizontalLazyGridItemWidthFactor = 1f
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        SnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
            },
            velocityThreshold = 500f
        )
    }

    // Current item tracking
    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    // Handle swipe to change song
    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || !swipeThumbnail || itemScrollOffset != 0 || currentMediaIndex < 0) return@LaunchedEffect

        if (currentItem > currentMediaIndex && canSkipNext) {
            playerConnection.seekToNext()
            if (com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.isRunning()) {
                try {
                    com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.restart()
                } catch (_: Exception) {
                }
            }
        } else if (currentItem < currentMediaIndex && canSkipPrevious) {
            playerConnection.seekToPrevious()
            if (com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.isRunning()) {
                try {
                    com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.restart()
                } catch (_: Exception) {
                }
            }
        }
    }

    // Update position when song changes
    LaunchedEffect(mediaMetadata, currentMediaItem?.mediaId, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (index >= 0 && index < thumbnailPages.size) {
            try {
                thumbnailLazyGridState.animateScrollToItem(index)
            } catch (e: Exception) {
                thumbnailLazyGridState.scrollToItem(index)
            }
        }
    }

    LaunchedEffect(playerConnection.player.currentMediaItemIndex, currentMediaItem?.mediaId) {
        val index = currentMediaIndex
        if (index >= 0 && index != currentItem) {
            thumbnailLazyGridState.scrollToItem(index)
        }
    }

    // Seek on double tap
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = modifier) {
        // Error view
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center),
        ) {
            error?.let { playbackError ->
                PlaybackError(
                    error = playbackError,
                    mediaId = currentMediaItem?.mediaId,
                    retry = {
                        playerConnection.player.prepare()
                        playerConnection.player.play()
                    },
                )
            }
        }

        // Main thumbnail view
        AnimatedVisibility(
            visible = error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Now Playing header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        color = textBackgroundColor
                    )
                    // Show album title or queue title
                    val playingFrom = queueTitle ?: mediaMetadata?.album?.title
                    if (!playingFrom.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playingFrom,
                            style = MaterialTheme.typography.titleMedium,
                            color = textBackgroundColor.copy(alpha = 0.8f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                // Thumbnail content
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
                    val containerMaxWidth = maxWidth

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = swipeThumbnail && isPlayerExpanded, // Only allow swipe when player is expanded
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = thumbnailPages,
                            key = { page ->
                                "${page.slotKey}:${page.windowIndex}:${page.mediaItem.mediaId.ifEmpty { "unknown" }}"
                            },
                            contentType = { "thumbnailPage" },
                        ) { page ->
                            val item = page.mediaItem
                            val incrementalSeekSkipEnabled by rememberPreference(
                                SeekExtraSeconds,
                                defaultValue = false
                            )
                            var skipMultiplier by remember { mutableStateOf(1) }
                            var lastTapTime by remember { mutableLongStateOf(0L) }
                            val itemMetadata = remember(item) { item.metadata }
                            val storefront =
                                remember {
                                    val country = Locale.getDefault().country
                                    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                                }
                            val shouldAnimateCanvas =
                                archiveTuneCanvasEnabled &&
                                        playerDesignStyle != PlayerDesignStyle.V7 &&
                                        item.mediaId.isNotBlank() &&
                                        item.mediaId == currentMediaItem?.mediaId
                            var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                            var canvasFetchedAtMs by remember(item.mediaId) { mutableLongStateOf(0L) }
                            var canvasFetchInFlight by remember(item.mediaId) { mutableStateOf(false) }

                            LaunchedEffect(shouldAnimateCanvas) {
                                if (!shouldAnimateCanvas) {
                                    canvasArtwork = null
                                    canvasFetchedAtMs = 0L
                                    canvasFetchInFlight = false
                                }
                            }

                            LaunchedEffect(shouldAnimateCanvas, item.mediaId) {
                                if (!shouldAnimateCanvas) return@LaunchedEffect

                                CanvasArtworkPlaybackCache.get(item.mediaId)?.let { cached ->
                                    canvasArtwork = cached
                                    canvasFetchedAtMs = System.currentTimeMillis()
                                    canvasFetchInFlight = false
                                    return@LaunchedEffect
                                }

                                val songTitleRaw =
                                    itemMetadata?.title
                                        ?.takeIf { it.isNotBlank() }
                                        ?: item.mediaMetadata.title?.toString()
                                        ?: return@LaunchedEffect

                                val artistNameRaw =
                                    itemMetadata?.artists?.firstOrNull()?.name
                                        ?.takeIf { it.isNotBlank() }
                                        ?: item.mediaMetadata.artist?.toString()
                                        ?: item.mediaMetadata.subtitle?.toString()
                                        ?: ""

                                val now = System.currentTimeMillis()
                                if (canvasFetchInFlight) return@LaunchedEffect
                                canvasFetchInFlight = true

                                val fetched =
                                    withContext(Dispatchers.IO) {
                                        fetchCanvasArtworkForPlayback(
                                            songTitleRaw = songTitleRaw,
                                            artistNameRaw = artistNameRaw,
                                            storefront = storefront,
                                        )
                                    }
                                // The resolver and this cache use two copies of the same model;
                                // a cast between them can never succeed and threw the moment a
                                // cover was found. Convert instead.
                                val playbackArtwork = fetched?.toPlaybackArtwork()
                                canvasArtwork = playbackArtwork
                                canvasFetchedAtMs = now
                                if (playbackArtwork != null) {
                                    CanvasArtworkPlaybackCache.put(item.mediaId, playbackArtwork)
                                }
                                canvasFetchInFlight = false
                            }

                            Box(
                                modifier = Modifier
                                    .width(horizontalLazyGridItemWidth)
                                    .fillMaxSize()
                                    .padding(horizontal = PlayerHorizontalPadding)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onDoubleTap = { offset ->
                                                val currentPosition =
                                                    playerConnection.player.currentPosition
                                                val duration = playerConnection.player.duration

                                                val now = System.currentTimeMillis()
                                                if (incrementalSeekSkipEnabled && now - lastTapTime < 1000) {
                                                    skipMultiplier++
                                                } else {
                                                    skipMultiplier = 1
                                                }
                                                lastTapTime = now

                                                val skipAmount = 5000 * skipMultiplier

                                                if ((layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                                    (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)
                                                ) {
                                                    playerConnection.player.seekTo(
                                                        (currentPosition - skipAmount).coerceAtLeast(
                                                            0
                                                        )
                                                    )
                                                    seekDirection =
                                                        context.getString(
                                                            R.string.seek_backward_dynamic,
                                                            skipAmount / 1000
                                                        )
                                                } else {
                                                    playerConnection.player.seekTo(
                                                        (currentPosition + skipAmount).coerceAtMost(
                                                            duration
                                                        )
                                                    )
                                                    seekDirection = context.getString(
                                                        R.string.seek_forward_dynamic,
                                                        skipAmount / 1000
                                                    )
                                                }
                                                // If a user double-tap skip lands on a new media item, restart presence manager to pick up artwork quickly
                                                if (com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.isRunning()) {
                                                    try {
                                                        com.ozyern.exhale.ui.screens.settings.DiscordPresenceManager.restart()
                                                    } catch (_: Exception) {
                                                    }
                                                }

                                                showSeekEffect = true
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val coverSize = containerMaxWidth - (PlayerHorizontalPadding * 2)

                                // Diffuse colour-bleed drop shadow. Rather than a flat black
                                // elevation shadow, this is a second, heavily blurred copy of the
                                // artwork itself pushed down behind the cover: it picks up the
                                // album's own colours, so the cover reads as floating and glowing
                                // over the liquid backdrop instead of pasted on it.
                                //
                                // Static per song, so the RenderEffect result is retained by the
                                // render node — the blur is paid for once, not per frame. Gated on
                                // API 31 because Modifier.blur is a silent no-op below it, and an
                                // un-blurred duplicate would just look like a misaligned cover.
                                if (!hidePlayerThumbnail && supportsLiveBlur) {
                                    val shadowUrl = item.metadata?.thumbnailUrl?.highRes()
                                        ?: item.mediaMetadata.artworkUri?.toString()
                                    if (shadowUrl != null) {
                                        AsyncImage(
                                            model = shadowUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(coverSize)
                                                .graphicsLayer {
                                                    // Slightly smaller and pushed down: the blur
                                                    // spreads it back out past the cover's edges,
                                                    // heaviest along the bottom where a real cast
                                                    // shadow would fall.
                                                    scaleX = 0.94f
                                                    scaleY = 0.94f
                                                    translationY = 26.dp.toPx()
                                                    alpha = 0.55f
                                                    clip = false
                                                }
                                                .blur(
                                                    radius = 44.dp,
                                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                                ),
                                        )
                                    }
                                }

                                // The frosted-glass CAPSULE the cover sits on.
                                //
                                // This — not the artwork — is the surface that carries the Liquid
                                // Glass treatment: a translucent film with a hairline light-catching
                                // border, letting the blurred mesh backdrop glow through from behind.
                                // The cover is then drawn on top of it, fully sharp.
                                //
                                // It is a sibling *behind* the clipped cover box rather than that
                                // box's own background, so nothing it draws can ever be composited
                                // over the artwork.
                                Box(
                                    modifier = Modifier
                                        .size(coverSize)
                                        // Subtle cast shadow so the cover reads as floating ON the
                                        // glass rather than pasted into it. Drawn by the plate, not
                                        // the cover, so it can never darken the artwork itself.
                                        .shadow(
                                            elevation = 18.dp,
                                            shape = RoundedCornerShape(thumbnailCornerRadius.dp),
                                            clip = false,
                                        )
                                        .clip(RoundedCornerShape(thumbnailCornerRadius.dp))
                                        .background(glassPlateBrush)
                                        .border(
                                            width = 1.dp,
                                            brush = glassPlateBorder,
                                            shape = RoundedCornerShape(thumbnailCornerRadius.dp),
                                        )
                                )

                                Box(
                                    modifier = Modifier
                                        .size(coverSize)
                                        .clip(RoundedCornerShape(thumbnailCornerRadius.dp))
                                ) {
                                    if (hidePlayerThumbnail) {
                                        // Show app logo when thumbnail is hidden
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.exhale),
                                                contentDescription = stringResource(R.string.hide_player_thumbnail),
                                                tint = textBackgroundColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(120.dp)
                                            )
                                        }
                                    } else {
                                        val primaryCanvasUrl = canvasArtwork?.animated
                                        val fallbackCanvasUrl = canvasArtwork?.videoUrl

                                        val shouldCropArtwork =
                                            cropThumbnailToSquare &&
                                                    playerDesignStyle != PlayerDesignStyle.V7

                                        // NOTE: a blurred, stretched copy of the cover used to be
                                        // drawn here to fill the letterbox left by ContentScale.Fit.
                                        // It bled album detail across — and, wherever the sharp
                                        // layer didn't cover, straight over — the artwork, which is
                                        // exactly the bug this fix removes. The frosted glass plate
                                        // behind the cover now fills that space instead, so the
                                        // artwork is the ONLY thing drawn inside this box and stays
                                        // 100% crisp.
                                        AsyncImage(
                                            model = item.metadata?.thumbnailUrl?.highRes()
                                                ?: item.mediaMetadata.artworkUri?.toString(),
                                            contentDescription = null,
                                            contentScale = if (shouldCropArtwork) ContentScale.Crop else ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .let { if (shouldCropArtwork) it.aspectRatio(1f) else it }
                                        )

                                        if (shouldAnimateCanvas && (!primaryCanvasUrl.isNullOrBlank() || !fallbackCanvasUrl.isNullOrBlank())) {
                                            CanvasArtworkPlayer(
                                                primaryUrl = primaryCanvasUrl,
                                                fallbackUrl = fallbackCanvasUrl,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }

                                // Tied on last, so it sits over the cover rather than under it.
                                // A sibling of the artwork box, not a child: the artwork box clips
                                // to the corner radius, and a bow meant to overhang the corner
                                // cannot live inside the thing clipping that corner off.
                                if (sabrinaCharms) {
                                    SabrinaCoverCharms(
                                        size = coverSize,
                                        colors = sabrinaCharmPalette,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            )
        }
    }
}


/*
 * Copyright (C) OuterTune Project
 * Custom SnapLayoutInfoProvider idea belongs to OuterTune
 */

// SnapLayoutInfoProvider
@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider = object : SnapLayoutInfoProvider {
    private val layoutInfo: LazyGridLayoutInfo
        get() = lazyGridState.layoutInfo

    override fun calculateApproachOffset(velocity: Float, decayOffset: Float): Float = 0f
    override fun calculateSnapOffset(velocity: Float): Float {
        val bounds = calculateSnappingOffsetBounds()

        // Only snap when velocity exceeds threshold
        if (abs(velocity) < velocityThreshold) {
            if (abs(bounds.start) < abs(bounds.endInclusive))
                return bounds.start

            return bounds.endInclusive
        }

        return when {
            velocity < 0 -> bounds.start
            velocity > 0 -> bounds.endInclusive
            else -> 0f
        }
    }

    fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

            // Find item that is closest to the center
            if (offset <= 0 && offset > lowerBoundOffset) {
                lowerBoundOffset = offset
            }

            // Find item that is closest to center, but after it
            if (offset >= 0 && offset < upperBoundOffset) {
                upperBoundOffset = offset
            }
        }

        return lowerBoundOffset.rangeTo(upperBoundOffset)
    }
}

fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val desiredDistance = positionInLayout(containerSize.toFloat(), item.size.width.toFloat())
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width