/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.flow.flowOf

/**
 * The hinge, in window pixels, while a foldable stands half open on its side like a laptop —
 * "tabletop" — or null in every other posture. Apple Music splits the player on the fold there:
 * the words on the half that stands up, the controls on the half lying on the table.
 */
@Composable
fun rememberTabletopHinge(): Rect? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val layoutInfo by remember(activity) {
        activity?.let { WindowInfoTracker.getOrCreate(it).windowLayoutInfo(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val fold = layoutInfo?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull {
            it.state == FoldingFeature.State.HALF_OPENED &&
                it.orientation == FoldingFeature.Orientation.HORIZONTAL
        }
    return fold?.bounds?.toComposeRect()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
