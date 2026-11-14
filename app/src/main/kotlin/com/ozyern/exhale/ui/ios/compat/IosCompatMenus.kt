/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import androidx.compose.runtime.compositionLocalOf
import android.os.Build
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.ozyern.exhale.ui.component.backdrop.catalog.utils.InteractiveHighlight
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosMenuMaterial
import com.ozyern.exhale.ui.ios.foundation.iosMenuSurface
import com.ozyern.exhale.ui.ios.foundation.menuVibrancy
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.iosRowPress
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle

/** Applies the shared menu panel colors to Material's anchored exposed-dropdown behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBox(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.material3.ExposedDropdownMenuBoxScope.() -> Unit,
) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val typography = androidx.compose.material3.MaterialTheme.typography
    val shapes = androidx.compose.material3.MaterialTheme.shapes
    val panel = if (IosTheme.colors.isDark) IosMenuMaterial.DarkPanelFill else Color(0xFFF5F5F5)
    val menuScheme = scheme.copy(
        surfaceContainerLowest = panel,
        surfaceContainerLow = panel,
        surfaceContainer = panel,
        surfaceContainerHigh = panel,
        surfaceContainerHighest = panel,
    )
    androidx.compose.material3.MaterialTheme(
        colorScheme = menuScheme,
        typography = typography,
        shapes = shapes,
    ) {
        androidx.compose.material3.ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            modifier = modifier,
            content = content,
        )
    }
}

/**
 * Cupertino-styled popup behind Material's [DropdownMenu] API. It keeps Material's anchor,
 * scroll, and dismiss behavior while passing [CupertinoMenuScope] to its menu items.
 * [offset] is ignored because Cupertino positions the popup itself.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    properties: PopupProperties = PopupProperties(focusable = true),
    surfaceModifier: Modifier? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Stays composed through the exit, like IosContextMenuOverlay.
    val state = remember { MutableTransitionState(false) }
    state.targetState = expanded
    if (!state.currentState && !state.targetState) return

    val transition = rememberTransition(state, label = "dropdownMenu")
    // Same morph as the context menu (iOS 27 clip): grows out of the anchor, per axis,
    // blurred until it lands, on one spring.
    val progress = transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.78f, stiffness = 700f) else tween(150)
        },
        label = "morph",
    ) { open -> if (open) 1f else 0f }
    // Width leads, height follows, each a little elastic: the panel stretches open.
    val growX = transition.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.62f, stiffness = 560f) else tween(140) },
        label = "growX",
    ) { open -> if (open) 1f else 0f }
    val growY = transition.animateFloat(
        transitionSpec = { if (targetState) spring(dampingRatio = 0.78f, stiffness = 330f) else tween(140) },
        label = "growY",
    ) { open -> if (open) 1f else 0f }
    val density = LocalDensity.current
    val marginPx = with(density) { 12.dp.roundToPx() }
    // The popup window clips whatever it draws; the panel's 40dp shadow needs room
    // inside it, or it ends in a hard rectangle.
    val roomPx = with(density) { ShadowRoom.roundToPx() }
    val blurPx = with(density) { 20.dp.toPx() }
    // Anchor rect relative to the popup: written by the position provider, read by the layer.
    val anchorInPopup = remember { floatArrayOf(0f, 0f, 1f, 1f) }
    val provider = remember(marginPx, roomPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                // iOS 27 clip: the menu opens *over* its button — top edges level, trailing
                // edges aligned (leading when the button sits in the left half) — and is
                // pushed up only as far as needed to stay on screen.
                val panelW = popupContentSize.width - 2 * roomPx
                val panelH = popupContentSize.height - 2 * roomPx
                val rightHalf = anchorBounds.center.x > windowSize.width / 2
                val x = (if (rightHalf) anchorBounds.right - panelW else anchorBounds.left)
                    .coerceIn(marginPx, (windowSize.width - panelW - marginPx).coerceAtLeast(marginPx))
                val y = anchorBounds.top
                    .coerceAtMost(windowSize.height - panelH - marginPx)
                    .coerceAtLeast(marginPx)
                anchorInPopup[0] = (anchorBounds.center.x - x).toFloat()
                anchorInPopup[1] = (anchorBounds.center.y - y).toFloat()
                anchorInPopup[2] = anchorBounds.width.toFloat()
                anchorInPopup[3] = anchorBounds.height.toFloat()
                return IntOffset(x - roomPx, y - roomPx)
            }
        }
    }

    Popup(popupPositionProvider = provider, onDismissRequest = onDismissRequest, properties = properties) {
        val scope = rememberCoroutineScope()
        val bloom = remember(scope) {
            InteractiveHighlight(animationScope = scope, radiusScale = 0.6f, sheenAlpha = 0.06f, spotAlpha = 0.32f, releasePulse = true)
        }
        // Press-and-slide from the trigger: the finger never leaves the trigger's window,
        // so its screen position is hit-tested against this panel's rows here.
        val rows = remember { MenuRows() }
        val haptics = com.ozyern.exhale.ui.theme.rememberAppHaptics()
        val openedAtRelease = remember { MenuSlide.release }
        LaunchedEffect(rows) {
            snapshotFlow { MenuSlide.pointer }.collect { p ->
                val over = p?.let { rows.at(it) }
                if (over != null && over !== rows.hovered) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                rows.hovered = over
            }
        }
        LaunchedEffect(MenuSlide.release) {
            if (MenuSlide.release != openedAtRelease) {
                rows.hovered?.let { row -> if (row.enabled) row.onClick() }
                rows.hovered = null
            }
        }
        CompositionLocalProvider(LocalInIosMenu provides true, LocalMenuRows provides rows) {
            Box(Modifier.padding(ShadowRoom)) {
            Column(
                modifier = modifier
                    .graphicsLayer {
                        val p = progress.value
                        alpha = (p / 0.35f).coerceIn(0f, 1f)
                        val sx = (anchorInPopup[2] / size.width).coerceIn(0.08f, 1f)
                        val sy = (anchorInPopup[3] / size.height).coerceIn(0.08f, 1f)
                        scaleX = sx + (1f - sx) * growX.value
                        scaleY = sy + (1f - sy) * growY.value
                        transformOrigin = TransformOrigin(
                            (anchorInPopup[0] / size.width).coerceIn(0f, 1f),
                            (anchorInPopup[1] / size.height).coerceIn(0f, 1f),
                        )
                        val r = if (transition.targetState) blurPx * (1f - minOf(growX.value, growY.value)).coerceIn(0f, 1f) else 0f
                        if (r > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = BlurEffect(r, r)
                        }
                    }
                    .width(IosMenuMaterial.Width)
                    .then(surfaceModifier ?: Modifier.iosMenuSurface(IosMenuMaterial.PanelShape, bloom))
                    .menuVibrancy()
                    .padding(vertical = 10.dp),
            ) { content() }
            }
        }
    }
}

private val ShadowRoom = 44.dp

/**
 * The live press on a menu trigger ([menuTrigger]): where the finger is, in screen pixels,
 * and a counter that ticks when it lifts. One press at a time, so one global is enough.
 */
