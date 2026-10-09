/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.constants.BottomSheetAnimationSpec
import com.ozyern.exhale.constants.BottomSheetSoftAnimationSpec
import com.ozyern.exhale.constants.EnableHapticFeedbackKey
import com.ozyern.exhale.constants.MiniPlayerHeight
import com.ozyern.exhale.constants.MiniPlayerPillCornerRadius
import com.ozyern.exhale.constants.MiniPlayerPillHorizontalInset
import com.ozyern.exhale.utils.rememberHaptic
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.compose.ui.util.fastRoundToInt

// At p=0 (fully collapsed) the content layer is squashed to this fraction of its natural height.
// It has to be aggressive enough that the controls visibly compress into the pill band, and stay
// well under ~0.45 or the collapse reads as a shrink rather than a morph.
//
// 0.32, not the 0.18 this used to be. Against a scaleX of ~0.9 that was a 5:1 anisotropic squash —
// past a certain ratio the eye stops reading "compressed into a band" and starts reading "smeared",
// and the artwork and title spent the first frames of every collapse as a horizontal streak. The
// crossfade below now holds the content visible longer, which makes those frames matter more, not
// less.
private const val PILL_VERTICAL_SQUASH = 0.5f

// The window over which the full player and the mini pill trade places, as a fraction of the
// progress range (0→1). Geometry still carries the gesture - this is a hand-off, not a
// crossfade that hides the transformation - but at 0.16 the full player was solid by a sixth of
// the way up, so most of an expansion was a complete, miniature player inflating. Over 0.28 the
// player arrives *into* the space the morph has already opened, which is the difference between a
// window growing and a screenshot being scaled.
//
// Both layers MUST ramp over this same window, in opposite directions, so their alphas sum to 1 at
// every p. They used to ramp over different windows (content over 0.12, pill over 0.25), which left
// a band around p≈0.15 where both were near-opaque and stacked — two different layouts of the same
// song superimposed. That double image was the single most visible flaw in the collapse.
private const val MORPH_HANDOFF_FRACTION = 0.28f

/** Below this progress the full player's content is gone; only the card is morphing. */
private const val CONTENT_GONE_BELOW = 0.38f

/** Over how much progress above [CONTENT_GONE_BELOW] the content fades in or out. */
private const val CONTENT_FADE_SPAN = 0.32f

/** Above this progress the mini pill's content is gone. Below [CONTENT_GONE_BELOW], so they never stack. */
private const val PILL_GONE_ABOVE = 0.26f



/**
 * Bottom Sheet
 * Modified from [ViMusic](https://github.com/vfsfitvnm/ViMusic)
 */
