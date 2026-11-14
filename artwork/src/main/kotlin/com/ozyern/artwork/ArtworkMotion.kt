package com.ozyern.artwork

import kotlin.math.exp

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
}
