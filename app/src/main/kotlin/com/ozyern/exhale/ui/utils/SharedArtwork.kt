@file:OptIn(ExperimentalSharedTransitionApi::class)

/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.utils

import android.net.Uri
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import coil3.Image
import coil3.toBitmap
import coil3.compose.AsyncImagePainter
import com.ozyern.artwork.ArtworkSourceRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.navigation.NavBackStackEntry

/** Shared-transition scope around the NavHost; null outside navigation and previews. */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The current NavHost entry scope used by shared transitions. */
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Unique key for the tile and destination artwork pair. */
data class SharedArtworkKey(val id: String)

/** Shared direction for both ends of the morph, set once per navigation change. */
private val isMorphClosing = mutableStateOf(false)
private val libraryMorph = mutableStateOf(false)
fun isLibraryArtworkMorph(): Boolean = libraryMorph.value

/** See [isMorphClosing]. */
fun markMorphDirection(isPop: Boolean) {
    isMorphClosing.value = isPop
}

/** Whether the current navigation is popping, used to avoid freezing predictive back. */
fun lastNavWasPop(): Boolean = isMorphClosing.value

/** Only the tapped tile claims the shared key; duplicate tiles may show the same artwork. */
class NavigationArtworkState {
    val sources = ArtworkSourceRegistry<Image>()
    val librarySourceOwners = mutableSetOf<Any>()
    private val libraryOrigins = mutableMapOf<String, Boolean>()
    private var pendingReturnId: String? = null
    var selected by mutableStateOf<ArtworkSourceRegistry.Source<Image>?>(null)
        private set

    fun select(id: String?, returning: Boolean): String? {
        val selectedId = if (returning) selected?.id ?: id else id
        if (selectedId.isNullOrBlank()) {
            if (!returning) pendingReturnId = null
            return null
        }
        pendingReturnId = selectedId.takeIf { returning }
        selected = sources.select(selectedId, returning)
        if (!returning) libraryOrigins[selectedId] = selected?.owner in librarySourceOwners
        libraryMorph.value = libraryOrigins[selectedId] == true
        return selected?.url
    }

    fun registerSource(owner: Any, id: String, url: String?) {
        sources.register(owner, id, url)
        if (pendingReturnId == id && selected?.owner != owner && !sources.isRegistered(selected?.owner)) {
            selected = sources.select(id, returning = true, preferredOwner = owner)
            if (selected?.owner == owner) {
                pendingReturnId = null
                libraryOrigins[id] = owner in librarySourceOwners
                libraryMorph.value = libraryOrigins[id] == true
            }
        }
    }
}

val LocalNavigationArtwork = compositionLocalOf<NavigationArtworkState?> { null }
val LocalArtworkSourceOwner = compositionLocalOf<Any?> { null }

@Composable
fun rememberArtworkSourceOwner(): Any = LocalArtworkSourceOwner.current
    ?: rememberSaveable { java.util.UUID.randomUUID().toString() }

@Composable
fun Modifier.artworkSourceTouch(owner: Any): Modifier {
    val state = LocalNavigationArtwork.current ?: return this
    return pointerInput(state, owner) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.sources.touch(owner)
        }
    }
}

@Composable
fun sharedArtworkImageListener(owner: Any, url: String?): (AsyncImagePainter.State.Success) -> Unit {
    val state = LocalNavigationArtwork.current
    return remember(state, owner, url) {
        { success -> state?.sources?.updateImage(owner, url, success.result.image) }
    }
}

@Composable
fun selectedArtworkImage(): Image? {
    val id = LocalMorphArtworkId.current ?: return null
    return LocalNavigationArtwork.current?.selected?.takeIf { it.id == id }?.image
}

@Composable
fun SharedArtworkSeedImage(modifier: Modifier, contentScale: ContentScale): Boolean {
    val image = selectedArtworkImage() ?: return false
    val bitmap = remember(image) { image.toBitmap().asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = contentScale)
    return true
}

