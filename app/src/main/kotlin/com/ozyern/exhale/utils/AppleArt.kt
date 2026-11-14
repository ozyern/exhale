/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import coil3.request.allowHardware
import coil3.toBitmap
import com.ozyern.exhale.ui.utils.resize
import com.ozyern.exhale.canvas.AppleArtist
import com.ozyern.exhale.canvas.AppleArtwork
import com.ozyern.exhale.canvas.AppleMusicCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** What a piece of artwork belongs to, so the same item can be looked up on Apple Music. */
sealed interface AppleArtQuery {
    data class Song(val title: String, val artist: String?) : AppleArtQuery
    data class Album(val title: String, val artist: String?) : AppleArtQuery
    data class Artist(val name: String) : AppleArtQuery
}


object AppleArt {
    @Volatile
    var enabled: Boolean = true

    /** Null when not looked up yet this session; otherwise the cached answer (value null = no match). */
    fun peek(query: AppleArtQuery): AppleMusicCatalog.Cached<AppleArtwork>? = when (query) {
        is AppleArtQuery.Song -> AppleMusicCatalog.peekSong(query.title, query.artist)
        is AppleArtQuery.Album -> AppleMusicCatalog.peekAlbum(query.title, query.artist)
        is AppleArtQuery.Artist -> AppleMusicCatalog.peekArtist(query.name)?.let { AppleMusicCatalog.Cached(it.value?.hero) }
    }

    /**
     * How long an image waits for Apple before showing YouTube's. The lookup itself keeps running
     * in the catalog's own scope, so a slow answer still lands in the cache for next time.
     */
    const val WAIT_MS = 2_000L

    suspend fun lookup(query: AppleArtQuery): AppleArtwork? = when (query) {
        is AppleArtQuery.Song -> AppleMusicCatalog.song(query.title, query.artist)
        is AppleArtQuery.Album -> AppleMusicCatalog.album(query.title, query.artist)
        is AppleArtQuery.Artist -> AppleMusicCatalog.artist(query.name, priority = false)?.hero
    }

    private val prefetchScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    private val prefetchGate = kotlinx.coroutines.sync.Semaphore(2)
    private val warming = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Resolve a small set of nearby artists as soon as YouTube returns metadata, before navigation. */
    fun prefetchArtistIds(items: List<com.music.innertube.models.YTItem>) {
        if (!enabled) return
        val names = items.asSequence().flatMap { item ->
            when (item) {
                is com.music.innertube.models.ArtistItem -> sequenceOf(item.title)
                is com.music.innertube.models.SongItem -> item.artists.asSequence().map { it.name }
                is com.music.innertube.models.AlbumItem -> item.artists.orEmpty().asSequence().map { it.name }
                else -> emptySequence() // A playlist author may be a user, not a recording artist.
            }
        }.filter { it.isNotBlank() }.distinctBy(AppleMusicCatalog::normalize).take(4)
        for (name in names) {
            if (warming.size >= 4) break

            val key = "id|${AppleMusicCatalog.storefront}|${AppleMusicCatalog.normalize(name)}"
            if (!warming.add(key)) continue
            prefetchScope.launch {
                try {
                    prefetchGate.acquire()
                    try {
                        if (enabled) artworkResult { AppleMusicCatalog.artistId(name) }
                    } finally { prefetchGate.release() }
                } finally { warming.remove(key) }
            }
        }
    }

    /** Visible artist cards warm both the hero and wordmark before navigation. */
    fun prefetch(context: android.content.Context, query: AppleArtQuery) {
        if (!enabled || query !is AppleArtQuery.Artist) return
        val identity = AppleMusicCatalog.storefront + "|" + AppleMusicCatalog.normalize(query.name)
        if (warming.size >= 4 || !warming.add(identity)) return
        prefetchScope.launch {
            try {
                prefetchGate.acquire()
                try {
                    if (enabled) artworkResult { AppleMusicCatalog.artist(query.name, priority = false) }
                        ?.let { prefetchArtistAssets(context.applicationContext, it) }
                } finally {
                    prefetchGate.release()
                }
            } finally {
                warming.remove(identity)
            }
        }
    }

    internal suspend fun prefetchArtistAssets(context: android.content.Context, artist: AppleArtist) {
        if (!enabled) return
        val loader = coil3.SingletonImageLoader.get(context.applicationContext)
        kotlinx.coroutines.coroutineScope {
            listOfNotNull(artist.hero?.let { it to false }, artist.logo?.let { it to true })
                .map { (art, png) ->
                    async {
                        val width = if (png) logoWidthPx(context) else 1200
                        val height = if (art.width > 0 && art.height > 0) width * art.height / art.width else width
                        loader.execute(coil3.request.ImageRequest.Builder(context.applicationContext)
                            .data(art.sized(width, png)).size(width, height.coerceAtLeast(1)).build())
                    }
                }.awaitAll()
        }
    }

    fun logoWidthPx(context: android.content.Context): Int =
        (286 * context.resources.displayMetrics.density).toInt().coerceIn(1, 1200)
}

