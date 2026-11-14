/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName", "UNUSED_PARAMETER")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import zone.ien.hig.CupertinoModalBottomSheet
import zone.ien.hig.CupertinoSheetState
import zone.ien.hig.PresentationDetent
import zone.ien.hig.PresentationStyle
import zone.ien.hig.rememberCupertinoSheetState
import zone.ien.hig.CupertinoScaffold
import zone.ien.hig.CupertinoSegmentedControl
import zone.ien.hig.CupertinoSegmentedControlTab
import com.ozyern.exhale.ui.component.PresentingSheetEffect
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle

/**
 * The Scaffold call shape. The layout is kept — slots, insets and nested scroll are not
 * Material-specific — and only its colours move to the iOS palette.
 */
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: androidx.compose.material3.FabPosition =
        androidx.compose.material3.FabPosition.End,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    contentWindowInsets: WindowInsets = WindowInsets(0, 0, 0, 0),
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = IosTheme.colors
    CupertinoScaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        // Cupertino places the fab itself and takes no position; iOS has no fab to place,
        // and the one caller that asks for Center gets End, which is where the app's
        // floating controls already sit.
        containerColor = if (containerColor.isSpecified) containerColor else colors.systemGroupedBackground,
        contentColor = if (contentColor.isSpecified) contentColor else colors.label,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/**
 * iOS has no ripple. A press dims or highlights the thing pressed; it never spreads a
 * circle out from the finger, so this resolves to no indication at all.
 */
@Suppress("UnusedReceiverParameter")
fun ripple(
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
): Indication? = null

@Composable
fun rememberIosModalSheetState(skipPartiallyExpanded: Boolean = false): CupertinoSheetState =
    rememberCupertinoSheetState(
        presentationStyle = PresentationStyle.Modal(
            detents = if (skipPartiallyExpanded) {
                setOf(PresentationDetent.Large)
            } else {
                setOf(PresentationDetent.Medium, PresentationDetent.Large)
            },
        ),
    )

@Composable
fun ModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: CupertinoSheetState = rememberIosModalSheetState(),
    sheetMaxWidth: Dp = 640.dp,
    shape: Shape? = null,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    scrimColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    dragHandle: (@Composable () -> Unit)? = { com.ozyern.exhale.ui.component.IosGrabber() },
    contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets(0, 0, 0, 0) },
    properties: DialogProperties = DialogProperties(
        usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    PresentingSheetEffect()
    val colors = IosTheme.colors
    val scope = rememberCoroutineScope()
    Dialog(
        onDismissRequest = { scope.launch { sheetState.hide() } },
        properties = properties,
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        // The presenter draws its own animated scrim.
        SideEffect { dialogWindow?.setDimAmount(0f) }
        CupertinoModalBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            sheetState = sheetState,
            sheetMaxWidth = sheetMaxWidth,
            shape = shape ?: ContinuousRoundedRectangle(topStart = 28.dp, topEnd = 28.dp),
            containerColor = if (containerColor.isSpecified) containerColor else colors.secondarySystemGroupedBackground,
            contentColor = if (contentColor.isSpecified) contentColor else colors.label,
            scrimColor = if (scrimColor.isSpecified) scrimColor else Color.Black.copy(alpha = 0.12f),
            dragHandle = dragHandle,
        ) {
            Column(Modifier.windowInsetsPadding(contentWindowInsets())) { content() }
        }
    }
}

/** An unstyled dialog container, kept unstyled — the iOS card is the caller's job. */
@Composable
fun BasicAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) = Dialog(onDismissRequest = onDismissRequest, properties = properties) {
    Box(modifier) { content() }
}

/** iOS shows a small dark capsule, not a Material tooltip plate. */
@Composable
fun PlainTooltip(
    modifier: Modifier = Modifier,
    caretSize: androidx.compose.ui.geometry.Size? = null,
    shape: Shape? = null,
    contentColor: Color = Color.Unspecified,
    containerColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    Box(
        modifier
            .clip(shape ?: ContinuousRoundedRectangle(10.dp))
            .background(
                if (containerColor.isSpecified) containerColor else colors.label.copy(alpha = 0.92f)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides
                (if (contentColor.isSpecified) contentColor else colors.systemBackground),
            LocalIosTextStyle provides IosTheme.typography.footnote,
        ) { ProvideTextStyle(IosTheme.typography.footnote, content) }
    }
}

