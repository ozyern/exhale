@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * FloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent is center-aligned so the collapse
 *   animation morphs around the center instead of the start edge.
 * - ExpandedTabs grew a draggable selection puck ported from Kyant0's
 *   AndroidLiquidGlass catalog (LiquidBottomTabs/LiquidBottomTab): equal-width
 *   tabs, a spring-damped indicator you can press-drag between tabs (release
 *   snaps to and navigates the nearest tab), a finger-tracking glow, and —
 *   when an optional [Backdrop] is supplied — chromatic lens refraction and
 *   an accent-tinted glass sample of the selected tab's icon showing through
 *   the puck. See [DampedDragAnimation] and [InteractiveHighlight].
 */

package com.ozyern.exhale.ui.component.floatingtabbar

import com.ozyern.exhale.ui.component.shapes.shadowShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.theme.LocalAccentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import com.ozyern.exhale.LocalAmbientBackgroundColor
import com.ozyern.exhale.ui.component.asPuckTint
import com.ozyern.exhale.constants.NavBarMeltTransitionKey
import com.ozyern.exhale.ui.component.LocalGlassEffectConfig
import com.ozyern.exhale.ui.component.elasticStretchLayerBlock
import com.ozyern.exhale.ui.component.glassResolutionScale
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.ui.component.backdrop.Backdrop
import com.ozyern.exhale.ui.component.backdrop.catalog.utils.DampedDragAnimation
import com.ozyern.exhale.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.ozyern.exhale.ui.component.backdrop.backdrops.layerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.backdrop.drawBackdrop
import com.ozyern.exhale.ui.component.backdrop.highlight.Highlight
import com.ozyern.exhale.ui.component.backdrop.shadow.InnerShadow
import com.ozyern.exhale.ui.component.backdrop.effects.blur
import com.ozyern.exhale.ui.component.backdrop.effects.lens
import com.ozyern.exhale.ui.component.backdrop.effects.vibrancy
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.player.PlayerMorph
import com.ozyern.exhale.ui.theme.isHueless
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign



// Increase temporarily to inspect bar transitions in slow motion.
internal const val DIAG_SLOWDOWN = 1

// Peak blur used while the bar shapes merge or split.
internal val GooeyPeakBlur = 12.dp

// Fixed-duration easing keeps the size, fade, and shared-bound animations aligned.
internal fun barEasing(collapsing: Boolean) =
    if (collapsing) BarMotion.BarSurfaceCollapseSpring else BarMotion.BarSurfaceExpandSpring
internal const val CollapseMs = 317 * DIAG_SLOWDOWN
internal const val ExpandMs = 251 * DIAG_SLOWDOWN
internal fun barMs(collapsing: Boolean) = if (collapsing) CollapseMs else ExpandMs
// The container morph outlasts its fades so the overshoot settles visibly.
internal const val CollapseMorphMs = 560 * DIAG_SLOWDOWN
internal const val ExpandMorphMs = 470 * DIAG_SLOWDOWN
internal fun barMorphMs(collapsing: Boolean) = if (collapsing) CollapseMorphMs else ExpandMorphMs

// Keep the container resizing until its accessory finishes moving.
internal fun barTotalMs(collapsing: Boolean) =
    (if (collapsing) BarMotion.CollapseAccessory else BarMotion.ExpandAccessory).durationMs

// Rest-state puck shadow: with zero press it would be invisible against a dark bar.
internal const val PuckRestShadowAlpha = 0.35f

/** Keeps the backdrop frozen briefly after scrolling stops. */
internal const val ScrollFreezeTailNs = 120_000_000L

// EMA weight for the scroll-velocity estimate. Plain value, no recomposition.
internal const val MomentumVelocityEmaAlpha = 0.35f

internal enum class FloatingTabBarVisual { INLINE, EXPANDED, SEARCH_EXPANDED }

// Used until the expanded accessory reports its measured height.
internal val ExpandedAccessoryFallbackHeight = 54.dp

internal val SearchBarRowHeight = 48.dp

/**
 * Floating tab bar that switches between inline and expanded layouts while scrolling.
 *
 * [tabBarContentModifier] is applied after surface styling and before content padding.
 * [accessory] is composed once and moved between inline and expanded slots; see [AccessoryHost].
 */
