/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player.customize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ozyern.exhale.constants.DiyLayoutKey
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws the DIY stickers over whatever is beneath them.
 *
 * The layer is purely decorative: it never takes a touch. Stickers sit between the artwork and
 * the transport controls, so a sticker can cover album art but can never swallow a tap meant for
 * the play button — which is why the whole thing is drawn with no pointer input at all rather
 * than with per-sticker hit-testing.
 *
 * @param zFilter which slice of the stack to draw. Callers render the layer twice — once behind
 *   the artwork for negative z, once in front for the rest — so ordering works without the
 *   player having to restructure itself.
 */
@Composable
fun DiyStickerLayer(
    layout: DiyLayout,
    orientation: DiyOrientation,
    modifier: Modifier = Modifier,
    zFilter: (Int) -> Boolean = { true },
    // Player sheet progress (0 closed, 1 open). When set, each sticker pops in on its own
    // staggered, overshooting beat as the player opens (and back out in reverse on close).
    entranceProgress: (() -> Float)? = null,
) {
    if (layout.isEmpty) return
    val visible = remember(layout, zFilter) {
        layout.stickers.filter { zFilter(it.z) }.sortedBy { it.z }
    }
    if (visible.isEmpty()) return
    // Landing order: a diagonal sweep from top-left to bottom-right, rank 0..1.
    val ranks = remember(visible, orientation) {
        val order = visible.sortedBy { val t = it.transformFor(orientation); t.y + EntranceSweepSlant * t.x }
        val last = (order.size - 1).coerceAtLeast(1)
        order.withIndex().associate { (i, sticker) -> sticker to i.toFloat() / last }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bounds = DiyBounds(maxWidth, maxHeight)
        visible.forEach { sticker ->
            val rank = ranks[sticker] ?: 0f
            DiyStickerContent(
                sticker = sticker,
                orientation = orientation,
                bounds = bounds,
                entrance = entranceProgress?.let { progress -> { stickerEntrance(progress(), rank) } },
            )
        }
    }
}

/** How much x counts against y in the landing sweep: mostly top-to-bottom, left breaking ties. */
private const val EntranceSweepSlant = 0.35f
/** Where in the open the first / last sticker starts popping, and how long each pop runs.
 *  Windows overlap heavily: never one-at-a-time, never all as one block. */
private const val EntranceFirstStart = 0.2f
private const val EntranceStartSpread = 0.2f
private const val EntranceSpan = 0.6f
/** Lively overshoot (~25%) that still settles exactly at the end of its window. */
private val EntranceSpring = com.ozyern.exhale.ui.component.floatingtabbar.SpringEasing(zeta = 0.4f, omegaT = 9f)

/** 0..1(+overshoot) pop scale driver for the sticker at [rank] at sheet [progress]. */
private fun stickerEntrance(progress: Float, rank: Float): Float {
    val start = EntranceFirstStart + EntranceStartSpread * rank
    return ((progress - start) / EntranceSpan).coerceIn(0f, 1f)
}

/** Player-relative geometry a sticker's normalised transform resolves against. */
data class DiyBounds(val width: Dp, val height: Dp) {
    /** Scale is expressed against the shorter edge so a sticker keeps its size across rotations. */
    val referenceEdge: Dp get() = Dp(min(width.value, height.value))
}

/**
 * One sticker, positioned and transformed.
 *
 * Position and rotation live in a `graphicsLayer` so moving a sticker is a draw-time transform:
 * the bitmap is decoded once and never re-rasterised, which is what keeps a ten-sticker player
 * from costing anything per frame.
 */
@Composable
fun DiyStickerContent(
    sticker: DiySticker,
    orientation: DiyOrientation,
    bounds: DiyBounds,
    modifier: Modifier = Modifier,
    // Local 0..1 pop fraction (see stickerEntrance); null = always fully shown (editor).
    entrance: (() -> Float)? = null,
) {
    val context = LocalContext.current
    val transform = sticker.transformFor(orientation)
    val sizeDp = bounds.referenceEdge * transform.scale
    val density = LocalDensity.current

    val offsetX = with(density) { (bounds.width * transform.x - sizeDp / 2).toPx() }
    val offsetY = with(density) { (bounds.height * transform.y - sizeDp / 2).toPx() }

    val base = modifier
        .size(sizeDp)
        .graphicsLayer {
            translationX = offsetX
            translationY = offsetY
            rotationZ = transform.rotation
            val t = entrance?.invoke() ?: 1f
            // Pops from a third of its size, overshoots, settles; fades in over its first third.
            val pop = if (t >= 1f) 1f else 0.3f + 0.7f * EntranceSpring.transform(t)
            alpha = sticker.opacity * (t * 3f).coerceAtMost(1f)
            scaleX = if (sticker.flipHorizontal) -pop else pop
            scaleY = pop
        }

    when (sticker.kind) {
        DiyStickerKind.EMOJI -> Box(base) {
            Text(
                text = sticker.source,
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = with(density) { sizeDp.toPx().toSp() * 0.82f }),
                modifier = Modifier.fillMaxSize(),
            )
        }

        DiyStickerKind.IMAGE -> {
            val file = remember(sticker.source) { DiyStore.stickerFile(context, sticker) }
            if (file?.isFile != true) return
            val shape = RoundedCornerShape(percent = (sticker.cornerRadius * 100).toInt())
            AsyncImage(
                model = ImageRequest.Builder(context).data(file).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = base
                    .then(if (sticker.shadow) Modifier.shadow(8.dp, shape) else Modifier)
                    .clip(shape),
            )
        }
    }
}

/** Reads the saved layout and keeps it live as the user edits it. */
@Composable
fun rememberDiyLayout(): DiyLayout {
    val (json) = rememberPreference(DiyLayoutKey, defaultValue = "{}")
    return remember(json) { DiyLayout.fromJson(json) }
}

/** True when the player should bother rendering a sticker layer at all. */
@Composable
fun rememberHasDiyStickers(): Boolean = !rememberDiyLayout().isEmpty

/**
 * Aligns the sticker layer to the shared player frame. Player layouts give this
 * negative-z layer different parent sizes, so it uses root coordinates and offsets
 * back to the root position. Its coordinates may settle one frame after composition.
 */
@Composable
fun AbsoluteFrameDiyStickers(
    rootCoordinates: LayoutCoordinates?,
    orientation: DiyOrientation,
    layout: DiyLayout,
    zFilter: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val root = rootCoordinates ?: return
    var selfCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val density = LocalDensity.current
    val rootSizeDp = remember(root.size, density) {
        with(density) { DpSize(root.size.width.toDp(), root.size.height.toDp()) }
    }

    Box(
        modifier = modifier
            .requiredSize(rootSizeDp)
            .onGloballyPositioned { selfCoordinates = it }
            .offset {
                val self = selfCoordinates
                // Both sides can go stale mid-teardown (sheet dismissing, screen rotating) --
                // localPositionOf throws on a detached LayoutCoordinates rather than returning
                // a default, so this has to check first.
                if (self == null || !root.isAttached || !self.isAttached) return@offset IntOffset.Zero
                val delta = root.localPositionOf(self, Offset.Zero)
                IntOffset(-delta.x.roundToInt(), -delta.y.roundToInt())
            },
    ) {
        DiyDesignCanvas(orientation = orientation, modifier = Modifier.fillMaxSize()) {
            DiyStickerLayer(layout = layout, orientation = orientation, zFilter = zFilter)
        }
    }
}
