/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The card-out-of-its-tile motion and its curves follow Convx 2.0.4
 * (github.com/cosmictaserdev-creator/Convx, GPL-3.0): 340ms out, 260ms back, on one easing.
 */

package com.ozyern.exhale.ui.utils

import android.os.SystemClock
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavBackStackEntry

/**
 * A release, playlist or artist page grows out of the tile that was tapped for it, as a rounded
 * card, and shrinks back into that tile when you go back.
 *
 * The tile reports where it is the moment a finger lands on it ([collectionMorphSource]); the
 * NavHost claims that rectangle for the page it opens ([claim]) and the page frames itself with
 * it ([CollectionMorphFrame]). A page opened any other way — a link, a list row, a deep link —
 * finds nothing to claim and keeps the ordinary artwork push.
 */
object CollectionMorph {
    /**
     * Out of the tile: the iOS card curve, front half quick, a long soft landing. A little longer
     * than the hero-only flight's 340: the whole page travels now, and the eye has more to follow.
     */
    const val EnterMillis = 420
    /** Back into it: shorter, the same curve. */
    const val ExitMillis = 320
    val Easing = CubicBezierEasing(0.3f, 0.5f, 0.4f, 1f)

    // A press only counts for the navigation it causes.
    private const val PressWindowMillis = 900L

    private var pending: Rect? = null
    private var pendingSeed: CollectionSeed? = null
    private var pendingAt = 0L
    private val origins = LinkedHashMap<String, Rect>()
    private val seeds = HashMap<String, CollectionSeed>()
    private var shrinking by mutableStateOf<String?>(null)

    internal fun press(bounds: Rect, seed: CollectionSeed?) {
        pending = bounds
        pendingSeed = seed
        pendingAt = SystemClock.uptimeMillis()
    }

    /** The tile the page [entryId] opens out of, claiming a press made just now; null for none. */
    fun claim(entryId: String): Rect? {
        origins[entryId]?.let { return it }
        val tile = pending ?: return null
        if (SystemClock.uptimeMillis() - pendingAt > PressWindowMillis) return null
        pending = null
        origins[entryId] = tile
        pendingSeed?.let { seeds[entryId] = it }
        pendingSeed = null
        while (origins.size > 32) {
            val oldest = origins.keys.first()
            origins.remove(oldest)
            seeds.remove(oldest)
        }
        return tile
    }

    fun originFor(entryId: String): Rect? = origins[entryId]

    /** What the tile [entryId] opened out of showed: enough for the page to draw its hero at once. */
    fun seedFor(entryId: String): CollectionSeed? = seeds[entryId]

    /** The destinations framed by [CollectionMorphFrame], by their route pattern. */
    fun isMorphPage(route: String?): Boolean = route in MorphRoutes

    private val MorphRoutes = setOf(
        "album/{albumId}",
        "artist/{artistId}",
        "online_playlist/{playlistId}",
        "local_playlist/{playlistId}",
        "auto_playlist/{playlist}",
        "youtube_browse/{browseId}?params={params}",
    )

    /** The page [entryId] is being popped: true when it has a tile to shrink back into. */
    fun shrink(entryId: String): Boolean {
        if (origins[entryId] == null) return false
        shrinking = entryId
        return true
    }

    internal fun isShrinking(entryId: String) = shrinking == entryId

    // Pages whose flight out of the tile has finished, so coming back to one does not replay it.
    private val landed = HashSet<String>()

    internal fun hasLanded(entryId: String) = entryId in landed

    internal fun markLanded(entryId: String) {
        landed += entryId
        if (landed.size > 64) landed.retainAll(origins.keys)
    }
}

/**
 * What a tile shows of the page it opens: its title, its artwork and its credit. The page draws
 * its hero from this while its own data loads, so the card grows out of the tile already looking
 * like the page instead of an empty box that fills in a skeleton (Convx seeds its heroes the same
 * way, from the route).
 */
data class CollectionSeed(val title: String, val thumbnail: String?, val subtitle: String? = null)

/** The seed of the page being composed, when it was opened from a tile. */
val LocalCollectionSeed = compositionLocalOf<CollectionSeed?> { null }

/** Marks a tile as something a page can grow out of. */
@Composable
fun Modifier.collectionMorphSource(seed: CollectionSeed? = null): Modifier {
    val currentSeed = androidx.compose.runtime.rememberUpdatedState(seed)
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onPlaced { coordinates[0] = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                coordinates[0]?.takeIf { it.isAttached }?.let { CollectionMorph.press(it.boundsInWindow(), currentSeed.value) }
            }
        }
}

// The shelf cards' corner, so the flight starts on the tile's own outline.
private val TileCornerRadius = 18.dp

/** How much of the morph the card takes to appear over the tile, and to go on the way back. */
private const val PageFadeFraction = 0.12f

/**
 * The flight a page's hero takes out of its tile: the tile's window rectangle, and how far along
 * the morph is (0 in the tile, 1 at rest in the page). Read in the draw phase only.
 */
class CollectionMorphFlight(val origin: Rect, val progress: () -> Float)

/** The flight of the page being composed, when it was opened from a tile. */
val LocalCollectionMorphFlight = compositionLocalOf<CollectionMorphFlight?> { null }

/**
 * Marks a page's hero artwork as the thing that flies out of the tile, as Convx's does: it starts
 * on the tile's exact pixels as a rounded square, and grows into its place in the page while the
 * rest of the page appears around it. Outside a morph it does nothing.
 */
