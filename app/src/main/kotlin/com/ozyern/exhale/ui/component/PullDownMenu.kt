/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.R

/**
 * A choice as iOS offers one: a pull-down menu that springs out of the row you touched, lists the
 * options with a check against the current one, and closes the moment one is picked.
 *
 * It replaces a centred Material dialog of radio buttons, which asked for the whole screen — a scrim,
 * a card in the middle, a column of circles — to change one setting. This stays next to the thing it
 * changes and takes only the room its options need.
 */
@Composable
fun <T> PullDownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) { visible.targetState = expanded }
    if (!visible.currentState && !visible.targetState) return

    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }
    var opensUp by remember { androidx.compose.runtime.mutableStateOf(false) }
    val position = remember(gap, edge) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                // Right edge on the row's right edge, just below it — or above, when there isn't room.
                val x = (anchorBounds.right - popupContentSize.width - edge).coerceIn(edge, (windowSize.width - popupContentSize.width - edge).coerceAtLeast(edge))
                val below = anchorBounds.bottom + gap
                opensUp = below + popupContentSize.height > windowSize.height - edge
                val y = if (opensUp) (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(edge) else below
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(120)) + scaleIn(
                initialScale = 0.72f,
                transformOrigin = TransformOrigin(1f, if (opensUp) 1f else 0f),
                animationSpec = spring(dampingRatio = 0.72f, stiffness = 620f),
            ),
            exit = fadeOut(tween(110)) + scaleOut(
                targetScale = 0.85f,
                transformOrigin = TransformOrigin(1f, if (opensUp) 1f else 0f),
                animationSpec = tween(130),
            ),
        ) {
            MenuPanel(options, selected, label) { value ->
                onSelect(value)
                onDismiss()
            }
        }
    }
}

@Composable
private fun <T> MenuPanel(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
) {
    // The app's own theme, which can differ from the system's.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // iOS menu geometry, not Material's.
    //
    // What was here read as a dialog that had lost its dialog: a 16dp panel as wide as the screen
    // allowed, rows of `bodyLarge` at 12dp padding, and a hairline between *every* option. A menu
    // on iOS has no rules between its items - they only separate sections - and its rows are 44pt
    // with a 17pt label, so five options take about the room three took here. The panel is also
    // narrow: it is sized by its longest label, not by the row that opened it.
    val shape = RoundedCornerShape(MenuCornerRadius)
    // Nearly opaque. iOS can afford a translucent menu because the system blurs everything under
    // it; a popup here gets no blur, so at 94% the settings rows underneath stayed legible through
    // the panel and the whole thing read as a rendering fault.
    val panel = if (dark) Color(0xFA17171A) else Color(0xFAFBFBFD)
    val rim = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)

    Column(
        Modifier
            .widthIn(min = 200.dp, max = 260.dp)
            // Sized by its longest option, like a menu, rather than always taking the maximum.
            .width(IntrinsicSize.Max)
            .shadow(
                elevation = 30.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.45f),
                spotColor = Color.Black.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(panel)
            // One pass of light across the top, so the panel sits above the page rather than on it.
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.05f else 0.25f),
                        Color.Transparent,
                    ),
                ),
            )
            .border(0.7.dp, rim, shape)
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEach { option ->
            MenuRow(label(option), option == selected) { onPick(option) }
        }
    }
}

/** iOS menus round at 13-14pt, not at the 16-20 a card uses. */
private val MenuCornerRadius = 14.dp

/** 44pt, the row height every iOS list and menu is built on. */
private val MenuRowHeight = 44.dp

@Composable
private fun MenuRow(text: String, checked: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MenuRowHeight)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.10f else 0f))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check leads, the way UIMenu shows a chosen item, so every label starts in the same
        // place whether or not anything is ticked.
        Box(Modifier.width(20.dp), contentAlignment = Alignment.CenterStart) {
            if (checked) {
                Icon(
                    painterResource(R.drawable.check),
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
