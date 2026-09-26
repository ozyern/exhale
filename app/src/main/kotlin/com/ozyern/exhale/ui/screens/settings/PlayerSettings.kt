/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.component.DefaultDialog
import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.LocalPlayerConnection
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import com.ozyern.exhale.constants.SpatialAudioProfileKey
import com.ozyern.exhale.constants.SpatialAudioProfile
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ArtistSeparatorsKey
import com.ozyern.exhale.constants.ExternalDownloaderEnabledKey
import com.ozyern.exhale.constants.ExternalDownloaderPackageKey
import com.ozyern.exhale.constants.AudioNormalizationKey
import com.ozyern.exhale.constants.AudioOffload
import com.ozyern.exhale.utils.DeviceAudio
import com.ozyern.exhale.constants.AudioQuality
import com.ozyern.exhale.constants.AudioCodec
import com.ozyern.exhale.constants.AudioCodecKey
import com.ozyern.exhale.constants.AudioQualityKey
import com.ozyern.exhale.constants.NetworkMeteredKey
import com.ozyern.exhale.constants.AutoDownloadOnLikeKey
import com.ozyern.exhale.constants.AutoStartOnBluetoothKey
import com.ozyern.exhale.constants.AutoSkipNextOnErrorKey
import com.ozyern.exhale.constants.PauseOnDeviceMuteKey
import com.ozyern.exhale.constants.PermanentShuffleKey
import com.ozyern.exhale.constants.PersistentQueueKey

