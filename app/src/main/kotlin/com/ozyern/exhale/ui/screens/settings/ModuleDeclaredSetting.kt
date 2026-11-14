package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.ModernSwitch
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.DropdownMenuItem
import com.ozyern.exhale.ui.ios.compat.ExposedDropdownMenuBox
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import com.ozyern.exhale.ui.ios.compat.Text
import com.music.spine.ModuleSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleDeclaredSetting(setting: ModuleSetting, value: String, onChange: (String) -> Unit) {
    Column {
        when {
            setting.options.isNotEmpty() -> {
                var expanded by remember(setting) { mutableStateOf(false) }
                Text(setting.label, style = AppTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = setting.options.firstOrNull { it.value == value }?.label ?: value,
                        onValueChange = {}, readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        setting.options.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = { onChange(option.value); expanded = false })
                        }
                    }
                }
            }
            setting.type.lowercase() in setOf("boolean", "switch", "toggle") -> Row {
                Text(setting.label, modifier = Modifier.weight(1f))
                ModernSwitch(value == "true", onCheckedChange = { onChange(it.toString()) })
            }
            else -> OutlinedTextField(
                value = value, onValueChange = onChange, singleLine = true,
                label = { Text(setting.label) },
                visualTransformation = if (setting.type.equals("password", true)) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (setting.description.isNotBlank()) Text(setting.description, style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}
