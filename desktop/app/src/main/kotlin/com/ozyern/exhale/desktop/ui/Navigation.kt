/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.innertube.models.AlbumItem
import com.ozyern.exhale.innertube.models.ArtistItem
import com.ozyern.exhale.innertube.models.BrowseEndpoint
import com.ozyern.exhale.innertube.models.PlaylistItem
import com.ozyern.exhale.innertube.models.SongItem
import com.ozyern.exhale.innertube.models.YTItem

/**
 * Where a tapped album, artist or playlist goes: its own page. Songs keep playing where they are.
 * Provided once by ExhaleApp, so any card, row or link can open a page without threading callbacks.
 */
class AppNavigator(
    val openArtist: (browseId: String, name: String?, thumbnail: String?) -> Unit,
    val openAlbum: (browseId: String, title: String?, thumbnail: String?) -> Unit,
    val openPlaylist: (playlistId: String, title: String?, thumbnail: String?) -> Unit,
    val openArtistItems: (endpoint: BrowseEndpoint, title: String) -> Unit,
    val openSoundChem: () -> Unit = {},
    val openFlac: () -> Unit = {},
    val openFlacRelease: (key: String) -> Unit = {},
    /** A Mood & Genres category, or any other YouTube browse page, by its endpoint. */
    val openBrowse: (browseId: String, params: String?, title: String) -> Unit = { _, _, _ -> },
)

val LocalNavigator = staticCompositionLocalOf<AppNavigator?> { null }

/** Opens [item]'s page, or plays it when it's a song (or when there's nowhere to navigate). */
fun AppNavigator?.open(item: YTItem, player: DesktopPlayer) {
    when {
        item is SongItem || this == null -> if (item is ArtistItem) player.playArtist(item.id) else player.playItem(item)
        item is AlbumItem -> openAlbum(item.browseId, item.title, item.thumbnail)
        item is PlaylistItem -> openPlaylist(item.id, item.title, item.thumbnail)
        item is ArtistItem -> openArtist(item.id, item.title, item.thumbnail)
    }
}

/** Plays [item] straight away — the round play button on a card, rather than the card itself. */
fun DesktopPlayer.playNow(item: YTItem) {
    if (item is ArtistItem) playArtist(item.id) else playItem(item)
}

@Composable
fun rememberItemOpener(player: DesktopPlayer): (YTItem) -> Unit {
    val navigator by rememberUpdatedState(LocalNavigator.current)
    return remember(player) { { item -> navigator.open(item, player) } }
}

// ── The cover in flight: a card's artwork becomes the page's header ──

@OptIn(ExperimentalSharedTransitionApi::class)
class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/** The page transition's scopes, so artwork anywhere in a page can take part in the flight. */
val LocalSharedScopes = staticCompositionLocalOf<SharedScopes?> { null }

/**
 * Which card was just clicked. The card claims its key a moment before it navigates, and the new
 * route takes it, so exactly one cover flies even when the same album sits on two shelves.
 */
object SharedArt {
    private var key: String? = null
    private var at = 0L

    fun claim(sharedKey: String) {
        key = sharedKey
        at = System.currentTimeMillis()
    }

    /** The key claimed in the last half second, once; stale clicks (a song that just played) don't count. */
    fun take(): String? {
        val claimed = key.takeIf { System.currentTimeMillis() - at < 500 }
        key = null
        return claimed
    }
}

/** Slightly under-damped: the cover overshoots its slot by a hair and settles, like it has weight. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val ArtworkBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.86f, stiffness = 280f, visibilityThreshold = Rect.VisibilityThreshold)
}

/** Marks artwork as one end of a cover flight. Does nothing without a key, a transition, or with reduced motion. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String?, shape: Shape): Modifier {
    val scopes = LocalSharedScopes.current
    if (key == null || scopes == null || AppSettings.current.reducedMotion || Bench.off("shared")) return this
    return with(scopes.transition) {
        this@sharedArtwork.sharedElement(
            rememberSharedContentState(key),
            scopes.visibility,
            boundsTransform = ArtworkBoundsTransform,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}
