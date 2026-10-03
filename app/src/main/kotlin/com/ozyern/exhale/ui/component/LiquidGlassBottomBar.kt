/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.ozyern.exhale.ui.component

import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import coil3.compose.AsyncImage
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.FloatingToolbarHorizontalPadding
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.extensions.togglePlayPause
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import com.ozyern.exhale.ui.screens.Screens
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.ozyern.exhale.ui.component.liquid.DampedDragAnimation
import kotlin.math.sign
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

/**
 * The app's floating "liquid glass" bottom bar. Presentation-only: it reads live playback
 * state from [LocalPlayerConnection] and drives navigation via callbacks, but never touches
 * the draggable full-screen player sheet directly — tapping the mini pill just asks the host
 * to open it via [onMiniPlayerClick].
 *
 * Two scroll-driven states, morphing into each other with spring physics:
 *  - **State A** (`collapsed == false`, at top): a wide frosted pill holding all main tabs
 *    with a sliding accent indicator, plus a separate frosted Search circle to its right.
 *  - **State B** (`collapsed == true`, scrolled down): a frosted Home circle on the far left,
 *    a center frosted pill (the mini-player `[art | title | play/pause]` when a song is
 *    playing, otherwise the active-tab compact pill), and a frosted Search circle on the right.
 */
