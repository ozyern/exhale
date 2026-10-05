/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AudioCrossfadeDurationKey
import com.ozyern.exhale.constants.AudioNormalizationKey
import com.ozyern.exhale.constants.EqualizerEnabledKey
import com.ozyern.exhale.constants.SpatialAudioKey
import com.ozyern.exhale.constants.SpatialAudioProfile
import com.ozyern.exhale.constants.SpatialAudioProfileKey
import com.ozyern.exhale.db.entities.FormatEntity
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import java.util.Locale

/**
 * What is happening to the music on its way out, right now, as three stages: where it comes from,
 * what the app does to it, and where it goes. Everything here is read, not assumed — the stream's
 * own format, the switches that are actually on, the device actually in use.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioPipelineCard(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current
    val format by (playerConnection?.currentFormat?.collectAsState(initial = null)
        ?: remember { androidx.compose.runtime.mutableStateOf<FormatEntity?>(null) })
    val metadata by (playerConnection?.mediaMetadata?.collectAsState()
        ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val spatial by rememberPreference(SpatialAudioKey, com.ozyern.exhale.utils.DeviceAudio.defaultSpatialAudio)
    val profile by rememberEnumPreference(SpatialAudioProfileKey, SpatialAudioProfile.CINEMA)
    val eqOn by rememberPreference(EqualizerEnabledKey, false)
    val normalization by rememberPreference(AudioNormalizationKey, true)
    val crossfade by rememberPreference(AudioCrossfadeDurationKey, 0)

    val context = LocalContext.current
    val output = remember { describeOutput(context) }

    val source = when {
        metadata == null -> "Nothing playing"
        format == null -> "Resolving the stream…"
        else -> describeFormat(format!!)
    }
    val stages = buildList {
        if (spatial) {
            add(
                "Spatial Audio · " + when (profile) {
                    SpatialAudioProfile.NATURAL -> "Natural"
                    SpatialAudioProfile.WIDE -> "Wide"
                    SpatialAudioProfile.CINEMA -> "Cinema"
                },
            )
        }
        if (eqOn) add("Equalizer")
        if (normalization) {
            val gain = format?.loudnessDb?.let { -it }
            add(if (gain != null) String.format(Locale.ROOT, "Loudness %+.1f dB", gain) else "Loudness matching")
        }
        if (crossfade > 0) add("Crossfade ${crossfade}s")
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .settingsGlassGroup()
            .padding(vertical = 14.dp, horizontal = 16.dp),
    ) {
        Text(
            text = "Audio path",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        PipelineStage(icon = R.drawable.graphic_eq, title = "Source", detail = { StageText(source) }, last = false)
        PipelineStage(
            icon = R.drawable.ic_spatial_audio,
            title = "Sound",
            detail = {
                if (stages.isEmpty()) {
                    StageText("Nothing added — the stream as it is")
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        stages.forEach { StageChip(it) }
                    }
                }
            },
            last = false,
        )
        PipelineStage(icon = R.drawable.headphones, title = "Output", detail = { StageText(output) }, last = true)
    }
}

@Composable
private fun PipelineStage(icon: Int, title: String, detail: @Composable () -> Unit, last: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            if (!last) {
                // The line the audio travels down, from one stage to the next.
                Canvas(Modifier.width(2.dp).height(22.dp)) {
                    drawLine(
                        brush = Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.15f))),
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = size.width,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = if (last) 0.dp else 10.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            detail()
        }
    }
}

@Composable
private fun StageText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun StageChip(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun describeFormat(format: FormatEntity): String {
    val codec = when {
        format.codecs.contains("opus", ignoreCase = true) -> "Opus"
        format.codecs.contains("flac", ignoreCase = true) || format.mimeType.contains("flac", ignoreCase = true) -> "FLAC"
        format.codecs.startsWith("mp4a", ignoreCase = true) || format.mimeType.contains("mp4", ignoreCase = true) -> "AAC"
        format.codecs.contains("vorbis", ignoreCase = true) -> "Vorbis"
        format.codecs.isNotBlank() -> format.codecs.uppercase(Locale.ROOT)
        else -> format.mimeType.substringAfter('/').uppercase(Locale.ROOT)
    }
    val parts = mutableListOf(codec)
    if (format.bitrate > 0) parts += "${format.bitrate / 1000} kbps"
    format.sampleRate?.takeIf { it > 0 }?.let { parts += String.format(Locale.ROOT, "%.1f kHz", it / 1000f).replace(".0 ", " ") }
    return parts.joinToString(" · ")
}

/** The device the sound is leaving by, and the rate the phone runs its output at. */
private fun describeOutput(context: Context): String {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    val bluetooth = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
    }
    val wired = devices.firstOrNull {
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
    }
    val name = when {
        bluetooth != null -> bluetooth.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
        wired != null -> wired.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL } ?: "Headphones"
        else -> Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
    val rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
    return if (rate != null && rate > 0) {
        "$name · " + String.format(Locale.ROOT, "%.1f kHz", rate / 1000f).replace(".0 ", " ")
    } else {
        name
    }
}

/**
 * The three stage widths as three tiles, each drawn as what it does: a listener, and how far round
 * them the sound is spread. Cinema adds the height ring.
 */
