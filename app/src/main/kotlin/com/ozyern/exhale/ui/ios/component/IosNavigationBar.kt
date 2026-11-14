/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosGlassSpec
import com.ozyern.exhale.ui.ios.foundation.IosGlassStyle
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import com.ozyern.exhale.ui.ios.foundation.iosGlass

/**
 * The iOS 26 navigation bar: a large title that shrinks into an inline one as the
 * content scrolls under it, with the bar's own background staying clear until it does.
 *
 * Toolbar buttons do not sit on a bar — they float in their own glass capsules, and a
 * [IosToolbarSpacer] between two of them splits the group into separate capsules.
 *
 * @param collapsedFraction 0 = large title, 1 = inline. Drive it from a list state
 *   with [rememberIosNavigationBarCollapse].
 */
@Composable
fun IosNavigationBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedFraction: Float = 0f,
    navigationIcon: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = IosTheme.colors
    val collapse by animateFloatAsState(
        targetValue = collapsedFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 1f, stiffness = 500f),
        label = "IosNavCollapse",
    )

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { navigationIcon?.invoke(this) }

            // The inline title only exists once the large one has scrolled away.
            Box(
                Modifier.graphicsLayer { alpha = collapse },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IosText(title, style = IosTheme.typography.headline)
                    if (subtitle != null) {
                        IosText(
                            text = subtitle,
                            color = colors.secondaryLabel,
                            style = IosTheme.typography.caption,
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { actions?.invoke(this) }
        }

        if (collapse < 1f) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        alpha = 1f - collapse
                        // The large title slides up as it fades, rather than just
                        // disappearing — what makes the iOS collapse read as one move.
                        translationY = -collapse * 12.dp.toPx()
                    }
            ) {
                IosText(title, style = IosTheme.typography.largeTitle)
                if (subtitle != null) {
                    IosText(
                        text = subtitle,
                        color = colors.secondaryLabel,
                        style = IosTheme.typography.subheadline,
                    )
                }
            }
        }
    }
}

/**
 * A group of toolbar buttons riding in one glass capsule — SwiftUI's ToolbarItemGroup
 * as iOS 26 draws it.
 */
@Composable
fun IosToolbarGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .iosGlass(IosCapsule, IosGlassStyle.Regular, IosGlassSpec.Chrome)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides IosTheme.colors.label,
            LocalIosTextStyle provides IosTheme.typography.body,
        ) { content() }
    }
}

/** SwiftUI's ToolbarSpacer: the gap that breaks one capsule into two. */
@Composable
fun IosToolbarSpacer() = Box(Modifier.padding(horizontal = 3.dp))

/**
 * Drives [IosNavigationBar]'s collapse from a list: the large title is gone once the
 * content has scrolled roughly its own height.
 */
@Composable
fun rememberIosNavigationBarCollapse(state: LazyListState, distance: Float = 120f): Float {
    val fraction by remember(state, distance) {
        derivedStateOf {
            if (state.firstVisibleItemIndex > 0) 1f
            else (state.firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
        }
    }
    return fraction
}