@Composable
fun LiquidGlassBottomBar(
    items: List<Screens>,
    pureBlack: Boolean,
    collapsed: Boolean,
    hasNowPlaying: Boolean,
    onMiniPlayerClick: () -> Unit,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Tabs shown in the nav pill (Search stays its own circle, never a tab).
    val tabs = remember(items) { items.filter { it.route != Screens.Search.route } }
    val searchScreen = remember(items) { items.find { it.route == Screens.Search.route } }
    // Which tab we are actually on, or null. `?: tabs.first()` here was a quiet lie: on a route
    // where no tab is selected it named Home, and the collapsed bar then drew Home's pill as
    // selected and reported a tap on it as a *re-tap* — which the host answers by scrolling the
    // page to the top rather than by navigating. A button that does nothing, on the one control
    // that is supposed to always get you out.
    val activeTab = tabs.find { isSelected(it) }
    val homeTab = tabs.find { it.route == Screens.Home.route } ?: tabs.firstOrNull()

    // Subtle premium haptic tick on every nav interaction. LocalHapticFeedback is the app-wide
    // custom provider that already respects the user's "haptic feedback" preference, so calling
    // it here is a no-op when the user has haptics off.
    val haptic = LocalHapticFeedback.current
    val onItemClickHaptic: (Screens, Boolean) -> Unit = { screen, selected ->
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onItemClick(screen, selected)
    }
    val onMiniPlayerClickHaptic: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onMiniPlayerClick()
    }

    // How big the round chrome is in each state, as one continuous value rather than two
    // composables of different sizes taking turns. Collapsed, the row belongs to the song: the
    // circles step down and their glyphs step down with them, which is what gives the pill the
    // width.
    val chromeCircleSize by animateDpAsState(
        targetValue = if (collapsed) CollapsedCircleSize else DockCircleSize,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
        label = "chromeCircleSize",
    )
    val chromeGlyphScale = chromeCircleSize / DockCircleSize

    // The home circle is not a thing that appears; it is a thing that grows.
    //
    // It used to live inside State B, which meant it came into being at the moment the morph
    // swapped states and had to cover that with a fade and a scale-up - two frames of nothing,
    // then a circle popping in, which is what made it read as coarse next to the search circle
    // beside it. Out here it exists in both states with a width of zero in the expanded one, so
    // collapsing is one continuous spring: the strip folds left, the circle opens out of the fold,
    // and the pill takes the rest. The gap follows it, so the expanded row has no hole where the
    // circle will be.
    val homeCircleSize by animateDpAsState(
        targetValue = if (collapsed) CollapsedCircleSize else 0.dp,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 420f),
        label = "homeCircleSize",
    )
    val homeGap = 10.dp * (homeCircleSize / CollapsedCircleSize).coerceIn(0f, 1f)

    // Folded with nothing playing: the dock is just the two circles, side by side in the
    // middle — no empty pill between them saying the name of the page you are already on. The
    // change between that and the full dock is a soft cross-dissolve with a little scale, not a cut.
    val idleFold = collapsed && !hasNowPlaying
    // One bar narrowing into the two circles rather than two layouts trading places: the row's
    // width springs from the full dock down to exactly two circles and their gap, the empty middle
    // closing between them as they slide together.
    val idle by animateFloatAsState(
        targetValue = if (idleFold) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 360f),
        label = "dockIdleFold",
    )
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
    val compactWidth = CollapsedCircleSize * 2 + 20.dp
    val rowWidth = androidx.compose.ui.unit.lerp(maxWidth, compactWidth.coerceAtMost(maxWidth), idle)
    run {
    Row(
        // Fixed height (the tallest child is 64dp) instead of IntrinsicSize.Min — this drops the
        // intrinsic-measurement pass the morph used to trigger on every animation frame.
        modifier = Modifier.width(rowWidth).height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The folded dock's first circle is the page you are on — the tab strip
        // folds into its own selected tab — falling back to Home off the tabs. A faint ring says
        // "you are here"; tapping it there scrolls the page to its top, which unfolds the dock.
        // Holding it fans the other tabs out above it, so you can change page without unfolding.
        val leftTab = activeTab ?: homeTab
        if (leftTab != null && homeCircleSize > 1.dp) {
            val here = isSelected(leftTab)
            var fanOpen by remember { mutableStateOf(false) }
            val accent = MaterialTheme.colorScheme.primary
            FrostedCircle(
                size = homeCircleSize,
                modifier = Modifier.drawWithContent {
                    drawContent()
                    if (here) {
                        drawCircle(
                            accent.copy(alpha = 0.55f),
                            radius = size.minDimension / 2f - 1.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                        )
                    }
                },
                onClick = { onItemClickHaptic(leftTab, here) },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    fanOpen = true
                },
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = leftTab,
                    animationSpec = tween(180),
                    label = "dockLeftTab",
                ) { tab ->
                    NavGlyph(
                        iconRes = if (isSelected(tab)) tab.iconIdActive else tab.iconIdInactive,
                        contentDescription = stringResource(tab.titleId),
                        tint = if (isSelected(tab)) accent else itemContentColor(pureBlack),
                        scale = (homeCircleSize / DockCircleSize).coerceAtMost(1f),
                    )
                }
                if (fanOpen) {
                    DockTabFan(
                        tabs = tabs.filter { it != leftTab },
                        pureBlack = pureBlack,
                        anchorHeight = homeCircleSize,
                        onPick = { tab ->
                            fanOpen = false
                            onItemClickHaptic(tab, isSelected(tab))
                        },
                        onDismiss = { fanOpen = false },
                    )
                }
            }
        }
        Spacer(Modifier.width(homeGap))

        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                // Nothing on the container. Every piece of the dock animates itself.
                //
                // The old spec faded, slid and scaled the *whole row* in both directions, which
                // costs two things. The obvious one is that it reads as a slide show: two complete
                // docks crossing over, each of them a ghost for the length of the transition. The
                // subtle one is that the search circle is at the same place and the same size in
                // both states — and cross-fading it against itself made the one element that
                // should have been nailed down flicker through half opacity every single time the
                // bar collapsed.
                //
                // With `None` here, a child that does not animate is simply opaque for the whole
                // transition, so that circle is now a shared element for free. The pieces that
                // really do change carry their own motion instead, and all of them tell the same
                // story: the strip folds along its length into the 64dp the home circle occupies,
                // the circle grows in place at that spot, and the pill slides out from behind it
                // into the room the strip gave up. Nothing arrives from off-screen, because
                // nothing was ever off-screen. See MorphFadeOut for how the glass hands over.
                //
                // `SizeTransform` still snaps — animating the container's width would remeasure
                // the frosted backdrops every frame, which is what the morph used to cost — and
                // `clip = false` keeps the springs' overshoot from being sheared off at the bounds.
                (EnterTransition.None togetherWith ExitTransition.None)
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "bottomBarState",
            modifier = Modifier.weight(1f),
        ) { isCollapsed ->
            if (!isCollapsed) {
                // ---- STATE A: wide tab pill + trailing search circle ----
                val stripInk by morphInk("stripInk")
                val stripFold by morphShape("stripFold")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiquidTabBar(
                        tabs = tabs,
                        pureBlack = pureBlack,
                        isSelected = isSelected,
                        onItemClick = onItemClickHaptic,
                        modifier = Modifier
                            // Fills the space it is given, rather than wrapping its content.
                            //
                            // With `fill = false` the strip was measured wrap-content, so its
                            // weighted tabs fell back to their intrinsic widths - one tab as wide
                            // as its label, the next as narrow as its own - while the selected
                            // capsule is drawn on an even pitch of total/tabs. The two disagreed,
                            // and the widest label ("Mood & Genres") ran straight out of the
                            // capsule and across its neighbours. Filling makes the pitch real, so
                            // every tab is the same width the capsule assumes and a long label
                            // ellipsises inside its own slot instead of escaping it.
                            .weight(1f)
                            .widthIn(max = LiquidTabBarMaxWidth)
                            // Folds along its length into the footprint the home circle is about
                            // to occupy, and unfolds back out of it.
                            //
                            // The fold is on X alone, and the target is measured rather than
                            // guessed: whatever the strip is this frame, it collapses to exactly
                            // 64dp of it. A uniform `scaleOut` squashed the height too, which
                            // turns a bar folding away into a lozenge shrinking to a point — a
                            // different object leaving rather than this one.
                            .graphicsLayer {
                                alpha = stripInk
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                val folded =
                                    (DockCircleSize.toPx() / size.width.coerceAtLeast(1f))
                                        .fastCoerceIn(0.05f, 1f)
                                scaleX = folded + (1f - folded) * stripFold
                            },
                    )
                }
            } else {
                // ---- STATE B: home circle | center pill | search circle ----
                val pillInk by morphInk("pillInk")
                val pillShape by morphShape("pillShape")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FrostedPill(
                        // Level with the circles either side: one
                        // band of three pieces of glass, not a tall pill between two buttons.
                        height = CollapsedCircleSize,
                        // The same accessory that was above the bar a moment ago, flown down into it.
                        sharedAccessoryScope = if (hasNowPlaying) this@AnimatedContent else null,
                        // Frostier than the rest of the dock when it carries the now-playing row:
                        // a page heading sliding under the song title at 13dp of blur stays
                        // legible and the two lines of type read as one collision.
                        extraTint = if (hasNowPlaying) MiniPlayerGlassExtraTint else DockGlassExtraTint,
                        blurRadius = if (hasNowPlaying) MiniPlayerGlassBlurRadius else DockGlassBlurRadius,
                        modifier = Modifier
                            .weight(1f)
                            // Out from behind the home circle, and back in behind it.
                            //
                            // It used to swell from its own middle, which is the one story that
                            // is not true in either direction: collapsing, the pill has to take
                            // the territory the strip is folding out of, and that vacancy opens
                            // left to right. Anchoring it to its left edge makes both directions
                            // the same reversible motion — the pill lives behind the circle.
                            .graphicsLayer {
                                alpha = pillInk * (1f - idle)
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                scaleX = 0.14f + 0.86f * pillShape
                                scaleY = 0.88f + 0.12f * pillShape
                            },
                    ) {
                        if (hasNowPlaying) {
                            MiniPlayerPill(pureBlack = pureBlack, onExpand = onMiniPlayerClickHaptic)
                        }
                    }

                }
            }
        }

        Spacer(Modifier.width(10.dp))

        // Outside the morph on purpose: see the note where the strip's copy used to be.
        if (searchScreen != null) {
            val searchActive = isSelected(searchScreen)
            FrostedCircle(
                size = chromeCircleSize,
                onClick = { onItemClickHaptic(searchScreen, searchActive) },
            ) {
                NavGlyph(
                    iconRes = if (searchActive) searchScreen.iconIdActive else searchScreen.iconIdInactive,
                    contentDescription = stringResource(searchScreen.titleId),
                    tint = if (searchActive) MaterialTheme.colorScheme.primary
                    else itemContentColor(pureBlack),
                    scale = chromeGlyphScale,
                )
            }
        }
    }
    }
    }
}

