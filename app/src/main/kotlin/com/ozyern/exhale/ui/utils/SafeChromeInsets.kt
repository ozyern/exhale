/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth

/**
 * The horizontal inset every piece of *floating* chrome must clear — the mini-player pill, the
 * frosted nav bar, the search bar, and the Dynamic-Island morph target they share.
 *
 * ### Why `systemBars ∪ displayCutout`
 *
 * `systemBars` alone is not the unsafe region on the skins this app targets. OnePlus and Oppo
 * (OxygenOS 16 / ColorOS 16) render a live "Fluid Cloud" capsule around the camera cutout, and
 * `displayCutout` is the only inset that reports a *side* cutout at all — which is what a device
 * held in landscape actually has. Chrome that respects only the system bars will happily slide
 * underneath it.
 *
 * ### Why one symmetric value instead of a left/right pair
 *
 * All of this chrome is centred, and — decisively — the morph target handed to `BottomSheet` is a
 * *single symmetric* `pillHorizontalInset`. The morph clips the collapsing player to
 * `[inset, width - inset]`. Feed that a value derived from one edge while the pill it is becoming
 * is positioned from the other, and on any device with an asymmetric inset (landscape, nav bar on
 * one side) the player shrinks into a rectangle offset from the pill and the hand-off visibly
 * jumps. Taking the wider edge and applying it to both keeps the two provably identical, at the
 * cost of a few dp of padding on the safe side — invisible next to a morph that lands wrong.
 *
 * Callers must apply this as plain symmetric `padding`, **not** `windowInsetsPadding`, which would
 * reintroduce the per-edge asymmetry this exists to avoid.
 *
 * ### Tablets and foldables
 *
 * On a wide window the inset also centres the chrome at [ChromeMaxWidth]: a dock or a mini player
 * stretched across 1000dp+ puts its tabs a hand-span apart and reads as a toolbar, not a dock.
 * Doing it here rather than with `widthIn` on each piece keeps the dock, the pill and the player's
 * morph target on one rectangle, the same way the symmetric cutout inset does.
 */
@Composable
fun safeHorizontalChromeInset(): Dp {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val windowWidth = LocalConfiguration.current.screenWidthDp.dp
    val safe = with(density) {
        maxOf(
            insets.getLeft(density, layoutDirection),
            insets.getRight(density, layoutDirection),
        ).toDp()
    }
    return maxOf(safe, (windowWidth - ChromeMaxWidth) / 2)
}

/** The widest the floating chrome (dock, mini player) grows; wider windows centre it. */
val ChromeMaxWidth = 560.dp

/**
 * A page of rows at a readable measure: the full height, at most [ReadableListMaxWidth] wide and
 * centred. On a phone it is the whole width, as before; on a tablet or an unfolded foldable it
 * stops a list of songs becoming a row a screen wide with the duration a hand-span from the title.
 */
fun androidx.compose.ui.Modifier.readableListWidth(): androidx.compose.ui.Modifier = this
    .fillMaxSize()
    .wrapContentWidth()
    .widthIn(max = ReadableListMaxWidth)
    .fillMaxWidth()

/** The widest a page of rows grows; wider windows centre it. */
val ReadableListMaxWidth = 900.dp
