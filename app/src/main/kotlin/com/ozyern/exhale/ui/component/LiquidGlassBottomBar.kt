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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.ui.graphics.BlurEffect
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
import com.ozyern.exhale.ui.utils.fadingMarquee
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.unit.lerp
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
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
import com.ozyern.exhale.constants.DockStyle
import com.ozyern.exhale.constants.DockStyleKey
import com.ozyern.exhale.ui.component.jelly.JellyDock
import com.ozyern.exhale.ui.component.jelly.JellyDockItem
import com.ozyern.exhale.ui.component.jelly.jellyGlass
import com.ozyern.exhale.ui.component.jelly.jellyInk
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.abs
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
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
 *    with a sliding glass capsule, plus a separate frosted Search circle to its right.
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
    /** How much of the folded dock's song pill shows, read in the draw phase. See MainActivity. */
    songPillReveal: () -> Float = { 1f },
    /** On the search pages, the jelly dock turns into the search row itself. */
    search: DockSearch? = null,
) {
    val searchScreen = remember(items) { items.find { it.route == Screens.Search.route } }

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

    // Folded with nothing playing: the dock is just the two circles, side by side in the
    // middle — no empty pill between them saying the name of the page you are already on. The
    // change between that and the full dock is a soft cross-dissolve with a little scale, not a cut.
    // How the open dock draws its tabs (Settings -> Appearance -> Dock). Only state A changes.
    val dockStyle by rememberEnumPreference(DockStyleKey, DockStyle.CONVX)
    // Both styles are the reference's folding tab bar (see FoldingDock): the liquid style's own
    // A <-> B change crossfaded the two states through each other instead of morphing.
    JellyFoldingDock(
        items = items,
        searchScreen = searchScreen,
        collapsed = collapsed,
        hasNowPlaying = hasNowPlaying,
        pureBlack = pureBlack,
        showLabels = true,
        isSelected = isSelected,
        onItemClick = onItemClickHaptic,
        onMiniPlayerClick = onMiniPlayerClickHaptic,
        search = search,
        modifier = modifier,
        songPillReveal = songPillReveal,
        // The classic style keeps its own open bar — its capsule, its swipe, its spacing — and
        // only borrows the fold; the floating one is one bar with search inside.
        liquidBar = dockStyle == DockStyle.LIQUID,
        convx = dockStyle != DockStyle.LIQUID,
    )
}

/* ----------------------------------------------------------------------- */
/* The A <-> B morph                                                        */
/* ----------------------------------------------------------------------- */
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
 *
 * The blur is what does that, not the tint: at 0.46 extra (a 76% black film in dark mode) the pill
 * was a slab with the glass buried under it — the page's colour, the lens at the rim, none of it
 * showed. A light film over the same heavy blur keeps letters unreadable and lets it be glass.
 */
internal const val MiniPlayerGlassExtraTint = 0.12f
internal val MiniPlayerGlassBlurRadius = 130.dp

