/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.ozyern.exhale.ui.ios.compat.DropdownMenuItem
import com.ozyern.exhale.ui.ios.compat.ExposedDropdownMenuBox
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.OutlinedTextField
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.EnabledModulesKey
import com.ozyern.exhale.constants.ModuleSettingsKey
import com.ozyern.exhale.constants.FetchedModulesKey
import com.ozyern.exhale.ui.component.ModernSwitch
import com.ozyern.exhale.ui.theme.AppleTokens
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import org.json.JSONArray
import org.json.JSONObject
import com.music.spine.ModuleManager
import com.music.spine.SpineModule
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val moduleDetailJson = Json { ignoreUnknownKeys = true }

private val QUALITY_OPTIONS = listOf(
    "Lossless (FLAC 16-bit/44.1kHz)" to "LOSSLESS",
    "Hi-Res (FLAC 24-bit)" to "HIRES",
    "Dolby Atmos (EAC3-JOC)" to "EAC3_JOC",
    "High (AAC 320kbps)" to "HIGH",
    "Low (AAC 96kbps)" to "LOW",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    moduleId: String,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (fetchedJson, _) = rememberPreference(FetchedModulesKey, defaultValue = "[]")
    val module = remember(fetchedJson, moduleId) {
        runCatching {
            moduleDetailJson.decodeFromString<List<SpineModule>>(fetchedJson)
                .firstOrNull { it.id == moduleId }
        }.getOrNull()
    }
    var runtime by remember(moduleId) { mutableStateOf<ModuleManager.LoadedModule?>(null) }
    LaunchedEffect(module) {
        val descriptor = module?.takeIf { it.download.startsWith("https://") } ?: return@LaunchedEffect
        ModuleManager.cacheDir = context.filesDir.resolve("module-scripts")
        runtime = ModuleManager.shared.loadModule(descriptor).getOrNull()
    }
    val qualityOptions = runtime?.manifest?.settings?.get("quality")?.options
        ?.takeIf { it.isNotEmpty() }?.map { it.label to it.value } ?: QUALITY_OPTIONS
    val qualityDefault = runtime?.manifest?.defaults?.get("quality").orEmpty()
    val (enabledJson, onEnabledJsonChange) = rememberPreference(EnabledModulesKey, defaultValue = "[]")
    val (moduleSettingsJson, onModuleSettingsJsonChange) = rememberPreference(ModuleSettingsKey, defaultValue = "{}")

    val enabledIds = remember(enabledJson) {
        runCatching {
            val arr = JSONArray(enabledJson)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrElse { emptySet<String>() }
    }

    val isEnabled = enabledIds.contains(moduleId)

    fun toggleEnabled() {
        val current = enabledIds.toMutableSet()
        if (current.contains(moduleId)) current.remove(moduleId) else current.add(moduleId)
        val arr = JSONArray()
        current.forEach { arr.put(it) }
        ModuleManager.shared.unloadModule(moduleId)
        onEnabledJsonChange(arr.toString())
    }

    val moduleSettings = remember(moduleSettingsJson) {
        runCatching {
            val obj = JSONObject(moduleSettingsJson)
            val inner = obj.optJSONObject(moduleId)
            if (inner != null) {
                inner.keys().asSequence().associateWith { inner.optString(it, "") }
            } else emptyMap()
        }.getOrElse { emptyMap<String, String>() }
    }

    fun updateModuleSetting(key: String, value: String) {
        val obj = runCatching { JSONObject(moduleSettingsJson) }.getOrElse { JSONObject() }
        val inner = obj.optJSONObject(moduleId) ?: JSONObject()
        inner.put(key, value)
        ModuleManager.shared.unloadModule(moduleId)
        obj.put(moduleId, inner)
        onModuleSettingsJsonChange(obj.toString())
    }

    var qualityExpanded by remember { mutableStateOf(false) }
    var tidalQualityExpanded by remember { mutableStateOf(false) }

    val currentQuality = moduleSettings["quality"] ?: ""
    val currentTidalQuality = moduleSettings["tidalQuality"] ?: ""
    var checking by remember(moduleId) { mutableStateOf(false) }
    var checkStatus by remember(moduleId) { mutableStateOf<String?>(null) }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.module_detail),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 24.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(
                    if (isEnabled) AppTheme.colorScheme.primaryContainer
                    else AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.enable_module),
                style = AppTheme.typography.titleMedium,
                color = if (isEnabled) AppTheme.colorScheme.onPrimaryContainer
                else AppTheme.colorScheme.onSurfaceVariant,
            )
            ModernSwitch(
                checked = isEnabled,
                onCheckedChange = { toggleEnabled() },
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.module_info),
            style = AppTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppleTokens.CardCorner))
                .background(AppTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            module?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ModuleLogo(it)
                    Spacer(Modifier.size(12.dp))
                    Column {
                        Text(it.name, style = AppTheme.typography.titleMedium)
                        Text("${it.author} · ${it.version}", style = AppTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            ModuleInfoRow(stringResource(R.string.module_id), moduleId)
            ModuleInfoRow(
                stringResource(R.string.module_status),
                if (isEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)
            )
            module?.takeIf { it.download.startsWith("https://") }?.let { descriptor ->
                com.ozyern.exhale.ui.ios.compat.TextButton(
                    enabled = !checking,
                    onClick = {
                        scope.launch {
                            checking = true
                            try {
                                ModuleManager.cacheDir = context.filesDir.resolve("module-scripts")
                                val result = ModuleManager.shared.loadModule(descriptor)
                                runtime = result.getOrNull()
                                checkStatus = context.getString(
                                    if (result.isSuccess) R.string.module_ready else R.string.module_load_failed,
                                )
                            } finally {
                                checking = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.module_check)) }
                checkStatus?.let { Text(it, style = AppTheme.typography.bodySmall) }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = module?.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.module_description),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "QUALITY SETTINGS",
            style = AppTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppleTokens.CardCorner))
                .background(AppTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
        ) {
            Text(
                text = runtime?.manifest?.settings?.get("quality")?.label?.takeIf { it.isNotBlank() } ?: "Primary Quality",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = qualityExpanded,
                onExpandedChange = { qualityExpanded = it },
            ) {
                OutlinedTextField(
                    value = qualityOptions.find { it.second == currentQuality.ifBlank { qualityDefault } }?.first ?: "Default",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = qualityExpanded,
                    onDismissRequest = { qualityExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Default") },
                        onClick = {
                            updateModuleSetting("quality", "")
                            qualityExpanded = false
                        },
                    )
                    qualityOptions.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                updateModuleSetting("quality", value)
                                qualityExpanded = false
                            },
                        )
                    }
                }
            }

            runtime?.manifest?.settings?.get("quality")?.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AppTheme.typography.bodySmall, color = AppTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }

            if (runtime == null || runtime?.manifest?.settings.isNullOrEmpty() ||
                runtime?.manifest?.settings?.containsKey("tidalQuality") == true) {
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Tidal Fallback Quality",
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(
                expanded = tidalQualityExpanded,
                onExpandedChange = { tidalQualityExpanded = it },
            ) {
                OutlinedTextField(
                    value = QUALITY_OPTIONS.find { it.second == currentTidalQuality }?.first ?: "Default",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tidalQualityExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = tidalQualityExpanded,
                    onDismissRequest = { tidalQualityExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Default") },
                        onClick = {
                            updateModuleSetting("tidalQuality", "")
                            tidalQualityExpanded = false
                        },
                    )
                    QUALITY_OPTIONS.forEach { (label, value) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                updateModuleSetting("tidalQuality", value)
                                tidalQualityExpanded = false
                            },
                        )
                    }
                }
            }
            }
            runtime?.manifest?.settings?.filterKeys { it != "quality" && it != "tidalQuality" }?.forEach { (key, setting) ->
                Spacer(Modifier.height(16.dp))
                ModuleDeclaredSetting(
                    setting = setting,
                    value = moduleSettings[key] ?: runtime?.manifest?.defaults?.get(key).orEmpty(),
                    onChange = { updateModuleSetting(key, it) },
                )
            }
        }

        Spacer(Modifier.height(36.dp))
    
    }
}

@Composable
private fun ModuleInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurface,
        )
    }
}
