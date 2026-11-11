/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/** How often the slow ambient motion (backgrounds, glows, the metal ring) takes a step. */
const val AmbientStepMs = 33L

/**
 * Waits for the next step of the shared ambient grid: the next multiple of [AmbientStepMs] on the
 * uptime clock. Each drifting surface stepping on its own 30ms timer landed on a different vsync
 * from the others, so three of them at "30 a second" redrew the window close to a hundred times a
 * second. On one grid they all move on the same frame.
 */
suspend fun awaitAmbientStep() {
    if (AmbientHold.held) AmbientHold.awaitRelease()
    delay(AmbientStepMs - SystemClock.uptimeMillis() % AmbientStepMs)
}

/**
 * Holds every ambient animation still while something that must not drop frames is on screen.
 *
 * The lyrics are that thing. Their panel is the same code as the reference player's, where it is
 * smooth, and here it stuttered: there it is drawn over a still backdrop, and here the ground
 * under it is a moving field. Each step of that field redraws the whole screen behind the lyrics,
 * the blurred cover laid over it, and every glass control that samples the two, thirty times a
 * second, in the same frames the lyric lines are sweeping, blurring and scrolling in. The words
 * are what is being watched; a ground that drifts a pixel a frame behind them is not, so it
 * waits until they are gone.
 */
object AmbientHold {
    private val holders = kotlinx.coroutines.flow.MutableStateFlow(0)

    val held: Boolean get() = holders.value > 0

    fun acquire() {
        holders.value += 1
    }

    fun release() {
        holders.value = (holders.value - 1).coerceAtLeast(0)
    }

    suspend fun awaitRelease() {
        holders.first { it == 0 }
    }
}