@Composable
private fun FrostedPill(
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    sharedAccessoryScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
    extraTint: Float = DockGlassExtraTint,
    blurRadius: Dp = DockGlassBlurRadius,
    // The jelly dock's smoked glass instead of the clear one, so state B matches the jelly bar.
    jelly: Boolean = false,
    // the reference dock's clearer glass, for the folded fluid dock.
    foldedDock: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(height)
            .nowPlayingAccessory(sharedAccessoryScope)
            .then(
                when {
                    jelly -> Modifier.jellyGlass(shape)
                    foldedDock -> Modifier.foldedGlass(shape)
                    else -> Modifier.clearGlass(shape)
                },
            ),
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
    // The folded dock keeps the softer clear glass; the open dock's circle matches its tab strip.
    openDockGlass: Boolean = false,
    // Jelly glass, and a jelly press: it squashes wide and short under the finger and wobbles back,
    // the way the jelly dock's pill does, instead of sinking.
    jelly: Boolean = false,
    // the reference dock's clearer glass, for the folded fluid dock.
    foldedDock: Boolean = false,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val squash by animateFloatAsState(
        targetValue = if (pressed && jelly) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "circleSquash",
    )
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
            .then(
                if (jelly) {
                    Modifier.graphicsLayer {
                        scaleX = 1f + 0.07f * squash
                        scaleY = 1f - 0.08f * squash
                    }
                } else {
                    Modifier.scale(pressScale)
                },
            )
            .then(
                when {
                    jelly -> Modifier.jellyGlass(CircleShape)
                    // One call site for both: the folded and open glass are the same material, and
                    // the search circle flips between them halfway through the fold. As two
                    // branches, the flip swapped composition groups, so the glass re-recorded its
                    // backdrop and blinked for a frame mid-motion.
                    foldedDock || openDockGlass -> Modifier.dockGlass(CircleShape)
                    else -> Modifier.clearGlass(CircleShape)
                },
            )
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

/* ----------------------------------------------------------------------- */
/* State A tab row with sliding glass capsule                               */
/* ----------------------------------------------------------------------- */

/**
 * The dock's tab strip: a glass capsule riding on the selected tab, dragged like a physical object.
 *
 * ### How it is drawn
 *
 *  1. the pane: the dock's glass, with the tabs on it — the selected one in full ink, the rest
 *     dimmed;
 *  2. an invisible twin of the row, drawn on a live sample of the page (saturated, lightly blurred,
 *     bending harder the more the capsule is pressed) and recorded into `tabsBackdrop`;
 *  3. the capsule, which reads `page + twin` through its own lens;
 *  4. the selected tab drawn once more on top of the capsule, crisp, fading out as it is pressed.
 *
 * At rest the capsule is a dark pill (pale in light theme) with the tab sharp on it. Held, its wash
 * clears and it becomes glass: what you see inside is the twin magnified and bent, so the page and
 * the tab's icon are both refracted by it rather than covered.
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
 * Falls back to a flat pill on devices with no `RuntimeShader`, where there is no lens.
 */
@Composable
private fun LiquidTabBar(
    tabs: List<Screens>,
    pureBlack: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    // Set inside the fluid dock's fold: the tabs fade in and out through a blur, and the selected
    // icon is the one that flies to and from the folded circle.
    morph: FluidMorph? = null,
) {
    if (tabs.isEmpty()) return
    val tabFade = morph?.let { Modifier.fluidTabFade(it.visibility) } ?: Modifier
    val flyingIcon = morph?.let { it.sharedIcon() } ?: Modifier

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = remember { RoundedCornerShape(percent = 50) }
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

    val showLabels = rememberShowDockLabels(tabs.size)

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    val labelFontSize = rememberDockLabelFontSize(tabs, tabWidthPx)

    // Inset of the capsule inside the pill, and therefore what the tab pitch is measured from.
    // 64dp of bar minus 4dp top and bottom is the capsule's 56dp.
    val inset = 4.dp
    val insetPx = with(density) { inset.toPx() }
    val capsuleHeight = height - inset * 2

    // The bar is tugged by the drag: the raw travel, eased and capped at a few dp. A plain float
    // written from the pointer callback, with one spring launched on release to let it go.
    val tug = remember { mutableFloatStateOf(0f) }
    val tugRelease = remember { arrayOfNulls<Job>(1) }
    val tugPx = with(density) { 4.dp.toPx() }
    val panelOffset: () -> Float = {
        val raw = tug.floatValue
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
                tugRelease[0]?.cancel()
                // Pressed on the capsule: it follows from where it is. Pressed anywhere else: it
                // flows across to the finger's tab.
                val fromStart = if (isLtr) position.x else totalWidthPx - position.x
                val under = if (tabWidthPx > 0f) (fromStart - insetPx) / tabWidthPx - 0.5f else value
                if (abs(under - value) >= 0.5f) followTo(under.fastCoerceIn(0f, lastIndex.toFloat()))
            },
            onDragStopped = {
                val landed = targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                updateValue(landed.toFloat())
                val tugFrom = tug.floatValue
                if (tugFrom != 0f) {
                    tugRelease[0] = scope.launch {
                        Animatable(tugFrom).animateTo(0f, spring(1f, 300f, 0.5f)) { tug.floatValue = value }
                    }
                }

                val screen = tabsState.getOrNull(landed)
                if (screen != null) {
                    if (landed != selectedIndexState) {
                        // Building the next page holds the main thread for a couple of hundred
                        // milliseconds, and no spring advances while it does: navigating at once
                        // froze the capsule on the old tab and then cut it to the new one. Let it
                        // travel first — capped, so a slow frame never makes the tap feel ignored.
                        scope.launch {
                            withTimeoutOrNull(TabNavigateMaxWaitMs) {
                                snapshotFlow { abs(value - landed) < TabNavigateArrival }.first { it }
                            }
                            onItemClickState(screen, false)
                        }
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
                    followTo(after)
                    tug.floatValue += dragAmount.x
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

    val appBackdrop = LocalAppBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val capsuleBackdrop = rememberCombinedBackdrop(appBackdrop, tabsBackdrop)
    // The capsule at rest is a dark (or, in light theme, pale) pill that clears to glass when held.
    val restWash = if (isDark) Color(0xFF1C1B1C) else Color(0xFFF2F2F2)
    // The page you are on takes the song's colour (the theme's primary follows the artwork), as
    // the folded dock's page glyph already did; the others stay the glass's neutral ink.
    val selectedInk = MaterialTheme.colorScheme.primary
    val restInk = clearGlassContentColor().copy(alpha = 0.6f)

    Box(
        // The gesture belongs to the whole bar, as on iOS: press any tab and the glass comes to
        // your finger and rides it. Taps are resolved in `onDragStopped` too, so the tabs carry
        // semantics for accessibility but no click handlers of their own to race it.
        modifier = modifier.height(height).then(dampedDragAnimation.modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ---- the pane, and the row you can see on it ---------------------------------
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
                .graphicsLayer { translationX = panelOffset() },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .dockGlass(shape)
                    // While held, the pane lights up a little and a soft glow sits under the
                    // capsule, following it as it is dragged.
                    .drawWithContent {
                        drawContent()
                        val press = dampedDragAnimation.pressProgress
                        if (press > 0.01f && tabWidthPx > 0f) {
                            val x = insetPx + (dampedDragAnimation.value + 0.5f) * tabWidthPx
                            val cx = if (isLtr) x else size.width - x
                            val outline = shape.createOutline(size, layoutDirection, this)
                            clipPath(androidx.compose.ui.graphics.Path().apply { addOutline(outline) }) {
                                drawRect(Color.White.copy(alpha = 0.06f * press), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                                drawRect(
                                    Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.16f * press), Color.Transparent),
                                        center = Offset(cx, size.height / 2f),
                                        radius = size.height * 1.5f,
                                    ),
                                    blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                                )
                            }
                        }
                    },
            )

            // The lit tab is the one the capsule is over, not the route: the two used to disagree
            // for the length of a navigation, with two tabs lit and the capsule on the old one.
            // Nothing selected (a page outside the tabs) still lights nothing.
            val inkIndex by remember(tabs.size) {
                derivedStateOf { dampedDragAnimation.value.fastRoundToInt().coerceIn(0, lastIndex) }
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, screen ->
                    LiquidTabItem(
                        screen = screen,
                        tint = if (selectedIndex >= 0 && index == inkIndex) selectedInk else restInk,
                        filled = index == selectedIndex,
                        showLabel = showLabels,
                        labelFontSize = labelFontSize,
                        scaleProvider = {
                            if (index == capsuleIndex) lerp(1f, 1.12f, dampedDragAnimation.pressProgress) else 1f
                        },
                        onClick = { onItemClick(screen, index == selectedIndex) },
                        gestureOwnedByBar = true,
                        itemModifier = tabFade,
                    )
                }
            }
        }

        // ---- the twin the capsule reads -------------------------------------------------
        // A real sample of the page — saturated, lightly blurred, bending more as the capsule is
        // pressed — with the tabs drawn on it. Never seen directly; held, the capsule magnifies
        // this, which is why it clears to bright glass rather than darkening.
        if (glassy) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset() }
                    .fillMaxWidth()
                    .height(capsuleHeight)
                    .padding(horizontal = inset)
                    .drawBackdrop(
                        backdrop = appBackdrop,
                        shape = { shape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(15f.dp.toPx() * progress, 18f.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, screen ->
                    LiquidTabItem(
                        screen = screen,
                        tint = selectedInk,
                        filled = true,
                        // Must match the visible row exactly: this is the layer the capsule reads.
                        showLabel = showLabels,
                        labelFontSize = labelFontSize,
                        scaleProvider = {
                            if (index == capsuleIndex) lerp(1f, 1.12f, dampedDragAnimation.pressProgress) else 1f
                        },
                        onClick = null,
                    )
                }
            }
        }

        // ---- the capsule ------------------------------------------------------------------
        if (tabWidthPx > 0f) {
            // Never narrower than it is tall. While the fluid dock unfolds, the bar is measured at every
            // width between a circle and the full row, and a capsule a third of that was a dark sliver
            // beside the icon; at least round, it is the circle the bar is growing out of.
            val tabWidth = with(density) { tabWidthPx.toDp() }.coerceAtLeast(capsuleHeight)
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
                            backdrop = capsuleBackdrop,
                            shape = { shape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                // Softly frosted at rest; clear, bending glass while held.
                                blur(3f.dp.toPx() * (1f - progress))
                                lens(
                                    lerp(0f, 10f.dp.toPx(), progress),
                                    lerp(0f, 12f.dp.toPx(), progress),
                                    chromaticAberration = progress > 0.01f,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = lerp(0.5f, 1f, dampedDragAnimation.pressProgress))
                            },
                            shadow = { Shadow(alpha = lerp(0.35f, 1f, dampedDragAnimation.pressProgress)) },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(radius = 8f.dp * progress, alpha = progress)
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                // Longer and flatter the faster it travels, round again as it slows.
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                drawRect(restWash.copy(alpha = 0.8f * (1f - 0.75f * dampedDragAnimation.pressProgress)))
                            },
                        )
                        .height(capsuleHeight)
                        .width(tabWidth),
                )

                // The tab under the capsule, drawn again on top of it. What shows through the glass
                // sits under the wash and so can never be at full strength; at rest this is the
                // crisp copy, and it fades out within the first third of a press.
                val capsuleTab by remember(tabs.size) {
                    derivedStateOf { dampedDragAnimation.value.fastRoundToInt().coerceIn(0, lastIndex) }
                }
                Row(
                    capsuleModifier
                        .graphicsLayer {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            alpha = (1f - dampedDragAnimation.pressProgress * 3f).fastCoerceIn(0f, 1f)
                        }
                        .clearAndSetSemantics {}
                        .height(capsuleHeight)
                        .width(tabWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.getOrNull(capsuleTab)?.let { screen ->
                        LiquidTabItem(
                            screen = screen,
                            tint = selectedInk,
                            filled = true,
                            showLabel = showLabels,
                            labelFontSize = labelFontSize,
                            scaleProvider = { 1f },
                            onClick = null,
                            iconModifier = flyingIcon,
                            labelModifier = tabFade,
                        )
                    }
                }
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

/**
 * How long a tap waits for the capsule to reach its tab before the page changes, at most, and how
 * close counts as there. The capsule's spring covers a tab in about this long, so the wait is the
 * slide itself rather than a pause after it.
 */
private const val TabNavigateMaxWaitMs = 180L
private const val TabNavigateArrival = 0.15f

/**
 * Labels go when there is no longer room to read them.
 *
 * The dock is as wide as the screen, but how many dp that is depends on the interface scale
 * (Settings -> Appearance -> Display): at 130% a 411dp phone reports 316dp, which leaves the
 * tab pill about 200dp -- 50dp a tab, an icon and roughly six characters. "Mood & Genres"
 * ellipsised to "Mood &..." is worse than no label at all, because a truncated word is
 * something the eye tries to read twice. Below the threshold the row is icons, which is a
 * complete design rather than a broken one.
 *
 * Derived from the configuration rather than from measurement so it is right on the first
 * frame; a row that renders labels and then drops them is the flicker this is avoiding. Shared by
 * the tab bar and the fold's plate, so the two never set the row differently.
 */
@Composable
private fun rememberShowDockLabels(tabCount: Int): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, tabCount) {
        val pillWidth = minOf(
            configuration.screenWidthDp.dp - DockSideChrome,
            LiquidTabBarMaxWidth,
        )
        (pillWidth - 8.dp) / tabCount.coerceAtLeast(1) >= LabelledTabMinWidth
    }
}