import com.ozyern.exhale.constants.SkipSilenceKey
import com.ozyern.exhale.constants.SpatialAudioKey
import com.ozyern.exhale.constants.StopMusicOnTaskClearKey
import com.ozyern.exhale.constants.WakelockKey
import com.ozyern.exhale.constants.HistoryDuration
import com.ozyern.exhale.constants.AudioCrossfadeDurationKey
import com.ozyern.exhale.constants.PlayerStreamClient
import com.ozyern.exhale.constants.PlayerStreamClientKey
import com.ozyern.exhale.constants.SeekExtraSeconds
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.ArtistSeparatorsDialog
import com.ozyern.exhale.ui.component.TagsManagementDialog
import com.ozyern.exhale.ui.component.TextFieldDialog
import com.ozyern.exhale.ui.component.EnumListPreference
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.ListDialog
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.ui.component.SliderPreference
import com.ozyern.exhale.ui.component.CrossfadeSliderPreference
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.LocalDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.HIGHEST
    )
    val (audioCodec, onAudioCodecChange) = rememberEnumPreference(
        AudioCodecKey,
        defaultValue = AudioCodec.AAC
    )
    val (playerStreamClient, onPlayerStreamClientChange) = rememberEnumPreference(
        PlayerStreamClientKey,
        defaultValue = PlayerStreamClient.ANDROID_VR
    )
    val (networkMetered, onNetworkMeteredChange) = rememberPreference(
        NetworkMeteredKey,
        defaultValue = false
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (permanentShuffle, onPermanentShuffleChange) = rememberPreference(
        PermanentShuffleKey,
        defaultValue = false
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (preferLocalLossless, onPreferLocalLosslessChange) = rememberPreference(
        com.ozyern.exhale.constants.PreferLocalLosslessKey,
        defaultValue = true
    )
    val losslessContext = androidx.compose.ui.platform.LocalContext.current
    val askForAudio = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) com.ozyern.exhale.utils.LocalLossless.invalidate() }
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )
    val (spatialAudio, onSpatialAudioChange) = rememberPreference(
        SpatialAudioKey,
        // On by default, except on OnePlus/OPPO, where OReality is already doing it and two
        // virtualizers in series is what makes those phones sound wrong. See DeviceAudio.
        defaultValue = com.ozyern.exhale.utils.DeviceAudio.defaultSpatialAudio
    )
    val (spatialProfile, onSpatialProfileChange) = rememberEnumPreference(
        SpatialAudioProfileKey,
        // Cinema out of the box. The stage widths are a matter of taste, and the widest one is the
        // one this app is tuned around - a defaults that ships the narrowest option is a defaults
        // that makes the feature look like it is not doing anything.
        defaultValue = SpatialAudioProfile.CINEMA,
    )
    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        AudioOffload,
        defaultValue = false
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) = rememberPreference(
        PauseOnDeviceMuteKey,
        defaultValue = false
    )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) = rememberPreference(
        AutoStartOnBluetoothKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )

    val (audioCrossfadeSeconds, onAudioCrossfadeSecondsChange) = rememberPreference(
        AudioCrossfadeDurationKey,
        defaultValue = 0
    )

    val (artistSeparators, onArtistSeparatorsChange) = rememberPreference(
        ArtistSeparatorsKey,
        defaultValue = ",;/&"
    )
    val (externalDownloaderEnabled, onExternalDownloaderEnabledChange) = rememberPreference(
        ExternalDownloaderEnabledKey,
        defaultValue = false
    )
    val (externalDownloaderPackage, onExternalDownloaderPackageChange) = rememberPreference(
        ExternalDownloaderPackageKey,
        defaultValue = ""
    )

    val (wakelockEnabled, onWakelockChange) = rememberPreference(
        WakelockKey,
        defaultValue = false
    )

    var showArtistSeparatorsDialog by remember { mutableStateOf(false) }
    var showTagsManagementDialog by remember { mutableStateOf(false) }
    var showPlayerStreamClientDialog by remember { mutableStateOf(false) }
    var showExternalDownloaderPackageDialog by remember { mutableStateOf(false) }
    val database = LocalDatabase.current

    if (showArtistSeparatorsDialog) {
        ArtistSeparatorsDialog(
            currentSeparators = artistSeparators,
            onDismiss = { showArtistSeparatorsDialog = false },
            onSave = { newSeparators ->
                onArtistSeparatorsChange(newSeparators)
                showArtistSeparatorsDialog = false
            }
        )
    }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            database = database,
            onDismiss = { showTagsManagementDialog = false }
        )
    }

    if (showExternalDownloaderPackageDialog) {
        TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(externalDownloaderPackage),
            onDone = { pkg ->
                onExternalDownloaderPackageChange(pkg)
                showExternalDownloaderPackageDialog = false
            },
            onDismiss = { showExternalDownloaderPackageDialog = false },
            singleLine = true,
            maxLines = 1,
        )
    }

    if (showPlayerStreamClientDialog) {
        // Two named choices with a sentence each, so this stays a dialog rather than a pull-down -
        // but it is an iOS option list inside it: a heading, the choice and its explanation, and a
        // tick against the one in force. Material radio circles down the left made the page's one
        // genuinely explanatory setting look like a form.
        DefaultDialog(
            onDismiss = { showPlayerStreamClientDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.player_stream_client),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            listOf(PlayerStreamClient.ANDROID_VR, PlayerStreamClient.WEB_REMIX)
                .forEachIndexed { index, value ->
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                        )
                    }
                    val chosen = value == playerStreamClient
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onPlayerStreamClientChange(value)
                                showPlayerStreamClientDialog = false
                            }
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (value) {
                                    PlayerStreamClient.ANDROID_VR ->
                                        stringResource(R.string.player_stream_client_android_vr)

                                    else -> stringResource(R.string.player_stream_client_web_remix)
                                },
                                fontSize = 17.sp,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = when (value) {
                                    PlayerStreamClient.ANDROID_VR ->
                                        stringResource(R.string.player_stream_client_android_vr_desc)

                                    else -> stringResource(R.string.player_stream_client_web_remix_desc)
                                },
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (chosen) {
                            Spacer(Modifier.width(12.dp))
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.player)
        )
        PreferenceGroup {
            EnumListPreference(
                title = { Text(stringResource(R.string.audio_quality)) },
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                selectedValue = audioQuality,
                onValueSelected = onAudioQualityChange,
                valueText = {
                    when (it) {
                        AudioQuality.HIGHEST -> stringResource(R.string.audio_quality_max)
                        AudioQuality.HIGH -> stringResource(R.string.audio_quality_high)
                        AudioQuality.AUTO -> stringResource(R.string.audio_quality_auto)
                        AudioQuality.LOW -> stringResource(R.string.audio_quality_low)
                    }
                }
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text(stringResource(R.string.audio_codec)) },
                icon = { Icon(painterResource(R.drawable.waves), null) },
                selectedValue = audioCodec,
                onValueSelected = onAudioCodecChange,
                valueText = {
                    when (it) {
                        AudioCodec.AAC -> stringResource(R.string.audio_codec_aac)
                        AudioCodec.OPUS -> stringResource(R.string.audio_codec_opus)
                        AudioCodec.AUTO -> stringResource(R.string.audio_codec_auto)
                    }
                }
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(stringResource(R.string.player_stream_client)) },
                description =
                when (playerStreamClient) {
                    PlayerStreamClient.ANDROID_VR -> stringResource(R.string.player_stream_client_android_vr)
                    else -> stringResource(R.string.player_stream_client_web_remix)
                },
                icon = { Icon(painterResource(R.drawable.integration), null) },
                onClick = { showPlayerStreamClientDialog = true }
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.network_metered_title)) },
                description = stringResource(R.string.network_metered_description),
                icon = { Icon(painterResource(R.drawable.android_cell), null) },
                checked = networkMetered,
                onCheckedChange = onNetworkMeteredChange
            )

            PreferenceGroupDivider()
            SliderPreference(
                title = { Text(stringResource(R.string.history_duration)) },
                icon = { Icon(painterResource(R.drawable.history), null) },
                value = historyDuration,
                onValueChange = onHistoryDurationChange,
            )

            PreferenceGroupDivider()
            CrossfadeSliderPreference(
                value = audioCrossfadeSeconds,
                onValueChange = onAudioCrossfadeSecondsChange,
                isEnabled = !audioOffload,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text("Prefer lossless files") },
                description = "Plays a FLAC, WAV or AIFF of the same song from this phone instead of streaming it, when there is one.",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = preferLocalLossless,
                onCheckedChange = { on ->
                    onPreferLocalLosslessChange(on)
                    if (on && !com.ozyern.exhale.utils.LocalMediaScanner.hasPermission(losslessContext)) {
                        askForAudio.launch(com.ozyern.exhale.utils.LocalMediaScanner.PermissionName)
                    }
                },
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.skip_silence)) },
                icon = { Icon(painterResource(R.drawable.fast_forward), null) },
                checked = skipSilence,
                onCheckedChange = onSkipSilenceChange,
                isEnabled = !audioOffload,
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_normalization)) },
                icon = { Icon(painterResource(R.drawable.volume_up), null) },
                checked = audioNormalization,
                onCheckedChange = onAudioNormalizationChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.spatial_audio)) },
                description = stringResource(R.string.spatial_audio_desc),
                icon = { Icon(painterResource(R.drawable.headphones), null) },
                checked = spatialAudio,
                onCheckedChange = onSpatialAudioChange
            )

            // How wide the stage is. A phone has two speakers, so the "5.1 / 7.1" switches other
            // players show have nothing to drive; what they are reaching for is this.
            AnimatedVisibility(visible = spatialAudio) {
                Column {
                PreferenceGroupDivider()
                EnumListPreference(
                    title = { Text(stringResource(R.string.spatial_audio_profile)) },
                    icon = { Icon(painterResource(R.drawable.equalizer), null) },
                    selectedValue = spatialProfile,
                    onValueSelected = onSpatialProfileChange,
                    valueText = {
                        stringResource(
                            when (it) {
                                SpatialAudioProfile.NATURAL -> R.string.spatial_profile_natural
                                SpatialAudioProfile.WIDE -> R.string.spatial_profile_wide
                                SpatialAudioProfile.CINEMA -> R.string.spatial_profile_cinema
                            }
                        )
                    },
                )
                }
            }

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(stringResource(R.string.system_audio_effects)) },
                description = stringResource(
                    if (com.ozyern.exhale.utils.DeviceAudio.isOplusDevice) R.string.system_audio_effects_oplus_desc
                    else R.string.system_audio_effects_desc
                ),
                icon = { Icon(painterResource(R.drawable.equalizer), null) },
                onClick = {
                    val session = playerConnection?.player?.audioSessionId ?: 0
                    if (!com.ozyern.exhale.utils.DeviceAudio.openSystemEffects(context, session)) {
                        Toast.makeText(context, context.getString(R.string.system_audio_effects_missing), Toast.LENGTH_SHORT).show()
                    }
                },
            )

            // Unavailable rather than silently ineffective on OnePlus/OPPO: an offloaded stream is
            // decoded past the effect chain, so turning this on there would switch OReality off
            // without saying so. See MusicService.updateAudioOffload.
            val offloadBlocked = DeviceAudio.isOplusDevice
            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.audio_offload)) },
                description = if (offloadBlocked) {
                    stringResource(R.string.audio_offload_oplus_desc)
                } else {
                    stringResource(R.string.audio_offload_desc)
                },
                icon = { Icon(painterResource(R.drawable.speed), null) },
                checked = audioOffload && !offloadBlocked,
                isEnabled = !offloadBlocked,
                onCheckedChange = { enabled ->
                    onAudioOffloadChange(enabled)
                    if (enabled) {
                        onSkipSilenceChange(false)
                    }
                }
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.seek_seconds_addup)) },
                description = stringResource(R.string.seek_seconds_addup_description),
                icon = { Icon(painterResource(R.drawable.arrow_forward), null) },
                checked = seekExtraSeconds,
                onCheckedChange = onSeekExtraSeconds
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.pause_on_device_mute)) },
                description = stringResource(R.string.pause_on_device_mute_desc),
                icon = { Icon(painterResource(R.drawable.volume_off), null) },
                checked = pauseOnDeviceMute,
                onCheckedChange = onPauseOnDeviceMuteChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_start_on_bluetooth)) },
                description = stringResource(R.string.auto_start_on_bluetooth_desc),
                icon = { Icon(painterResource(R.drawable.bluetooth), null) },
                checked = autoStartOnBluetooth,
                onCheckedChange = onAutoStartOnBluetoothChange
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.queue)
        )
        PreferenceGroup {
            SwitchPreference(
                title = { Text(stringResource(R.string.persistent_queue)) },
                description = stringResource(R.string.persistent_queue_desc),
                icon = { Icon(painterResource(R.drawable.queue_music), null) },
                checked = persistentQueue,
                onCheckedChange = onPersistentQueueChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.permanent_shuffle)) },
                description = stringResource(R.string.permanent_shuffle_desc),
                icon = { Icon(painterResource(R.drawable.shuffle), null) },
                checked = permanentShuffle,
                onCheckedChange = onPermanentShuffleChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_download_on_like)) },
                description = stringResource(R.string.auto_download_on_like_desc),
                icon = { Icon(painterResource(R.drawable.download), null) },
                checked = autoDownloadOnLike,
                onCheckedChange = onAutoDownloadOnLikeChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.auto_skip_next_on_error)) },
                description = stringResource(R.string.auto_skip_next_on_error_desc),
                icon = { Icon(painterResource(R.drawable.skip_next), null) },
                checked = autoSkipNextOnError,
                onCheckedChange = onAutoSkipNextOnErrorChange
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.misc)
        )
        PreferenceGroup {
            SwitchPreference(
                title = { Text(stringResource(R.string.stop_music_on_task_clear)) },
                icon = { Icon(painterResource(R.drawable.clear_all), null) },
                checked = stopMusicOnTaskClear,
                onCheckedChange = onStopMusicOnTaskClearChange
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.wakelock)) },
                description = stringResource(R.string.wakelock_desc),
                icon = { Icon(painterResource(R.drawable.bolt), null) },
                checked = wakelockEnabled,
                onCheckedChange = onWakelockChange
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(stringResource(R.string.artist_separators)) },
                description = artistSeparators.map { "\"$it\"" }.joinToString("  "),
                icon = { Icon(painterResource(R.drawable.artist), null) },
                onClick = { showArtistSeparatorsDialog = true }
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(stringResource(R.string.manage_playlist_tags)) },
                description = stringResource(R.string.manage_playlist_tags_desc),
                icon = { Icon(painterResource(R.drawable.style), null) },
                onClick = { showTagsManagementDialog = true }
            )

            PreferenceGroupDivider()
            SwitchPreference(
                title = { Text(stringResource(R.string.external_downloader)) },
                description = stringResource(R.string.external_downloader_desc),
                icon = { Icon(painterResource(R.drawable.download), null) },
                checked = externalDownloaderEnabled,
                onCheckedChange = onExternalDownloaderEnabledChange
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text(stringResource(R.string.external_downloader_package)) },
                description = externalDownloaderPackage.ifEmpty { stringResource(R.string.external_downloader_package_desc) },
                icon = { Icon(painterResource(R.drawable.integration), null) },
                onClick = { showExternalDownloaderPackageDialog = true },
                isEnabled = externalDownloaderEnabled
            )
        }

    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.player_and_audio)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        }
    )
}