@Composable
fun FloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    tabBarContentModifier: Modifier = Modifier,
    accessory: (@Composable (modifier: Modifier, isInline: Boolean) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    // Optional backdrop used for puck refraction.
    backdrop: Backdrop? = null,
    accentColor: Color? = null,
    // Current puck position when selection is driven by an external pager.
    tabPosition: (() -> Float?)? = null,
    // Draw-phase snap animation value; read without recomposition.
    snapPulse: () -> Float = { 0f },
    // Show the search field in place of the regular tab group.
    searchMode: Boolean = false,
    searchBarContent: (@Composable (Modifier) -> Unit)? = null,
    // Keep search width stable after its first expanded-width measurement.
    expandedContentWidthPx: Int? = null,
    onExpandedWidthChanged: ((Int) -> Unit)? = null,
    onMorphActiveChanged: ((Boolean) -> Unit)? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    // Rebuild tab lambdas so they always capture the current selection and colors.
    val scope = FloatingTabBarScopeImpl().apply { content() }

    // Where the accessory's slot sits in each bar shape; the host flies the one accessory
    // between them. Null = no accessory, the bars reserve no slot.
    val accessoryFallbackHeightPx = with(LocalDensity.current) { ExpandedAccessoryFallbackHeight.roundToPx() }
    val accessorySlots = if (accessory != null) remember { AccessorySlots(accessoryFallbackHeightPx) } else null

    val visual = when {
        scrollConnection.isInline -> FloatingTabBarVisual.INLINE
        searchMode && searchBarContent != null -> FloatingTabBarVisual.SEARCH_EXPANDED
        else -> FloatingTabBarVisual.EXPANDED
    }

    // Keep the transition object here to drive the gooey blur pulse.
    val transition = updateTransition(targetState = visual, label = "floatingTabBarVisual")
    SideEffect {
        onMorphActiveChanged?.invoke(transition.currentState != transition.targetState)
    }
    // Gooey transitions use an offscreen blur; the preference allows a cheaper crossfade.
    val meltTransitionEnabled by rememberPreference(NavBarMeltTransitionKey, defaultValue = true)
    val gooeyBlurPx = with(LocalDensity.current) { GooeyPeakBlur.toPx() }
    // Pulse the blur during the transition, then return it to zero.
    val gooeyProgress by transition.animateFloat(
        transitionSpec = {
            val ms = barMs(targetState == FloatingTabBarVisual.INLINE)
            keyframes {
                durationMillis = ms
                0f at 0
                1f atFraction 0.5f using FastOutSlowInEasing
                0f at ms
            }
        },
        label = "gooeyProgress"
    ) { _ -> 0f }

    SharedTransitionLayout(modifier = modifier) {
        // Keep the gooey capture inside AnimatedContent so shared elements draw above it.
        // Glass reads this lambda during drawing, avoiding recomposition when freeze state changes.
        val frozenWhileAnimating = remember(transition, scrollConnection) {
            {
                transition.currentState != transition.targetState || scrollConnection.isScrolling() ||
                    // Resume capture near the end of the morph.
                    PlayerMorph.glassFrozen
            }
        }
        val collapsing = transition.targetState == FloatingTabBarVisual.INLINE
        val boundsTransforms = remember(collapsing) {
            if (collapsing) BarBoundsTransforms.Collapse else BarBoundsTransforms.Expand
        }
        CompositionLocalProvider(
            LocalTabBarBackdropFrozen provides frozenWhileAnimating,
            LocalBarBoundsTransforms provides boundsTransforms,
        ) {
            // Blur the bar during transitions without clipping item stretch.
            Box {
            Box(
                Modifier
                    .gooey { if (meltTransitionEnabled) gooeyBlurPx * gooeyProgress else 0f }
                    .graphicsLayer { clip = false }
            ) {
                transition.AnimatedContent(
                    transitionSpec = {
                        val collapsing = targetState == FloatingTabBarVisual.INLINE
                        val ms = barMs(collapsing)
                        val sizeMs = barTotalMs(collapsing)
                        // Overlap fades to avoid a blank midpoint.
                        (fadeIn(
                            tween(
                                durationMillis = (ms * 0.62f).toInt(),
                                delayMillis = (ms * 0.16f).toInt(),
                                easing = LinearOutSlowInEasing,
                            )
                        ) togetherWith fadeOut(
                            // Fade icons after the pill starts retracting.
                            tween(
                                durationMillis = (barMorphMs(collapsing) * 0.6f).toInt(),
                                easing = FastOutLinearInEasing,
                            )
                        ))
                            .using(
                                SizeTransform(clip = false) { _, _ ->
                                    // Delay resizing so the accessory stays aligned with the bar.
                                    val yDelay = if (collapsing) BarMotion.CollapseYDelayMs else 0
                                    tween(
                                        durationMillis = sizeMs - yDelay,
                                        delayMillis = yDelay,
                                        easing = barEasing(collapsing),
                                    )
                                }
                            )
                    },
                    contentAlignment = Alignment.BottomCenter
                ) { targetVisual ->
            when (targetVisual) {
                FloatingTabBarVisual.INLINE -> InlineBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessorySlots = accessorySlots,
                    onInlineTabClick = { scrollConnection.expand() },
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
                FloatingTabBarVisual.EXPANDED -> ExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    tabPosition = tabPosition,
                    accessorySlots = accessorySlots,
                    initialTabRowWidthPx = expandedContentWidthPx,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent,
                    backdrop = backdrop,
                    accentColor = accentColor,
                    onWidthMeasured = onExpandedWidthChanged
                )
                FloatingTabBarVisual.SEARCH_EXPANDED -> SearchExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessorySlots = accessorySlots,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent,
                    searchBarContent = searchBarContent ?: {},
                    targetWidthPx = expandedContentWidthPx
                )
            }
            }
            if (accessory != null && accessorySlots != null) {
                AccessoryHost(
                    accessory = accessory,
                    slots = accessorySlots,
                    targetVisual = { transition.targetState },
                    colors = colors,
                    shapes = shapes,
                    elevations = elevations,
                    modifier = Modifier.matchParentSize(),
                )
            }
            }
        }
        }
    }
}

