/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.ozyern.exhale.ui.screens.Screens

/**
 * How one page gives way to another.
 *
 * Tabs swap outright: the bar is the navigation and carries its own movement, so a fade between
 * two of them only reads as a stutter. A page opened from a tile grows out of that tile as a card,
 * the way ColorOS opens a widget into its app (see ContainerTransform), and shrinks back into it.
 * Every other page crossfades, quickly, so the app answers the tap rather than performing at it.
 */
object PageMotion {
    const val FadeMillis = 180

    private val tabRoutes: Set<String> by lazy { Screens.MainRoutes + Screens.Explore.route }

    private fun isTabSwap(from: String?, to: String?) = from in tabRoutes && to in tabRoutes

    /** A tile was tapped to open [route]: the page grows out of it as a card (ContainerTransform). */
    private fun grows(route: String?) = isMorphRoute(route) && isArtworkMorph()

    fun enter(from: String?, to: String?): EnterTransition = when {
        isTabSwap(from, to) -> EnterTransition.None
        // The card brings the page in itself.
        grows(to) -> EnterTransition.None
        else -> fadeIn(tween(FadeMillis))
    }

    fun exit(from: String?, to: String?): ExitTransition = when {
        isTabSwap(from, to) -> ExitTransition.None
        // The page the card grows over dims behind it.
        grows(to) -> fadeOut(tween(ContainerMillis), targetAlpha = ContainerDim)
        else -> fadeOut(tween(FadeMillis))
    }

    fun popEnter(from: String?, to: String?): EnterTransition = when {
        isTabSwap(from, to) -> EnterTransition.None
        grows(from) -> fadeIn(tween(ContainerMillis), initialAlpha = ContainerDim)
        else -> fadeIn(tween(FadeMillis))
    }

    fun popExit(from: String?, to: String?): ExitTransition = when {
        isTabSwap(from, to) -> ExitTransition.None
        // Held on screen for the card's trip home; the card draws the page as it shrinks.
        grows(from) -> fadeOut(tween(ContainerMillis), targetAlpha = 0.999f)
        else -> fadeOut(tween(FadeMillis))
    }

    /** How dark the page under a growing card gets; the blur behind it does the rest (ContainerTransform). */
    private const val ContainerDim = 0.6f
}