/**
 * How big the labels can be before they touch the ends of the capsule.
 *
 * "Mood & Genres" at 11sp is 90dp wide, and on a 411dp phone a tab slot is 93dp - so the label
 * fitted, and sat hard against the curved ends of the glass with a dp to spare on each side.
 * That is the overlap: not text running past its slot, but text with no room inside it.
 *
 * The slot cannot grow - four even tabs across the width of the phone is what there is - so
 * the type gives way instead, by exactly as much as it has to and no more. Measured against
 * the longest label at the heaviest weight it is ever drawn in, and one size is used for all
 * four, because tabs set at different sizes look like a mistake rather than a fit. Apple sets
 * a tab bar label at 10pt, so this lands where iOS already is.
 */
@Composable
private fun rememberDockLabelFontSize(tabs: List<Screens>, tabWidthPx: Float): TextUnit {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val labels = tabs.map { stringResource(it.dockTitleId) }
    return remember(labels, tabWidthPx, labelStyle, density) {
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
}

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
    // Hooks for the fold (see FluidDock): what fades the tab in and out, and what flies its icon.
    itemModifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    labelModifier: Modifier = Modifier,
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
            }
            .then(itemModifier),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(screen.classicIcon(filled)),
            contentDescription = stringResource(screen.titleId),
            tint = tint,
            modifier = iconModifier.size(26.dp),
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
                modifier = Modifier.padding(horizontal = DockLabelSideRoom).then(labelModifier),
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
    /** Folded into the dock's row: the title alone. Open, the artist under it, as the sheet's pill. */
    inline: Boolean = true,
) {
    // Eased rather than switched, so the title does not jump when the artist line comes and goes
    // on the way between the dock's row and the sheet's pill.
    val artistShown by animateFloatAsState(
        targetValue = if (inline) 0f else 1f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "pillArtist",
    )
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val haptic = LocalHapticFeedback.current
    // Convx 2.0.4 keeps the artist under the title folded too, and its cover at 38dp rounded 22%;
    // only the forward button leaves as the pill drops into the row.
    val convx = LocalConvxChrome.current
    val artistLine = if (convx) 1f else artistShown

    // Where the song is, for the liquid metal ring round play/pause in the folded row: read from the
    // player a few times a second while it plays, still while it doesn't.
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
    val ringProgress by animateFloatAsState(
        progress,
        tween(260, easing = androidx.compose.animation.core.LinearEasing),
        label = "pillRing",
    )

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
                                playerConnection.seekToNext()
                            }
                            horizontal && dragX >= skipDistance -> {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.seekToPrevious()
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
            .padding(start = 8.dp, end = 4.dp),
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
                // Album art (clean circle — no wavy/floral shapes). Where the cover is lifted from
                // when the player opens from the dock, and where it lands on close.
                // Folded into the dock's row (state B) the cover is a disc, as the iOS music app's
                // inline accessory has it; lifting into the open pill it squares off to the sheet's
                // own 10dp, so it lands on the sheet's cover in the sheet's shape.
                // A disc in the Convx dock too, so the video playing in it is round as well (it is
                // drawn inside this clip). Lifted open it takes Convx's own 22% corner.
                val artCorner = lerp(19.dp, if (convx) 38.dp * 0.22f else 10.dp, artistShown)
                val flight = com.ozyern.exhale.ui.player.LocalCoverFlight.current
                val flightOwner = remember { Any() }
                if (flight != null) {
                    androidx.compose.runtime.DisposableEffect(flight) {
                        onDispose { flight.releaseMini(flightOwner) }
                    }
                }
                Box(
                    modifier = Modifier
                        .then(
                            flight?.run {
                                // Two places: folded into the row, and lifted into the open pill.
                                // Each is the landing only while the dock is the one showing the
                                // song in that shape.
                                Modifier.reportMini(
                                    flightOwner,
                                    corner = artCorner,
                                    slot = if (inline) "dockInline" else "dockOpen",
                                    visible = {
                                        com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.fold > 0.5f &&
                                            com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.inline == inline
                                    },
                                )
                            } ?: Modifier,
                        )
                        // The folded row's 38dp, growing to the open pill's 42dp as it lifts, so
                        // it lands on the sheet's own cover at the sheet's own size.
                        .size(if (convx) 38.dp else lerp(38.dp, 42.dp, artistShown))
                        .clip(RoundedCornerShape(artCorner))
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
                    // The video the player was showing when it closed carries on here, small.
                    com.ozyern.exhale.ui.player.DockedVideo(
                        metadata?.id,
                        Modifier.fillMaxSize(),
                        visible = {
                            com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.fold > 0.5f &&
                                com.ozyern.exhale.ui.component.foldingdock.FoldingDockMotion.inline == inline
                        },
                    )
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
                    // the iOS music app's folded bar: the title, bold, and nothing under it.
                    Text(
                        text = metadata?.title.orEmpty(),
                        fontSize = if (convx) 17.sp else 16.sp,
                        lineHeight = if (convx) 21.sp else 20.sp,
                        fontWeight = if (convx) FontWeight.SemiBold else FontWeight.Bold,
                        color = clearGlassContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fadingMarquee(),
                    )
                    if (artistLine > 0.01f) {
                        Text(
                            text = metadata?.artists?.joinToString { it.name }.orEmpty(),
                            fontSize = if (convx) 13.sp else 14.sp,
                            lineHeight = if (convx) 16.sp else 17.sp,
                            fontWeight = FontWeight.Normal,
                            color = clearGlassContentColor().copy(alpha = 0.72f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .graphicsLayer { alpha = artistLine }
                                // Its height grows with it, so the title glides up rather than jumps.
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    val h = (placeable.height * artistLine).roundToInt()
                                    layout(placeable.width, h) { placeable.place(0, 0) }
                                },
                        )
                    }
                }
            }
        }

        // Play / pause and forward as the sheet's pill draws them (Convx's): bare solid glyphs on
        // the glass. Folded into the row there is room for play alone, so forward opens out with
        // the artist line as the pill lifts and closes with it as the pill drops.
        val ink = clearGlassContentColor()
        Box(contentAlignment = Alignment.Center) {
            // Folded into the row (state B), the song's progress is a ring of liquid metal round
            // play/pause, flowing while it plays; it fades as the pill lifts into the open one.
            val ringShown = 1f - artistShown
            if (ringShown > 0.01f) {
                Box(
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { alpha = ringShown }
                        .liquidMetalRing(
                            progress = { ringProgress },
                            flowing = isPlaying && inline,
                            trackColor = ink.copy(alpha = 0.16f),
                        ),
                )
            }
            com.ozyern.exhale.ui.player.MiniGlyphButton(
                icon = when {
                    convx && isPlaying -> R.drawable.sf_pause_fill
                    convx -> R.drawable.sf_play_fill
                    isPlaying -> R.drawable.pause
                    else -> R.drawable.play
                },
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                tint = ink.copy(alpha = 0.92f),
                onClick = { playerConnection.player.togglePlayPause() },
                // Inside the ring folded; the sheet pill's own size once lifted.
                glyphSize = lerp(if (convx) 18.dp else 20.dp, if (convx) 22.dp else 30.dp, artistShown),
            )
        }
        if (artistShown > 0.01f) {
            val canSkipNext by playerConnection.canSkipNext.collectAsState()
            Box(
                Modifier
                    .graphicsLayer { alpha = artistShown }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val w = (placeable.width * artistShown).roundToInt()
                        layout(w, placeable.height) { placeable.place(0, 0) }
                    },
            ) {
                com.ozyern.exhale.ui.player.MiniGlyphButton(
                    icon = if (convx) R.drawable.sf_forward_fill else R.drawable.forward_fill,
                    contentDescription = stringResource(R.string.next),
                    tint = (if (convx) ink else androidx.compose.ui.graphics.lerp(ink, MaterialTheme.colorScheme.primary, 0.45f))
                        .copy(alpha = if (canSkipNext) 0.9f else 0.35f),
                    onClick = { if (canSkipNext) playerConnection.seekToNext() },
                    glyphSize = 24.dp,
                )
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
private val SearchRowHeight = 50.dp

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
    // In the jelly style the search row is made of the dock's own material, so the bottom of the
    // screen doesn't change from one kind of glass to another when you open Search.
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
                    if (leadingIsBack) R.drawable.chevron_back else R.drawable.home_house_fill,
                ),
                contentDescription = stringResource(
                    if (leadingIsBack) R.string.back else R.string.home,
                ),
                // Full ink, as the iOS music app's Home beside its field: the grey made it read disabled.
                tint = clearGlassContentColor(),
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        // Search input capsule — a SEPARATE frosted pill. Tapping anywhere on it expands
        // the real type-in field (host-owned overlay). the iOS music app's field is a slim
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

