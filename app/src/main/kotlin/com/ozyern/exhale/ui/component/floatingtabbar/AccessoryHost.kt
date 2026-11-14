/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component.floatingtabbar

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * Where the accessory's slot sits in each bar shape.
 *
 * Each rect is the slot's SETTLED (lookahead) bounds, with y measured from the bar's bottom
 * edge (so <= 0): the bar is bottom-aligned while its height animates, and its bottom is the
 * one edge that holds still through a transition.
 */
@Stable
internal class AccessorySlots(fallbackExpandedHeightPx: Int) {
    private val rects = mutableStateMapOf<FloatingTabBarVisual, Rect>()
    private val lookaheadSizes = HashMap<FloatingTabBarVisual, IntSize>()

    /** Lookahead (settled) height of the bar, written by the host each lookahead pass. */
    internal var barLookaheadHeightPx = 0

    /** The accessory's own height in an expanded slot; the expanded slots reserve exactly this. */
    var expandedHeightPx by mutableIntStateOf(fallbackExpandedHeightPx)

    fun rect(visual: FloatingTabBarVisual): Rect? = rects[visual]

    internal fun recordLookaheadSize(visual: FloatingTabBarVisual, size: IntSize) {
        lookaheadSizes[visual] = size
    }

    internal fun report(visual: FloatingTabBarVisual, left: Float, topFromRoot: Float) {
        val size = lookaheadSizes[visual] ?: return
        val top = topFromRoot - barLookaheadHeightPx
        val rect = Rect(left, top, left + size.width, top + size.height)
        if (rects[visual] != rect) rects[visual] = rect
    }
}

/**
 * The accessory's content width range while it rides the host (see noOvershootContent): the
 * content follows the surface's animating width, clamped to [minWidth]..[maxWidth] -- the
 * inline and expanded slot widths -- so it reflows smoothly through a switch but never rides
 * the surface's springy overshoot past either end.
 */
internal class AccessoryContentSpec {
    var minWidth: Int = 0
    var maxWidth: Int = 0
    var reportsNaturalHeight: Boolean = false
    var onNaturalHeight: (Int) -> Unit = {}
}

internal val LocalAccessoryContentSpec = staticCompositionLocalOf<AccessoryContentSpec?> { null }

/**
 * An empty slot in one of the bar shapes. Draws nothing; reports its settled bounds so the
 * host knows where to fly the accessory. Only the slot of the shape the bar is heading TO
 * reports -- the outgoing one, and every in-between frame of the container, would drag the
 * target around mid-flight.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun SharedTransitionScope.accessorySlot(
    slots: AccessorySlots,
    visual: FloatingTabBarVisual,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier = Modifier.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val lookingAhead = isLookingAhead
    if (lookingAhead) slots.recordLookaheadSize(visual, IntSize(placeable.width, placeable.height))
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
        val coordinates = coordinates
        if (!lookingAhead && coordinates != null &&
            animatedVisibilityScope.transition.targetState == EnterExitState.Visible
        ) {
            val position = lookaheadScopeCoordinates.localLookaheadPositionOf(coordinates)
            slots.report(visual, position.x, position.y)
        }
    }
}

/** Composes the accessory once and moves it between slots without rebuilding its content each frame. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun SharedTransitionScope.AccessoryHost(
    accessory: @Composable (Modifier, Boolean) -> Unit,
    slots: AccessorySlots,
    targetVisual: () -> FloatingTabBarVisual,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    modifier: Modifier = Modifier,
) {
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    val contentSpec = remember(slots) {
        AccessoryContentSpec().apply {
            onNaturalHeight = { h -> if (h > 0 && slots.expandedHeightPx != h) slots.expandedHeightPx = h }
        }
    }

    LaunchedEffect(slots) {
        var shownVisual: FloatingTabBarVisual? = null
        snapshotFlow { targetVisual().let { it to slots.rect(it) } }
            .collectLatest { (visual, target) ->
                if (target == null) return@collectLatest
                val switched = visual != shownVisual
                shownVisual = visual
                when {
                    !placed -> {
                        rect.snapTo(target)
                        placed = true
                    }
                    // A new shape, or the target nudged mid-flight (e.g. the expanded slot's
                    // height settling): animate from the current rect AND velocity.
                    switched || rect.isRunning -> rect.animateTo(
                        target,
                        if (visual == FloatingTabBarVisual.INLINE) BarMotion.CollapseAccessory else BarMotion.ExpandAccessory,
                    )
                    // At rest (rotation, insets): follow without animating.
                    else -> rect.snapTo(target)
                }
            }
    }

    val isInline = targetVisual() == FloatingTabBarVisual.INLINE
    Layout(
        content = {
            CompositionLocalProvider(LocalAccessoryContentSpec provides contentSpec) {
                accessory(
                    Modifier
                        .pillShadow(
                            shapes.accessoryShape,
                            if (isInline) elevations.inlineElevation else elevations.expandedElevation,
                        )
                        // No .clip: the G2 capsule is a path, and a path clip is a CPU mask
                        // uploaded every frame the glass re-renders. The fill and the glass
                        // (masked by the shared pool) follow the outline on their own.
                        .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape),
                    isInline,
                )
            }
        },
        modifier = modifier.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2f),
    ) { measurables, constraints ->
        if (isLookingAhead) slots.barLookaheadHeightPx = constraints.maxHeight
        val visual = targetVisual()
        val target = slots.rect(visual)
        val inlineWidth = slots.rect(FloatingTabBarVisual.INLINE)?.width?.roundToInt()
        val otherWidth = slots.rect(if (visual == FloatingTabBarVisual.INLINE) FloatingTabBarVisual.EXPANDED else visual)
            ?.width?.roundToInt()
        val targetWidth = target?.width?.roundToInt() ?: 0
        // Both ends known: clamp to the span between them. Otherwise: the target only.
        contentSpec.minWidth = minOf(inlineWidth ?: targetWidth, otherWidth ?: targetWidth, targetWidth)
        contentSpec.maxWidth = maxOf(inlineWidth ?: targetWidth, otherWidth ?: targetWidth, targetWidth)
        contentSpec.reportsNaturalHeight = visual != FloatingTabBarVisual.INLINE
        val current = if (isLookingAhead || !placed) target else rect.value
        val child = measurables.firstOrNull()
        if (current == null || child == null) {
            return@Layout layout(constraints.maxWidth, constraints.maxHeight) {}
        }
        val size = Size(current.width.coerceAtLeast(0f), current.height.coerceAtLeast(0f))
        val placeable = child.measure(Constraints.fixed(size.width.roundToInt(), size.height.roundToInt()))
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Absolute x (lookahead positions already account for layout direction), y back
            // from the bottom edge to this frame's top-left.
            placeable.place(current.left.roundToInt(), (constraints.maxHeight + current.top).roundToInt())
        }
    }
}