/* ----------------------------------------------------------------------- */
/* The A <-> B morph                                                        */
/* ----------------------------------------------------------------------- */

/**
 * The size of every round piece of dock chrome, and therefore what the tab strip folds down to.
 */
private val DockCircleSize = 64.dp

/**
 * The round chrome in State B, a step down from the dock's own 64dp.
 *
 * Collapsed, the row is mostly the song: the reference this is drawn from gives the pill the width
 * and keeps the two circles as small marks at either end. At 64dp each they took a third of the
 * row between them and the pill was the narrowest thing in a bar that exists, in that state, to
 * show what is playing.
 */
private val CollapsedCircleSize = 50.dp

/**
 * Opacity: the outgoing state leaves before the incoming one arrives.
 *
 * The two states are composed on top of each other for the length of the morph and each of them
 * is a sheet of frosted glass, so what the eye judges is how much glass is over any given pixel at
 * any given moment. Two matched linear ramps sum to exactly one at every instant — the outgoing
 * state gives up precisely what the incoming one takes — so the bar holds one plate's worth of
 * material all the way through and never flashes a shade lighter or darker.
 *
 * The two ramps used to be matched and simultaneous, on the reasoning that two halves summing to
 * one keeps the glass an even thickness. What it actually produced was both layouts legible at
 * once for a fifth of a second — tab labels showing through a mini-player pill, two pieces of
 * artwork, a home circle sitting on top of the pill — which reads as a crossfade between two
 * different bars rather than one bar changing shape.
 *
 * So the handoff is now sequential: the state that is leaving is gone in 110ms, and the one
 * arriving starts 90ms in. Nothing is ever doubled, and the shape change — carried entirely by
 * [MorphShapeSpring], which runs through both — is what the eye follows across the gap.
 */
private val MorphFadeOut: FiniteAnimationSpec<Float> =
    tween(durationMillis = 110, easing = LinearEasing)
private val MorphFadeIn: FiniteAnimationSpec<Float> =
    tween(durationMillis = 170, delayMillis = 90, easing = LinearEasing)

/**
 * Geometry.
 *
 * Underdamped, so pieces arrive with a little overshoot and settle. The bar is meant to read as a
 * blob of liquid finding a new shape, and liquid that stops dead was never moving.
 */
private val MorphShapeSpring: FiniteAnimationSpec<Float> =
    spring(dampingRatio = AquamorphicDampingRatio, stiffness = AquamorphicStiffness)

/** 1 while this state is the one on screen, 0 while it is off-stage. */
@Composable
private fun AnimatedVisibilityScope.morphProgress(
    label: String,
    enter: FiniteAnimationSpec<Float>,
    exit: FiniteAnimationSpec<Float>,
): State<Float> =
    transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = label,
    ) { if (it == EnterExitState.Visible) 1f else 0f }

@Composable
private fun AnimatedVisibilityScope.morphInk(label: String): State<Float> =
    morphProgress(label, MorphFadeIn, MorphFadeOut)

@Composable
private fun AnimatedVisibilityScope.morphShape(label: String): State<Float> =
    morphProgress(label, MorphShapeSpring, MorphShapeSpring)

/* ----------------------------------------------------------------------- */
/* Frosted glass containers (share the app-wide 56dp / transparent / 0.20 tint look) */
/* ----------------------------------------------------------------------- */

/**
 * How much opacity the dock adds over the base chrome tint, and how far it blurs.
 *
 * These are the dock's material, and they are deliberately *one* pair of numbers rather than one
 * per state. The bar's two states are supposed to be the same pane of glass caught mid-morph, so
 * the moment State A and State B disagree about their tint the morph stops being a shape change
 * and becomes a cross-fade between two different materials.
 *
 * They went up because the dock is the one surface in the app that is never over a background of
 * its own choosing: at the top of Home it sits over album art, over a grid of thumbnails, over
 * whatever the row underneath happens to be, and at the old strength a 10sp tab label over a busy
 * cover was genuinely hard to read. The extra blur is doing most of that work — it is what
 * destroys the *detail* behind the pane, and detail is what competes with small type — with the
 * tint only there to stop the result going transparent again over a light photo.
 *
 * It stays a pane and not a slab because the dock is 64dp tall. The same numbers on the 48dp
 * search row read as solid, which is why that row keeps its own, lighter pair.
 */
private const val DockGlassExtraTint = 0.07f
private val DockGlassBlurRadius = 68.dp

/**
 * The now-playing pill's glass. Text lives on it that page text must never be read through, so it
 * is frosted to the point where a heading passing under it is colour, not letters (the radius is
 * quartered by the chrome modifier, so this is ~28dp of real blur).
 */
internal const val MiniPlayerGlassExtraTint = 0.46f
internal val MiniPlayerGlassBlurRadius = 130.dp

