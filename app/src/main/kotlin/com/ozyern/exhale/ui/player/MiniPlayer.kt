/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.ozyern.exhale.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.constants.LiquidGlassNavBarKey
import com.ozyern.exhale.constants.CompactMiniPlayerHeight
import com.ozyern.exhale.constants.CompactMiniPlayerPillCornerRadius
import com.ozyern.exhale.constants.MiniPlayerHeight
import com.ozyern.exhale.constants.MiniPlayerPillCornerRadius
import com.ozyern.exhale.constants.SwipeSensitivityKey
import com.ozyern.exhale.ui.component.BottomSheetState
import com.ozyern.exhale.ui.component.MiniPlayerGlassBlurRadius
import com.ozyern.exhale.ui.component.MiniPlayerGlassExtraTint
import com.ozyern.exhale.ui.component.rememberChromeGlassModifier
import com.ozyern.exhale.utils.rememberPreference
import kotlin.math.roundToInt


/**
 * @param compact lay the pill out at [CompactMiniPlayerHeight] inside the standard slot, for the
 *   surfaces whose bottom row is the slim search bar rather than the 64dp dock.
 */
@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    navController: NavController,
    state: BottomSheetState,
    compact: Boolean = false,
) {
    NewMiniPlayer(
        position = position,
        duration = duration,
        modifier = modifier,
        pureBlack = pureBlack,
        navController = navController,
        state = state,
        compact = compact,
    )
}

@Composable
private fun NewMiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    navController: NavController,
    state: BottomSheetState,
    compact: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeThumbnail by rememberPreference(com.ozyern.exhale.constants.SwipeThumbnailKey, true)
    val liquidGlass by rememberPreference(LiquidGlassNavBarKey, defaultValue = true)

    SwipeableMiniPlayerBox(
        modifier = modifier,
        swipeSensitivity = swipeSensitivity,
        swipeThumbnail = swipeThumbnail,
        playerConnection = playerConnection,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        pureBlack = pureBlack,
        useLegacyBackground = false,
        compact = compact,
    ) { offsetX ->
        // Shared with BottomSheet's Dynamic-Island morph target — the full player shrinks into
        // exactly this pill, so the two must read the same constants.
        val shape = RoundedCornerShape(
            if (compact) CompactMiniPlayerPillCornerRadius else MiniPlayerPillCornerRadius,
        )
        val barModifier = Modifier
            .fillMaxWidth()
            .height(if (compact) CompactMiniPlayerHeight else MiniPlayerHeight)
            .offset { IntOffset(offsetX.roundToInt(), 0) }

        val content: @Composable () -> Unit = {
            NewMiniPlayerContent(
                pureBlack = pureBlack,
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                compact = compact,
            )
        }

        // AMOLED black keeps the glass: the pill is a lens onto the live artwork, not a surface.
        if (liquidGlass) {
            // The SAME glass as the dock, from the same helper, with the same numbers — not a
            // second frosted material that happens to sit next to it.
            //
            // This used to be `GlassSurface`, the Haze-backed panel the in-content cards use. It
            // is a different renderer with a different tint, a different blur radius and a
            // different fallback, so the pill and the dock 8dp below it were two visibly
            // different densities of glass stacked on each other — one milkier, one greyer.
            // Nothing about the pill is in-content: like the dock, it is composed in the
            // Scaffold's bottomBar slot, a sibling drawn *over* the NavHost, so consuming
            // `LocalAppBackdrop` here is safe for exactly the reason it is safe there and is not
            // the re-entrant layer draw that in-content glass would be.
            Box(
                modifier = barModifier
                    .then(
                        rememberChromeGlassModifier(
                            shape = shape,
                            dark = isSystemInDarkTheme(),
                            // Verbatim from `LiquidGlassBottomBar.frostedGlassModifier`. Copied
                            // rather than shared because that one is private to the bar; if
                            // either moves, they must move together.
                            // Frosted past the dock's strength on purpose: the song title sits on
                            // this pane, and at the dock's blur a page heading scrolling underneath
                            // stayed readable and collided with it.
                            tintAlpha = (if (isSystemInDarkTheme()) 0.30f else 0.26f) + MiniPlayerGlassExtraTint,
                            blurRadius = MiniPlayerGlassBlurRadius,
                            quality = 0.5f,
                        ),
                    )
                    // `drawBackdrop` paints the pane; it does not clip what is drawn on top of
                    // it. Without this the progress hairline runs straight out past the capsule's
                    // rounded ends.
                    .clip(shape),
            ) {
                content()
            }
        } else {
            Box(
                modifier = barModifier
                    .clip(shape)
                    .background(color = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                content()
            }
        }
    }
}