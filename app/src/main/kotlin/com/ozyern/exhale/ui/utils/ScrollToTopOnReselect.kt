/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history and NOTICE for contributors
 */

package com.ozyern.exhale.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

/**
 * Glides a tab's root page back to its top when its tab is tapped again. The navigation bar raises
 * the `scrollToTop` flag on the page's back-stack entry (see AppRootContent's onNavItemClick);
 * this answers it and lowers it again.
 */
@Composable
fun ScrollToTopOnReselect(navController: NavController, scrollToTop: suspend () -> Unit) {
    val entry = navController.currentBackStackEntry ?: return
    val flag by entry.savedStateHandle.getStateFlow(SCROLL_TO_TOP, false).collectAsStateWithLifecycle()
    val scroll by rememberUpdatedState(scrollToTop)
    LaunchedEffect(flag) {
        if (!flag) return@LaunchedEffect
        scroll()
        entry.savedStateHandle[SCROLL_TO_TOP] = false
    }
}

private const val SCROLL_TO_TOP = "scrollToTop"