@Composable
fun BottomSheet(
    state: BottomSheetState,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onDismiss: (() -> Unit)? = null,
    /**
     * Opt in to the "Dynamic Island" rectangle→pill morph (see [dynamicIslandMorph]). Only the
     * player sheet has a pill to morph into; the queue and lyrics sheets leave this off and keep
     * the plain slide.
     */
    dynamicIslandMorph: Boolean = false,
    /**
     * The cover-flight open instead of the morph: the sheet slides, its content rises into place
     * a beat behind it, and the cover itself is carried by [com.ozyern.exhale.ui.player.CoverFlight].
     */
    coverFlight: Boolean = false,
    /**
     * Morph target geometry — the bounds, *inside this sheet's own coordinate space*, of whatever
     * the player is collapsing into. Defaults describe the standalone mini-player pill (State A);
     * the host overrides them with the nav bar's centre capsule when the bar is collapsed
     * (State B). See [pillTopOffset].
     */
    pillHeight: Dp = MiniPlayerHeight,
    pillHorizontalInset: Dp = MiniPlayerPillHorizontalInset,
    pillCornerRadius: Dp = MiniPlayerPillCornerRadius,
    /**
     * How far below the top of the sheet's collapsed region the target pill sits. `0` for the
     * mini-player pill, which occupies that region's top strip; a positive value walks the morph
     * down onto the nav bar for State B.
     */
    pillTopOffset: Dp = 0.dp,
    /**
     * Whether a tap on the collapsed strip opens the sheet. Off while the mini player is folded
     * into the dock (State B): the strip is then empty air over the page, and taking its taps
     * swallowed whatever sat there — the Settings search capsule, for one.
     */
    collapsedTapToExpand: Boolean = true,
    collapsedContent: @Composable BoxScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val collapsedInteractionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .offset {
                val y =
                    (state.expandedBound - state.value)
                        .roundToPx()
                        .coerceAtLeast(0)
                IntOffset(x = 0, y = y)
            }
            // Folded into the dock, the sheet is empty air over the bottom of the page, and a
            // gesture detector there wins the hit test over whatever the page has in that spot —
            // which is how the Settings search capsule stopped answering. The dock pill opens it.
            .then(
                if (!collapsedTapToExpand && state.isCollapsed) Modifier
                else Modifier.bottomSheetDraggable(state, onDismiss),
            )
            // Corner rounding and scrim alpha both track state.progress, which ticks every
            // frame of a drag or spring. Reading it here in a graphicsLayer/drawBehind lambda
            // instead of in composition keeps those frames in the draw phase only — reading it
            // in composition used to recompose this whole subtree (the entire player) ~60x/s,
            // which was the single biggest source of micro-stutter during the transition.
            .graphicsLayer {
                clip = true
                val corner = 16.dp * (1f - state.progress.coerceIn(0f, 1f))
                shape = RoundedCornerShape(topStart = corner, topEnd = corner)
            }
            .drawBehind {
                // The morphing player paints its ground inside its own shrinking window (below).
                // Painted here, across the whole sheet, it was a dim full-screen rectangle laid
                // over the page for the length of every close — the "ghost" behind the pill.
                if (dynamicIslandMorph) return@drawBehind
                val p = state.progress.coerceIn(0f, 1f)
                if (coverFlight) {
                    // The ground fills in over the first half: the page shows through the rising
                    // sheet at first and is covered by the time the cover is in place.
                    val g = (p / 0.5f).coerceIn(0f, 1f)
                    val alpha = backgroundColor.alpha * g * g * (3f - 2f * g)
                    if (alpha > 0f) drawRect(backgroundColor.copy(alpha = alpha))
                    return@drawBehind
                }
                // Ramp the scrim in over the first 20% of travel, then hold — the sheet must be
                // fully opaque well before it reaches the top or the content behind shows through.
                val fade = p * (p / 0.2f).coerceAtMost(1f)
                val alpha = backgroundColor.alpha * fade
                if (alpha > 0f) drawRect(backgroundColor.copy(alpha = alpha))
            },
    ) {
        if (!state.isCollapsed && !state.isDismissed) {
            BackHandler(onBack = state::collapseSoft)
        }

        if (!state.isCollapsed) {
            // Two stacked layers, deliberately separate:
            //
            //  * the OUTER layer is the morph *window* — an animated hardware outline clip that
            //    contracts from the full screen down to the mini-player pill (rectangle → pill
            //    corner sweep). It carries no transform, so the window stays in the sheet's own
            //    coordinate space and lands exactly on the pill's bounds.
            //  * the INNER layer is the morph *content* — a uniform scale about the top edge, so
            //    the artwork and controls appear to be drawn down into that shrinking window
            //    rather than merely cropped by it, plus the fade that hands off to the real pill.
            //
            // Fusing them into one layer would scale the clip window too, and the player would
            // shrink into a rectangle smaller than the pill it is supposed to become.
            Box(
                modifier = if (!dynamicIslandMorph) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            clip = true
                            val p = state.progress.coerceIn(0f, 1f)
                            shape = DynamicIslandMorphShape(
                                progress = p,
                                pillHeightPx = pillHeight.toPx(),
                                pillInsetPx = pillHorizontalInset.toPx(),
                                pillRadiusPx = pillCornerRadius.toPx(),
                                pillTopOffsetPx = pillTopOffset.toPx(),
                            )
                            // No flight shadow: a shadow traced around an outline that changes shape
                            // every frame was redrawn from scratch sixty times a second, on top of a
                            // full-screen player, and it was a large part of why the close stuttered.
                        }
                        // The sheet's ground, inside the window: it shrinks with the player instead
                        // of hanging behind it at full size.
                        .drawBehind {
                            val p = state.progress.coerceIn(0f, 1f)
                            val alpha = backgroundColor.alpha * (p / 0.15f).coerceIn(0f, 1f)
                            if (alpha > 0f) drawRect(backgroundColor.copy(alpha = alpha))
                        }
                },
            ) {
                BoxWithConstraints(
                    modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = state.progress.coerceIn(0f, 1f)
                            if (coverFlight) {
                                // The player rises into place a beat behind the sheet carrying it,
                                // and settles from slightly smaller: it follows the cover rather
                                // than arriving with it.
                                val r = ((p - 0.1f) / 0.55f).coerceIn(0f, 1f)
                                val e = r * r * (3f - 2f * r)
                                alpha = e
                                translationY = 56.dp.toPx() * (1f - e)
                                val s = 0.97f + 0.03f * e
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                return@graphicsLayer
                            }
                            if (!dynamicIslandMorph) {
                                // Legacy behaviour for the queue/lyrics sheets: fade only.
                                alpha = ((p - 0.25f) * 4).coerceIn(0f, 1f)
                                return@graphicsLayer
                            }

                            // ---- The geometry half of the morph ----
                            //
                            // The collapse scale is DERIVED from the pill, not hand-tuned: at p=0
                            // the clip window is exactly `width - 2*inset` wide, so scaling the
                            // content by that same ratio makes its edges land on the window's
                            // edges. The content therefore converges *onto* the pill's real bounds
                            // instead of onto an arbitrary rectangle, which is the whole difference
                            // between a morph and a shrink-then-swap.
                            //
                            // Shrink about the TOP-CENTRE: the pill occupies the top strip of the
                            // sheet's collapsed region, so that is the fixed point both layers
                            // rotate around. Anything else and the content slides as it scales.
                            val insetPx = pillHorizontalInset.toPx()
                            val pillWidthRatio =
                                if (size.width > 0f) {
                                    // Floor at 0.3, not 0.5. In State B the inset is the whole
                                    // nav-bar side slot — a home circle, a search circle and their
                                    // gaps — which on a narrow phone is well over a quarter of the
                                    // display from each side. Clamping at 0.5 there stopped the
                                    // content shrinking as far as its own clip window, so the last
                                    // stretch of the collapse cropped the player instead of
                                    // scaling it, and the pill appeared to swallow it whole.
                                    ((size.width - 2f * insetPx) / size.width).coerceIn(0.3f, 1f)
                                } else {
                                    1f
                                }
                            // Vertical squash goes further than the horizontal one — the pill is a
                            // 64dp strip out of a full-height sheet, so a uniform scale alone can
                            // never read as "collapsing into it". Squashing Y harder makes the
                            // controls visibly compress into the pill's band the way the Dynamic
                            // Island's content does.
                            val eased = morphEase(p)
                            scaleX = pillWidthRatio + (1f - pillWidthRatio) * eased
                            scaleY = PILL_VERTICAL_SQUASH + (1f - PILL_VERTICAL_SQUASH) * eased
                            transformOrigin = TransformOrigin(0.5f, 0f)

                            // The translate half of the morph. With a top-centre origin the scale
                            // alone always parks the content at the top of the sheet, which is
                            // right for State A but leaves it hovering above the nav bar in State
                            // B. Sliding the layer down by the same offset the clip window uses
                            // keeps content and window locked together, so the player appears to
                            // travel *into* the bar. Pure graphicsLayer — no layout is involved,
                            // so this costs nothing per frame beyond the render-thread transform.
                            // LINEAR, matching `DynamicIslandMorphShape`, which walks its window
                            // down by `pillTopOffsetPx * (1 - progress)`. This used to ease with
                            // `(1 - eased)` while the window stayed linear, so through the middle
                            // of every State-B collapse the two disagreed by up to ~12% of the
                            // offset: the content slid *inside* its own clip window instead of
                            // travelling with it, and the player looked like it was being wiped
                            // into the nav bar rather than shrinking into it. In State A the
                            // offset is 0 and this line is a no-op either way, which is why the
                            // mismatch only ever showed up against the collapsed dock.
                            translationY = pillTopOffset.toPx() * (1f - p)

                            // ---- The opacity half ----
                            //
                            // Held near-solid across almost the entire travel and released only in
                            // the last sliver before the pill takes over. The old ramp
                            // (`(p - 0.08) / 0.30`) had the content fully transparent below p=0.08
                            // and fully opaque by p=0.38 — so two thirds of the drag showed no
                            // transformation at all, which is exactly why it read as a snap. Now
                            // the geometry above is visible for the whole gesture and the fade is
                            // just the hand-off, meeting the pill's own fade-out at the crossover.
                            // The player's own content leaves over the upper half of the travel and the
                            // glass card alone finishes the trip into the pill: a shrinking miniature
                            // of the lyrics or the queue flying into the dock is what read as
                            // unpolished. The pill's content only comes in once the card has landed.
                            val handoff = ((p - CONTENT_GONE_BELOW) / CONTENT_FADE_SPAN).coerceIn(0f, 1f)
                            alpha = handoff
                            // A focus pull instead of a double exposure: through the hand-off the
                            // player softens as the pill sharpens (and the reverse on the way up),
                            // so the two read as one thing changing shape rather than two layouts
                            // of the same song stacked. Only while it is moving — settled, no
                            // effect runs at all.
                            // No blur. A focus pull over the whole full-screen player — cover, video,
                            // animated backdrop — was a render-effect pass over every pixel of the
                            // screen on every frame of the gesture, which is what made it drop frames.
                            renderEffect = null
                        },
                    content = content,
                )
            }
        }

        if (!state.isExpanded && (onDismiss == null || !state.isDismissed)) {
            Box(
                modifier =
                Modifier
                    .graphicsLayer {
                        val p = state.progress.coerceIn(0f, 1f)
                        if (dynamicIslandMorph) {
                            // Counter-morph: pill grows toward the player's width as it fades,
                            // so it reads as "expanding into" the full player rather than
                            // disappearing while the player appears from nowhere.
                            val grow = 1f + 0.12f * morphEase(p)
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            // Exactly the complement of the content layer's ramp above, so the two
                            // always sum to 1 and never both paint at full strength.
                            val handoff = (p / PILL_GONE_ABOVE).coerceIn(0f, 1f)
                            alpha = 1f - handoff
                            // The other half of the focus pull.
                            renderEffect = null
                        } else if (coverFlight) {
                            // The bar's title and buttons drift up and out as its cover lifts away.
                            val f = (p / 0.22f).coerceIn(0f, 1f)
                            alpha = 1f - f
                            translationY = -10.dp.toPx() * f
                        } else {
                            alpha = 1f - (p * 4).coerceAtMost(1f)
                        }
                    }
                    .then(
                        if (collapsedTapToExpand) {
                            Modifier.clickable(
                                interactionSource = collapsedInteractionSource,
                                indication = null,
                                onClick = state::expandSoft,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .height(state.collapsedBound),
                content = collapsedContent,
            )
        }
    }
}

@Stable
class BottomSheetState(
    draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
    /** Used only to convert fling velocities out of pixel space. See [performFling]. */
    private val density: Density,
) : DraggableState by draggableState {
    val dismissedBound: Dp
        get() = animatable.lowerBound!!

    val expandedBound: Dp
        get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed by derivedStateOf {
        value == animatable.lowerBound!!
    }

    val isCollapsed by derivedStateOf {
        value == collapsedBound
    }

    val isExpanded by derivedStateOf {
        value == animatable.upperBound
    }

    val progress by derivedStateOf {
        1f - (animatable.upperBound!! - animatable.value) / (animatable.upperBound!! - collapsedBound)
    }

    fun collapse(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        onAnchorChanged(COLLAPSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(collapsedBound, animationSpec, initialVelocity)
        }
    }

    fun expand(animationSpec: AnimationSpec<Dp>, initialVelocity: Dp = 0.dp) {
        onAnchorChanged(EXPANDED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.upperBound!!, animationSpec, initialVelocity)
        }
    }

    private fun collapse(initialVelocity: Dp = 0.dp) {
        collapse(BottomSheetAnimationSpec, initialVelocity)
    }

    private fun expand(initialVelocity: Dp = 0.dp) {
        expand(BottomSheetAnimationSpec, initialVelocity)
    }

    fun collapseSoft() {
        collapse(com.ozyern.exhale.constants.BottomSheetCloseAnimationSpec)
    }

    fun expandSoft() {
        expand(com.ozyern.exhale.constants.BottomSheetOpenAnimationSpec)
    }

    fun dismiss() {
        onAnchorChanged(DISMISSED_ANCHOR)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(animatable.lowerBound!!)
        }
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.snapTo(value)
        }
    }

    /**
     * @param velocity release velocity in **pixels per second**, positive = the sheet is travelling
     *   up (toward expanded).
     */
    fun performFling(
        velocity: Float,
        onDismiss: (() -> Unit)?,
    ) {
        // Hand the gesture's own speed to the spring instead of starting it from rest.
        //
        // This used to consume `velocity` purely as a direction test and then animate from zero, so
        // a hard flick and a slow release collapsed at exactly the same canned speed — the sheet
        // stopped dead the instant the finger lifted and then re-launched itself. Seeding the
        // spring is what makes the player feel like it was *thrown* down rather than released and
        // separately animated, and it is the difference the eye reads as "weight".
        val initialVelocity = with(density) { velocity.toDp() }

        if (velocity > 250) {
            expand(initialVelocity)
        } else if (velocity < -250) {
            if (value < collapsedBound && onDismiss != null) {
                dismiss()
                onDismiss.invoke()
            } else {
                collapse(initialVelocity)
            }
        } else {
            val l0 = dismissedBound
            // Midpoints between the anchors. l2 was half the open travel measured from zero rather
            // than from the collapsed bar, which put the "opens" line well below halfway: a pull
            // released a third of the way up still sprang the player open.
            val l1 = dismissedBound + (collapsedBound - dismissedBound) / 2
            val l2 = collapsedBound + (expandedBound - collapsedBound) / 2
            val l3 = expandedBound

            when (value) {
                in l0..l1 -> {
                    if (onDismiss != null) {
                        dismiss()
                        onDismiss.invoke()
                    } else {
                        collapse(initialVelocity)
                    }
                }

                // Below the fling threshold this is a positional snap, but the finger was still
                // moving; carrying the residual through means the sheet never visibly stalls
                // between the release and the spring picking it up.
                in l1..l2 -> collapse(initialVelocity)
                in l2..l3 -> expand(initialVelocity)
                else -> Unit
            }
        }
    }

    val preUpPostDownNestedScrollConnection
        get() =
            object : NestedScrollConnection {
                var isTopReached = false

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (isExpanded && available.y < 0) {
                        isTopReached = false
                    }

                    return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                        dispatchRawDelta(available.y)
                        available
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isTopReached) {
                        isTopReached = consumed.y == 0f && available.y > 0
                    }

                    return if (isTopReached && source == NestedScrollSource.UserInput) {
                        dispatchRawDelta(available.y)
                        available
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity =
                    if (isTopReached) {
                        val velocity = -available.y
                        performFling(velocity, null)

                        available
                    } else {
                        Velocity.Zero
                    }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    isTopReached = false
                    return Velocity.Zero
                }
            }
}

