/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/*
 * The hero cover is never quite still, as on Exhale master's player: a slow breath of zoom and a
 * drift across the picture while the song plays, and paused, it sinks back and dims.
 */

/** How far the cover dims when paused. */
internal const val COVER_PAUSE_DIM = 0.24f

/**
 * Seconds of drift, advancing only while [playing] and the player is [settled]. Speed eases in and
 * out over about a second, so a pause slows the cover to a stop rather than freezing it, and the
 * clock sleeps when nothing moves instead of drawing a still cover every frame.
 *
 * Held while the sheet moves: the cover is the backdrop the glass controls refract, and a cover
 * drifting under a moving sheet re-blurred all of them on every frame of the open and close.
 */
@Composable
internal fun rememberCoverDrift(playing: Boolean, settled: Boolean): State<Float> {
    val drift = remember { mutableFloatStateOf(0f) }
    val running by rememberUpdatedState(playing && settled)
    val moving by rememberUpdatedState(!settled)
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        var speed = 0f
        while (isActive) {
            // 0.02 of full speed is already still.
            if (moving || (!running && speed < 0.02f)) {
                speed = 0f
                snapshotFlow { running }.first { it }
                last = System.nanoTime()
            }
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                speed += ((if (running) 1f else 0f) - speed) * (1f - exp(-dt / 1.2f))
                drift.floatValue = (drift.floatValue + dt * speed) % 100_000f
            }
        }
    }
    return drift
}

/**
 * The drift applied to a layer: up to 8.5% of zoom and a small pan, scaled by [live] (0 holds the
 * cover exactly edge to edge, for the morphs that fly it in and out of the header and the dock).
 */
internal fun GraphicsLayerScope.coverDrift(t: Float, live: Float) {
    if (live <= 0f) return
    val zoom = 1f + live * (0.05f + 0.035f * sin(t * 0.21f))
    scaleX *= zoom
    scaleY *= zoom
    translationX += live * size.width * 0.018f * sin(t * 0.13f)
    translationY += live * size.height * 0.014f * cos(t * 0.17f)
    // Above centre, where a face usually is, so the breath reads as the picture and not its frame.
    transformOrigin = TransformOrigin(0.5f, 0.42f)
}