internal object MenuSlide {
    var pointer by mutableStateOf<Offset?>(null)
    var release by mutableIntStateOf(0)
}

/** The rows of the open panel, for [MenuSlide] hit-testing. */
private class MenuRows {
    val rows = mutableListOf<MenuRowHandle>()
    var hovered by mutableStateOf<MenuRowHandle?>(null)

    fun at(screen: Offset): MenuRowHandle? = rows.firstOrNull { row ->
        val c = row.coords ?: return@firstOrNull false
        if (!c.isAttached) return@firstOrNull false
        val tl = c.localToScreen(Offset.Zero)
        Rect(tl, Size(c.size.width.toFloat(), c.size.height.toFloat())).contains(screen)
    }
}

private class MenuRowHandle {
    var coords: LayoutCoordinates? = null
    var onClick: () -> Unit = {}
    var enabled: Boolean = true
}

private val LocalMenuRows = compositionLocalOf<MenuRows?> { null }

/**
 * Makes this the trigger of a [DropdownMenu], iOS-style: the menu opens on touch-down,
 * and the finger can slide straight onto a row and lift to choose it — no second tap.
 * A plain tap just opens the menu.
 */
fun Modifier.menuTrigger(onOpen: () -> Unit): Modifier = composed {
    val open by rememberUpdatedState(onOpen)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                open()
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull() ?: break
                    val c = coords[0]
                    if (c != null && c.isAttached) MenuSlide.pointer = c.localToScreen(change.position)
                    change.consume()
                    if (!change.pressed) break
                }
                MenuSlide.release++
                MenuSlide.pointer = null
            }
        }
}

/**
 * True inside a [DropdownMenu] panel. [DropdownMenuItem] then draws as an iOS menu row;
 * outside one — a few call sites reuse it as a plain list row — it draws a list row.
 */
private val LocalInIosMenu = compositionLocalOf { false }

/**
 * One row of an iOS context menu: label left, glyph right, hairline above every row but
 * the first. iOS separates menu rows with a line; Material separates them with nothing.
 */
