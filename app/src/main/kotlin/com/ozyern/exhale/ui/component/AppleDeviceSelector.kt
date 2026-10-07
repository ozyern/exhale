package com.ozyern.exhale.ui.player

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.layout.onSizeChanged
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LiquidGlassSheet
import com.ozyern.exhale.ui.component.liquid.LiquidSlider

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun V8DeviceSelector(
    textBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeviceSheet by remember { mutableStateOf(false) }

    val availableDevices = remember {
        getAvailableDevices(context)
    }

    val activeDevice = remember(availableDevices) {
        getActiveDevice(context, availableDevices)
    }

    val castState by com.ozyern.exhale.playback.cast.CastController.state.collectAsState()
    val deviceIcon = when {
        castState.casting -> R.drawable.cast
        activeDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                activeDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                activeDevice?.type == AudioDeviceInfo.TYPE_BLE_HEADSET -> R.drawable.bluetooth

        else -> R.drawable.airplay
    }

    val isBluetooth = castState.casting || activeDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            activeDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            activeDevice?.type == AudioDeviceInfo.TYPE_BLE_HEADSET

    // AirPlay-style trigger button
    Surface(
        onClick = { showDeviceSheet = true },
        shape = CircleShape,
        color = if (isBluetooth) textBackgroundColor.copy(alpha = 0.15f) else Color.Transparent,
        modifier = modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                painter = painterResource(deviceIcon),
                contentDescription = "AirPlay",
                tint = textBackgroundColor.copy(alpha = if (isBluetooth) 1f else 0.7f),
                modifier = Modifier.size(22.dp)
            )
        }
    }

    if (showDeviceSheet) {
        DeviceSelectionBottomSheet(
            onDismiss = { showDeviceSheet = false },
            availableDevices = availableDevices,
            activeDevice = activeDevice,
            onDeviceSelected = { showDeviceSheet = false },
        )
    }
}

/**
 * The output picker, rebuilt on the app's own frosted sheet.
 *
 * It used to be a flat Material `Dialog` + `Surface` — an opaque slab with a title, a list of
 * rows and a "Close" button, which was the only sheet in the app that did not look like the app.
 * The structure now follows the reference the user gave (Spotify's Connect panel): the device you
 * are *on* is a hero card with its output quality, the track it is carrying and its own volume
 * control, and the devices you are *not* on are a quiet list underneath.
 *
 * One honest limitation, stated in the UI rather than hidden: Android gives an ordinary app no way
 * to route audio to an arbitrary output. Selecting a row cannot move the stream — that switch
 * belongs to the system. So the other-device rows open the system output picker instead of
 * pretending to have done something, and the "Bluetooth settings" tile is the real escape hatch.
 */
