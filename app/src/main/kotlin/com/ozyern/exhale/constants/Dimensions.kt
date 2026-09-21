/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.constants

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val CONTENT_TYPE_HEADER = 0
const val CONTENT_TYPE_LIST = 1
const val CONTENT_TYPE_SONG = 2
const val CONTENT_TYPE_ARTIST = 3
const val CONTENT_TYPE_ALBUM = 4
const val CONTENT_TYPE_PLAYLIST = 5

val FloatingToolbarHeight = 72.dp
val SlimFloatingToolbarHeight = 64.dp
val FloatingToolbarHorizontalPadding = 16.dp
val FloatingToolbarBottomPadding = 12.dp
val NavigationBarHeight = FloatingToolbarHeight
val SlimNavBarHeight = SlimFloatingToolbarHeight
val MiniPlayerHeight = 64.dp
val MiniPlayerBottomSpacing = 8.dp // Space between MiniPlayer and NavigationBar

/**
 * Geometry of the collapsed mini-player *pill*. These three values are the morph target for the
 * Dynamic-Island transition in `BottomSheet`, so they must stay in lockstep with how
 * `SwipeableMiniPlayerBox` / `NewMiniPlayer` actually lay the pill out — if they drift, the full
 * player shrinks into the wrong rectangle and the hand-off to the pill visibly jumps.
 */
/**
 * Deliberately the *same* value as [FloatingToolbarHorizontalPadding], not a number of its own.
 *
 * The pill is never on screen alone. It floats directly above the dock, or above the search row,
 * and both of those are laid out at [FloatingToolbarHorizontalPadding] from the edge. At the old
 * 12dp the player was 4dp wider on each side than whatever it was sitting on, so the bottom of
 * the screen was a stack of two capsules whose edges did not line up — most obvious over the
 * Search tab, where the row below is also visibly shorter. Four device-independent pixels is
 * small enough to look like a rendering mistake rather than a design decision, which is the worst
 * size for a misalignment to be.
 */
val MiniPlayerPillHorizontalInset = FloatingToolbarHorizontalPadding
val MiniPlayerPillCornerRadius = 32.dp

/**
 * The pill's geometry **anywhere but Home**.
 *
 * Home is where you start something: the dock is right underneath, the wide pill's swipe-to-skip
 * and like button are worth the strip of screen they cost, and a 64dp player over a 64dp dock
 * reads as one stacked object. Everywhere else you are reading — a results list, an album, a
 * settings page — and the player is a status line you occasionally reach for, so it loses a type
 * step, loses the like button, and gives 8dp back to the page.
 *
 * This slot briefly held a top-docked "Dynamic Island" instead, and the reason that failed is
 * worth keeping written down: on iOS the island works because the OS owns the status bar and
 * moves the clock aside for it. Android does not — the status bar is a system window drawn above
 * every app — so the thing either sits *under* the clock and the battery, or hangs below the inset
 * where it is no longer an island at all, just a card at the wrong end of the screen, a thumb's
 * length from where every other control in the app lives. Slimming the pill is the same idea
 * (take less room on pages that are not about playback) without moving the player away from the
 * hand that uses it.
 *
 * The pill is *drawn* at this height inside the unchanged [MiniPlayerHeight] slot rather than
 * changing the sheet's collapsed bound, which is captured once in `rememberBottomSheetState` and
 * cannot be re-derived per route without resetting the sheet. [CompactMiniPlayerTopInset] is the
 * resulting gap above it, and the Dynamic-Island morph is handed the same three numbers so the
 * full player still shrinks onto the pill that is actually there.
 */
val CompactMiniPlayerHeight = 56.dp
val CompactMiniPlayerPillCornerRadius = CompactMiniPlayerHeight / 2
val CompactMiniPlayerTopInset = (MiniPlayerHeight - CompactMiniPlayerHeight) / 2