@Composable
private fun FrostedPill(
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    sharedAccessoryScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    extraTint: Float = DockGlassExtraTint,
    blurRadius: Dp = DockGlassBlurRadius,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(height)
            .nowPlayingAccessory(sharedAccessoryScope)
            .dockGlass(shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FrostedCircle(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 64.dp,
    extraTint: Float = DockGlassExtraTint,
    blurRadius: Dp = DockGlassBlurRadius,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        // Underdamped and stiff. At 0.8/300 the circle sank under the finger and eased back like
        // a button on a lift; the capsule beside it is a droplet that overshoots and wobbles, and
        // two press physics on one bar is one too many.
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "circlePress",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .scale(pressScale)
            .dockGlass(CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressed = true
                    waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    pressed = false
                }
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun NavGlyph(
    iconRes: Int,
    contentDescription: String?,
    tint: Color,
    // Drawn smaller rather than measured smaller: the glyph follows the circle down in the collapsed
    // state, and a scale on the draw layer costs nothing on a frame where the circle is already
    // being remeasured.
    scale: Float = 1f,
) {
    // Outlined to solid is a change of state, so it is watched happening rather than swapped on a frame.
    Crossfade(targetState = iconRes, animationSpec = tween(170), label = "navGlyph") { res ->
        Icon(
            painter = painterResource(res),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

/* ----------------------------------------------------------------------- */
/* State A tab row with sliding accent indicator                            */
/* ----------------------------------------------------------------------- */

/**
 * The dock's tab strip: a permanent glass lens riding on the selected tab, dragged like a
 * physical object.
 *
 * ### Why the selected tab is not simply painted in the accent colour
 *
 * It is — on a *hidden* copy of the row. The strip is composed in four pieces:
 *
 *  1. a bare glass plate, no content on it, recorded into `glassBackdrop`;
 *  2. the visible row of tabs in the resting colour, drawn over that plate;
 *  3. an invisible twin (`alpha = 0`) of the same row in the **accent** colour with filled icons,
 *     recorded into `tabsBackdrop` — no glass of its own, just glyphs on nothing;
 *  4. the capsule, which draws `plate + twin` through a lens.
 *
 * So the blue icon and label of the selected tab are not a tint applied to a widget: they are the
 * hidden layer *seen through the glass*. Everything the lens does to the pixels underneath — the
 * magnification, the edge refraction, the chromatic fringe as it accelerates — happens to the
 * icon and the label too, because as far as the shader is concerned they are just more backdrop.
 * A capsule drawn over an already-blue tab can only ever look like a sticker on top of it; this
 * looks like the tab is *inside* the glass, which is the entire effect being copied.
 *
 * ### Why it is split that way, and not the obvious way
 *
 * The obvious build is two full copies of the dock — glass and all — with the capsule sampling
 * the app content plus the accent copy. That is what this was, and it cost three backdrop passes
 * per frame: the visible dock blurring and refracting the whole NavHost recording, the twin doing
 * the identical work again a pixel underneath, and the capsule compositing the app layer a third
 * time. At 120Hz over a 64dp strip that is what the dragging felt like.
 *
 * Splitting the plate away from the content means the expensive pass — vibrancy, a 13dp blur and
 * a lens over live app pixels — happens exactly **once**, and the other two layers are recordings
 * of already-drawn content, which cost a `RenderNode` draw each. The plate has to be contentless
 * for this to work: if the recording contained the resting icons, the capsule would show them
 * *and* the accent ones stacked as a double image.
 *
 * ### How it moves
 *
 * Press anywhere on the bar and the capsule swells (78dp of travel space for a 56dp pill) and
 * glides to your finger on a stiff, critically damped spring, then rides it. Its stretch comes from
 * its real speed — longer and flatter while it travels, back to round the moment it stops — so it
 * never rings. The whole bar is tugged a few dp in the direction of the drag and springs back on
 * release. Letting go lands it on the nearest tab without overshoot, and only then does the route
 * change, so a drag that changes its mind costs nothing. A route change from anywhere else gives
 * the same press, slide and settle, so a tap reads like a short drag.
 *
 * Falls back to a flat accent wash on devices with no `RuntimeShader`, where there is no lens to
 * see the hidden layer through and the selected tab therefore has to colour itself.
 */
@Composable
private fun LiquidTabBar(
    tabs: List<Screens>,
    pureBlack: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    if (tabs.isEmpty()) return

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = remember { RoundedCornerShape(percent = 50) }
    val accent = MaterialTheme.colorScheme.primary
    val restColor = itemContentColor(pureBlack)
    val glassy = remember { isRuntimeShaderSupported() }
    val isDark = isSystemInDarkTheme()

    val lastIndex = (tabs.size - 1).coerceAtLeast(0)

    // Two different questions, and folding them into one number was a bug.
    //
    // `selectedIndex` is -1 when no tab is selected, which is the truth and is what decides
    // whether a tap is a navigation or a re-tap. `capsuleIndex` is where the glass capsule has to
    // rest, and it has to be a real index because the capsule is always somewhere.
    //
    // Clamping the first into the second meant "nothing is selected" read as "Home is selected",
    // so a tap on Home reported itself as a re-tap and the host answered it by scrolling to the
    // top instead of navigating.
    val selectedIndex = tabs.indexOfFirst { isSelected(it) }
    val capsuleIndex = selectedIndex.coerceIn(0, lastIndex)

    // Labels go when there is no longer room to read them.
    //
    // The dock is as wide as the screen, but how many dp that is depends on the interface scale
    // (Settings -> Appearance -> Display): at 130% a 411dp phone reports 316dp, which leaves the
    // tab pill about 200dp -- 50dp a tab, an icon and roughly six characters. "Mood & Genres"
    // ellipsised to "Mood &..." is worse than no label at all, because a truncated word is
    // something the eye tries to read twice. Below the threshold the row is icons, which is a
    // complete design rather than a broken one.
    //
    // Derived from the configuration rather than from measurement so it is right on the first
    // frame; a row that renders labels and then drops them is the flicker this is avoiding.
    val configuration = LocalConfiguration.current
    val showLabels = remember(configuration.screenWidthDp, tabs.size) {
        val pillWidth = minOf(
            configuration.screenWidthDp.dp - DockSideChrome,
            LiquidTabBarMaxWidth,
        )
        (pillWidth - 8.dp) / tabs.size.coerceAtLeast(1) >= LabelledTabMinWidth
    }

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    // How big the labels can be before they touch the ends of the capsule.
    //
    // "Mood & Genres" at 11sp is 90dp wide, and on a 411dp phone a tab slot is 93dp - so the label
    // fitted, and sat hard against the curved ends of the glass with a dp to spare on each side.
    // That is the overlap: not text running past its slot, but text with no room inside it.
    //
    // The slot cannot grow - four even tabs across the width of the phone is what there is - so
    // the type gives way instead, by exactly as much as it has to and no more. Measured against
    // the longest label at the heaviest weight it is ever drawn in, and one size is used for all
    // four, because tabs set at different sizes look like a mistake rather than a fit. Apple sets
    // a tab bar label at 10pt, so this lands where iOS already is.
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = tabs.map { stringResource(it.dockTitleId) }
    val labelFontSize = remember(labels, tabWidthPx, labelStyle, density) {
        val room = tabWidthPx - with(density) { DockLabelSideRoom.toPx() * 2f }
        if (room <= 0f) {
            DockLabelMaxSize
        } else {
            val widest = labels.maxOfOrNull { label ->
                textMeasurer.measure(
                    text = label,
                    style = labelStyle.copy(
                        fontSize = DockLabelMaxSize,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            } ?: 0f
            if (widest <= room || widest == 0f) {
                DockLabelMaxSize
            } else {
                (DockLabelMaxSize.value * (room / widest))
                    .coerceAtLeast(DockLabelMinSize.value)
                    .sp
            }
        }
    }

    // Inset of the capsule inside the pill, and therefore what the tab pitch is measured from.
    // 64dp of bar minus 4dp top and bottom is the capsule's 56dp.
    val inset = 4.dp
    val insetPx = with(density) { inset.toPx() }
    val capsuleHeight = height - inset * 2

    // The bar is tugged by the drag: the raw travel, eased and capped at a few dp.
    val tug = remember(tabs.size) { Animatable(0f) }
    val tugPx = with(density) { 4.dp.toPx() }
    val panelOffset: () -> Float = {
        val raw = tug.value
        if (totalWidthPx == 0f || raw == 0f) {
            0f
        } else {
            val fraction = (raw / totalWidthPx).fastCoerceIn(-1f, 1f)
            tugPx * fraction.sign * EaseOut.transform(abs(fraction))
        }
    }

    // The drag callbacks outlive the composition that built them, so everything they need from
    // the current frame is read through a State rather than captured by value.
    val onItemClickState by rememberUpdatedState(onItemClick)
    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val tabsState by rememberUpdatedState(tabs)

    // `moved` separates "the user dragged the capsule somewhere" from "the user tapped the
    // capsule", which are the same gesture as far as the drag inspector is concerned but mean
    // different things to the host.
    val moved = remember { mutableFloatStateOf(0f) }
    val tapSlopPx = with(density) { 12.dp.toPx() }

    val dampedDragAnimation = remember(scope, tabs.size, isLtr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = capsuleIndex.toFloat(),
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // 78dp of swell on a 56dp capsule: enough that the pill bulges past the dock's edges
            // while held, which is what sells it as a drop of liquid on the bar.
            pressedScale = 78f / 56f,
            onDragStarted = { position ->
                moved.floatValue = 0f
                // Pressed on the capsule: it follows from where it is. Pressed anywhere else: it
                // flows across to the finger's tab.
                val fromStart = if (isLtr) position.x else totalWidthPx - position.x
                val under = if (tabWidthPx > 0f) (fromStart - insetPx) / tabWidthPx - 0.5f else value
                if (abs(under - value) >= 0.5f) updateValue(under.fastCoerceIn(0f, lastIndex.toFloat()))
            },
            onDragStopped = {
                val landed = targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                updateValue(landed.toFloat())
                scope.launch { tug.animateTo(0f, spring(1f, 300f, 0.5f)) }

                val screen = tabsState.getOrNull(landed)
                if (screen != null) {
                    if (landed != selectedIndexState) {
                        onItemClickState(screen, false)
                    } else if (moved.floatValue < tapSlopPx) {
                        // Never travelled: a tap on the capsule, which sits on the active tab. Sent
                        // as a re-tap so the host can scroll the page back to the top.
                        onItemClickState(screen, true)
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    moved.floatValue += abs(dragAmount.x)
                    val before = targetValue
                    val after = (before + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, lastIndex.toFloat())
                    if (after.fastRoundToInt() != before.fastRoundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    updateValue(after)
                    scope.launch { tug.snapTo(tug.value + dragAmount.x) }
                }
            },
            velocityDampingRatio = 1f,
        )
    }

    // Route changes that did not come from a drag on this bar (a deep link, the back stack, the
    // search circle) move the capsule with the same press, slide and settle a tap gets. On first
    // composition it is already in place, so there is nothing to animate.
    LaunchedEffect(dampedDragAnimation, capsuleIndex) {
        val target = capsuleIndex.toFloat()
        if (abs(dampedDragAnimation.targetValue - target) > 0.001f) {
            dampedDragAnimation.animateToValue(target)
        }
    }

    val glassBackdrop = rememberLayerBackdrop()
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(glassBackdrop, tabsBackdrop)
    val containerGlass = Modifier.dockGlass(shape)

    Box(
        // The gesture belongs to the whole bar, as on iOS: press any tab and the glass comes to
        // your finger and rides it. Taps are resolved in `onDragStopped` too, so the tabs carry
        // semantics for accessibility but no click handlers of their own to race it.
        modifier = modifier.height(height).then(dampedDragAnimation.modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ---- 1 + 2. the plate, and the row you can see on it ------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val width = coords.size.width.toFloat()
                    if (totalWidthPx != width) {
                        totalWidthPx = width
                        tabWidthPx = ((width - insetPx * 2f) / tabs.size).coerceAtLeast(0f)
                    }
                }
                .graphicsLayer {
                    translationX = panelOffset()
                },
        ) {
            // Contentless on purpose — see the KDoc. `layerBackdrop` records everything drawn
            // *after* it in the chain, so it has to sit before the glass modifier to capture the
            // pane at all.
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (glassy) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                    .then(containerGlass)
            )

            Row(
                modifier = Modifier.fillMaxSize().padding(inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, screen ->
                    LiquidTabItem(
                        screen = screen,
                        // Without a lens there is no hidden layer to reveal, so the selection has
                        // to be painted here instead.
                        tint = if (!glassy && index == selectedIndex) accent else restColor,
                        filled = !glassy && index == selectedIndex,
                        showLabel = showLabels,
                        labelFontSize = labelFontSize,
                        scaleProvider = { 1f },
                        onClick = { onItemClick(screen, index == selectedIndex) },
                        gestureOwnedByBar = true,
                    )
                }
            }
        }

        // ---- 3. the twin the lens reads --------------------------------------------
        if (glassy) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .fillMaxWidth()
                    .height(capsuleHeight)
                    .graphicsLayer { translationX = panelOffset() }
                    .padding(horizontal = inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { screen ->
                    LiquidTabItem(
                        screen = screen,
                        tint = accent,
                        filled = true,
                        // Must match the visible row exactly: this is the layer the lens reads, so
                        // a twin carrying labels the row has dropped would magnify text that is
                        // not there.
                        showLabel = showLabels,
                        labelFontSize = labelFontSize,
                        // Magnified with the press, so squeezing the capsule appears to draw the
                        // icon towards the surface of the glass.
                        scaleProvider = { lerp(1f, 1.12f, dampedDragAnimation.pressProgress) },
                        onClick = null,
                    )
                }
            }
        }

        // ---- 4. the capsule ---------------------------------------------------------
        if (tabWidthPx > 0f) {
            val tabWidth = with(density) { tabWidthPx.toDp() }
            val capsuleModifier = Modifier
                .padding(horizontal = inset)
                .graphicsLayer {
                    val travel = dampedDragAnimation.value * tabWidthPx
                    translationX = (if (isLtr) travel else -travel) + panelOffset()
                }

            if (glassy) {
                Box(
                    capsuleModifier
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { shape },
                            effects = {
                                // Never fully off. A capsule with no refraction at rest is a
                                // coloured rectangle, and the resting state is the one the user
                                // spends all their time looking at — in the reference the pill is
                                // visibly bending the label underneath it before anyone touches
                                // it. The press deepens the bend rather than switching it on.
                                val progress = dampedDragAnimation.pressProgress
                                // At rest it should already look like a lens sitting on the bar,
                                // as the reference's does, so the resting bend is half the pressed one.
                                val depth = 0.55f + 0.45f * progress
                                lens(
                                    10f.dp.toPx() * depth,
                                    18f.dp.toPx() * depth,
                                    true,
                                    // The extra sample cost of the colour fringe only buys
                                    // anything while the thing is moving.
                                    progress > 0.02f,
                                )
                            },
                            highlight = {
                                // A rim you can see at rest: it is what makes the pill read as a
                                // raised piece of glass rather than a darker patch of the bar.
                                Highlight.Ambient.copy(
                                    alpha = 0.62f + 0.38f * dampedDragAnimation.pressProgress,
                                )
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = 3f.dp + 5f.dp * progress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = 0.35f + 0.65f * progress,
                                )
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                // Conservation of volume, roughly: the faster it travels the
                                // longer and flatter it gets, and it recovers as it slows.
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                // A wash at rest so the capsule is still legible as a selected
                                // slot on a busy backdrop, fading out as the lens takes over.
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    color = if (isDark) Color.White.copy(alpha = 0.16f)
                                    else Color.Black.copy(alpha = 0.07f),
                                    alpha = 1f - progress * 0.7f,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .height(capsuleHeight)
                        .width(tabWidth),
                )
            } else {
                Box(
                    capsuleModifier
                        .graphicsLayer {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                        }
                        .clip(shape)
                        .background(selectedItemContainerColor(pureBlack), shape)
                        .height(capsuleHeight)
                        .width(tabWidth),
                )
            }
        }
    }
}

/**
 * How wide the tab pill is allowed to get. On a tablet the dock would otherwise stretch to the
 * full width of the screen and put an inch of glass between two tabs.
 */
// Wide enough for the longest label the app has.
//
// At 420dp three tabs get 140dp each minus their own 8dp padding, and "Mood & Genres" at 11sp is
// right on that line - it fit only because the pitch is now even, with nothing to spare. The extra
// 40dp is the margin that keeps it honest on a narrower phone or a larger display scale.
private val LiquidTabBarMaxWidth = 460.dp

/** Clear space at each end of a tab label, inside the capsule. */
private val DockLabelSideRoom = 7.dp

/** What a dock label is set at when it fits, and the floor it is never set below. */
private val DockLabelMaxSize = 11.sp
private val DockLabelMinSize = 9.sp

/**
 * Everything in the dock row that is not the tab pill: the row's padding on both sides, the gap,
 * and the search circle. Subtracted from the screen to work out what the pill actually gets.
 */
private val DockSideChrome = FloatingToolbarHorizontalPadding * 2 + 10.dp + 64.dp

/**
 * The narrowest a tab can be and still carry a readable word under its icon. Below this the row
 * goes icon-only -- see the note in LiquidTabBar.
 */
private val LabelledTabMinWidth = 68.dp

/**
 * One tab. Equal width by construction — [RowScope.weight] rather than each tab measuring to its
 * own label — because the capsule is a single fixed-pitch slot sliding across the row, and a row
 * whose slots are "Home"-wide and "Mood & Genres"-wide cannot be indexed by multiplication.
 *
 * [onClick] is null for the hidden twin: it is a rendering of the row, not a copy of its
 * behaviour, and a second set of invisible click targets stacked over the real ones would
 * swallow every tap.
 */
@Composable
private fun RowScope.LiquidTabItem(
    screen: Screens,
    tint: Color,
    filled: Boolean,
    showLabel: Boolean,
    labelFontSize: TextUnit,
    scaleProvider: () -> Float,
    onClick: (() -> Unit)?,
    gestureOwnedByBar: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .then(
                if (onClick != null && gestureOwnedByBar) {
                    // Touch is the bar's; this is only what TalkBack and switch access see.
                    Modifier.semantics {
                        role = Role.Tab
                        onClick { onClick(); true }
                    }
                } else if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scaleProvider()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(if (filled) screen.iconIdActive else screen.iconIdInactive),
            contentDescription = stringResource(screen.titleId),
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        if (showLabel) {
            Text(
                text = stringResource(screen.dockTitleId),
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = labelFontSize,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                // Kept clear of the capsule's curved ends, which is the room the size above was
                // solved for.
                modifier = Modifier.padding(horizontal = DockLabelSideRoom),
            )
        }
    }
}

/* ----------------------------------------------------------------------- */
/* State B center mini-player pill                                          */
/* ----------------------------------------------------------------------- */

@Composable
private fun MiniPlayerPill(
    pureBlack: Boolean,
    onExpand: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Where the song is, for the ring round play/pause: read from the player a few times a second
    // while it plays, still while it doesn't.
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying, mediaMetadata?.id) {
        while (true) {
            val p = playerConnection.player
            val d = p.duration
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            if (!isPlaying) break
            kotlinx.coroutines.delay(250)
        }
    }
    val ringProgress by animateFloatAsState(progress, tween(260, easing = LinearEasing), label = "pillRing")

    // Swipes on the pill: sideways to skip, upwards to open the player. Which one is decided by the
    // axis the finger mostly travelled, so a slightly diagonal flick still does what it looked like.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val skipDistance = with(density) { 56.dp.toPx() }
    val openDistance = with(density) { 36.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val nudge by animateFloatAsState(
        targetValue = (dragX / skipDistance).coerceIn(-1f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "pillNudge",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        val horizontal = kotlin.math.abs(dragX) > kotlin.math.abs(dragY)
                        when {
                            horizontal && dragX <= -skipDistance -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.player.seekToNext()
                            }
                            horizontal && dragX >= skipDistance -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.player.seekToPrevious()
                            }
                            !horizontal && dragY <= -openDistance -> onExpand()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = { dragX = 0f; dragY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                    },
                )
            }
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The song, as one thing that changes as one thing.
        //
        // Art, title and artist used to swap their contents in place: the cover blinked to the new
        // URL the instant it arrived, the two lines of text re-laid out underneath it on whatever
        // frame their own state landed on, and a track change came out as three small unrelated
        // glitches. Now the whole block travels — the outgoing song lifts out through the top of
        // the pill as the incoming one rises into it — which also makes it the one piece of motion
        // in the dock that means *something changed by itself* rather than *you touched something*.
        //
        // Keyed on the metadata rather than on the id, so a re-emission of the same song (a like,
        // a download finishing) compares equal and does not re-run the transition.
        AnimatedContent(
            targetState = mediaMetadata,
            transitionSpec = {
                (fadeIn(tween(190)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(130)) + slideOutVertically { -it / 2 }) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            label = "miniPillTrack",
            modifier = Modifier
                .weight(1f)
                // Follows the finger a little, so a skip swipe is visibly the song being pushed.
                .graphicsLayer {
                    translationX = nudge * 18.dp.toPx()
                    alpha = 1f - kotlin.math.abs(nudge) * 0.35f
                },
        ) { metadata ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Album art (clean circle — no wavy/floral shapes).
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = metadata?.thumbnailUrl
                    if (thumb != null) {
                        AsyncImage(
                            // Same pinned request the standalone mini-player pill uses, so the two
                            // share one memory-cache entry and the art survives the A/B morph
                            // without a reload.
                            model = rememberPinnedArtworkRequest(thumb),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.exhale),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    // The title alone, at reading size. Two stacked lines at label size turned the
                    // collapsed pill into a miniature of the full mini-player; the bar has room for
                    // one thing and the song's name is the thing.
                    Text(
                        text = metadata?.title.orEmpty(),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = clearGlassContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    // Who it is by, quieter, under the name: the pill stops at the title.
                    val artists = metadata?.artists?.joinToString(", ") { it.name }.orEmpty()
                    if (artists.isNotBlank()) {
                        Text(
                            text = artists,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            color = clearGlassContentColor().copy(alpha = 0.62f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Play / pause, as glass: the same lens the dock is made of, holding the accent inside it
        // rather than sitting on it as a coloured chip. Shape carries the state — a rounded square
        // while playing, a circle when paused — so the pill reads at a glance without a label.
        val ink = clearGlassContentColor()
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    playerConnection.player.togglePlayPause()
                }
                // How far through the song: a thin ring that fills clockwise from the top.
                .drawBehind {
                    val stroke = 2.2.dp.toPx()
                    val inset = stroke / 2 + 2.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(ink.copy(alpha = 0.16f), 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        ink.copy(alpha = 0.9f), -90f, 360f * ringProgress, false, topLeft, arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.55f, stiffness = 700f), initialScale = 0.6f) + fadeIn(tween(120))) togetherWith
                        (scaleOut(tween(120), targetScale = 0.6f) + fadeOut(tween(100)))
                },
                label = "pillPlayPause",
            ) { playing ->
                Icon(
                    painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The other tabs, fanned up out of the folded dock's first circle on a long press: each a small
 * glass capsule with its glyph and name, rising one after another from the circle. Tap one to go
 * there; tap anywhere else to put them away.
 */
@Composable
private fun DockTabFan(
    tabs: List<Screens>,
    pureBlack: Boolean,
    anchorHeight: Dp,
    onPick: (Screens) -> Unit,
    onDismiss: () -> Unit,
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val lift = with(density) { (anchorHeight + 12.dp).roundToPx() }
    androidx.compose.ui.window.Popup(
        alignment = Alignment.BottomStart,
        offset = androidx.compose.ui.unit.IntOffset(0, -lift),
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Nearest the circle first, so the list reads upward from the finger.
            tabs.reversed().forEachIndexed { index, tab ->
                val order = tabs.size - 1 - index
                val appear = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(order * 35L)
                    appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 520f))
                }
                val shape = RoundedCornerShape(percent = 50)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = appear.value.coerceIn(0f, 1f)
                            val s = 0.6f + 0.4f * appear.value
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(0f, 1f)
                            translationY = (1f - appear.value) * 18.dp.toPx() * (order + 1)
                        }
                        .height(46.dp)
                        .clip(shape)
                        .background(
                            if (pureBlack) Color(0xF2111111)
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.12f), shape)
                        .clickable { onPick(tab) }
                        .padding(start = 14.dp, end = 18.dp),
                ) {
                    Icon(
                        painter = painterResource(tab.iconIdInactive),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(tab.titleId),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Search screen unified bottom bar                                         */
/* ----------------------------------------------------------------------- */

/**
 * Slim on purpose, and not the dock's height.
 *
 * It was briefly 56dp, on the theory that a row which is *nearly* the dock reads as a mistake.
 * It does not — it reads as a search field that has been inflated. The field is one line of text
 * and a glyph; at 56dp the type sits in the middle of a lot of nothing and the capsule looks
 * padded rather than considered. The gap that change was really aimed at was never the row's
 * height anyway: it was the row hanging at the bottom of a taller reservation, which is fixed at
 * the call site by filling that band and centring in it.
 */
private val SearchRowHeight = 58.dp

/**
 * See [frostedGlassModifier]'s `extraTint` for why the search row is not dock-strength glass.
 *
 * Both numbers were higher — 0.14 and 72dp — and the result was a capsule that read as *thicker*
 * even though its height had not moved. A milky, heavily blurred pane at 48dp has no interior to
 * speak of, so the eye stops seeing a thin sheet of glass with content behind it and starts seeing
 * a solid slab, and a solid slab at that size looks bloated. Legibility over a busy result grid
 * was the goal and it costs far less than that: a few points of tint over the base chrome, and a
 * blur a little under the dock's own — which the row can afford to sit below precisely because it
 * is short enough that its interior would otherwise disappear.
 */
// The search row is thinner than the dock, not made of something else.
//
// At 0.05 extra tint and a 60dp blur the row was effectively clear: category artwork and its
// titles read straight through the capsule, which is the one thing a frosted pane must never let
// happen to the text sitting on it. These are the dock's numbers pulled back a little for the
// lower height - a difference of degree, which is what the helper's doc always claimed this was.
private const val SearchGlassExtraTint = 0.26f
private val SearchGlassBlurRadius = 110.dp

/**
 * The bottom chrome for **both** search surfaces — the Search tab and the results page for a
 * committed query. It replaces the morphing A/B nav bar entirely on those routes (the host
 * disables the scroll-collapse logic there).
 *
 * The search field never leaves the bottom of the screen. The results page used to put the field
 * back in the Scaffold's `topBar`, so committing a query threw the thing you were typing into to
 * the opposite end of the display and left your thumb pointing at nothing. Now only the leading
 * button and the text change: a Home circle and the placeholder while browsing, a back circle and
 * the live query once results are on screen.
 *
 * Layout: TWO separate frosted pieces side by side — a standalone circular button on the left in
 * its own round frosted pill, and the search input field in its own capsule beside it. The
 * mini-player floats directly ABOVE this row (handled by the host's sheet stack) and never merges
 * into it.
 */
@Composable
fun SearchBottomBar(
    pureBlack: Boolean,
    placeholder: String,
    onHomeClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Non-null once a query has been committed: the pill shows it in the accent colour
    // instead of the grey placeholder, so the bar doubles as the results page's title.
    committedQuery: String? = null,
    // Swaps the leading circle from "go home" to "go back", which is what the button
    // actually does once you are a level deep in results.
    leadingIsBack: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Standalone circular leading pill — its OWN frosted glass surface. Sized to match the
        // slim search capsule beside it, NOT the fat 64dp nav circles.
        FrostedCircle(
            onClick = onHomeClick,
            size = SearchRowHeight,
            extraTint = SearchGlassExtraTint,
            blurRadius = SearchGlassBlurRadius,
        ) {
            Icon(
                painter = painterResource(
                    if (leadingIsBack) R.drawable.chevron_back else R.drawable.home_outlined,
                ),
                contentDescription = stringResource(
                    if (leadingIsBack) R.string.back else R.string.home,
                ),
                tint = itemContentColor(pureBlack),
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        // Search input capsule — a SEPARATE frosted pill. Tapping anywhere on it expands
        // the real type-in field (host-owned overlay). Apple Music's field is a slim
        // ~48dp capsule, noticeably thinner than the 64dp nav-bar pills.
        FrostedPill(
            modifier = Modifier.weight(1f),
            height = SearchRowHeight,
            extraTint = SearchGlassExtraTint,
            blurRadius = SearchGlassBlurRadius,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(onClick = onSearchClick),
            ) {
                Spacer(Modifier.width(20.dp))
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = if (committedQuery != null) MaterialTheme.colorScheme.primary
                    else itemContentColor(pureBlack),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = committedQuery ?: placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (committedQuery != null) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (committedQuery != null) MaterialTheme.colorScheme.primary
                    else itemContentColor(pureBlack),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(
                        if (committedQuery != null) R.drawable.close else R.drawable.mic,
                    ),
                    contentDescription = null,
                    tint = itemContentColor(pureBlack),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(20.dp))
            }
        }
    }
}

/* ----------------------------------------------------------------------- */
/* Colors (mirror FloatingNavigationToolbar's palette)                      */
/* ----------------------------------------------------------------------- */

@Composable
private fun itemContentColor(pureBlack: Boolean): Color =
    if (pureBlack) Color.White.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant

// A low-alpha wash of the PRIMARY brand colour, matching the floating toolbar — see the tint note
// there. A solid `secondaryContainer` chip both drifted off-accent under dynamic colour and read as
// opaque against the frosted bar it sits on.
@Composable
private fun selectedItemContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) {
        Color.White.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }
