/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import coil3.request.transformations
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ozyern.exhale.ui.utils.highRes

/**
 * An [ImageRequest] for now-playing artwork that is pinned to the memory cache under a stable key.
 *
 * The mini player exists in two places that swap during the Dynamic-Island morph: the standalone
 * pill above the nav bar (State A) and the capsule inside the nav bar (State B). Those are
 * different composables, so the artwork leaves the composition and re-enters it mid-animation.
 * With Coil's defaults that means a fresh request, a fresh (possibly async) decode and a 100ms
 * crossfade from nothing — the artwork visibly blinks at exactly the moment the morph is drawing
 * the eye to it.
 *
 * Two things prevent that:
 *
 *  * **A stable key.** `memoryCacheKey` + `placeholderMemoryCacheKey` are both set to the URL, so
 *    every site that shows this song's art reads and writes the same entry. The second site gets
 *    a synchronous cache hit and, failing that, still paints the cached bitmap as its placeholder
 *    instead of empty space. (Coil's default key includes the resolved size, so two views of
 *    different sizes would otherwise miss each other entirely.)
 *  * **No crossfade.** A crossfade is only ever correct when something is actually arriving; on a
 *    cache hit it just fades in a bitmap that was already available. Off, a re-entering image is
 *    painted whole on its first frame.
 *
 * The app-wide loader already reserves a quarter of the heap for the memory cache (see
 * `App.newImageLoader`), so the entry survives comfortably for the length of any transition.
 */
@Composable
fun rememberPinnedArtworkRequest(url: String): ImageRequest {
    val context = LocalContext.current
    return remember(context, url) { pinnedArtworkRequest(context, url) }
}

private fun pinnedArtworkRequest(context: android.content.Context, url: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(url)
        .placeholderMemoryCacheKey(url)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .transformations(TrimVideoBars)
        .build()

/** The player's own cover request, shared by the player and the cover flight: one key, one bitmap. */
fun playerArtworkRequest(context: android.content.Context, thumbnailUrl: String): ImageRequest =
    ImageRequest.Builder(context)
        .data(thumbnailUrl.highRes())
        .size(1200, 1200)
        .placeholderMemoryCacheKey(thumbnailUrl)
        .transformations(TrimVideoBars)
        .build()

/**
 * The covers of the next songs in the queue, fetched while this one plays — the mini player's and
 * the player's — so a skip, or a song ending, paints the new cover on its first frame instead of
 * waiting on the network for it. Only while the app is on screen: in the background nothing would
 * show them, and the disk cache still has them from the last time they were fetched.
 */
@Composable
fun PrefetchUpcomingArtwork() {
    val playerConnection = com.ozyern.exhale.LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val index by playerConnection.currentMediaItemIndex.collectAsState()
    val windows by playerConnection.queueWindows.collectAsState()
    LaunchedEffect(index, windows) {
        // A breath after the song changes, so its own stream and cover go first.
        kotlinx.coroutines.delay(800)
        com.ozyern.exhale.utils.awaitAppVisible()
        val player = playerConnection.player
        val loader = context.imageLoader
        var next = index
        repeat(UPCOMING_ARTWORK) {
            next = runCatching {
                player.currentTimeline.getNextWindowIndex(next, player.repeatMode, player.shuffleModeEnabled)
            }.getOrDefault(C.INDEX_UNSET)
            if (next == C.INDEX_UNSET || next == index) return@LaunchedEffect
            val url = runCatching { player.getMediaItemAt(next).mediaMetadata.artworkUri?.toString() }.getOrNull()
                ?: return@repeat
            loader.enqueue(pinnedArtworkRequest(context, url))
            loader.enqueue(playerArtworkRequest(context, url))
        }
    }
}

private const val UPCOMING_ARTWORK = 2
