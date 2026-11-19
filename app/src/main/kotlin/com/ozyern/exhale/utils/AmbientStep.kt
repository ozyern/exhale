/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

const val AmbientStepMs = 33L

suspend fun awaitAmbientStep() {
    if (AmbientHold.held) AmbientHold.awaitRelease()
    delay(AmbientStepMs - SystemClock.uptimeMillis() % AmbientStepMs)
}

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