/** Apple artwork is preferred; existing art can paint immediately during a cold lookup. */
sealed interface ArtState {
    data object Loading : ArtState
    data class Ready(val url: String?) : ArtState
}

@Composable
fun rememberAppleArtwork(query: AppleArtQuery?, fallbackUrl: String?, px: Int): ArtState {
    if (query == null || !AppleArt.enabled || (fallbackUrl != null && !fallbackUrl.startsWith("http"))) {
        return ArtState.Ready(fallbackUrl)
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(query) { AppleArt.prefetch(context, query) }
    val cached = remember(query, px) { AppleArt.peek(query) }
    val initial = cached?.let { ArtState.Ready(it.value?.sized(px) ?: fallbackUrl) }
        ?: fallbackUrl?.let { ArtState.Ready(it) } ?: ArtState.Loading
    return androidx.compose.runtime.key(query, px, fallbackUrl) {
        val state by produceState<ArtState>(initial) {
            if (cached != null) return@produceState
            kotlinx.coroutines.coroutineScope {
                val request = async(Dispatchers.IO) { artworkResult { AppleArt.lookup(query) } }
                val art = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) { request.await() }
                value = ArtState.Ready(art?.sized(px) ?: fallbackUrl)
                if (art == null) request.await()?.let { value = ArtState.Ready(it.sized(px)) }
            }
        }
        state
    }
}

@Composable
fun rememberAppleArtworkUrl(query: AppleArtQuery?, fallbackUrl: String?, px: Int): String? =
    (rememberAppleArtwork(query, fallbackUrl, px) as? ArtState.Ready)?.url

data class AppleArtistState(val loading: Boolean, val artist: AppleArtist?)

@Composable
fun rememberAppleArtist(name: String?): AppleArtistState {
    if (!AppleArt.enabled) return AppleArtistState(false, null)
    if (name.isNullOrBlank()) return AppleArtistState(true, null)
    val context = androidx.compose.ui.platform.LocalContext.current
    val cached = remember(name) { AppleMusicCatalog.peekArtist(name) }
    val initial = cached?.let { AppleArtistState(false, it.value) } ?: AppleArtistState(true, null)
    return androidx.compose.runtime.key(name) {
        val state by produceState(initial) {
            if (cached != null) {
                cached.value?.let { AppleArt.prefetchArtistAssets(context, it) }
                return@produceState
            }
            kotlinx.coroutines.coroutineScope {
                val request = async(Dispatchers.IO) { artworkResult { AppleMusicCatalog.artist(name) } }
                val artist = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) { request.await() }
                value = AppleArtistState(false, artist)
                artist?.let { AppleArt.prefetchArtistAssets(context, it) }
                if (artist == null) request.await()?.let {
                    value = AppleArtistState(false, it)
                    AppleArt.prefetchArtistAssets(context, it)
                }
            }
        }
        state
    }
}

private suspend fun <T> artworkResult(block: suspend () -> T): T? = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

private val estimatedBgCache = android.util.LruCache<String, Int>(64)

/** Estimates a missing artwork color from a small on-device decode. */
@Composable
fun rememberEstimatedArtworkBg(url: String?): Int? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bg by produceState(url?.let { estimatedBgCache.get(it) }, url) {
        if (url == null || value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val request = coil3.request.ImageRequest.Builder(context)
                .data(url.resize(120, 120))
                .size(120)
                .allowHardware(false)
                .build()
            val result = coil3.SingletonImageLoader.get(context).execute(request) as? coil3.request.SuccessResult
            result?.image?.let { image ->
                runCatching {
                    com.ozyern.exhale.ui.theme.AppleColors.estimateArtworkBackground(image.toBitmap())
                }.getOrNull()
            }?.also { estimatedBgCache.put(url, it) }
        }
    }
    return bg
}

/** Cached album artwork is ready on entry; cold lookups get a short loading window. */
data class AppleAlbumState(val loading: Boolean, val art: AppleArtwork?)

@Composable
fun rememberAppleAlbum(title: String?, artist: String?): AppleAlbumState {
    if (!AppleArt.enabled) return AppleAlbumState(false, null)
    if (title.isNullOrBlank()) return AppleAlbumState(true, null)
    val cached = remember(title, artist) { AppleMusicCatalog.peekAlbum(title, artist) }
    val initial = cached?.let { AppleAlbumState(false, it.value) } ?: AppleAlbumState(true, null)
    return androidx.compose.runtime.key(title, artist) {
        val state by produceState(initial) {
            if (cached != null) return@produceState
            kotlinx.coroutines.coroutineScope {
                val request = async(Dispatchers.IO) {
                    artworkResult { AppleMusicCatalog.album(title, artist, priority = true) }
                }
                val art = kotlinx.coroutines.withTimeoutOrNull(AppleArt.WAIT_MS) { request.await() }
                value = AppleAlbumState(false, art)
                if (art == null) request.await()?.let { value = AppleAlbumState(false, it) }
            }
        }
        state
    }
}