/* ----------------------------------------------------------------------- */
/* The fluid dock: the reference dock's fold                                          */
/* ----------------------------------------------------------------------- */
private const val FluidKeyIcon = "fluidDockIcon"


/** The fold's two halves, handed to the tab strip so it can take part in it. */
@OptIn(ExperimentalSharedTransitionApi::class)
private class FluidMorph(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
) {
    @Composable
    fun sharedIcon(): Modifier = with(shared) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(FluidKeyIcon),
            animatedVisibilityScope = visibility,
            zIndexInOverlay = 2f,
        )
    }
}

/**
 * A tab that is not travelling: it arrives late and leaves early, through a 50dp blur. the reference dock's
 * animateEnterExitTab - in over 150ms, starting halfway and done at 80%, so the glass has already
 * stretched most of the way before anything appears on it.
 */
@Composable
private fun Modifier.fluidTabFade(visibility: AnimatedVisibilityScope): Modifier {
    val transition = visibility.transition
    val blurPx = with(LocalDensity.current) { 50.dp.toPx() }
    val alpha = transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = 150
                if (targetState == EnterExitState.Visible) {
                    0f atFraction 0.5f using FastOutSlowInEasing
                    1f atFraction 0.8f
                }
            }
        },
        label = "fluidTabAlpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val blur = transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = 150
                if (targetState == EnterExitState.Visible) {
                    blurPx atFraction 0.5f using FastOutSlowInEasing
                    0f atFraction 0.8f
                }
            }
        },
        label = "fluidTabBlur",
    ) { if (it == EnterExitState.Visible) 0f else blurPx }
    return graphicsLayer {
        this.alpha = alpha.value
        val radius = blur.value
        renderEffect = if (radius > 0.5f) BlurEffect(radius, radius) else null
    }
}

