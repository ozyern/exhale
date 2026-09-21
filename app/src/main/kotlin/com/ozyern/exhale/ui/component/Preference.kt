/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateFloatAsState
import com.ozyern.exhale.ui.theme.MotionTokens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

val LocalPreferenceInGroup = compositionLocalOf { false }

@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: (@Composable () -> Unit)? = null,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
) {
    val inGroup = LocalPreferenceInGroup.current
    // Subtle premium haptic tick on every preference tap (respects the user's haptics setting
    // via the app-wide LocalHapticFeedback provider).
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !inGroup) MotionTokens.PressedScale else 1f,
        animationSpec = MotionTokens.PressSpring,
        label = "prefScale",
    )

    val rowContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Grouped rows bounce individually; standalone rows bounce via the Card.
                // Instant (ACTION_DOWN) observer — avoids the ~100ms scroll tap-delay.
                .then(if (inGroup) Modifier.pressScaleContainer() else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (inGroup) LocalIndication.current else null,
                    enabled = isEnabled && onClick != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick?.invoke()
                    },
                )
                .alpha(if (isEnabled) 1f else 0.5f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    icon()
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                ) {
                    title()
                }

                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        it()
                    }
                }

                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                content?.invoke()
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    trailingContent()
                }
            }
        }
    }

    if (inGroup) {
        rowContent()
    } else {
        // Apple Music-style inset row: NO Material Card (no elevation, no border, no tonal
        // tint). A 16dp clip over the SOLID group surface — the same colour the grouped cards
        // use — so a standalone row and a row inside a group are visibly the same material.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
        ) {
            rowContent()
        }
    }
}

@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showMenu by remember {
        mutableStateOf(false)
    }

    // The choice opens as a pull-down from this row, as iOS does it — not a dialog over the page.
    Box(modifier = modifier) {
        PreferenceEntry(
            title = title,
            description = valueText(selectedValue),
            icon = icon,
            onClick = { showMenu = true },
            isEnabled = isEnabled,
        )
        PullDownMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            options = values,
            selected = selectedValue,
            label = valueText,
            onSelect = onValueSelected,
        )
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    ListPreference(
        modifier = modifier,
        title = title,
        icon = icon,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    // Direct toggle drags fire their own haptic tick; row taps get theirs from PreferenceEntry.
    val haptic = LocalHapticFeedback.current
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            LiquidToggle(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                modifier = if (!isEnabled) Modifier.alpha(0.38f) else Modifier
            )
        },
        onClick = { if (isEnabled) onCheckedChange(!checked) },
        isEnabled = isEnabled
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            initialTextFieldValue =
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length),
                ),
            singleLine = singleLine,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value)
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue)
            },
            onCancel = {
                sliderValue = value
                showDialog = false
            },
            onReset = {
                sliderValue = 30f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.seconds,
                            sliderValue.roundToInt(),
                            sliderValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    SquigglySlider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 15f..60f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value.roundToInt().toString(),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CrossfadeSliderPreference(
    modifier: Modifier = Modifier,
    value: Int,
    onValueChange: (Int) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value.toFloat()) }

    // Actualizar localValue cuando value cambia desde fuera
    androidx.compose.runtime.LaunchedEffect(value) {
        localValue = value.toFloat()
    }

    val displayValue = localValue.roundToInt().coerceIn(0, 10)
    val isCrossfadeEnabled = displayValue > 0

    val descriptionText = when (displayValue) {
        0 -> stringResource(R.string.crossfade_disabled_description)
        else -> pluralStringResource(R.plurals.seconds, displayValue, displayValue)
    }

    if (showDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.audio_crossfade_title),
            onDismiss = { showDialog = false },
            onConfirm = {
                val finalValue = localValue.roundToInt().coerceIn(0, 10)
                onValueChange(finalValue)
                showDialog = false
            },
            onCancel = {
                localValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                localValue = 0f
            },
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Valor actual
                    Text(
                        text = if (displayValue == 0) {
                            stringResource(R.string.dark_theme_off)
                        } else {
                            pluralStringResource(R.plurals.seconds, displayValue, displayValue)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCrossfadeEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Slider
                    SquigglySlider(
                        value = localValue,
                        onValueChange = {
                            localValue = it.roundToInt()
                                .coerceIn(0, 10)
                                .toFloat()
                        },
                        valueRange = 0f..10f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Marcas del slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0, 2, 4, 6, 8, 10).forEach { mark ->
                            Text(
                                text = "${mark}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (displayValue >= mark && mark > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.audio_crossfade_title))
                if (isCrossfadeEnabled) {
                    CrossfadeBadge(duration = displayValue)
                }
            }
        },
        description = descriptionText,
        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
private fun CrossfadeBadge(duration: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier
            .padding(start = 8.dp)
            .size(width = 48.dp, height = 24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${duration}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 10,
    valueText: (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    title()
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                sliderValue = rounded.toFloat()
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = minValue.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                    Text(
                        text = valueText(rounded),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    SquigglySlider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it.roundToInt()
                                .coerceIn(minValue, maxValue)
                                .toFloat()
                        },
                        valueRange = minValue.toFloat()..maxValue.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = valueText(value),
        icon = icon,
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        // Apple Music-style grouped inset list: a Column clipped to 16dp over a frosted
        // translucent surface — NO Material Card (no elevation, no border). Rows inside sit
        // flush, separated by hairline dividers (callers place PreferenceGroupDivider between
        // rows; never at the very top or bottom of the group).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        ) {
            CompositionLocalProvider(LocalPreferenceInGroup provides true) {
                Column(content = content)
            }
        }
    }
}

@Composable
fun PreferenceGroupDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 60.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    // Apple Music-style large, bold section header.
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
    )
}