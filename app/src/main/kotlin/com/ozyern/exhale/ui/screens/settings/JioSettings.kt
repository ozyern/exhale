/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ozyern.exhale.ui.ios.compat.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.RadioButton
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.EnableSaavnStreamingKey
import com.ozyern.exhale.constants.ForceSelectedQualityKey
import com.ozyern.exhale.constants.SaavnAudioQuality
import com.ozyern.exhale.constants.SaavnAudioQualityKey
import com.ozyern.exhale.constants.SaavnFallbackToYouTubeKey
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.component.ModernSwitch
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JioSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (saavnEnabled, onSaavnEnabledChange) = rememberPreference(
        EnableSaavnStreamingKey,
        defaultValue = true
    )
    val (saavnQuality, onSaavnQualityChange) = rememberEnumPreference(
        SaavnAudioQualityKey,
        defaultValue = SaavnAudioQuality.QUALITY_320
    )
    val (fallbackToYouTube, onFallbackToYouTubeChange) = rememberPreference(
        SaavnFallbackToYouTubeKey,
        defaultValue = true
    )
    val (preferArchiveFlac, onPreferArchiveFlacChange) = rememberPreference(
        com.ozyern.exhale.constants.PreferArchiveFlacKey,
        defaultValue = true
    )
    val (forceSelectedQuality, onForceSelectedQualityChange) = rememberPreference(
        ForceSelectedQualityKey,
        defaultValue = false
    )

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.jiosaavn_settings),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 24.dp,
    ) {
        Text(
            text = stringResource(R.string.enable_saavn_streaming_desc),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
        )

        // Large capsule banner for main toggle
        val containerColor by animateColorAsState(
            targetValue = if (saavnEnabled) {
                AppTheme.colorScheme.primaryContainer
            } else {
                AppTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            label = "containerColor"
        )

        val contentColor = if (saavnEnabled) {
            AppTheme.colorScheme.onPrimaryContainer
        } else {
            AppTheme.colorScheme.onSurfaceVariant
        }

        Card(
            onClick = { onSaavnEnabledChange(!saavnEnabled) },
            shape = RoundedCornerShape(50),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.enable_saavn_streaming),
                    style = AppTheme.typography.titleMedium,
                    color = contentColor
                )
                ModernSwitch(
                    checked = saavnEnabled,
                    onCheckedChange = onSaavnEnabledChange
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        IosSettingsGroup(
            title = "Lossless",
            items = listOf(
                IosSettingsItem(
                    title = { Text("Prefer FLAC from the Internet Archive") },
                    description = { Text("When a song has a FLAC copy on the Internet Archive, play that first, before JioSaavn or YouTube.") },
                    trailingContent = { ModernSwitch(checked = preferArchiveFlac, onCheckedChange = onPreferArchiveFlacChange) },
                    onClick = { onPreferArchiveFlacChange(!preferArchiveFlac) },
                ),
            ),
        )

        Spacer(modifier = Modifier.height(24.dp))

        IosSettingsGroup(
            title = stringResource(R.string.saavn_audio_quality),
            items = listOf(
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = saavnQuality == SaavnAudioQuality.QUALITY_320,
                            onClick = null,
                            enabled = saavnEnabled
                        )
                    },
                    title = { Text(SaavnAudioQuality.QUALITY_320.toLabel()) },
                    enabled = saavnEnabled,
                    onClick = { onSaavnQualityChange(SaavnAudioQuality.QUALITY_320) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = saavnQuality == SaavnAudioQuality.QUALITY_160,
                            onClick = null,
                            enabled = saavnEnabled
                        )
                    },
                    title = { Text(SaavnAudioQuality.QUALITY_160.toLabel()) },
                    enabled = saavnEnabled,
                    onClick = { onSaavnQualityChange(SaavnAudioQuality.QUALITY_160) }
                ),
                IosSettingsItem(
                    leadingContent = {
                        RadioButton(
                            selected = saavnQuality == SaavnAudioQuality.QUALITY_96,
                            onClick = null,
                            enabled = saavnEnabled
                        )
                    },
                    title = { Text(SaavnAudioQuality.QUALITY_96.toLabel()) },
                    enabled = saavnEnabled,
                    onClick = { onSaavnQualityChange(SaavnAudioQuality.QUALITY_96) }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    title = { Text(stringResource(R.string.saavn_fallback_to_youtube)) },
                    description = { Text(stringResource(R.string.saavn_fallback_to_youtube_desc)) },
                    enabled = saavnEnabled,
                    trailingContent = {
                        ModernSwitch(
                            checked = fallbackToYouTube,
                            enabled = saavnEnabled,
                            onCheckedChange = onFallbackToYouTubeChange,
                        )
                    },
                    onClick = { onFallbackToYouTubeChange(!fallbackToYouTube) }
                ),
                IosSettingsItem(
                    title = { Text(stringResource(R.string.force_selected_quality)) },
                    description = { Text(stringResource(R.string.force_selected_quality_desc)) },
                    enabled = saavnEnabled,
                    trailingContent = {
                        ModernSwitch(
                            checked = forceSelectedQuality,
                            enabled = saavnEnabled,
                            onCheckedChange = onForceSelectedQualityChange,
                        )
                    },
                    onClick = { onForceSelectedQualityChange(!forceSelectedQuality) }
                ),
            )
        )

        Row(
            modifier = Modifier.padding(top = 24.dp),
            verticalAlignment = Alignment.Top
        ) {
            SymbolIcon(symbol = sf("info.circle"), contentDescription = null, modifier = Modifier.padding(end = 8.dp), tint = AppTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.jiosaavn_beta_info),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    
    }
}
