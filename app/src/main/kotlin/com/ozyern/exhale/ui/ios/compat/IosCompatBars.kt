/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName", "UNUSED_PARAMETER")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import zone.ien.hig.CupertinoTopAppBar
import zone.ien.hig.CupertinoTopAppBarDefaults
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle

/**
 * The TopAppBar call shape, drawn as the iOS inline navigation bar: 44pt tall, title
 * centred in headline weight, back chevron and actions flanking it.
 *
 * The bar is clear until content slides under it, then its fill and hairline fade in —
 * iOS never draws a bar over empty space. The [scrollBehavior] is only read for that
 * fraction; Material's own collapsing (and its elevation ladder) is not reproduced,
 * because iOS collapses a *large title* into this bar rather than shrinking the bar.
 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets? = null,
    colors: androidx.compose.material3.TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    expandedHeight: Dp = 44.dp,
    subtitle: (@Composable () -> Unit)? = null,
) {
    // The library derives the bar fill and hairline from its backdrop and scroll state.
    CupertinoTopAppBar(
        title = { ProvideTextStyle(IosTheme.typography.headline, title) },
        subtitle = subtitle?.let { { ProvideTextStyle(IosTheme.typography.caption, it) } },
        modifier = modifier.fillMaxWidth(),
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets ?: WindowInsets(0, 0, 0, 0),
        colors = CupertinoTopAppBarDefaults.topAppBarColors(
            navigationIconContentColor = IosTheme.tint,
            actionIconContentColor = IosTheme.tint,
        ),
    )
}

/** The large-title variant collapses into the same inline bar once content scrolls up. */
@Composable
fun LargeTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets? = null,
    colors: androidx.compose.material3.TopAppBarColors? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val iosColors = IosTheme.colors
    val collapse by remember(scrollBehavior) {
        derivedStateOf { scrollBehavior?.state?.collapsedFraction?.coerceIn(0f, 1f) ?: 0f }
    }

    Column(modifier) {
        TopAppBar(
            title = { if (collapse > 0.5f) title() },
            navigationIcon = navigationIcon,
            actions = actions,
            windowInsets = windowInsets,
            scrollBehavior = scrollBehavior,
        )
        if (collapse < 1f) {
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .graphicsLayer {
                        alpha = 1f - collapse
                        translationY = -collapse * 12.dp.toPx()
                    }
            ) {
                CompositionLocalProvider(
                    LocalIosContentColor provides iosColors.label,
                    LocalIosTextStyle provides IosTheme.typography.largeTitle,
                    LocalContentColor provides iosColors.label,
                ) { ProvideTextStyle(IosTheme.typography.largeTitle, title) }
            }
        }
    }
}

/** An iOS grouped card: flat fill, continuous corners, no elevation and no ripple. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.CardColors? = null,
    elevation: androidx.compose.material3.CardElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fill = colors?.containerColor?.takeIf { it.isSpecified }
        ?: IosTheme.colors.secondarySystemGroupedBackground
    val resolved = shape ?: ContinuousRoundedRectangle(IosMetrics.GroupedCornerRadius)
    Column(
        modifier
            .clip(resolved)
            .background(fill)
            .then(if (border != null) Modifier.border(border, resolved) else Modifier)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides (
                colors?.contentColor?.takeIf { it.isSpecified } ?: IosTheme.colors.label
            ),
            content = { content() },
        )
    }
}

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.CardColors? = null,
    elevation: androidx.compose.material3.CardElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = Card(
    modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    content = content,
)

/**
 * The FilterChip call shape as an iOS filter capsule: tinted fill when selected, a plain
 * fill when not. No Material outline, no leading check box.
 */
@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.SelectableChipColors? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
) {
    val iosColors = IosTheme.colors
    val fill = if (selected) IosTheme.tint else iosColors.secondarySystemFill
    val content = if (selected) iosColors.onAccent else iosColors.label

    Row(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(fill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides content,
            LocalContentColor provides content,
        ) {
            leadingIcon?.invoke()
            ProvideTextStyle(IosTheme.typography.subheadline, label)
            trailingIcon?.invoke()
        }
    }
}

/**
 * The ToggleButton call shape as an iOS segmented-style capsule: filled when on, a plain
 * fill when off.
 */
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: Any? = null,
    colors: androidx.compose.material3.ToggleButtonColors? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val iosColors = IosTheme.colors
    val fill = when {
        checked -> IosTheme.tint
        else -> iosColors.secondarySystemGroupedBackground
    }
    val content0 = if (checked) iosColors.onAccent else IosTheme.tint

    Row(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(fill)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides content0,
            LocalIosTextStyle provides IosTheme.typography.subheadline,
            LocalContentColor provides content0,
        ) { content() }
    }
}

/**
 * The snackbar host, drawn as the iOS toast: a dark capsule floating over the content,
 * not a squared Material bar pinned to the bottom edge.
 *
 * The state stays Material's — every `showSnackbar` call site keeps working — because
 * the queueing behaviour is fine; only the shape of the thing was wrong.
 */
@Composable
fun SnackbarHost(
    hostState: androidx.compose.material3.SnackbarHostState,
    modifier: Modifier = Modifier,
    snackbar: @Composable (androidx.compose.material3.SnackbarData) -> Unit = { IosToast(it) },
) = androidx.compose.material3.SnackbarHost(hostState, modifier, snackbar)

@Composable
fun IosToast(data: androidx.compose.material3.SnackbarData) {
    val iosColors = IosTheme.colors
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(ContinuousCapsule())
            .background(iosColors.label.copy(alpha = 0.92f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = data.visuals.message,
            color = iosColors.systemBackground,
            style = IosTheme.typography.subheadline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false),
        )
        data.visuals.actionLabel?.let { action ->
            Text(
                text = action,
                color = IosTheme.tint,
                style = IosTheme.typography.headline,
                modifier = Modifier.clickable { data.performAction() },
            )
        }
    }
}

/** Material's expressive loading indicator has no iOS twin; it draws as the iOS ring. */
@Composable
fun ContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    indicatorColor: Color = Color.Unspecified,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(ContinuousCapsule())
            .background(
                if (containerColor.isSpecified) containerColor
                else IosTheme.colors.secondarySystemFill
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = indicatorColor)
    }
}
