/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("FunctionName", "UNUSED_PARAMETER")

package com.ozyern.exhale.ui.ios.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.component.IosCheckmark
import zone.ien.hig.CupertinoCheckBox
import zone.ien.hig.CupertinoAlertDialog
import com.ozyern.exhale.ui.ios.foundation.IosTheme

/**
 * The OutlinedTextField call shape, drawn the iOS way: a filled rounded field with the
 * label sitting *above* it as plain text. iOS has no outline, and no floating label that
 * animates up into the border.
 */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.TextFieldColors? = null,
) {
    val iosColors = IosTheme.colors
    val style = if (textStyle == TextStyle.Default) IosTheme.typography.body else textStyle

    Column(modifier) {
        label?.let {
            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                ProvideTextStyle(IosTheme.typography.footnote) { it() }
            }
            Spacer(Modifier.size(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(12.dp))
                .background(iosColors.tertiarySystemFill)
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                leadingIcon?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
            }

            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    placeholder?.let {
                        CompositionLocalProvider(LocalContentColor provides iosColors.placeholderText) {
                            ProvideTextStyle(style) { it() }
                        }
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = style.merge(
                        color = if (isError) iosColors.systemRed else iosColors.label
                    ),
                    cursorBrush = SolidColor(IosTheme.tint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                trailingIcon?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
        }

        supportingText?.let {
            Spacer(Modifier.size(4.dp))
            CompositionLocalProvider(
                LocalContentColor provides if (isError) iosColors.systemRed else iosColors.secondaryLabel
            ) {
                ProvideTextStyle(IosTheme.typography.caption) { it() }
            }
        }
    }
}

/** The Checkbox call shape, drawn as the iOS checkmark — iOS has no boxed checkbox. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.CheckboxColors? = null,
) {
    // The 44dp box is Apple's minimum touch target and stays ours; cupertino's checkbox
    // sizes itself to the glyph.
    Box(
        modifier = modifier.size(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        CupertinoCheckBox(
            checked = checked,
            onCheckedChange = { onCheckedChange?.invoke(it) },
            enabled = enabled && onCheckedChange != null,
        )
    }
}

/**
 * The AlertDialog call shape as the iOS 26 alert: a card with 30pt continuous corners,
 * everything centred, and the actions stacked as full-width capsules rather than laid
 * out as a trailing row of text buttons.
 */
@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = Color.Unspecified,
    titleContentColor: Color = Color.Unspecified,
    textContentColor: Color = Color.Unspecified,
    tonalElevation: androidx.compose.ui.unit.Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = IosTheme.colors
    CupertinoAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides IosTheme.tint) { it() }
                }
                title?.let {
                    ProvideTextStyle(
                        IosTheme.typography.headline.merge(textAlign = TextAlign.Center)
                    ) { it() }
                }
            }
        },
        message = {
            text?.let {
                CompositionLocalProvider(LocalContentColor provides colors.secondaryLabel) {
                    ProvideTextStyle(IosTheme.typography.footnote) { it() }
                }
            }
        },
        containerColor = if (containerColor.isSpecified) containerColor else colors.systemBackground,
        shape = shape ?: ContinuousRoundedRectangle(30.dp),
        properties = properties,
    ) {
        // Material provides opaque button slots, so the caller's buttons keep handling taps.
        // Only the button itself is clickable; the rest of each row is inert.
        action(onClick = {}) { confirmButton() }
        dismissButton?.let { button -> action(onClick = {}) { button() } }
    }
}

/** The expressive wavy ring has no iOS counterpart, so it draws as the iOS ring. */
@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    stroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    trackStroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    gapSize: androidx.compose.ui.unit.Dp = 0.dp,
    amplitude: Float = 0f,
    wavelength: androidx.compose.ui.unit.Dp = 0.dp,
) = CircularProgressIndicator(modifier = modifier, color = color)

@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified,
    stroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    trackStroke: androidx.compose.ui.graphics.drawscope.Stroke? = null,
    gapSize: androidx.compose.ui.unit.Dp = 0.dp,
    amplitude: ((Float) -> Float)? = null,
    wavelength: androidx.compose.ui.unit.Dp = 0.dp,
) = CircularProgressIndicator(progress = progress, modifier = modifier, color = color)

/** The TextFieldValue overload, for the fields that drive their own selection. */
@Composable
fun OutlinedTextField(
    value: androidx.compose.ui.text.input.TextFieldValue,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    shape: androidx.compose.ui.graphics.Shape? = null,
    colors: androidx.compose.material3.TextFieldColors? = null,
) {
    val iosColors = IosTheme.colors
    val style = if (textStyle == TextStyle.Default) IosTheme.typography.body else textStyle

    Column(modifier) {
        label?.let {
            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                ProvideTextStyle(IosTheme.typography.footnote) { it() }
            }
            Spacer(Modifier.size(6.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(12.dp))
                .background(iosColors.tertiarySystemFill)
                .defaultMinSize(minHeight = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                leadingIcon?.let {
                    it()
                    Spacer(Modifier.width(8.dp))
                }
            }

            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    placeholder?.let {
                        CompositionLocalProvider(LocalContentColor provides iosColors.placeholderText) {
                            ProvideTextStyle(style) { it() }
                        }
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    readOnly = readOnly,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    minLines = minLines,
                    visualTransformation = visualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = style.merge(
                        color = if (isError) iosColors.systemRed else iosColors.label
                    ),
                    cursorBrush = SolidColor(IosTheme.tint),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CompositionLocalProvider(LocalContentColor provides iosColors.secondaryLabel) {
                trailingIcon?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
        }

        supportingText?.let {
            Spacer(Modifier.size(4.dp))
            CompositionLocalProvider(
                LocalContentColor provides if (isError) iosColors.systemRed else iosColors.secondaryLabel
            ) {
                ProvideTextStyle(IosTheme.typography.caption) { it() }
            }
        }
    }
}

/**
 * The RadioButton call shape. iOS marks the selected row of a group with a trailing
 * checkmark — it has no radio ring — so that is what this draws.
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.RadioButtonColors? = null,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            IosCheckmark(color = if (enabled) IosTheme.tint else IosTheme.colors.tertiaryLabel)
        }
    }
}
