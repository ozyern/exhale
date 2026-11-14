/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.ozyern.exhale.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.RadioButton
import com.ozyern.exhale.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppFont
import com.ozyern.exhale.constants.BrandFontEnabledKey
import com.ozyern.exhale.constants.CustomFontEnabledKey
import com.ozyern.exhale.constants.CustomFontNameKey
import com.ozyern.exhale.constants.CustomFontPathKey
import com.ozyern.exhale.constants.SelectedFontKey
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.theme.GoogleSansFontFamily
import com.ozyern.exhale.ui.theme.OutfitFontFamily
import com.ozyern.exhale.ui.theme.PlusJakartaSansFontFamily
import com.ozyern.exhale.ui.theme.SansFlexFontFamily
import com.ozyern.exhale.ui.theme.copyCustomFont
import com.ozyern.exhale.ui.utils.appTopBarWindowInsets
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSelectionScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (selectedFont, onSelectedFontChange) = rememberPreference(
        SelectedFontKey,
        defaultValue = AppFont.PLUS_JAKARTA_SANS.value
    )

    val (brandFont, onBrandFontChange) = rememberPreference(
        BrandFontEnabledKey,
        defaultValue = true
    )
    val (customFontEnabled, onCustomFontEnabledChange) = rememberPreference(
        CustomFontEnabledKey,
        defaultValue = false
    )
    val (customFontPath, onCustomFontPathChange) = rememberPreference(
        CustomFontPathKey,
        defaultValue = ""
    )
    val (customFontName, onCustomFontNameChange) = rememberPreference(
        CustomFontNameKey,
        defaultValue = ""
    )
    val context = LocalContext.current
    val customFontScope = rememberCoroutineScope()
    val customFontPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val displayName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: uri.lastPathSegment.orEmpty()
        customFontScope.launch {
            val newPath = withContext(Dispatchers.IO) { copyCustomFont(context, uri) }
            if (newPath != null) {
                onCustomFontPathChange(newPath)
                onCustomFontNameChange(displayName)
                onCustomFontEnabledChange(true)
            }
        }
    }

    val activeFontFamily = remember(selectedFont) {
        when (AppFont.fromValue(selectedFont)) {
            AppFont.SYSTEM -> FontFamily.Default
            AppFont.GOOGLE_SANS -> GoogleSansFontFamily
            AppFont.SANS_FLEX -> SansFlexFontFamily
            AppFont.OUTFIT -> OutfitFontFamily
            AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        }
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.app_font),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 16.dp,
    ) {
            // Typography Preview Card
            Card(
                shape = AppTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = AppTheme.colorScheme.surfaceVariant,
                    contentColor = AppTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = stringResource(R.string.typography_preview).uppercase(),
                        style = AppTheme.typography.labelMedium,
                        color = AppTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = stringResource(R.string.preview_text_quote),
                        fontFamily = activeFontFamily,
                        style = AppTheme.typography.headlineMedium,
                        color = AppTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "Expressive typeface is applied to display, headlines, and titles. Body copy and labels remain in the system font for maximum readability.",
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            IosSettingsGroup(
                title = stringResource(R.string.font_selection),
                items = listOf(
                    IosSettingsItem(
                        leadingContent = {
                            RadioButton(
                                selected = selectedFont == AppFont.SYSTEM.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_system),
                                fontFamily = FontFamily.Default
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_system_desc),
                                fontFamily = FontFamily.Default
                            )
                        },
                        onClick = { onSelectedFontChange(AppFont.SYSTEM.value) }
                    ),
                    IosSettingsItem(
                        leadingContent = {
                            RadioButton(
                                selected = selectedFont == AppFont.GOOGLE_SANS.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_google_sans),
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_google_sans_desc),
                                fontFamily = GoogleSansFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(AppFont.GOOGLE_SANS.value) }
                    ),
                    IosSettingsItem(
                        leadingContent = {
                            RadioButton(
                                selected = selectedFont == AppFont.SANS_FLEX.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_sans_flex),
                                fontFamily = SansFlexFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_sans_flex_desc),
                                fontFamily = SansFlexFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(AppFont.SANS_FLEX.value) }
                    ),
                    IosSettingsItem(
                        leadingContent = {
                            RadioButton(
                                selected = selectedFont == AppFont.OUTFIT.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_outfit),
                                fontFamily = OutfitFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_outfit_desc),
                                fontFamily = OutfitFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(AppFont.OUTFIT.value) }
                    ),
                    IosSettingsItem(
                        leadingContent = {
                            RadioButton(
                                selected = selectedFont == AppFont.PLUS_JAKARTA_SANS.value,
                                onClick = null
                            )
                        },
                        title = {
                            Text(
                                text = stringResource(R.string.font_plus_jakarta_sans),
                                fontFamily = PlusJakartaSansFontFamily
                            )
                        },
                        description = {
                            Text(
                                text = stringResource(R.string.font_plus_jakarta_sans_desc),
                                fontFamily = PlusJakartaSansFontFamily
                            )
                        },
                        onClick = { onSelectedFontChange(AppFont.PLUS_JAKARTA_SANS.value) }
                    )
                )
            )
            Spacer(modifier = Modifier.height(27.dp))

            IosSettingsGroup(
                title = stringResource(R.string.font_additional),
                items = listOf(
                    IosSettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.brand_font)) },
                        description = { Text(stringResource(R.string.brand_font_desc)) },
                        trailingContent = {
                            Switch(
                                checked = brandFont,
                                onCheckedChange = onBrandFontChange
                            )
                        },
                        onClick = { onBrandFontChange(!brandFont) }
                    ),
                    IosSettingsItem(
                        icon = painterResource(R.drawable.palette),
                        title = { Text(stringResource(R.string.custom_font)) },
                        description = {
                            Text(
                                if (customFontPath.isEmpty()) stringResource(R.string.custom_font_none)
                                else customFontName.ifEmpty { stringResource(R.string.custom_font_set) }
                            )
                        },
                        trailingContent = {
                            if (customFontPath.isNotEmpty()) {
                                Switch(
                                    checked = customFontEnabled,
                                    onCheckedChange = onCustomFontEnabledChange
                                )
                            }
                        },
                        onClick = { customFontPickerLauncher.launch(arrayOf("*/*")) }
                    )
                )
            )
            Spacer(modifier = Modifier.height(36.dp))
        
    }
}