/**
 * Geometry of the floating nav bar's own centre pill — the **State B** morph target.
 *
 * When the user minimises the player while scrolled down, the full player must not land on the
 * standalone mini-player pill (which is hidden in that state); it has to shrink straight into the
 * frosted nav container. These values describe that container as laid out by
 * `LiquidGlassBottomBar`: a [NavBarPillHeight]-tall capsule flanked by a home circle and a search
 * circle, so the centre pill starts [NavBarPillSideSlot] in from the row's own horizontal padding.
 *
 * They must stay in lockstep with `LiquidGlassBottomBar` — if they drift, the player shrinks into
 * a rectangle that is not the bar and the hand-off visibly jumps.
 */
val NavBarPillHeight = 64.dp
private val NavBarPillGap = 10.dp
val NavBarPillSideSlot = FloatingToolbarHorizontalPadding + NavBarPillHeight + NavBarPillGap
val NavBarPillCornerRadius = NavBarPillHeight / 2
val QueuePeekHeight = 64.dp
val AppBarHeight = 64.dp

val ListItemHeight = 64.dp
val SuggestionItemHeight = 56.dp
val SearchFilterHeight = 48.dp
val ListThumbnailSize = 48.dp
val SmallGridThumbnailHeight = 104.dp
val GridThumbnailHeight = 128.dp
val AlbumThumbnailSize = 144.dp

val ThumbnailCornerRadius = 10.dp
val GridThumbnailCornerRadius = 8.dp

val PlayerHorizontalPadding = 32.dp

val NavigationBarAnimationSpec = spring<Dp>(
	dampingRatio = Spring.DampingRatioNoBouncy,
	stiffness = Spring.StiffnessLow
)

/**
 * The "Aquamorphic" spring — the single curve every *layout morph* in the app settles on.
 *
 * Tuned to sit between two motion languages the app is deliberately borrowing from:
 *
 *  * **ColorOS / OxygenOS 16** move things as though they were viscous — mass arrives, decelerates
 *    through a long tail and settles without a hard stop. That is a low stiffness.
 *  * **iOS** never lets that tail turn into wobble; there is exactly one small overshoot and then
 *    the object is at rest. That is damping just under 1.
 *
 * 0.75 / 250 is the meeting point. Against the previous 0.7 / 400 the travel is noticeably longer
 * (~2.5× the settle time) and the single overshoot is smaller — the difference between an object
 * being *snapped* into place and one being *poured* into it. Anything stiffer than ~300 loses the
 * liquid tail entirely on a 120 Hz panel; anything below ~0.7 damping reads as loose rather than
 * physical.
 *
 * Exposed as two scalars rather than a pre-built `spring()` because the call sites need different
 * type arguments (`Dp`, `Float`, `IntSize`, `IntOffset`) and Kotlin cannot infer those through a
 * shared `AnimationSpec` value.
 */
const val AquamorphicDampingRatio = 0.8f
const val AquamorphicStiffness = 330f

/**
 * The spring every bottom sheet settles on, and therefore the curve the Dynamic-Island morph is
 * driven by — the morph reads `state.progress`, so this spec *is* the animation.
 *
 * Deliberately under-damped: a critically damped spring glides to a stop, which makes the player
 * look like it is being *lowered*. See [AquamorphicDampingRatio] for why these two numbers.
 * Identical to the spec the floating nav bar uses for its own A/B morph and sliding tab indicator,
 * so the whole bottom of the screen moves as one system.
 *
 * Expansion cannot visibly overshoot: `Animatable` is bounded by `expandedBound`, so the bounce
 * only ever appears on the way *down*, which is the direction the transition is about.
 */
val BottomSheetAnimationSpec = spring<Dp>(
	dampingRatio = AquamorphicDampingRatio,
	stiffness = AquamorphicStiffness
)

/**
 * Used for programmatic (tapped, not flung) transitions — `expandSoft` / `collapseSoft`. The same
 * curve as [BottomSheetAnimationSpec] on purpose: minimising by tapping the chevron and minimising
 * by flinging the sheet are the same gesture as far as the user is concerned, and they used to
 * settle on visibly different springs.
 */
val BottomSheetSoftAnimationSpec = spring<Dp>(
	dampingRatio = AquamorphicDampingRatio,
	stiffness = AquamorphicStiffness
)
