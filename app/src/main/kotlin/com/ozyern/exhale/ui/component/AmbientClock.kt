/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.ozyern.exhale.utils.awaitAmbientStep
import com.ozyern.exhale.utils.awaitAppVisible
import com.ozyern.exhale.utils.isAppVisible
import kotlinx.coroutines.flow.first

@Composable
fun rememberAmbientSeconds(running: Boolean = true): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val isRunning by rememberUpdatedState(running)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            if (!isRunning || !isAppVisible) {
                snapshotFlow { isRunning }.first { it }
                awaitAppVisible()
                last = 0L
            }
            withFrameNanos { now ->
                if (last != 0L) seconds.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
            }
            awaitAmbientStep()
        }
    }
    return seconds
}

@Composable
fun rememberAmbientPhase(cycleMs: Int, running: Boolean = true): State<Float> {
    val seconds = rememberAmbientSeconds(running)
    val cycle = cycleMs / 1000f
    return remember(cycle) { derivedStateOf { (seconds.value / cycle) % 1f } }
}
