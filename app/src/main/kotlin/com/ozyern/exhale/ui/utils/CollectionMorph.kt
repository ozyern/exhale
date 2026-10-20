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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
    /** Out of the tile: the iOS card curve, front half quick, a long soft landing. */
    const val EnterMillis = 340
    /** Back into it: shorter, the same curve. */
    const val ExitMillis = 260
    val Easing = CubicBezierEasing(0.3f, 0.5f, 0.4f, 1f)

    // A press only counts for the navigation it causes.
    private const val PressWindowMillis = 900L

    private var pending: Rect? = null
    private var pendingAt = 0L
    private val origins = LinkedHashMap<String, Rect>()
    private var shrinking by mutableStateOf<String?>(null)

    internal fun press(bounds: Rect) {
        pending = bounds
        pendingAt = SystemClock.uptimeMillis()
    }

    /** The tile the page [entryId] opens out of, claiming a press made just now; null for none. */
    fun claim(entryId: String): Rect? {
        origins[entryId]?.let { return it }
        val tile = pending ?: return null
        if (SystemClock.uptimeMillis() - pendingAt > PressWindowMillis) return null
        pending = null
        origins[entryId] = tile
        while (origins.size > 32) origins.remove(origins.keys.first())
        return tile
    }

    fun originFor(entryId: String): Rect? = origins[entryId]

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
}

/** Marks a tile as something a page can grow out of. */
@Composable
fun Modifier.collectionMorphSource(): Modifier {
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    return this
        .onPlaced { coordinates[0] = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                coordinates[0]?.takeIf { it.isAttached }?.let { CollectionMorph.press(it.boundsInWindow()) }
            }
        }
}

private val TileCornerRadius = 10.dp

/**
 * Frames an artwork page for the morph. With no tile to come out of it is just [content].
 *
 * The page is drawn whole, scaled uniformly by width so the tile's artwork becomes its hero, and
 * clipped to a rounded card whose height opens with it. It fades in over the first few frames
 * (the tile is under it, so it reads as the tile lifting) and melts back into the tile at the end
 * of the way home.
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
    val progress by transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(CollectionMorph.EnterMillis, easing = CollectionMorph.Easing)
            } else {
                tween(CollectionMorph.ExitMillis, easing = CollectionMorph.Easing)
            }
        },
        label = "collectionMorph",
    ) { state ->
        when (state) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible -> 1f
            // Covered by a page pushed over it, it stays where it is; only going back shrinks it.
            EnterExitState.PostExit -> if (CollectionMorph.isShrinking(entry.id)) 0f else 1f
        }
    }
    val pageOrigin = remember { floatArrayOf(0f, 0f) }
    val ground = MaterialTheme.colorScheme.background
    val radiusPx = remember { floatArrayOf(0f) }
    val card = remember {
        object : Shape {
            var height = 0f
            var radius = 0f
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
                Outline.Rounded(RoundRect(0f, 0f, size.width, height.coerceIn(0f, size.height), CornerRadius(radius)))
        }
    }
    Box(
        Modifier
            .onPlaced {
                val at = it.positionInWindow()
                pageOrigin[0] = at.x
                pageOrigin[1] = at.y
            }
            .graphicsLayer {
                val p = progress
                if (p >= 1f || size.width <= 0f) {
                    clip = false
                    return@graphicsLayer
                }
                radiusPx[0] = TileCornerRadius.toPx()
                val tile = origin.translate(-pageOrigin[0], -pageOrigin[1])
                val s = lerp(tile.width / size.width, 1f, p).coerceAtLeast(0.01f)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = s
                scaleY = s
                translationX = lerp(tile.left, 0f, p)
                translationY = lerp(tile.top, 0f, p)
                card.height = lerp(tile.height, size.height, p) / s
                card.radius = lerp(radiusPx[0], 0f, p) / s
                shape = card
                clip = true
                alpha = (p / 0.18f).coerceIn(0f, 1f)
            }
            .drawBehind {
                // A ground for the card while it moves, for pages that are see-through at rest;
                // it thins away over the landing so nothing pops when the morph lets go.
                val p = progress
                if (p < 1f) {
                    drawRect(ground.copy(alpha = ground.alpha * (1f - ((p - 0.75f) / 0.25f).coerceIn(0f, 1f))))
                }
            },
    ) {
        content()
    }
}
