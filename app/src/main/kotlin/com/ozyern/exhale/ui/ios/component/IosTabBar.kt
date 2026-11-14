/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosGlassSpec
import com.ozyern.exhale.ui.ios.foundation.IosGlassStyle
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import com.ozyern.exhale.ui.ios.foundation.iosGlass

/** SwiftUI's TabRole. A Search tab breaks out into its own trailing capsule. */
enum class IosTabRole { Standard, Search }

/** One entry of an [IosTabBar]. */
@Immutable
data class IosTab(
    val title: String,
    val icon: @Composable (Color) -> Unit,
    val role: IosTabRole = IosTabRole.Standard,
    /** SwiftUI's .badge(_:). Null hides it; an empty string draws a bare dot. */
    val badge: String? = null,
)

/** SwiftUI's tabBarMinimizeBehavior(_:). */
enum class IosTabBarMinimizeBehavior { Never, OnScrollDown, OnScrollUp }

/**
 * Tracks how far the tab bar has minimised, 0 = full, 1 = collapsed.
 *
 * Hand [nestedScrollConnection] to the scrollable the bar floats over.
 */
@Immutable
class IosTabBarState internal constructor(
    private val behavior: IosTabBarMinimizeBehavior,
) {
    internal var minimizeProgress by mutableFloatStateOf(0f)
        private set

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (behavior == IosTabBarMinimizeBehavior.Never) return Offset.Zero
            val scrollingDown = available.y < 0f
            val shouldMinimize = when (behavior) {
                IosTabBarMinimizeBehavior.OnScrollDown -> scrollingDown
                IosTabBarMinimizeBehavior.OnScrollUp -> !scrollingDown
                IosTabBarMinimizeBehavior.Never -> false
            }
            // A single gesture direction settles the bar; iOS does not track it
            // proportionally either, it snaps once the direction is unambiguous.
            if (kotlin.math.abs(available.y) > 1f) {
                minimizeProgress = if (shouldMinimize) 1f else 0f
            }
            return Offset.Zero
        }
    }
}

@Composable
fun rememberIosTabBarState(
    behavior: IosTabBarMinimizeBehavior = IosTabBarMinimizeBehavior.Never,
): IosTabBarState = remember(behavior) { IosTabBarState(behavior) }

/**
 * The iOS 26 floating tab bar: a glass capsule that hugs its items rather than
 * spanning the window, with the selected item wearing a filled pill.
 *
 * A [IosTabRole.Search] tab is lifted out into its own trailing capsule, which is
 * what iOS 26 does with `role: .search`.
 *
 * @param accessory SwiftUI's tabViewBottomAccessory — a second capsule that floats
 *   directly above the bar. Exhale uses it for the mini player.
 */
@Composable
fun IosTabBar(
    tabs: List<IosTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: IosTabBarState = rememberIosTabBarState(),
    accessory: (@Composable () -> Unit)? = null,
) {
    val minimize by animateFloatAsState(
        targetValue = state.minimizeProgress,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "IosTabBarMinimize",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                // Minimising slides the bar down behind the home indicator and fades
                // it, leaving the accessory in place — the iOS 26 behaviour.
                translationY = minimize * 40.dp.toPx()
                alpha = 1f - 0.35f * minimize
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (accessory != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .iosGlass(IosCapsule, IosGlassStyle.Regular, IosGlassSpec.Chrome),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalIosTextStyle provides IosTheme.typography.body,
                    LocalIosContentColor provides IosTheme.colors.label,
                ) { accessory() }
            }
        }

        val standard = tabs.filter { it.role == IosTabRole.Standard }
        val search = tabs.firstOrNull { it.role == IosTabRole.Search }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .iosGlass(IosCapsule, IosGlassStyle.Regular, IosGlassSpec.Chrome)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                standard.forEach { tab ->
                    val index = tabs.indexOf(tab)
                    TabBarItem(
                        tab = tab,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                    )
                }
            }

            if (search != null) {
                val index = tabs.indexOf(search)
                Box(
                    modifier = Modifier
                        .iosGlass(IosCapsule, IosGlassStyle.Regular, IosGlassSpec.Chrome)
                        .padding(4.dp),
                ) {
                    TabBarItem(
                        tab = search,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        labelVisible = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBarItem(
    tab: IosTab,
    selected: Boolean,
    onClick: () -> Unit,
    labelVisible: Boolean = true,
) {
    val colors = IosTheme.colors
    val contentColor = if (selected) IosTheme.tint else colors.label
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .clip(IosCapsule)
            .background(if (selected) colors.quaternarySystemFill else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .widthIn(min = 56.dp)
            .defaultMinSize(minHeight = 46.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                tab.icon(contentColor)
            }
            if (tab.badge != null) {
                IosBadge(tab.badge, Modifier.padding(start = 10.dp))
            }
        }
        if (labelVisible) {
            IosText(
                text = tab.title,
                color = contentColor,
                style = IosTheme.typography.caption2,
            )
        }
    }
}

/** The red count bubble SwiftUI's .badge(_:) draws. An empty label draws a dot. */
@Composable
fun IosBadge(label: String, modifier: Modifier = Modifier) {
    val colors = IosTheme.colors
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = if (label.isEmpty()) 8.dp else 16.dp, minHeight = if (label.isEmpty()) 8.dp else 16.dp)
            .clip(IosCapsule)
            .background(colors.systemRed)
            .padding(horizontal = if (label.isEmpty()) 0.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) {
            IosText(label, color = Color.White, style = IosTheme.typography.caption2)
        }
    }
}