@Composable
fun artworkTransitionRunning(): Boolean {
    val navTransition = LocalNavAnimatedVisibilityScope.current?.transition
    return navTransition?.let { it.currentState != it.targetState } == true ||
        LocalSharedTransitionScope.current?.isTransitionActive == true
}

@Composable
fun ArtworkSourceHost(modifier: Modifier, fromLibrary: Boolean = false, content: @Composable (Modifier) -> Unit) {
    val owner = rememberSaveable { java.util.UUID.randomUUID().toString() }
    val state = LocalNavigationArtwork.current
    DisposableEffect(state, owner, fromLibrary) {
        if (fromLibrary) state?.librarySourceOwners?.add(owner)
        onDispose {
            state?.sources?.unregister(owner)
            state?.librarySourceOwners?.remove(owner)
        }
    }
    CompositionLocalProvider(LocalArtworkSourceOwner provides owner) {
        content(modifier.artworkSourceTouch(owner))
    }
}

@Composable
fun selectedArtworkTitle(): String? {
    val id = LocalMorphArtworkId.current ?: return null
    return LocalNavigationArtwork.current?.selected?.takeIf { it.id == id }?.title
}

@Composable
fun Modifier.sharedTitleSource(title: String): Modifier {
    val owner = LocalArtworkSourceOwner.current ?: return this
    val state = LocalNavigationArtwork.current ?: return this
    state.sources.setTitle(owner, title)
    val source = state.selected?.takeIf { it.owner == owner } ?: return this
    return sharedTitleElement(source.id)
}

@Composable
fun Modifier.sharedTitleTarget(): Modifier {
    val id = LocalMorphArtworkId.current ?: return this
    if (!hasMorphSource(id)) return this
    return sharedTitleElement(id)
}

@Composable
private fun Modifier.sharedTitleElement(id: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current ?: return this
    val animatedScope = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(sharedScope) {
        this@sharedTitleElement.sharedBounds(
            sharedContentState = rememberSharedContentState("title:$id"),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> Motion.artwork() },
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 2f,
        )
    }
}

/** Artwork route arguments are shared by the navigation listener and destination. */
fun NavBackStackEntry.morphArtworkId(): String? = arguments.morphArtworkId()
fun NavBackStackEntry.morphArtworkThumb(): String? = arguments.morphArtworkThumb()

/** Looks up artwork arguments from the Bundle passed to navigation listeners. */
fun Bundle?.morphArtworkThumb(): String? =
    this?.getString("thumb")
        ?: this?.getString("thumbnail")

fun Bundle?.morphArtworkId(): String? =
    this?.getString("morphId")
        ?: this?.getString("albumId")
        ?: this?.getString("artistId")
        ?: this?.getString("playlistId")
        ?: this?.getString("browseId")
        ?: this?.getString("playlist")
        ?: this?.getString("top")
        ?: this?.getString("path")

/** True for destinations that use the shared artwork morph. */
fun isMorphRoute(route: String?): Boolean =
    route != null && MorphRoutePrefixes.any(route::startsWith)

/** Adds optional title and artwork so the destination can render its hero immediately. */
fun onlinePlaylistRoute(id: String, title: String?, thumbnail: String?): String = buildString {
    append("online_playlist/").append(id)
    if (!title.isNullOrEmpty() || !thumbnail.isNullOrEmpty()) {
        append("?title=").append(Uri.encode(title ?: ""))
        append("&thumbnail=").append(Uri.encode(thumbnail ?: ""))
    }
}

fun youtubeBrowseRoute(browseId: String, params: String?): String = buildString {
    append("youtube_browse/").append(Uri.encode(browseId))
    params?.let { append("?params=").append(Uri.encode(it)) }
}

private val MorphRoutePrefixes =
    listOf(
        "album/",
        "artist/",
        "online_playlist/",
        "local_playlist/",
        "auto_playlist/",
        "cache_playlist/",
        "top_playlist/",
        "local_folder/",
        "youtube_browse/",
        "browse/",
    )