/** Draw-phase flag that reuses glass captures during bar transitions, scrolling, and artwork morphs. */
internal val LocalTabBarBackdropFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }

/** Tracks nested scroll and switches the bar between inline and expanded states. */
class FloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val expandThresholdPx: Float = scrollThresholdPx,
    private val inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    private var accumulatedScroll = 0f

    // Plain arrays are read during drawing; snapshot state would invalidate every frame.
    private val scrollClockNs = longArrayOf(0L)

    // Keep scroll velocity outside snapshot state; only graphics and transition code reads it.
    private val smoothedVelocityPx = floatArrayOf(0f)
    private val lastDeltaClockNs = longArrayOf(0L)

    // Scroll velocity controls only the collapse/expand spring stiffness.
    fun scrollSpeed(): Float = abs(smoothedVelocityPx[0])

    /** True during scrolling and briefly afterward, so glass can reuse its last capture. */
    fun isScrolling(): Boolean {
        val last = scrollClockNs[0]
        return last != 0L && System.nanoTime() - last < ScrollFreezeTailNs
    }

    private fun markScrolled() {
        scrollClockNs[0] = System.nanoTime()
    }

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Even a fixed bar needs its backdrop frozen while content scrolls underneath.
        if (inlineBehavior == FloatingTabBarInlineBehavior.Never) {
            markScrolled()
            return Offset.Zero
        }

        markScrolled()
        // Smooth noisy per-frame deltas before using them to animate the bar.
        smoothedVelocityPx[0] = smoothedVelocityPx[0] * (1f - MomentumVelocityEmaAlpha) +
            available.y * MomentumVelocityEmaAlpha
        lastDeltaClockNs[0] = System.nanoTime()

        val scrollDelta = available.y

        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FloatingTabBarInlineBehavior.OnScrollDown -> {
                // Collapse needs more hysteresis to avoid flicker; expansion should respond sooner.
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    isInline = true
                    accumulatedScroll = 0f
                } else if (accumulatedScroll >= expandThresholdPx && isInline) {
                    isInline = false
                    accumulatedScroll = 0f
                }
            }

            FloatingTabBarInlineBehavior.OnScrollUp -> {
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    isInline = true
                    accumulatedScroll = 0f
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    isInline = false
                    accumulatedScroll = 0f
                }
            }

            FloatingTabBarInlineBehavior.ExpandedAtTopOnly -> {
                // Handle this after the content reports how much scroll it consumed.
            }

            FloatingTabBarInlineBehavior.Never -> {
            }
        }

        return Offset.Zero // Don't consume the scroll, let it pass through
    }

    /** Uses unconsumed upward scroll to detect the top of the content. */
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (inlineBehavior != FloatingTabBarInlineBehavior.ExpandedAtTopOnly) return Offset.Zero

        if (available.y > AtTopSlopPx) {
            if (isInline) {
                isInline = false
                accumulatedScroll = 0f
            }
        } else if (abs(consumed.y) > AtTopSlopPx && !isInline) {
            isInline = true
            accumulatedScroll = 0f
        }
        return Offset.Zero
    }
}

