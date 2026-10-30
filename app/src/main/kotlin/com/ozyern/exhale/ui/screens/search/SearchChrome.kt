/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.runtime.compositionLocalOf

/**
 * What the Search tab needs from the app around it to draw the iOS music app's header itself: whether
 * the field sits at the top (under the title) or at the foot (in the dock), how to start typing,
 * and how to open the account sheet from the avatar beside the title.
 */
data class SearchChrome(
    val fieldAtTop: Boolean,
    val openSearch: () -> Unit,
    val openAccount: () -> Unit,
    /** The gear beside the account picture, when Settings > Appearance turns it on. */
    val openSettings: () -> Unit = {},
)

val LocalSearchChrome = compositionLocalOf<SearchChrome?> { null }