/* The jelly dock: the reference's folding tab bar                          */
/* ----------------------------------------------------------------------- */

/**
 * What the jelly dock needs to become the search row on the search pages: the tab group
 * shrinks to the page you came from and the search circle widens into this field.
 */
class DockSearch(
    val placeholder: String,
    /** The committed query on a results page, shown in place of the placeholder. */
    val committedQuery: String?,
    /** On a results page the leading arrow goes back a level rather than home. */
    val leadingIsBack: Boolean,
    val onExit: () -> Unit,
    val onTapBar: () -> Unit,
    /** The field is open for typing: the row widens into one full-width field and hands over. */
    val typing: Boolean = false,
)

@Composable
private fun JellyFoldingDock(
    items: List<Screens>,
    searchScreen: Screens?,
    collapsed: Boolean,
    hasNowPlaying: Boolean,
    pureBlack: Boolean,
    showLabels: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onMiniPlayerClick: () -> Unit,
    search: DockSearch?,
    modifier: Modifier = Modifier,
    songPillReveal: () -> Float = { 1f },
    liquidBar: Boolean = false,
    /** Convx 2.0.4's dock; false draws the classic one, kept as it was (Settings > Appearance > Dock). */
    convx: Boolean = true,
) {
    // Search last, at the trailing end, where the folded bar detaches it into its own circle.
    val ordered = remember(items, searchScreen) { items.sortedBy { it == searchScreen } }
    val titles = ordered.map { stringResource(it.dockTitleId) }
    val selected = ordered.firstOrNull { isSelected(it) }
    if (!convx) {
        ClassicFoldingDock(
            ordered = ordered,
            titles = titles,
            selected = selected,
            searchScreen = searchScreen,
            collapsed = collapsed,
            hasNowPlaying = hasNowPlaying,
            pureBlack = pureBlack,
            showLabels = showLabels,
            isSelected = isSelected,
            onItemClick = onItemClick,
            onMiniPlayerClick = onMiniPlayerClick,
            search = search,
            modifier = modifier,
            songPillReveal = songPillReveal,
            liquidBar = liquidBar,
        )
        return
    }
    val tabs = ordered.mapIndexed { i, screen ->
        val here = screen == selected
        com.ozyern.exhale.ui.component.foldingdock.FoldingDockTab(
            key = screen.route,
            title = titles[i],
            icon = if (here) screen.iconIdActive else screen.iconIdInactive,
            // Convx 2.0.4's sizes: glyphs that fill their whole viewbox are drawn smaller so every tab reads the same.
            glyphSize = if (screen == searchScreen || screen == Screens.MoodAndGenres) 28.dp else 32.dp,
            symbol = screen.dockSymbol(),
            onClick = {
                when {
                    screen == searchScreen && search != null -> search.onTapBar()
                    // The circle on the left of the search row takes you back to that page.
                    search != null && !here -> onItemClick(screen, false)
                    else -> onItemClick(screen, here)
                }
            },
        )
    }
    com.ozyern.exhale.ui.component.foldingdock.FoldingDock(
        tabs = tabs,
        selectedKey = selected?.route,
        folded = collapsed,
        // Tapping the folded page circle unfolds the bar by taking the page back to its top.
        onUnfold = { selected?.let { onItemClick(it, true) } },
        hasSearchTab = searchScreen != null,
        // Convx 2.0.4's accent, Apple Music red (AppleTokens.AccentRed).
        accentColor = DockAccentRed,
        showLabels = showLabels,
        accessory = if (hasNowPlaying) {
            { accessoryModifier, isInline ->
                Box(accessoryModifier, contentAlignment = Alignment.Center) {
                    MiniPlayerPill(pureBlack = pureBlack, onExpand = onMiniPlayerClick, inline = isInline)
                }
            }
        } else {
            null
        },
        accessoryReveal = songPillReveal,
        // The open player lands exactly on the sheet's own slim pill — its height, and the air
        // above the dock it sits in — so the hand-over at the end of an unfold is invisible.
        openPlayerHeight = com.ozyern.exhale.constants.CompactMiniPlayerHeight,
        openPlayerLift = com.ozyern.exhale.constants.MiniPlayerBottomSpacing +
            com.ozyern.exhale.constants.CompactMiniPlayerTopInset,
        searchMode = search != null,
        typing = search?.typing == true,
        openBar = if (liquidBar) {
            { barModifier ->
                LiquidTabBar(
                    tabs = ordered.filter { it != searchScreen },
                    pureBlack = pureBlack,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    modifier = barModifier,
                )
            }
        } else {
            null
        },
        // The liquid bar's own spacing: 10dp between the tab capsule and the search circle.
        openGap = if (liquidBar) 10.dp else 8.dp,
        searchBarContent = search?.let { s -> { fieldModifier -> DockSearchField(s, fieldModifier) } },
        modifier = modifier,
    )
}

