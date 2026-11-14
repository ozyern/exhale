/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosMenuColors
import com.ozyern.exhale.ui.ios.foundation.IosMenuMaterial
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import com.ozyern.exhale.ui.ios.foundation.iosMenuSurface
import com.ozyern.exhale.ui.ios.foundation.menuVibrancy

/** One entry of an [IosMenu]. */
@Immutable
data class IosMenuItem(
    val title: String,
    /** The second, dimmer line iOS 26 menus can carry under the title. */
    val description: String? = null,
    val icon: (@Composable (Color) -> Unit)? = null,
    val role: IosButtonRole? = null,
    /** Draws a leading checkmark, the way a Picker inside a Menu marks its selection. */
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/** A group of items with a separator band above it, and an optional small header. */
@Immutable
data class IosMenuSection(
    val items: List<IosMenuItem>,
    val header: String? = null,
)

/**
 * An iOS-style context menu with optional quick actions.
 *
 * While a finger is down anywhere on the panel, one bright bloom follows it
 * across the whole panel and the panel leans toward it -- no per-row highlight; lifting
 * the finger picks the row under it. So a press can slide
 * across rows and choose on release, exactly as on iOS — a plain tap is just the
 * zero-length case.
 */
@Composable
fun IosMenu(
    sections: List<IosMenuSection>,
    modifier: Modifier = Modifier,
    quickActions: List<IosMenuItem> = emptyList(),
    onDismissRequest: () -> Unit = {},
) {
    val menuColors = IosMenuMaterial.colors
    // Window px through the current density: the V2 player rescales dp, where
    // screenHeightDp overshot and the panel ran off the bottom in landscape.
    val maximumHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        (androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() - 24.dp).coerceAtLeast(48.dp)
    }
    val haptic = com.ozyern.exhale.ui.theme.rememberAppHaptics()
    val scope = rememberCoroutineScope()
    // Bloom radius ~ 0.6 of the panel width, far brighter than a button's sheen.
    val bloom = remember(scope) {
        InteractiveHighlight(animationScope = scope, radiusScale = 0.6f, sheenAlpha = 0.06f, spotAlpha = 0.32f, releasePulse = true)
    }

    // Every pickable entry, in hit-test order: quick actions first, then rows.
    val entries = remember(sections, quickActions) { quickActions + sections.flatMap { it.items } }
    // Coordinates, not rects: resolved against the panel at hit time, so nesting
    // (quick actions sit inside a Row) and the panel's own padding need no bookkeeping.
    val entryCoords = remember(entries) { arrayOfNulls<LayoutCoordinates>(entries.size) }
    val panelCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var hovered by remember(entries) { mutableIntStateOf(-1) }

    fun hit(position: Offset): Int {
        val panel = panelCoords[0]?.takeIf { it.isAttached } ?: return -1
        return entryCoords.indexOfFirst { c ->
            c != null && c.isAttached && panel.localBoundingBoxOf(c, clipBounds = false).contains(position)
        }.takeIf { it >= 0 && entries[it].enabled } ?: -1
    }

    Column(
        modifier = modifier
            .width(IosMenuMaterial.Width)
            .heightIn(max = maximumHeight)
            .iosMenuSurface(IosMenuMaterial.PanelShape, bloom)
            .menuVibrancy()
            .onGloballyPositioned { panelCoords[0] = it }
            .pointerInput(entries) {
                // Observe only: the bloom/stretch watches the same stream.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var scrolled = false
                    hovered = hit(down.position)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (change.isConsumed && change.position != change.previousPosition) scrolled = true
                        if (!change.pressed) break
                        val now = hit(change.position)
                        if (now != hovered) {
                            if (now >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hovered = now
                        }
                    }
                    val picked = hovered
                    hovered = -1
                    if (picked >= 0 && !scrolled) {
                        entries[picked].onClick()
                        onDismissRequest()
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp),
    ) {
        var index = 0
        if (quickActions.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 10.dp),
            ) {
                quickActions.forEach { action ->
                    val i = index++
                    IosMenuQuickAction(
                        item = action,
                        menuColors = menuColors,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { entryCoords[i] = it },
                    )
                }
            }
        }
        sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0 || quickActions.isNotEmpty()) IosMenuSeparator(menuColors)

            if (section.header != null) {
                Box(Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 4.dp)) {
                    IosText(
                        text = section.header,
                        color = menuColors.secondaryLabel,
                        style = IosTheme.typography.footnote,
                    )
                }
            }

            // A picker section (any row selectable) keeps a leading checkmark column.
            val checkColumn = section.items.any { it.selected }
            section.items.forEach { item ->
                val i = index++
                IosMenuRow(
                    item = item,
                    checkColumn = checkColumn,
                    menuColors = menuColors,
                    modifier = Modifier.onGloballyPositioned { entryCoords[i] = it },
                )
            }
        }
    }
}

@Composable
private fun IosMenuSeparator(menuColors: IosMenuColors) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(21.dp)
            .padding(horizontal = IosMenuMaterial.SeparatorInset),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(menuColors.separator),
        )
    }
}

@Composable
private fun IosMenuQuickAction(
    item: IosMenuItem,
    menuColors: IosMenuColors,
    modifier: Modifier = Modifier,
) {
    val color = if (item.enabled) menuColors.label else menuColors.secondaryLabel
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
            item.icon?.invoke(color)
        }
        IosText(
            text = item.title,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = IosTheme.typography.caption.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun IosMenuRow(
    item: IosMenuItem,
    checkColumn: Boolean,
    menuColors: IosMenuColors,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    val contentColor = when {
        !item.enabled -> menuColors.secondaryLabel
        item.role == IosButtonRole.Destructive -> colors.systemRed
        else -> menuColors.label
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .defaultMinSize(minHeight = 44.dp)
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkColumn) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (item.selected) IosCheckmark(color = contentColor)
            }
            Spacer(Modifier.width(6.dp))
        }
        if (item.icon != null) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                item.icon.invoke(contentColor)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            IosText(
                text = item.title,
                color = contentColor,
                style = IosTheme.typography.body.copy(
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.43).sp,
                ),
            )
            if (item.description != null) {
                IosText(
                    text = item.description,
                    color = menuColors.secondaryLabel,
                    style = IosTheme.typography.footnote,
                )
            }
        }
    }
}

/**
 * Anchors a menu to [content], opening it below on tap — SwiftUI's Menu.
 */
@Composable
fun IosMenuAnchor(
    sections: List<IosMenuSection>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier) {
        Box(Modifier.clickable { onExpandedChange(true) }) { content() }

        if (expanded) {
            Popup(
                onDismissRequest = { onExpandedChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                CompositionLocalProvider(
                    LocalIosTextStyle provides IosTheme.typography.body,
                    LocalIosContentColor provides IosMenuMaterial.colors.label,
                ) {
                    IosMenu(sections, onDismissRequest = { onExpandedChange(false) })
                }
            }
        }
    }
}

/** The checkmark iOS draws next to a selected menu row. */
@Composable
fun IosCheckmark(modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val stroke = if (color == Color.Unspecified) IosTheme.tint else color
    androidx.compose.foundation.Canvas(modifier.size(width = 16.dp, height = 12.dp)) {
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height * 0.55f)
                lineTo(size.width * 0.35f, size.height)
                lineTo(size.width, 0f)
            },
            color = stroke,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
    }
}
