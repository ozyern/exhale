package com.ozyern.exhale

import androidx.navigation.NavHostController

/** Navigates to a tab while preserving its back stack, or directly to another screen. */
internal fun NavHostController.navigateToAppRoute(route: String) {
    if (AppRoutePolicy.isTabRoot(route)) {
        navigate(route) {
            popUpTo(graph.startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route)
    }
}
