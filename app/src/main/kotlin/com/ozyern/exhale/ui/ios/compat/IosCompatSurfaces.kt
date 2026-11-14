/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName")

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.component.IosButtonRole
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import androidx.compose.foundation.layout.PaddingValues
import zone.ien.hig.CupertinoBorderedTextField
import zone.ien.hig.CupertinoSearchTextField
import zone.ien.hig.CupertinoSlider
import zone.ien.hig.rememberCupertinoSearchTextFieldState
import zone.ien.hig.CupertinoSurface
import zone.ien.hig.CupertinoSwitch
import com.ozyern.exhale.ui.ios.foundation.IosTheme

/*
 * The container half of the compat layer: surfaces, alerts, fields and the two
 * controls whose Material versions are unmistakable on sight.
 */

/**
 * M3's Surface without the elevation tint ladder — iOS surfaces are flat fills, so
 * `tonalElevation` and `shadowElevation` are accepted and ignored rather than turning
 * the card progressively purple.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val colors = IosTheme.colors
    CupertinoSurface(
        modifier = modifier,
        shape = shape,
        color = if (color.isSpecified) color else colors.secondarySystemGroupedBackground,
        contentColor = if (contentColor.isSpecified) contentColor else colors.label,
    ) {
        // Cupertino provides its own content colour through the local we bridged to ours,
        // but the border is a Material affordance it has no parameter for.
        Box(if (border != null) Modifier.border(border, shape) else Modifier) { content() }
    }
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RectangleShape,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: androidx.compose.foundation.BorderStroke? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) = Surface(
    modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    shape = shape,
    color = color,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
    border = border,
    content = content,
)

/** An iOS grouped card: flat fill, continuous corners, no elevation. */
@Composable
fun IosCard(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(IosMetrics.GroupedCornerRadius),
    color: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IosTheme.colors
    Column(
        modifier
            .clip(shape)
            .background(if (color.isSpecified) color else colors.secondarySystemGroupedBackground)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.label) { content() }
    }
}

/** One button of an [IosAlertDialog]. */
data class IosAlertAction(
    val title: String,
    val role: IosButtonRole? = null,
    val prominent: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The iOS 26 alert: a thick rounded card with the message centred, and its actions as
 * stacked filled capsules — the hairline-divided button rows of older iOS are gone.
 */
@Composable
fun IosAlertDialog(
    onDismissRequest: () -> Unit,
    actions: List<IosAlertAction>,
    modifier: Modifier = Modifier,
    title: String? = null,
    message: String? = null,
) {
    val colors = IosTheme.colors
    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = modifier
                .width(300.dp)
                .clip(ContinuousRoundedRectangle(30.dp))
                .background(colors.systemBackground)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = IosTheme.typography.headline,
                    color = colors.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (message != null) {
                Text(
                    text = message,
                    style = if (title == null) IosTheme.typography.headline else IosTheme.typography.footnote,
                    color = if (title == null) colors.label else colors.secondaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            actions.forEach { action ->
                val contentColor = when {
                    action.prominent -> colors.onAccent
                    action.role == IosButtonRole.Destructive -> colors.systemRed
                    else -> colors.label
                }
                val fill = when {
                    action.prominent -> IosTheme.tint
                    else -> colors.secondarySystemFill
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IosCapsule)
                        .background(fill)
                        .clickable {
                            action.onClick()
                            onDismissRequest()
                        }
                        .defaultMinSize(minHeight = 46.dp)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = action.title,
                        style = IosTheme.typography.headline,
                        color = contentColor,
                    )
                }
            }
        }
    }
}

/**
 * M3's Switch call shape, drawn as cupertino's iOS switch.
 *
 * [colors] is accepted and ignored: the iOS switch is systemGreen on and systemGray5 off,
 * and a recoloured track stops reading as one. Dropping the parameter would break every
 * settings row that passes M3's.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.SwitchColors? = null,
) = CupertinoSwitch(
    checked = checked,
    onCheckedChange = { onCheckedChange?.invoke(it) },
    modifier = modifier,
    enabled = enabled && onCheckedChange != null,
)

/** M3's Slider call shape, drawn as cupertino's iOS slider. Steps mean the same thing. */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) = CupertinoSlider(
    value = value,
    onValueChange = onValueChange,
    modifier = modifier,
    enabled = enabled,
    valueRange = valueRange,
    steps = steps,
    backdrop = zone.ien.hig.utils.rememberDefaultBackdrop(),
    onValueChangeFinished = onValueChangeFinished ?: {},
)

/**
 * The iOS grouped text field: a filled rounded rect, no floating label and no
 * underline.
 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = IosTheme.typography.body,
) {
    CupertinoBorderedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        textStyle = textStyle,
        placeholder = placeholder?.let { { Text(it, style = textStyle) } },
        shape = ContinuousRoundedRectangle(12.dp),
        paddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    )
}

/** The iOS search field: the same fill, with a leading magnifier well. */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    CupertinoSearchTextField(
        value = value,
        onValueChange = onValueChange,
        state = rememberCupertinoSearchTextFieldState(),
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
    )
}