@Composable
fun DeviceSelectionBottomSheet(
    onDismiss: () -> Unit,
    availableDevices: List<AudioDeviceInfo>,
    activeDevice: AudioDeviceInfo?,
    onDeviceSelected: (AudioDeviceInfo) -> Unit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val format by (playerConnection?.currentFormat
        ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState(initial = null)

    LiquidGlassSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.device_sheet_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                color = onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
            )

            // ── The outputs, as a list ────────────────────────────────────
            //
            // One card, one row per output, a tick on the one you are hearing - which is what
            // AirPlay shows and what makes the sheet answer its own question at a glance. It used
            // to be a hero card for the active device and a separate list for the others, so the
            // two halves of one question were in two different shapes, and the device you were on
            // was the only one whose name was set large.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(onSurface.copy(alpha = 0.07f)),
            ) {
                val ordered = buildList {
                    activeDevice?.let(::add)
                    addAll(availableDevices.filter { it != activeDevice })
                }
                ordered.forEachIndexed { index, device ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 58.dp),
                            thickness = 0.5.dp,
                            color = onSurface.copy(alpha = 0.12f),
                        )
                    }
                    OutputRow(
                        device = device,
                        active = device == activeDevice,
                        accent = accent,
                        subtitle = if (device == activeDevice) {
                            format?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                        } else {
                            null
                        },
                        onClick = {
                            if (device != activeDevice) {
                                openSystemOutputPicker(context)
                                onDeviceSelected(device)
                            }
                        },
                    )
                }
            }

            // ── Chromecast ────────────────────────────────────────────────
            CastSection(accent = accent)

            // ── Volume ────────────────────────────────────────────────────
            // The TV's own volume while casting: the phone is silent then, and its level is moot.
            val castState by com.ozyern.exhale.playback.cast.CastController.state.collectAsState()
            if (castState.casting) {
                Spacer(Modifier.height(14.dp))
                val castVolume by com.ozyern.exhale.playback.cast.CastController.volume.collectAsState()
                IosVolumeBar(
                    value = castVolume,
                    onValueChange = { com.ozyern.exhale.playback.cast.CastController.setVolume(it) },
                    tint = onSurface,
                )
            } else if (playerConnection != null) {
                Spacer(Modifier.height(14.dp))
                val volume by playerConnection.service.playerVolume.collectAsState()
                IosVolumeBar(
                    value = volume,
                    onValueChange = { playerConnection.service.playerVolume.value = it },
                    tint = onSurface,
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── The one place that can actually reroute ───────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(onSurface.copy(alpha = 0.07f))
                    .clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.bluetooth),
                    contentDescription = null,
                    tint = onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.device_sheet_bluetooth_settings),
                    fontSize = 17.sp,
                    color = onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.device_sheet_routing_note),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * The Chromecasts on this network, as rows in the same card style as the outputs above.
 *
 * Scanned for actively only while the sheet is open — a receiver turns up in a couple of seconds
 * that way instead of a minute — and not at all where Google Play Services is missing. Tapping a
 * receiver casts to it; tapping the one in use brings the music back to this phone.
 */
