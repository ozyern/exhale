/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The now-playing accessory, as one object in two places.
 *
 * The wide mini-player above the dock and the slim pill inside it are composed in different
 * subtrees — one is the player sheet's collapsed content, the other is a child of the bottom bar —
 * so when the bar changed state the first was removed and the second was added, and no amount of
 * fading makes a removal and an addition read as one thing moving. What the eye is offered is a
 * player that blinks out over here and blinks in over there.
 *
 * A shared element is the fix: both places claim the same key inside one [SharedTransitionScope],
 * Compose measures the two rectangles, and the accessory *flies* between them — the artwork,
 * the title and the controls carried across, shrinking into the bar as it goes. It is the same
 * mechanism Apple's tab-bar accessory uses, and the one Echo's dock uses.
 *
 * The scope is published here rather than passed down because the two ends are far apart in the
 * tree; whoever owns both (the Scaffold's bottom bar slot) provides it.
 */
val LocalNowPlayingSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

private const val NowPlayingAccessoryKey = "nowPlayingAccessory"

/**
 * Marks this composable as one end of that flight. [visibilityScope] is the enter/exit scope it
 * lives in — the `AnimatedVisibility` around the sheet's mini-player, or the bar's own
 * `AnimatedContent`. Without a scope, or outside a [SharedTransitionScope], it does nothing, so a
 * caller that is not part of the transition is unaffected.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.nowPlayingAccessory(visibilityScope: AnimatedVisibilityScope?): Modifier {
    val shared = LocalNowPlayingSharedScope.current ?: return this
    if (visibilityScope == null) return this
    return with(shared) {
        this@nowPlayingAccessory.sharedBounds(
            sharedContentState = rememberSharedContentState(NowPlayingAccessoryKey),
            animatedVisibilityScope = visibilityScope,
            // The content of the two ends differs — the wide one carries a progress line and a
            // heart the slim one has no room for — so they cross-fade while the bounds fly.
            enter = fadeIn(),
            exit = fadeOut(),
            // Laid out again at each size on the way, rather than scaled: text that scales
            // stretches, and the title is the thing you are most likely to be reading.
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.85f, stiffness = 380f) },
        )
    }
}
