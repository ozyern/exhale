/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.foldingdock.foldingDockGlass
import com.ozyern.exhale.ui.screens.Screens

/**
 * True in a window wide enough for Apple Music's large-screen chrome: an unfolded foldable on its
 * side, or a tablet. There the tabs leave the bottom of the screen for a rail down the right-hand
 * edge and the bottom carries only the player, as a transport bar.
 *
 * Both terms count. A foldable opened in portrait is tall and only ~670dp wide, and Apple Music
 * keeps the phone's dock there; a phone on its side is wide but not large, and its 400dp of
 * height has no room for a rail of five tabs.
 */
val LocalWideChrome = staticCompositionLocalOf { false }

@Composable
fun isWideChromeWindow(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.smallestScreenWidthDp >= 600 && configuration.screenWidthDp >= 800
}

/** What the rail takes from the right of a page: its 56dp capsule and the air either side of it. */
val WideRailReserve: Dp = 56.dp + 12.dp * 2

private val RailItemSize = 52.dp

/**
 * The tab rail of the wide layout: one capsule of the dock's glass on the right-hand edge, a glyph
 * per tab with Search last, the page you are on in the accent colour, as iPadOS draws its tab bar
 * when it stands it on end.
 */
@Composable
fun WideTabRail(
    items: List<Screens>,
    isSelected: (Screens) -> Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ordered = remember(items) { items.sortedBy { it.route == Screens.Search.route } }
    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    Column(
        modifier = modifier
            .width(56.dp)
            .clip(shape)
            .foldingDockGlass(shape)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ordered.forEach { screen ->
            val here = isSelected(screen)
            // A small lift on the tab you land on, so a tap reads as having taken.
            val lift by animateFloatAsState(
                targetValue = if (here) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "railLift",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(RailItemSize)
                    .clip(CircleShape)
                    .semantics { selected = here }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onItemClick(screen, here)
                    },
            ) {
                Icon(
                    painter = painterResource(if (here) screen.iconIdActive else screen.iconIdInactive),
                    contentDescription = stringResource(screen.titleId),
                    tint = if (here) accent else ink.copy(alpha = 0.82f),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            val s = 1f + 0.08f * lift
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
    }
}

/**
 * True while the rail carries the way back. The pages' own back discs stand aside then, as Apple
 * Music's do on a large screen: back is the first button at the top of the rail, not a second one
 * floating over the page.
 */
val LocalRailBack = compositionLocalOf { false }

/**
 * The top of the rail: back, and share where the page is something that can be shared. Back goes
 * through the activity's back dispatcher, so a page's own steps back (closing its search,
 * leaving a selection) still come first.
 */
@Composable
fun WideRailActions(
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    val shape = RoundedCornerShape(percent = 50)
    Column(
        modifier = modifier
            .width(56.dp)
            .clip(shape)
            .foldingDockGlass(shape)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RailActionButton(
            icon = com.ozyern.exhale.R.drawable.chevron_back,
            label = stringResource(com.ozyern.exhale.R.string.back),
            ink = ink,
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBack()
        }
        if (onShare != null) {
            RailActionButton(
                icon = com.ozyern.exhale.R.drawable.share,
                label = stringResource(com.ozyern.exhale.R.string.share),
                ink = ink,
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun RailActionButton(icon: Int, label: String, ink: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(RailItemSize)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = ink.copy(alpha = 0.9f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A web link for the page on [route], when the page is one YouTube Music can open. */
fun railShareLink(route: String?, arguments: android.os.Bundle?): String? {
    if (route == null || arguments == null) return null
    return when {
        route.startsWith("album/") -> arguments.getString("albumId")?.let { "https://music.youtube.com/browse/$it" }
        route.startsWith("online_playlist/") ->
            arguments.getString("playlistId")?.removePrefix("VL")?.let { "https://music.youtube.com/playlist?list=$it" }
        route == "artist/{artistId}" -> arguments.getString("artistId")?.let { "https://music.youtube.com/channel/$it" }
        else -> null
    }
}