/** The classic dock's own glyphs, from before the Convx port's SF Symbols. */
internal fun Screens.classicIcon(active: Boolean): Int = when (this) {
    Screens.Home -> R.drawable.home_house_fill
    Screens.Search -> if (active) R.drawable.search_filled else R.drawable.search
    Screens.Library -> if (active) R.drawable.library_filled else R.drawable.library_outlined
    Screens.MoodAndGenres -> if (active) R.drawable.radio_filled else R.drawable.radio
    else -> if (active) iconIdActive else iconIdInactive
}

/**
 * The classic dock, wired exactly as it was before the Convx 2.0.4 port: its own fold and glass
 * ([com.ozyern.exhale.ui.component.foldingdock.classic.FoldingDock]), its glyphs, the theme's accent,
 * and, in the liquid style, its own open tab bar beside a separate search circle.
 */
@Composable
private fun ClassicFoldingDock(
    ordered: List<Screens>,
    titles: List<String>,
    selected: Screens?,
    searchScreen: Screens?,
    collapsed: Boolean,
    hasNowPlaying: Boolean,
    pureBlack: Boolean,
    showLabels: Boolean,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onMiniPlayerClick: () -> Unit,
    search: DockSearch?,
    modifier: Modifier,
    songPillReveal: () -> Float,
    liquidBar: Boolean,
) {
    val tabs = ordered.mapIndexed { i, screen ->
        val here = screen == selected
        com.ozyern.exhale.ui.component.foldingdock.classic.FoldingDockTab(
            key = screen.route,
            title = titles[i],
            icon = screen.classicIcon(here),
            glyphSize = if (screen == searchScreen) 36.dp else 32.dp,
            onClick = {
                when {
                    screen == searchScreen && search != null -> search.onTapBar()
                    search != null && !here -> onItemClick(screen, false)
                    else -> onItemClick(screen, here)
                }
            },
        )
    }
    com.ozyern.exhale.ui.component.foldingdock.classic.FoldingDock(
        tabs = tabs,
        selectedKey = selected?.route,
        folded = collapsed,
        onUnfold = { selected?.let { onItemClick(it, true) } },
        hasSearchTab = searchScreen != null,
        showLabels = showLabels,
        accessory = if (hasNowPlaying) {
            { accessoryModifier, isInline ->
                Box(accessoryModifier, contentAlignment = Alignment.Center) {
                    MiniPlayerPill(pureBlack = pureBlack, onExpand = onMiniPlayerClick, inline = isInline)
                }
            }
        } else {
            null
        },
        accessoryReveal = songPillReveal,
        openPlayerHeight = com.ozyern.exhale.constants.CompactMiniPlayerHeight,
        openPlayerLift = com.ozyern.exhale.constants.MiniPlayerBottomSpacing +
            com.ozyern.exhale.constants.CompactMiniPlayerTopInset,
        searchMode = search != null,
        typing = search?.typing == true,
        openBar = if (liquidBar) {
            { barModifier ->
                LiquidTabBar(
                    tabs = ordered.filter { it != searchScreen },
                    pureBlack = pureBlack,
                    isSelected = isSelected,
                    onItemClick = onItemClick,
                    modifier = barModifier,
                )
            }
        } else {
            null
        },
        openGap = if (liquidBar) 10.dp else 8.dp,
        searchBarContent = search?.let { s -> { fieldModifier -> DockSearchField(s, fieldModifier) } },
        modifier = modifier,
    )
}

