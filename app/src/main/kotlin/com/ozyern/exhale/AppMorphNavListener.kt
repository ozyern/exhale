/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.ozyern.exhale.ui.component.prewarmHeroTint
import com.ozyern.exhale.ui.player.prewarmMeshPalette
import com.ozyern.exhale.ui.utils.markMorphDirection
import com.ozyern.exhale.ui.utils.markSharedArtworkSource
import com.ozyern.exhale.ui.utils.isMorphRoute
import com.ozyern.exhale.ui.utils.morphArtworkId

/** Sets the shared artwork source and preloads its colors when navigation changes. */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun InstallMorphNavListener(navController: NavHostController, artworkState: com.ozyern.exhale.ui.utils.NavigationArtworkState) {
    val context = LocalContext.current
    DisposableEffect(navController, artworkState) {
        var previousRoute: String? = null
        // The entries seen, bottom to top. currentBackStack lags a change behind inside this
        // listener (home -> search read 0 -> 2, and a back press read 4 -> 5), so a pop was taken
        // for a push and the page flew in instead of home: a return to an entry already on our
        // stack is the pop.
        val seenEntries = ArrayList<String>()
        @SuppressLint("RestrictedApi")
        val listener = NavController.OnDestinationChangedListener { controller, destination, args ->
            val entryId = controller.currentBackStackEntry?.id
            val seenAt = entryId?.let(seenEntries::indexOf) ?: -1
            val isPop = seenAt >= 0 && seenAt < seenEntries.lastIndex
            if (seenAt >= 0) {
                while (seenEntries.size > seenAt + 1) seenEntries.removeAt(seenEntries.lastIndex)
            } else if (entryId != null) {
                seenEntries.add(entryId)
            }
            markMorphDirection(isPop)

            val artworkId = if (isPop && isMorphRoute(previousRoute)) {
                artworkState.selected?.id ?: args.morphArtworkId()
            } else {
                args.morphArtworkId()
            }
            val thumbnailUrl = markSharedArtworkSource(artworkState, artworkId, isPop)
            // The way back shrinks the page into the tile it was opened from (ContainerReturn).
            if (isPop && isMorphRoute(previousRoute)) {
                com.ozyern.exhale.ui.utils.ContainerReturn.closed()
            } else if (!isPop && isMorphRoute(destination.route)) {
                com.ozyern.exhale.ui.utils.ContainerReturn.opened(
                    artworkState.selected?.owner?.let { artworkState.tileBounds[it] },
                )
            }
            previousRoute = destination.route
            val collection = destination.route?.let { it.startsWith("album/") || it.contains("playlist/") } == true
            if (!collection) {
                prewarmHeroTint(context, thumbnailUrl)
                prewarmMeshPalette(context, thumbnailUrl)
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