const val EXPANDED_ANCHOR = 2
const val COLLAPSED_ANCHOR = 1
const val DISMISSED_ANCHOR = 0

@Composable
fun rememberBottomSheetState(
    dismissedBound: Dp,
    expandedBound: Dp,
    collapsedBound: Dp = dismissedBound,
    initialAnchor: Int = DISMISSED_ANCHOR,
    /**
     * Fire a physical "snap" when the sheet commits to a new anchor.
     *
     * Off by default. Only the *player* sheet opts in: it is the one whose morph is a whole-screen
     * geometric transformation, and it is the outermost sheet, so the queue and lyrics sheets
     * stacked inside it must stay silent or a single flick would fire two or three vibrations.
     */
    hapticFeedback: Boolean = false,
): BottomSheetState {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Respect the app's own haptics switch as well as the caller's opt-in. Read here, at the
    // composable, so toggling the preference takes effect without recreating the sheet state.
    val (hapticsEnabled) = rememberPreference(EnableHapticFeedbackKey, defaultValue = true)
    val haptic = rememberHaptic(enabled = hapticFeedback && hapticsEnabled)
    // The state below is remembered across recompositions, so it would otherwise capture whatever
    // HapticManager existed when it was first built and keep buzzing after the user turned
    // haptics off. rememberUpdatedState keeps the captured reference current.
    val currentHaptic by rememberUpdatedState(haptic)

    var previousAnchor by rememberSaveable {
        mutableIntStateOf(initialAnchor)
    }
    val animatable =
        remember {
            Animatable(0.dp, Dp.VectorConverter)
        }

    return remember(dismissedBound, expandedBound, collapsedBound, coroutineScope, density) {
        val initialValue =
            when (previousAnchor) {
                EXPANDED_ANCHOR -> expandedBound
                COLLAPSED_ANCHOR -> collapsedBound
                DISMISSED_ANCHOR -> dismissedBound
                else -> error("Unknown BottomSheet anchor")
            }

        animatable.updateBounds(dismissedBound.coerceAtMost(expandedBound), expandedBound)
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            animatable.animateTo(initialValue, BottomSheetAnimationSpec)
        }

        BottomSheetState(
            draggableState =
            DraggableState { delta ->
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    animatable.snapTo(animatable.value - with(density) { delta.toDp() })
                }
            },
            onAnchorChanged = { anchor ->
                // Fire on the COMMIT, not on the landing. The spring takes ~half a second to
                // settle; waiting for it would put the tick long after the finger left the glass
                // and it would read as a delayed rattle rather than as the object being released.
                // Guarded on an actual change so re-collapsing an already-collapsed sheet (a tap
                // on the scrim, a redundant back-press) stays silent.
                if (anchor != previousAnchor) currentHaptic.morph()
                previousAnchor = anchor
            },
            coroutineScope = coroutineScope,
            animatable = animatable,
            collapsedBound = collapsedBound,
            density = density,
        )
    }
}