@Composable
fun SpatialStagePicker(
    selected: SpatialAudioProfile,
    onSelect: (SpatialAudioProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SpatialAudioProfile.entries.forEach { profile ->
            StageTile(
                profile = profile,
                selected = profile == selected,
                onClick = { onSelect(profile) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StageTile(
    profile: SpatialAudioProfile,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val border by animateColorAsState(if (selected) accent else ink.copy(alpha = 0.12f), label = "stageBorder")
    val fill by animateColorAsState(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent, label = "stageFill")
    val lift by animateFloatAsState(if (selected) 1f else 0.96f, label = "stageLift")
    val (label, spread, rings) = when (profile) {
        SpatialAudioProfile.NATURAL -> Triple("Natural", 0.45f, 1)
        SpatialAudioProfile.WIDE -> Triple("Wide", 0.75f, 2)
        SpatialAudioProfile.CINEMA -> Triple("Cinema", 1f, 3)
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .clip(RoundedCornerShape(16.dp))
            .background(fill)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val arcColor = if (selected) accent else ink.copy(alpha = 0.55f)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.6f)
                .padding(horizontal = 10.dp),
        ) {
            val center = Offset(size.width / 2f, size.height * 0.62f)
            // The listener.
            drawCircle(ink.copy(alpha = 0.85f), radius = size.height * 0.10f, center = Offset(center.x, center.y - size.height * 0.08f))
            // Sound around them: wider arcs for a wider stage.
            for (ring in 1..rings) {
                val r = size.height * (0.22f + 0.16f * ring)
                val sweep = 70f + 110f * spread
                drawArc(
                    color = arcColor.copy(alpha = 1f - 0.22f * (ring - 1)),
                    startAngle = -90f - sweep / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            if (profile == SpatialAudioProfile.CINEMA) {
                // The height: a ring overhead.
                drawOval(
                    color = arcColor.copy(alpha = 0.6f),
                    topLeft = Offset(center.x - size.width * 0.34f, center.y - size.height * 0.62f),
                    size = Size(size.width * 0.68f, size.height * 0.16f),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else ink,
        )
    }
}

/**
 * What the phone's audio track is really running at — the output device, its rate and its sample
 * format, read from the track Exhale opened — and the choice between 16-bit and 32-bit float output.
 */
@Composable
fun OutputPrecisionRow(
    status: com.ozyern.exhale.playback.OutputStatus?,
    float: Boolean,
    onFloatChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val device = remember(status) { describeOutput(context).substringBefore(" · ") }
    val readout = buildString {
        append("AudioTrack · ")
        append(device)
        status?.let {
            append(" · ")
            append(String.format(Locale.ROOT, "%.1f kHz", it.sampleRateHz / 1000f))
            append(" · ")
            append(it.encodingLabel)
        } ?: append(" · starts with the next song")
    }
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.graphic_eq),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Output precision", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    readout,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val ink = MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ink.copy(alpha = 0.08f))
                .padding(4.dp),
        ) {
            listOf(false to "16-bit PCM", true to "32-bit float").forEach { (value, label) ->
                val chosen = value == float
                val fill by animateColorAsState(if (chosen) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent, label = "precisionFill")
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(fill)
                        .clickable { onFloatChange(value) }
                        .padding(vertical = 11.dp),
                ) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (chosen) ink else ink.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Float keeps hi-res sources in full precision end to end. A change applies the next time playback starts fresh.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How hard Automix works in the background, as three tiles: a meter with one, two or three bars
 * lit, and what the choice means underneath.
 */
@Composable
fun AutomixPerformancePicker(
    selected: com.ozyern.exhale.playback.automix.AutomixPerformance,
    onSelect: (com.ozyern.exhale.playback.automix.AutomixPerformance) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.ozyern.exhale.playback.automix.AutomixPerformance.entries.forEachIndexed { index, mode ->
                val chosen = mode == selected
                val border by animateColorAsState(if (chosen) accent else ink.copy(alpha = 0.12f), label = "mixBorder")
                val fill by animateColorAsState(if (chosen) accent.copy(alpha = 0.12f) else Color.Transparent, label = "mixFill")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(fill)
                        .border(1.dp, border, RoundedCornerShape(16.dp))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Canvas(Modifier.width(34.dp).height(22.dp)) {
                        val bars = 3
                        val gap = size.width * 0.12f
                        val barWidth = (size.width - gap * (bars - 1)) / bars
                        for (bar in 0 until bars) {
                            val height = size.height * (0.45f + 0.275f * bar)
                            val lit = bar <= index
                            drawRoundRect(
                                color = if (lit) (if (chosen) accent else ink.copy(alpha = 0.7f)) else ink.copy(alpha = 0.14f),
                                topLeft = Offset(bar * (barWidth + gap), size.height - height),
                                size = Size(barWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 3f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mode.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (chosen) accent else ink,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            selected.detail,
            style = MaterialTheme.typography.bodySmall,
            color = ink.copy(alpha = 0.62f),
        )
    }
}

/** Where Automix has got to with the playing song and the next one. */
@Composable
fun AutomixStatusLine(
    service: com.ozyern.exhale.playback.MusicService?,
    modifier: Modifier = Modifier,
) {
    val status by androidx.compose.runtime.produceState("", service) {
        while (true) {
            com.ozyern.exhale.utils.awaitAppVisible()
            value = service?.automixStatus().orEmpty()
            kotlinx.coroutines.delay(1_000)
        }
    }
    if (status.isEmpty()) return
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        )
    }
}
