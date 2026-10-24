/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import kotlin.math.exp

/**
 * The artwork curve: the shape of a critically damped spring released from rest, run over a fixed
 * time. It leaves quickly and spends most of its length settling, so a page or a cover arriving on
 * it reads as landing rather than being slid into place.
 */
object ArtworkMotion {
    const val DurationMillis = 350
    private const val Endpoint = 9f
    private val normalization = 1f / (1f - (1f + Endpoint) * exp(-Endpoint))

    fun progress(fraction: Float): Float {
        if (!fraction.isFinite()) return 1f
        if (fraction <= 0f) return 0f
        if (fraction >= 1f) return 1f
        val position = Endpoint * fraction
        return (1f - (1f + position) * exp(-position)) * normalization
    }

    val Easing: Easing = Easing(::progress)

    fun <T> spec(): FiniteAnimationSpec<T> = tween(DurationMillis, easing = Easing)

    /** Pages that open out of a piece of artwork: a release, a playlist, an artist. */
    fun isArtworkPage(route: String?): Boolean = route != null && (
        route.startsWith("album/") || route.startsWith("artist/") ||
            route.startsWith("online_playlist/") || route.startsWith("local_playlist/") ||
            route.startsWith("auto_playlist/") || route.startsWith("youtube_browse/")
        )
}
