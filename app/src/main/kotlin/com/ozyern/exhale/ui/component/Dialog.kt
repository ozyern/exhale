/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.ozyern.exhale.ui.ios.compat.ProvideTextStyle
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField as TextField
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.ozyern.exhale.ui.utils.Motion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ozyern.exhale.R

import kotlinx.coroutines.delay

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .dialogEnter()
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = ContinuousRoundedRectangle(30.dp)
                ),
            shape = ContinuousRoundedRectangle(30.dp),
            color = IosTheme.colors.secondarySystemGroupedBackground,
        ) {
            Column(
                horizontalAlignment = horizontalAlignment,
                modifier = modifier
                    .padding(24.dp)
            ) {
                if (icon != null) {
                    CompositionLocalProvider(LocalContentColor provides IosTheme.tint) {
                        Box(
                            Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            icon()
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides IosTheme.colors.label) {
                        ProvideTextStyle(IosTheme.typography.headline) {
                            Box(
                                Modifier.align(if (icon == null) Alignment.Start else Alignment.CenterHorizontally)
                            ) {
                                title()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }

                content()

                if (buttons != null) {
                    Spacer(Modifier.height(24.dp))

                    FlowRow(
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        CompositionLocalProvider(LocalContentColor provides IosTheme.tint) {
                            ProvideTextStyle(
                                value = IosTheme.typography.headline
                            ) {
                                buttons()
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPromptDialog(
    title: String? = null,
    titleBar: @Composable (RowScope.() -> Unit)? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onReset: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = if (titleBar != null) {
            { Row { titleBar() } }
        } else if (title != null) {
            {
                Text(
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = IosTheme.typography.headline,
                )
            }
        } else null,
        buttons = {
            if (onReset != null) {
                Row(modifier = Modifier.weight(1f)) {
                    TextButton(
                        onClick = { onReset() },
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }

            if (onCancel != null) {
                TextButton(
                    onClick = { onCancel() }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }

            TextButton(
                onClick = { onConfirm() }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        content()
    }
}

@Composable
fun ListDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .dialogEnter()
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.15f),
                    shape = ContinuousRoundedRectangle(30.dp)
                ),
            shape = ContinuousRoundedRectangle(30.dp),
            color = IosTheme.colors.systemBackground,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = modifier
                    .padding(vertical = 24.dp)
                    .imePadding(),
            ) {
                LazyColumn(content = content)
            }
        }
    }
}

@Composable
fun InfoLabel(
    text: String
) = Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier.padding(horizontal = 8.dp)
) {
    SymbolIcon(symbol = sf("info.circle"), contentDescription = null, modifier = Modifier.padding(4.dp), tint = IosTheme.colors.secondaryLabel)
    Text(
        text = text,
        style = IosTheme.typography.footnote,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
fun TextFieldDialog(
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    initialTextFieldValue: TextFieldValue = TextFieldValue(),
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    autoFocus: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 10,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardType: KeyboardType = KeyboardType.Text,
    onDone: (String) -> Unit = {},

    textFields: List<Pair<String, TextFieldValue>>? = null,
    onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
    onDoneMultiple: ((List<String>) -> Unit)? = null,

    onDismiss: () -> Unit,
    autoDismiss: Boolean = true,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val legacyFieldState = remember { mutableStateOf(initialTextFieldValue) }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (autoFocus) {
            delay(300)
            focusRequester.requestFocus()
        }
    }

    DefaultDialog(
        onDismiss = onDismiss,
        modifier = modifier,
        icon = icon,
        title = title,
        buttons = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }

            val isValid = textFields?.all { isInputValid(it.second.text) }
                ?: isInputValid(legacyFieldState.value.text)

            TextButton(
                enabled = isValid,
                onClick = {
                    if (autoDismiss) onDismiss()
                    if (textFields != null && onDoneMultiple != null) {
                        onDoneMultiple(textFields.map { it.second.text })
                    } else {
                        onDone(legacyFieldState.value.text)
                    }
                }
            ) {
                Text(text = stringResource(android.R.string.ok))
            }
        }
    ) {
        Column(
            modifier = Modifier.weight(weight = 1f, fill = false)
        ) {
            if (textFields != null) {
                textFields.forEachIndexed { index, (label, value) ->
                    TextField(
                        value = value,
                        onValueChange = { onTextFieldsChange?.invoke(index, it) },
                        placeholder = { Text(label) },
                        singleLine = singleLine,
                        maxLines = maxLines,
                        colors = OutlinedTextFieldDefaults.colors(),
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                            keyboardType = keyboardType
                        ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (onDoneMultiple != null) {
                                onDoneMultiple(textFields.map { it.second.text })
                                if (autoDismiss) onDismiss()
                            }
                        }
                    ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (index < textFields.size - 1) 12.dp else 0.dp)
                            .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    )
                }
            } else {
                TextField(
                    value = legacyFieldState.value,
                    onValueChange = { legacyFieldState.value = it },
                    placeholder = placeholder,
                    singleLine = singleLine,
                    maxLines = maxLines,
                    colors = OutlinedTextFieldDefaults.colors(),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (singleLine) ImeAction.Done else ImeAction.None,
                        keyboardType = keyboardType
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onDone(legacyFieldState.value.text)
                            if (autoDismiss) onDismiss()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            extraContent?.invoke()
        }
    }
}

/**
 * Dialog entrance: scale 0.96 -> 1 + fade on [Motion.appear]. Entry-only — the
 * platform Dialog window is torn down on dismiss, so no exit can be animated.
 */
@Composable
private fun Modifier.dialogEnter(): Modifier {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = Motion.appear(),
        label = "DialogEnterScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = Motion.appear(),
        label = "DialogEnterAlpha",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .alpha(alpha)
}