@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    val colors = IosTheme.colors
    val contentColor = if (enabled) colors.label else colors.tertiaryLabel

    if (LocalInIosMenu.current) {
        // Highlight the active row and add a trailing glow for touch feedback.
        val menuColors = IosMenuMaterial.colors
        val rowColor = if (enabled) menuColors.label else menuColors.secondaryLabel
        val scope = rememberCoroutineScope()
        val glow = remember(scope) { InteractiveHighlight(animationScope = scope, spotAlpha = 0.2f) }
        val rowStyle = IosTheme.typography.body.copy(fontSize = 17.sp, lineHeight = 20.sp, letterSpacing = (-0.43).sp)
        // Registered for press-and-slide selection from the trigger.
        val registry = LocalMenuRows.current
        val handle = remember { MenuRowHandle() }
        handle.onClick = onClick
        handle.enabled = enabled
        if (registry != null) {
            DisposableEffect(registry) {
                registry.rows += handle
                onDispose { registry.rows -= handle }
            }
        }
        val slidOver = registry?.hovered === handle
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .onGloballyPositioned { handle.coords = it }
                .clip(CircleShape)
                .background(if (slidOver) menuColors.highlight else Color.Transparent)
                .iosRowPress(enabled = enabled, onClick = onClick)
                .then(glow.modifier)
                .then(glow.gestureModifier)
                .defaultMinSize(minHeight = 44.dp)
                .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalIosContentColor provides rowColor,
                LocalIosTextStyle provides rowStyle,
                LocalContentColor provides rowColor,
            ) {
                leadingIcon?.let {
                    Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(
                            com.ozyern.exhale.ui.component.LocalSymbolChrome provides com.ozyern.exhale.ui.utils.MenuItemChrome,
                            com.ozyern.exhale.ui.component.LocalSymbolIconSize provides 22.dp,
                        ) { it() }
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f)) { ProvideTextStyle(rowStyle) { text() } }
                trailingIcon?.let {
                    Spacer(Modifier.width(8.dp))
                    CompositionLocalProvider(
                        com.ozyern.exhale.ui.component.LocalSymbolChrome provides com.ozyern.exhale.ui.utils.MenuItemChrome,
                        com.ozyern.exhale.ui.component.LocalSymbolIconSize provides 22.dp,
                    ) { it() }
                }
            }
        }
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .iosRowPress(enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides contentColor,
            LocalIosTextStyle provides IosTheme.typography.body,
            LocalContentColor provides contentColor,
        ) {
            leadingIcon?.let {
                it()
                Spacer(Modifier.width(12.dp))
            }
            Box(Modifier.weight(1f)) {
                ProvideTextStyle(IosTheme.typography.body) { text() }
            }
            trailingIcon?.let {
                Spacer(Modifier.width(12.dp))
                it()
            }
        }
    }
}

/** SwiftUI's `Divider()` inside a menu — a full-bleed hairline. */
@Composable
fun IosMenuDivider() {
    if (LocalInIosMenu.current) {
        Box(Modifier.fillMaxWidth().height(21.dp).padding(horizontal = IosMenuMaterial.SeparatorInset), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(IosMenuMaterial.colors.separator))
        }
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .height(IosMetrics.SeparatorThickness)
                .background(IosTheme.colors.separator)
        )
    }
}

/**
 * M3's ListItem call shape, drawn as an iOS list row: 44pt minimum, body/footnote type,
 * secondary-label supporting text, and no tonal elevation.
 */
@Composable
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: (@Composable () -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    colors: androidx.compose.material3.ListItemColors? = null,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    shadowElevation: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val colors = IosTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = IosMetrics.ListRowHeight)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent?.let {
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalContentColor provides colors.label,
            ) { it() }
            Spacer(Modifier.width(12.dp))
        }

        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            overlineContent?.let {
                CompositionLocalProvider(
                    LocalIosContentColor provides colors.secondaryLabel,
                    LocalIosTextStyle provides IosTheme.typography.caption,
                    LocalContentColor provides colors.secondaryLabel,
                ) { ProvideTextStyle(IosTheme.typography.caption, it) }
            }
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalIosTextStyle provides IosTheme.typography.body,
                LocalContentColor provides colors.label,
            ) { ProvideTextStyle(IosTheme.typography.body, headlineContent) }
            supportingContent?.let {
                CompositionLocalProvider(
                    LocalIosContentColor provides colors.secondaryLabel,
                    LocalIosTextStyle provides IosTheme.typography.footnote,
                    LocalContentColor provides colors.secondaryLabel,
                ) { ProvideTextStyle(IosTheme.typography.footnote, it) }
            }
        }

        trailingContent?.let {
            Spacer(Modifier.width(8.dp))
            CompositionLocalProvider(
                LocalIosContentColor provides colors.secondaryLabel,
                LocalContentColor provides colors.secondaryLabel,
            ) { it() }
        }
    }
}
