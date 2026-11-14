/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.utils.MenuItemChrome
import com.ozyern.exhale.ui.utils.MenuIconChrome
import com.ozyern.exhale.ui.utils.additiveChrome
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.backdrop.catalog.utils.InteractiveHighlight
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.ProvideTextStyle
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosMenuMaterial
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.iosMenuSurface
import com.ozyern.exhale.ui.ios.foundation.menuVibrancy
import com.ozyern.exhale.ui.ios.foundation.iosRowPress
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle

/** Corner radius shared by grouped menu cards. */
val MenuGroupCornerRadius = 32.dp

/**
 * An iOS inset-grouped action list: one continuous-cornered card whose rows are split
 * by hairline separators inset to the text column.
 *
 * This replaced a stack of individually rounded Material cards — iOS never breaks a
 * group into separate cards, and the per-row rounding was the loudest Material tell in
 * every menu sheet.
 */
@Composable
fun IosMenuGroup(
    items: List<IosMenuItemData>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    if (LocalInMenuPanel.current) {
        FlatMenuGroup(items, modifier)
        return
    }
    val colors = IosTheme.colors

    // One-shot elastic entrance: the group rises 28dp on a bouncy spring,
    // draw-phase only, no recomposition per frame.
    val enterOffset = remember { Animatable(28f) }
    val enterAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { enterOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        launch { enterAlpha.animateTo(1f, tween(180)) }
    }

    val stretchScope = rememberCoroutineScope()
    val bloom = remember(stretchScope) {
        InteractiveHighlight(animationScope = stretchScope, radiusScale = 0.6f, sheenAlpha = 0.06f, spotAlpha = 0.32f, releasePulse = true)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = enterOffset.value
                alpha = enterAlpha.value
            }
            .iosMenuSurface(ContinuousRoundedRectangle(MenuGroupCornerRadius), bloom, frosted = false)
            .menuVibrancy()
            .animateContentSize()
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .height(IosMetrics.SeparatorThickness)
                        .background(IosMenuMaterial.colors.separator)
                )
            }
            IosMenuItemRow(item)
        }
    }
}

/**
 * A group inside the menu panel: no card of its own — iOS menu items sit straight on the
 * menu — just a separator band above it and hairlines between rows.
 */
@Composable
private fun FlatMenuGroup(items: List<IosMenuItemData>, modifier: Modifier) {
    val separator = IosMenuMaterial.colors.separator
    Column(modifier.fillMaxWidth()) {
        // Existing group spacing plus the hairline creates a 21dp separator band.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .height(1.dp)
                .background(separator),
        )
        items.forEach { item -> IosMenuItemRow(item) }
    }
}

@Composable
private fun IosMenuItemRow(item: IosMenuItemData) {
    val colors = IosTheme.colors
    // A destructive row colours its label as well as its icon — that, not a red fill,
    // is how iOS marks "Delete" apart from the rows above it.
    val contentColor = if (item.tint.isSpecified) item.tint else IosMenuMaterial.colors.label
    // Finger-tracking press glow over the flat iOS fill.
    val glowScope = rememberCoroutineScope()
    val glow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .iosRowPress(onClick = item.onClick)
            .then(glow.modifier)
            .then(glow.gestureModifier)
            .defaultMinSize(minHeight = 44.dp)
            .padding(start = 8.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterStart) {
                // Quieter than the label: the additive chrome lifts it back up.
                val iconColor = if (item.tint.isSpecified) contentColor else contentColor
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalSymbolChrome provides MenuItemChrome,
                    LocalSymbolIconSize provides 22.dp,
                ) {
                    ProvideIosContent(iconColor, IosTheme.typography.body) { icon() }
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ProvideIosContent(
                contentColor,
                IosTheme.typography.body.copy(
                    fontSize = 17.sp,
                    letterSpacing = (-0.43).sp,
                )
            ) { item.title() }
            item.description?.let { desc ->
                ProvideIosContent(colors.secondaryLabel, IosTheme.typography.footnote) { desc() }
            }
        }

        item.trailingContent?.let { trailing ->
            Spacer(Modifier.width(6.dp))
            ProvideIosContent(colors.secondaryLabel, IosTheme.typography.body) { trailing() }
        }
    }
}

/**
 * Pushes a colour and text style into both the iOS locals and the Material ones, so a
 * row's slot content lands the same whether it was written with [com.ozyern.exhale.ui.ios.component.IosText]
 * or a still-Material `Text`.
 */
@Composable
private fun ProvideIosContent(
    color: Color,
    style: androidx.compose.ui.text.TextStyle,
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalIosContentColor provides color,
        LocalIosTextStyle provides style,
        com.ozyern.exhale.ui.ios.compat.LocalContentColor provides color,
    ) {
        ProvideTextStyle(style, content)
    }
}

/** One row of an [IosMenuGroup]. */
data class IosMenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    /** Label + icon colour. Unspecified reads as the normal label colour. */
    val tint: Color = Color.Unspecified,
    val trailingContent: (@Composable () -> Unit)? = null,
)
