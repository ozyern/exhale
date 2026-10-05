/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.first

/**
 * Suspends for as long as no screen of the app is showing, and returns at once when one is.
 *
 * Compose stops handing out frames when the app goes to the background, so anything driven by
 * `withFrameNanos` — the glass, the animations — already rests. Loops driven by `delay` do not: a
 * screen polling the player position every few hundred milliseconds keeps doing it with the phone
 * in a pocket. Those call this at the top of each pass, and sleep with the app instead.
 */
suspend fun awaitAppVisible() {
    val lifecycle = ProcessLifecycleOwner.get().lifecycle
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.STARTED) }
}

/** Whether a screen of the app is showing right now. */
val isAppVisible: Boolean
    get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
