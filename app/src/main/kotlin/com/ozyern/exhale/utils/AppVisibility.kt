/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

/**
 * Master's "wait until a screen of the app is showing", for the code ported from it (the intros,
 * Espresso, the lyric styles). This build has no process-lifecycle dependency, so it returns at
 * once: the loops that call it are frame-driven, and Compose already stops handing out frames
 * while the app is in the background.
 */
suspend fun awaitAppVisible() = Unit

/** Whether a screen of the app is showing right now; see [awaitAppVisible]. */
val isAppVisible: Boolean get() = true
