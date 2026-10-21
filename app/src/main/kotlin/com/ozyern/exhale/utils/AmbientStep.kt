/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.os.SystemClock
import kotlinx.coroutines.delay

/** How often the slow ambient motion (backgrounds, glows, the metal ring) takes a step. */
const val AmbientStepMs = 33L

/**
 * Waits for the next step of the shared ambient grid: the next multiple of [AmbientStepMs] on the
 * uptime clock. Each drifting surface stepping on its own 30ms timer landed on a different vsync
 * from the others, so three of them at "30 a second" redrew the window close to a hundred times a
 * second. On one grid they all move on the same frame.
 */
suspend fun awaitAmbientStep() {
    delay(AmbientStepMs - SystemClock.uptimeMillis() % AmbientStepMs)
}