/** Below this many pixels a scroll event is rounding noise, not a move. */
internal const val AtTopSlopPx = 0.5f

/**
 * Creates and remembers a [FloatingTabBarScrollConnection] instance.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded). Default is false.
 * @param scrollThreshold The scroll distance required to collapse to inline. Default is 50.dp.
 * @param expandThreshold The scroll distance required to expand back. Default is 8.dp — small
 * enough that the bar returns immediately on scroll up, large enough that a shaky finger
 * mid-scroll cannot oscillate it.
 * @param inlineBehavior controls when the bar collapses to inline mode
 * @return A remembered [FloatingTabBarScrollConnection] instance.
 */
@Composable
fun rememberFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 50.dp,
    expandThreshold: Dp = 8.dp,
    inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown
): FloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    val expandThresholdPx = expandThreshold.toPx()
    remember(scrollThresholdPx, expandThresholdPx, inlineBehavior, initialIsInline) {
        FloatingTabBarScrollConnection(
            initialIsInline,
            scrollThresholdPx,
            expandThresholdPx,
            inlineBehavior,
        )
    }
}

/**
 * Defines when the floating tab bar should transition to inline state.
 */
enum class FloatingTabBarInlineBehavior {
    /** Never transition to inline - it stays in expanded state */
    Never,

    /** Transition to inline when scrolling down */
    OnScrollDown,

    /** Transition to inline when scrolling up */
    OnScrollUp,

    /**
     * Expanded only while the content is at its top; inline the moment it scrolls away,
     * in either direction, and expanded again only on arriving back at the top.
     *
     * No thresholds and no accumulated distance: "at the top" is read from the scroll the
     * content could NOT consume upward, which is the only thing a nested-scroll connection
     * actually knows about the list's position.
     */
    ExpandedAtTopOnly
}

interface FloatingTabBarScope {
    /**
     * Adds a regular tab to the floating tab bar.
     *
     * @param key Unique identifier for the tab
     * @param title Composable content for the tab title
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     * @param onLongClick Callback for a press-and-hold, handed the cell's own bounds in window
     * coordinates so the caller can anchor a context menu to the tab that was actually held.
     */
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current },
        onLongClick: ((Rect) -> Unit)? = null
    )

    /**
     * Adds a standalone tab to the floating tab bar.
     *
     * Calling this method again replaces the previous standalone tab.
     *
     * @param key Unique identifier for the standalone tab
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     * @param title Composable content for the tab title. Never actually shown —
     * the standalone tab is always an icon-only floating circle, in both inline
     * and expanded states (see InlineStandaloneTab / ExpandedStandaloneTab).
     */
    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current },
        title: @Composable () -> Unit = {},
        onLongClick: ((Rect) -> Unit)? = null
    )
}

/**
 * Plain release-triggered [clickable]. Firing on finger-DOWN (the old behaviour)
 * meant any touch that turned into a scroll or a puck drag still navigated first —
 * "opens a screen even when I didn't drop the puck". Release-fire lets Compose's
 * touch-slop arbitration cancel the tap when the gesture becomes a drag/scroll, so
 * only a real tap navigates. Keeps the custom [indication].
 */
@Composable
internal fun Modifier.tapClickable(
    indication: Indication?,
    onClick: () -> Unit,
    onLongClick: ((Rect) -> Unit)? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    if (onLongClick == null) {
        return clickable(
            onClick = onClick,
            indication = indication,
            interactionSource = interactionSource
        )
    }
    // Hold-to-open needs to tell the caller WHERE it was held, so the context menu can grow
    // out of that tab rather than out of the middle of the bar. Written from layout, read
    // from the gesture callback: a plain array, never snapshot state, so reporting bounds
    // every layout pass costs no recomposition (same technique as the scroll clocks above).
    val bounds = remember { arrayOf(Rect.Zero) }
    return onGloballyPositioned { bounds[0] = it.boundsInWindow() }
        .combinedClickable(
            onClick = onClick,
            onLongClick = { onLongClick(bounds[0]) },
            indication = indication,
            interactionSource = interactionSource
        )
}
