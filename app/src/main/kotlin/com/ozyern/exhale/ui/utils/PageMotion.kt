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
 * How one page gives way to another, as the reference player does it: tabs swap outright, and
 * every other page crossfades, quickly.
 *
 * A tab is not a place you travel to: the bar is the navigation and carries its own movement, so a
 * fade between two of them only reads as a stutter. A pushed page is a real change of context and
 * gets the fade, short enough that the app answers the tap rather than performing at it. The cover
 * flight and the sideways push this replaces took up to half a second, and the cover changed shape
 * on the way (square tile into a tall, full-bleed hero), which no timing made clean.
 */
object PageMotion {
    const val FadeMillis = 180

    private val tabRoutes: Set<String> by lazy { Screens.MainRoutes + Screens.Explore.route }

    private fun isTabSwap(from: String?, to: String?) = from in tabRoutes && to in tabRoutes

    fun enter(from: String?, to: String?): EnterTransition =
        if (isTabSwap(from, to)) EnterTransition.None else fadeIn(tween(FadeMillis))

    fun exit(from: String?, to: String?): ExitTransition =
        if (isTabSwap(from, to)) ExitTransition.None else fadeOut(tween(FadeMillis))
}