@Composable
private fun CastSection(accent: Color) {
    val context = LocalContext.current
    val onSurface = MaterialTheme.colorScheme.onSurface
    val state by com.ozyern.exhale.playback.cast.CastController.state.collectAsState()
    androidx.compose.runtime.DisposableEffect(state.supported) {
        com.ozyern.exhale.playback.cast.CastController.ensureStarted(context)
        val stop = com.ozyern.exhale.playback.cast.CastController.discover(active = true)
        onDispose { stop() }
    }
    if (!state.supported) return

    Spacer(Modifier.height(18.dp))
    Text(
        text = "Cast",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(onSurface.copy(alpha = 0.07f)),
    ) {
        if (state.devices.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Looking for TVs and speakers on this Wi-Fi…",
                    fontSize = 15.sp,
                    color = onSurface.copy(alpha = 0.6f),
                )
            }
        }
        state.devices.forEachIndexed { index, device ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 58.dp),
                    thickness = 0.5.dp,
                    color = onSurface.copy(alpha = 0.12f),
                )
            }
            val haptic = LocalHapticFeedback.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (device.connected) {
                            com.ozyern.exhale.playback.cast.CastController.disconnect(resumeHere = true)
                        } else {
                            com.ozyern.exhale.playback.cast.CastController.connect(device.id)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.cast),
                    contentDescription = null,
                    tint = if (device.connected) accent else onSurface.copy(alpha = 0.70f),
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontSize = 17.sp,
                        fontWeight = if (device.connected) FontWeight.SemiBold else FontWeight.Normal,
                        color = onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (device.connected) {
                        Text(
                            text = "Playing here · tap to bring it back to this phone",
                            fontSize = 13.sp,
                            color = onSurface.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    device.connecting ->
                        androidx.compose.material3.CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    device.connected -> Icon(
                        painter = painterResource(R.drawable.done),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
    }
}

/**
 * One output: glyph, name, and a tick when it is the one playing.
 *
 * The tick rather than a chevron. A chevron on every row promised each one led somewhere, when in
 * fact the only thing a row can do is hand the reroute to the system picker; a tick says which
 * output you are on, which is the question the sheet exists to answer.
 */
@Composable
private fun OutputRow(
    device: AudioDeviceInfo,
    active: Boolean,
    accent: Color,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val wash by animateFloatAsState(
        targetValue = if (pressed) 0.06f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "outputRowPress",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(onSurface.copy(alpha = wash))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Icon(
            painter = painterResource(deviceIcon(device)),
            contentDescription = null,
            tint = if (active) accent else onSurface.copy(alpha = 0.70f),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceLabel(device),
                fontSize = 17.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }
        if (active) {
            Spacer(Modifier.width(10.dp))
            Icon(
                painter = painterResource(R.drawable.done),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

/**
 * iOS's volume control: a tall rounded track that fills, with the speaker inside it.
 *
 * Not a Material slider with a thumb. The thumb is the giveaway - iOS has not drawn one on a
 * volume bar since Control Center was introduced, because the bar itself is the handle: you push
 * the level up and down by dragging anywhere across it, and the glyph rides at the left so the
 * control says what it controls without a label.
 */
@Composable
private fun IosVolumeBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    tint: Color,
) {
    var width by remember { mutableStateOf(1f) }
    val level = value.coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.10f))
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onValueChange((offset.x / width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onValueChange((change.position.x / width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(level)
                .background(tint.copy(alpha = 0.85f)),
        )
        Icon(
            painter = painterResource(
                if (level <= 0.001f) R.drawable.volume_off else R.drawable.volume_up,
            ),
            contentDescription = null,
            // Dark against the filled part, light against the empty one.
            tint = if (level > 0.10f) {
                MaterialTheme.colorScheme.surface
            } else {
                tint.copy(alpha = 0.65f)
            },
            modifier = Modifier
                .padding(start = 16.dp)
                .size(19.dp),
        )
    }
}




/** Opens the platform's own output switcher, which is the only thing that can actually reroute. */
private fun openSystemOutputPicker(context: Context) {
    val intents = listOf(
        Intent("com.android.settings.panel.action.MEDIA_OUTPUT"),
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
    )
    for (intent in intents) {
        val launched = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (launched) return
    }
}

@Composable
private fun deviceLabel(device: AudioDeviceInfo): String {
    val context = LocalContext.current
    // The phone itself is named, not numbered.
    //
    // `productName` for a built-in output is `Build.MODEL`, so this row read "CPH2649" while every
    // other row in the list showed a name somebody had chosen. See DeviceNames for where a real
    // one comes from.
    if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
        device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    ) {
        return com.ozyern.exhale.utils.DeviceNames.thisDevice(context)
    }
    val product = device.productName?.toString()
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals(android.os.Build.MODEL, ignoreCase = true) }
    return product ?: when (device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> stringResource(R.string.device_speaker)
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> stringResource(R.string.device_wired_headphones)
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> stringResource(R.string.device_wired_headset)
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> stringResource(R.string.device_bluetooth)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> stringResource(R.string.device_bluetooth_sco)
        AudioDeviceInfo.TYPE_BLE_HEADSET -> stringResource(R.string.device_ble_headset)
        else -> stringResource(R.string.device_generic)
    }
}

private fun deviceIcon(device: AudioDeviceInfo): Int = when (device.type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET -> R.drawable.bluetooth

    else -> R.drawable.airplay
}

// Returns the available audio output devices
private fun getAvailableDevices(context: Context): List<AudioDeviceInfo> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { device ->
            device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        .sortedBy { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> 0

                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> 1

                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
                else -> 3
            }
        }
}

// Returns the currently active output device
private fun getActiveDevice(context: Context, devices: List<AudioDeviceInfo>): AudioDeviceInfo? {
    // Priority: Bluetooth > Wired > Speaker
    val bluetoothDevices = devices.filter {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    if (bluetoothDevices.isNotEmpty()) {
        return bluetoothDevices.firstOrNull()
    }

    val wiredDevices = devices.filter {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
    }

    if (wiredDevices.isNotEmpty()) {
        return wiredDevices.firstOrNull()
    }

    return devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
}