/**
 * Cupertino's segmented control, driven from M3's call shape.
 *
 * Cupertino wants the selected index up front to place its sliding indicator; M3's segments
 * each carry their own `selected` flag instead. The row therefore hands its children a slot
 * to report into: each [SegmentedButton] takes the next index as it composes and, if it is
 * the selected one, writes that index back. The write happens in a [SideEffect] because
 * writing state during composition is what a recomposition loop is made of — so the
 * indicator settles one frame after a selection change rather than in the same frame.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SingleChoiceSegmentedButtonRow(
    modifier: Modifier = Modifier,
    space: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val selectedIndex = remember { mutableIntStateOf(0) }
    val cursor = remember { SegmentCursor() }
    cursor.next = 0

    CupertinoSegmentedControl(
        selectedTabIndex = selectedIndex.intValue,
        modifier = modifier,
    ) {
        CompositionLocalProvider(
            LocalSegmentCursor provides cursor,
            LocalSelectedSegment provides selectedIndex,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

/** Hands each segment its position in the row as the row composes. */
private class SegmentCursor {
    var next: Int = 0
}

private val LocalSegmentCursor = compositionLocalOf<SegmentCursor?> { null }
private val LocalSelectedSegment = compositionLocalOf<MutableIntState?> { null }

@Composable
fun RowScope.SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    icon: (@Composable () -> Unit)? = null,
    label: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit = {},
) {
    val colors = IosTheme.colors
    val cursor = LocalSegmentCursor.current
    val selectedSlot = LocalSelectedSegment.current
    val index = remember(cursor) { cursor?.next ?: 0 }
    if (cursor != null) cursor.next = index + 1
    if (selected && selectedSlot != null) {
        SideEffect { selectedSlot.intValue = index }
    }

    CupertinoSegmentedControlTab(
        onClick = onClick,
        isSelected = selected,
        modifier = modifier.weight(1f),
    ) {
        Row(
            modifier = Modifier.defaultMinSize(minHeight = 30.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(
                LocalIosContentColor provides colors.label,
                LocalContentColor provides colors.label,
                LocalIosTextStyle provides IosTheme.typography.subheadline,
            ) {
                ProvideTextStyle(IosTheme.typography.subheadline) {
                    icon?.invoke()
                    label?.invoke()
                    content()
                }
            }
        }
    }
}

/** Every segment of an iOS control is the same capsule, so the index does not matter. */
object SegmentedButtonDefaults {
    fun itemShape(index: Int, count: Int, baseShape: Shape? = null): Shape = ContinuousCapsule()
}

/** iOS has no FAB; the nearest thing is a floating capsule button on the tint. */
@Composable
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
) {
    val fill = if (containerColor.isSpecified) containerColor else IosTheme.tint
    val content = if (contentColor.isSpecified) contentColor else IosTheme.colors.onAccent

    Row(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(fill)
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 50.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides content,
            LocalContentColor provides content,
            LocalIosTextStyle provides IosTheme.typography.headline,
        ) {
            icon()
            if (expanded) ProvideTextStyle(IosTheme.typography.headline, text)
        }
    }
}

/** A circular icon toggle: filled with the tint when on, a plain fill when off. */
@Composable
fun FilledIconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape? = null,
    colors: androidx.compose.material3.IconToggleButtonColors? = null,
    content: @Composable () -> Unit,
) {
    val iosColors = IosTheme.colors
    val fill = if (checked) IosTheme.tint else iosColors.secondarySystemFill
    val tint = if (checked) iosColors.onAccent else IosTheme.tint

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape ?: ContinuousCapsule())
            .background(fill)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalIosContentColor provides tint,
            LocalContentColor provides tint,
        ) { content() }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) = CircularProgressIndicator(modifier = modifier, color = color)

@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
) = LinearProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)

@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    stroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    trackStroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    gapSize: Dp = 0.dp,
    stopSize: Dp = 0.dp,
    amplitude: ((Float) -> Float)? = null,
    wavelength: Dp = 0.dp,
    waveSpeed: Dp = 0.dp,
) {
    val tint = if (color.isSpecified) color else IosTheme.tint
    val track = if (trackColor.isSpecified) trackColor else IosTheme.colors.systemGray5
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(ContinuousCapsule())
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress().coerceIn(0f, 1f))
                .height(4.dp)
                .clip(ContinuousCapsule())
                .background(tint)
        )
    }
}