@Composable
fun Modifier.bottomSheetDraggable(
    state: BottomSheetState,
    onDismiss: (() -> Unit)? = null,
): Modifier {
    return this.pointerInput(state) {
        val velocityTracker = VelocityTracker()
        // Where the pull began and when: a short, quick swipe can lift with no speed on its last
        // event at all, and read as a slow release it snapped back instead of flying.
        var pullStartNanos = 0L
        var pulled = 0f

        detectVerticalDragGestures(
            onDragStart = {
                pullStartNanos = System.nanoTime()
                pulled = 0f
            },
            onVerticalDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)
                pulled += dragAmount
                state.dispatchRawDelta(dragAmount)
            },
            onDragCancel = {
                velocityTracker.resetTracking()
                state.snapTo(state.collapsedBound)
            },
            onDragEnd = {
                val lifted = -velocityTracker.calculateVelocity().y
                velocityTracker.resetTracking()
                val seconds = (System.nanoTime() - pullStartNanos) / 1e9f
                // Only for a quick swipe: over a long, deliberate drag the average would overrule a
                // finger that came to rest where it meant to leave the player.
                val average = if (seconds in 0.001f..0.25f) -pulled / seconds else 0f
                // The finger's speed as it lifted, or across the whole pull if that was faster.
                val velocity = if (kotlin.math.abs(average) > kotlin.math.abs(lifted)) average else lifted
                state.performFling(velocity, onDismiss)
            },
        )
    }
}