/**
 * The search field the search circle widens into, before the keyboard: back arrow, then the
 * placeholder or the query that was searched. A tap anywhere but the arrow opens the keyboard.
 */
@Composable
private fun DockSearchField(state: DockSearch, modifier: Modifier) {
    val ink = clearGlassContentColor()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = state.onTapBar,
            )
            .padding(horizontal = 4.dp),
    ) {
        // Convx 2.0.4's SearchBarPlaceholder: the field always leads with the back arrow, on Search
        // itself as on a results page; it steps back a level (results to Search, Search to the page
        // you came from).
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = state.onExit)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back),
                contentDescription = stringResource(R.string.back),
                tint = ink,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.weight(1f)) {
            Text(
                text = state.committedQuery ?: state.placeholder,
                // Convx's field: 17sp, the query in full ink and the prompt at 60%.
                // In the app's own face: a bare TextStyle here fell back to the system font.
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    color = if (state.committedQuery != null) ink else ink.copy(alpha = 0.6f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Convx 2.0.4's accent, Apple Music red: the selected tab and its label. */
internal val DockAccentRed = Color(0xFFFA2D48)

/** Whether the Convx 2.0.4 dock is on (Settings > Appearance > Dock style); the mini player sizes to it. */
val LocalConvxChrome = androidx.compose.runtime.staticCompositionLocalOf { true }

/** The SF Symbol each tab wears in the dock, bold, as the reference draws its tab bar. */
internal fun Screens.dockSymbol(): String? = when (this) {
    Screens.Home -> "house.fill"
    Screens.MoodAndGenres -> "square.grid.2x2.fill"
    Screens.Library -> "music.note.square.stack.fill"
    Screens.Search -> "magnifyingglass"
    else -> null
}
