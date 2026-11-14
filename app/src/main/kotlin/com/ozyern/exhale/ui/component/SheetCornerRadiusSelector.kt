/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.ios.compat.AppTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.ozyern.exhale.ui.ios.compat.Button
import com.ozyern.exhale.ui.ios.compat.FilterChip
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.utils.rimLight

/**
 * Same shape/pattern as [ThumbnailCornerRadiusModal] (preset chips, custom field, slider),
 * but the live preview is a miniature of the app's actual floating sheet — the sheet
 * shown here is real, not a mock, so dragging the slider shows exactly what
 * [BottomSheetMenu]/[BottomSheetPage] will look like rather than an approximation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetCornerRadiusModal(
    initialRadius: Float,
    onDismiss: () -> Unit,
    onRadiusSelected: (Float) -> Unit
) {
    var cornerRadius by remember { mutableFloatStateOf(initialRadius) }
    val presetValues = listOf(16f, 24f, 28f, 32f, 40f)
    var customValue by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isCustomSelected = !presetValues.contains(initialRadius)
        if (isCustomSelected) {
            customValue = initialRadius.roundToInt().toString()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints {
            val screenWidth = maxWidth
            val dialogWidth = when {
                screenWidth > 840.dp -> 0.5f
                screenWidth > 600.dp -> 0.7f
                else -> 0.95f
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth(dialogWidth)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(30.dp)),
                shape = AppTheme.shapes.large,
                color = AppTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.sheet_corner_radius),
                        style = AppTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Live preview: a real floating-sheet card, reacting to the slider
                    // the same way BottomSheetMenu itself would.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val previewShape = remember(cornerRadius) { ContinuousRoundedRectangle(cornerRadius.dp) }
                        Column(
                            modifier = Modifier
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                .fillMaxWidth(0.85f)
                                .clip(previewShape)
                                .background(AppTheme.colorScheme.surfaceContainerHigh, previewShape)
                                .rimLight(shape = previewShape),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp, bottom = 8.dp)
                                    .size(width = 36.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(AppTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .padding(bottom = 14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    ChipsGridSingleRow(
                        values = presetValues,
                        selectedValue = if (isCustomSelected) null else cornerRadius,
                        onValueSelected = { value ->
                            cornerRadius = value
                            isCustomSelected = false
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isCustomSelected,
                            onClick = {
                                isCustomSelected = true
                                if (customValue.isEmpty()) {
                                    customValue = cornerRadius.roundToInt().toString()
                                }
                            },
                            label = { Text(text = stringResource(R.string.custom), style = AppTheme.typography.bodyMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AppTheme.colorScheme.primaryContainer,
                                selectedLabelColor = AppTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedTextField(
                            value = customValue,
                            onValueChange = { newValue ->
                                if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                    customValue = newValue
                                    newValue.toIntOrNull()?.let { intValue ->
                                        val limitedValue = minOf(intValue, 48).toFloat()
                                        cornerRadius = limitedValue
                                        isCustomSelected = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp),
                            enabled = isCustomSelected,
                            label = { Text(text = stringResource(R.string.value), style = AppTheme.typography.bodyMedium) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            trailingIcon = {
                                Text(text = "dp", style = AppTheme.typography.bodyMedium, color = AppTheme.colorScheme.onSurfaceVariant)
                            },
                            shape = AppTheme.shapes.small
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "0", style = AppTheme.typography.bodySmall, color = AppTheme.colorScheme.onSurfaceVariant)

                        Slider(
                            value = cornerRadius,
                            onValueChange = { newValue ->
                                cornerRadius = newValue
                                customValue = newValue.roundToInt().toString()
                                isCustomSelected = !presetValues.contains(newValue)
                            },
                            valueRange = 0f..48f,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = AppTheme.colorScheme.primary,
                                activeTrackColor = AppTheme.colorScheme.primary,
                                inactiveTrackColor = AppTheme.colorScheme.surfaceVariant
                            )
                        )

                        Text(text = "48", style = AppTheme.typography.bodySmall, color = AppTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = AppTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { cornerRadius = 28f; customValue = "28"; isCustomSelected = false },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(text = stringResource(id = R.string.reset), style = AppTheme.typography.labelLarge)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(text = stringResource(id = android.R.string.cancel), style = AppTheme.typography.labelLarge)
                        }

                        Button(
                            onClick = { onRadiusSelected(cornerRadius) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppTheme.colorScheme.primary,
                                contentColor = AppTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(text = stringResource(id = android.R.string.ok), style = AppTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipsGridSingleRow(
    values: List<Float>,
    selectedValue: Float?,
    onValueSelected: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = selectedValue == value,
                onClick = { onValueSelected(value) },
                label = { Text(text = "${value.roundToInt()}", style = AppTheme.typography.bodyMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppTheme.colorScheme.primaryContainer,
                    selectedLabelColor = AppTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
