/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

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
    val shape = RoundedCornerShape(16.dp)
    val panel = if (dark) Color(0xF2242428) else Color(0xF5FBFBFD)
    val rim = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.06f)
    Column(
        Modifier
            .widthIn(min = 210.dp, max = 280.dp)
            .shadow(24.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(shape)
            .background(panel)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (dark) 0.04f else 0.25f), Color.Transparent)))
            .border(0.7.dp, rim, shape)
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dark) 0.10f else 0.08f))
            }
            MenuRow(label(option), option == selected) { onPick(option) }
        }
    }
}

@Composable
private fun MenuRow(text: String, checked: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.08f else 0f))
            .clickable(interactionSource = source, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The check sits in front, the way iOS's menus put it, so every label starts in the same place.
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) {
            if (checked) {
                Icon(painterResource(R.drawable.check), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