/** Selects the tapped tile as the morph source; null IDs preserve it for reverse navigation. */
fun markSharedArtworkSource(state: NavigationArtworkState, id: String?, returning: Boolean): String? =
    state.select(id, returning)

/** Returns the route thumbnail or the live tile thumbnail for [id]. */
@Composable
fun pendingSharedArtworkThumbFor(id: String?): String? {
    if (id.isNullOrEmpty()) return null
    return LocalNavigationArtwork.current?.sources?.source(id)?.url
}

/** Builds an album route with an optional thumbnail for immediate hero rendering. */
fun albumRoute(id: String, thumbnail: String?, sharedArtworkId: String? = null): String = buildString {
    append("album/").append(id)
    val query = mutableListOf<String>()
    thumbnail?.takeIf(String::isNotEmpty)?.let { query += "thumb=${Uri.encode(it)}" }
    sharedArtworkId?.takeIf(String::isNotEmpty)?.let { query += "morphId=${Uri.encode(it)}" }
    if (query.isNotEmpty()) {
        append('?').append(query.joinToString("&"))
    }
}

/** Builds an artist route with an optional thumbnail. */
fun artistRoute(id: String, thumbnail: String?): String = buildString {
    append("artist/").append(id)
    if (!thumbnail.isNullOrEmpty()) {
        append("?thumb=").append(Uri.encode(thumbnail))
    }
}

/** Builds a local playlist route with an optional thumbnail. */
fun localPlaylistRoute(id: String, thumbnail: String?): String = buildString {
    append("local_playlist/").append(id)
    if (!thumbnail.isNullOrEmpty()) {
        append("?thumb=").append(Uri.encode(thumbnail))
    }
}

/** True only when a visible tile registered this id as a shared-artwork source. */
@Composable
fun hasMorphSource(id: String): Boolean = LocalNavigationArtwork.current?.selected?.id == id

/** Records a tile and joins the morph only when that tile was tapped. */
@Composable
fun Modifier.sharedArtworkSource(id: String?, thumbnailUrl: String? = null, owner: Any = rememberArtworkSourceOwner()): Modifier {
    if (id.isNullOrEmpty()) return this
    val state = LocalNavigationArtwork.current ?: return this

    DisposableEffect(state, owner, id) { onDispose { state.sources.unregister(owner) } }
    val track = remember(state, owner, id, thumbnailUrl) {
        Modifier.onPlaced { state.registerSource(owner, id, thumbnailUrl) }
    }

    return if (state.selected?.owner != owner || state.selected?.id != id) {
        then(track)
    } else {
        then(track).sharedArtworkElement(id)
    }
}

/** Marks the destination hero as the other end of the morph. Place before size, clip, and shadow. */
@Composable
fun Modifier.sharedArtworkTarget(): Modifier {
    val id = LocalMorphArtworkId.current ?: return this
    if (!hasMorphSource(id)) return this
    return sharedArtworkElement(id)
}

/** Artwork id provided to a morph destination; null outside a morph route. */
val LocalMorphArtworkId = compositionLocalOf<String?> { null }

/** Adds a shared element when both transition scopes are available. */
@Composable
private fun Modifier.sharedArtworkElement(id: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animatedScope = LocalNavAnimatedVisibilityScope.current
    if (sharedScope == null || animatedScope == null) return this
    return with(sharedScope) {
        this@sharedArtworkElement.sharedElement(
            sharedContentState = rememberSharedContentState(SharedArtworkKey(id)),
            animatedVisibilityScope = animatedScope,
            boundsTransform = { _, _ -> Motion.sharedArtwork() },
            // Draw above both pages so their clips cannot cut off the artwork.
            renderInOverlayDuringTransition = true,
            zIndexInOverlay = 1f,
        )
    }
}