@Composable
fun Modifier.collectionMorphHero(): Modifier {
    val flight = LocalCollectionMorphFlight.current ?: return this
    val at = remember { floatArrayOf(0f, 0f) }
    val card = remember { MorphCardShape() }
    return this
        .onPlaced {
            val position = it.positionInWindow()
            at[0] = position.x
            at[1] = position.y
        }
        .graphicsLayer {
            val p = flight.progress()
            if (p >= 1f || size.width <= 0f) {
                clip = false
                return@graphicsLayer
            }
            val tile = flight.origin
            // Uniform, by width: the tile's square becomes the hero's width and the height opens
            // with it, so the picture is never stretched on the way.
            val s = lerp(tile.width / size.width, 1f, p).coerceAtLeast(0.01f)
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = s
            scaleY = s
            translationX = lerp(tile.left - at[0], 0f, p)
            translationY = lerp(tile.top - at[1], 0f, p)
            card.height = lerp(tile.height, size.height, p) / s
            card.radius = lerp(TileCornerRadius.toPx(), 0f, p) / s
            shape = card
            clip = true
        }
}

/** A rounded rectangle whose height and radius the morph sets each frame, so no shape is allocated per frame. */
private class MorphCardShape : Shape {
    var height = 0f
    var radius = 0f
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(0f, 0f, size.width, height.coerceIn(0f, size.height), CornerRadius(radius)))
}

/**
 * Frames an artwork page for the morph. With no tile to come out of it is just [content].
 *
 * Convx's motion: the page itself does not travel. It appears in place over the first fifth of the
 * morph (the tile is under it, so nothing blinks), and its hero — marked [collectionMorphHero] —
 * flies out of the tile into the header. On the way back the hero flies home and the page goes in
 * the last fifth. A page without a marked hero simply fades.
 */
@Composable
fun AnimatedContentScope.CollectionMorphFrame(
    entry: NavBackStackEntry,
    content: @Composable () -> Unit,
) {
    val origin = remember(entry.id) { CollectionMorph.originFor(entry.id) }
    if (origin == null) {
        content()
        return
    }
    val seed = remember(entry.id) { CollectionMorph.seedFor(entry.id) }

    // Out of the tile: on a clock of its own that starts once the page has been drawn. Building a
    // page can hold the main thread for a few hundred milliseconds, and the navigation transition's
    // clock runs through that — so the flight was most of the way home on the first frame anyone
    // saw. A page that has already landed (come back to from deeper in) starts at rest.
    val enter = remember(entry.id) {
        Animatable(if (CollectionMorph.hasLanded(entry.id)) 1f else 0f)
    }
    LaunchedEffect(entry.id) {
        if (enter.value < 1f) {
            withFrameNanos {}
            withFrameNanos {}
            enter.animateTo(1f, tween(CollectionMorph.EnterMillis, easing = CollectionMorph.Easing))
        }
        CollectionMorph.markLanded(entry.id)
    }
    // Back into it: on the transition's clock, which is what keeps the page composed until it has.
    val back by transition.animateFloat(
        transitionSpec = { tween(CollectionMorph.ExitMillis, easing = CollectionMorph.Easing) },
        label = "collectionMorphBack",
    ) { state ->
        // Covered by a page pushed over it, it stays where it is; only going back shrinks it.
        if (state == EnterExitState.PostExit && CollectionMorph.isShrinking(entry.id)) 0f else 1f
    }
    val progress: () -> Float = { minOf(enter.value, back) }

    // The whole page is the card, as iOS's zoom transition has it: the page starts shrunk onto the
    // tile, a rounded card showing its own top (the cover, where the tile's picture was), and grows
    // to fill the screen with everything in it travelling together. Only the hero used to fly, and
    // the title, buttons and list popped in around it already in place, which read as cheap; and a
    // page with no marked hero (an artist's) only faded in, which read as no animation at all.
    val ground = MaterialTheme.colorScheme.background
    // An artist's tile is a disc, so the card starts as one; everything else is a rounded square.
    val fromDisc = entry.destination.route?.startsWith("artist/") == true
    val at = remember { floatArrayOf(0f, 0f) }
    val card = remember { MorphCardShape() }
    Box(
        Modifier
            .onPlaced {
                val position = it.positionInWindow()
                at[0] = position.x
                at[1] = position.y
            }
            .graphicsLayer {
                val p = progress()
                alpha = (p / PageFadeFraction).coerceIn(0f, 1f)
                if (p >= 1f || size.width <= 0f) {
                    clip = false
                    return@graphicsLayer
                }
                // Uniform, by width, so nothing on the page is ever stretched on the way.
                val s = lerp(origin.width / size.width, 1f, p).coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                translationX = lerp(origin.left - at[0], 0f, p)
                translationY = lerp(origin.top - at[1], 0f, p)
                // The card's height opens from the tile's to the whole page's: at the start only the
                // top of the page, the size of the tile, shows.
                card.height = lerp(origin.height, size.height * s, p) / s
                val startRadius = if (fromDisc) origin.minDimension / 2f else TileCornerRadius.toPx()
                card.radius = lerp(startRadius, ScreenCornerRadius.toPx(), p) / s
                shape = card
                clip = true
            }
            .drawBehind {
                // An opaque ground inside the card, for pages that are see-through at rest, so it
                // reads as a card over the page beneath; it thins away over the landing.
                val p = progress()
                if (p < 1f) {
                    drawRect(ground.copy(alpha = ground.alpha * (1f - ((p - 0.8f) / 0.2f).coerceIn(0f, 1f))))
                }
            },
    ) {
        // No separate hero flight: the hero travels with the page now.
        CompositionLocalProvider(LocalCollectionSeed provides seed) { content() }
    }
}

/** The card's corners as it reaches the screen: about a phone display's own, so it lands flush. */
private val ScreenCornerRadius = 28.dp
