/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The tone pad, faders and balance follow BitChord's equaliser screen
 * (github.com/kushagrasinghx/BitChord, GPL-3.0); the live response curve over them is Exhale's.
 */

package com.ozyern.exhale.ui.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.SoundBalanceKey
import com.ozyern.exhale.constants.SoundEqBandsKey
import com.ozyern.exhale.constants.SoundEqEnabledKey
import com.ozyern.exhale.constants.SoundEqFocusedKey
import com.ozyern.exhale.constants.SoundEqModeKey
import com.ozyern.exhale.constants.SoundEqToneXKey
import com.ozyern.exhale.constants.SoundEqToneYKey
import com.ozyern.exhale.playback.EqCurve
import com.ozyern.exhale.playback.EqLayout
import com.ozyern.exhale.playback.SoundEqMode
import com.ozyern.exhale.playback.SoundPreset
import com.ozyern.exhale.playback.decodeSoundBands
import com.ozyern.exhale.playback.encodeSoundBands
import com.ozyern.exhale.playback.manualCurve
import com.ozyern.exhale.playback.toneCurve
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import com.ozyern.exhale.ui.menu.EqualizerDialog
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.DeviceAudio
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Exhale's equaliser.
 *
 * **Dynamic** is a tone pad — one puck over a dot grid, warmer to brighter left to right, scooped
 * to forward bottom to top — for everyone who wants it warmer or brighter and has no interest in
 * which decibel went where. **Manual** is seven faders under a row of presets. Both drive the same
 * filters, so switching glides rather than clicks, and the curve they make is drawn live at the top
 * of the page, so what a move does is seen as well as heard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundEqualizerScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val (enabled, setEnabled) = rememberPreference(SoundEqEnabledKey, false)
    val (mode, setMode) = rememberEnumPreference(SoundEqModeKey, SoundEqMode.DYNAMIC)
    val (toneX, setToneX) = rememberPreference(SoundEqToneXKey, 0)
    val (toneY, setToneY) = rememberPreference(SoundEqToneYKey, 0)
    val (focused, setFocused) = rememberPreference(SoundEqFocusedKey, false)
    val (bandsRaw, setBandsRaw) = rememberPreference(SoundEqBandsKey, "")
    val (balance, setBalance) = rememberPreference(SoundBalanceKey, 0f)
    val bands = remember(bandsRaw) { decodeSoundBands(bandsRaw) }
    val preset = remember(bands) { SoundPreset.matching(bands) }
    var showDeviceEffects by rememberSaveable { mutableStateOf(false) }

    val curve = remember(enabled, mode, toneX, toneY, focused, bands) {
        when {
            !enabled -> EqCurve.FLAT
            mode == SoundEqMode.DYNAMIC -> toneCurve(toneX, toneY, focused)
            else -> manualCurve(bands)
        }
    }

    if (showDeviceEffects) {
        EqualizerDialog(
            onDismiss = { showDeviceEffects = false },
            openSystemEqualizer = {
                val session = playerConnection?.player?.audioSessionId ?: 0
                if (!DeviceAudio.openSystemEffects(context, session)) {
                    Toast.makeText(context, context.getString(R.string.system_audio_effects_missing), Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            // Below the content, not around the viewport: the page scrolls on under the dock.
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        ResponseCard(curve = curve, enabled = enabled)

        PreferenceGroup {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { setEnabled(!enabled) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Equalizer", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = if (enabled) {
                            when (mode) {
                                SoundEqMode.DYNAMIC -> "Dynamic · " + toneSummary(toneX, toneY)
                                SoundEqMode.MANUAL -> "Manual · " + preset.label
                            }
                        } else {
                            "Off — the music exactly as it is"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LiquidToggle(checked = enabled, onCheckedChange = setEnabled)
            }
        }

        // Off, everything below reads as unavailable rather than merely idle.
        val liveAlpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(220), label = "eqLive")
        Column(Modifier.graphicsLayer { alpha = liveAlpha }) {
            Spacer(Modifier.height(18.dp))
            ModeSwitch(
                selected = mode,
                onSelect = setMode,
                enabled = enabled,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "eqMode",
            ) { shown ->
                Column {
                    when (shown) {
                        SoundEqMode.DYNAMIC -> {
                            PreferenceGroup(title = "Tone") {
                                TonePad(
                                    x = toneX,
                                    y = toneY,
                                    enabled = enabled,
                                    onChange = { x, y ->
                                        setToneX(x)
                                        setToneY(y)
                                    },
                                )
                                ToneReadout(x = toneX, y = toneY)
                            }
                            PreferenceGroup(title = "Range") {
                                ChoiceRow("Broad", "Wide, gentle moves — a richer sound", !focused, enabled) { setFocused(false) }
                                ChoiceRow("Focused", "Narrow moves on just the range you aim at", focused, enabled) { setFocused(true) }
                            }
                        }

                        SoundEqMode.MANUAL -> {
                            PresetStrip(
                                selected = preset,
                                enabled = enabled,
                                onSelect = { setBandsRaw(encodeSoundBands(it.bands)) },
                            )
                            PreferenceGroup(title = "Bands") {
                                BandFaders(
                                    bands = bands,
                                    enabled = enabled,
                                    onChange = { setBandsRaw(encodeSoundBands(it)) },
                                )
                                Text(
                                    text = "Reset to Flat",
                                    fontSize = 16.sp,
                                    color = if (preset == SoundPreset.FLAT) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = enabled && preset != SoundPreset.FLAT) {
                                            setBandsRaw(encodeSoundBands(SoundPreset.FLAT.bands))
                                        }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                )
                            }
                        }
                    }
                }
            }

            PreferenceGroup(title = "Balance") {
                BalanceControl(balance = balance, enabled = enabled, onChange = setBalance)
            }
        }

        // Outside the dimmed block: the phone's own effects work whether or not ours is on.
        PreferenceGroup(title = "This phone") {
            PreferenceEntry(
                title = { Text("Device effects") },
                description = "The phone's own equalizer bands, bass boost and virtualizer",
                icon = { Icon(painterResource(R.drawable.equalizer), null) },
                onClick = { showDeviceEffects = true },
            )
            PreferenceEntry(
                title = { Text(stringResource(R.string.system_audio_effects)) },
                description = stringResource(
                    if (DeviceAudio.isOplusDevice) R.string.system_audio_effects_oplus_desc
                    else R.string.system_audio_effects_desc,
                ),
                icon = { Icon(painterResource(R.drawable.tune), null) },
                onClick = {
                    val session = playerConnection?.player?.audioSessionId ?: 0
                    if (!DeviceAudio.openSystemEffects(context, session)) {
                        Toast.makeText(context, context.getString(R.string.system_audio_effects_missing), Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        Text(
            text = "Boosts are paid for with headroom: the whole curve is lowered by as much as its " +
                "loudest point was raised, so nothing clips. Off, the equalizer passes the music through untouched.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    SettingsTopAppBar(
        title = { Text("Equalizer") },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

private fun toneSummary(x: Int, y: Int): String {
    val tilt = when {
        x < 0 -> "Warmer ${-x}"
        x > 0 -> "Brighter $x"
        else -> null
    }
    val contour = when {
        y < 0 -> "Scooped ${-y}"
        y > 0 -> "Forward $y"
        else -> null
    }
    return listOfNotNull(tilt, contour).joinToString(" · ").ifEmpty { "Neutral" }
}

// ---- The response ----------------------------------------------------------------------------

/**
 * The curve the equaliser is making, 20 Hz to 20 kHz on a log scale, preamp included — so it sits
 * below the zero line wherever a boost elsewhere has taken headroom. Morphs from one curve to the
 * next rather than jumping.
 */
@Composable
private fun ResponseCard(curve: EqCurve, enabled: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val target = remember(curve) { FloatArray(RESPONSE_SAMPLES) { curve.responseDb(sampleHz(it)) } }
    var from by remember { mutableStateOf(target) }
    var to by remember { mutableStateOf(target) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(target) {
        // Start from wherever the drawing is now, mid-morph or not.
        val t = morph.value
        from = FloatArray(RESPONSE_SAMPLES) { from[it] + (to[it] - from[it]) * t }
        to = target
        morph.snapTo(0f)
        morph.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
    }
    val glow by animateFloatAsState(if (enabled) 1f else 0.35f, tween(300), label = "eqGlow")

    PreferenceGroup {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(132.dp),
            ) {
                val range = 12f
                fun yOf(db: Float) = size.height / 2f - (db / range).coerceIn(-1f, 1f) * (size.height / 2f - 6.dp.toPx())
                // Grid: the zero line, and decades at 100 Hz, 1 kHz and 10 kHz.
                drawLine(ink.copy(alpha = 0.18f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.dp.toPx())
                for (decade in listOf(100f, 1_000f, 10_000f)) {
                    val x = size.width * xFraction(decade)
                    drawLine(ink.copy(alpha = 0.08f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                }
                val t = morph.value
                val path = Path()
                val fill = Path()
                for (i in 0 until RESPONSE_SAMPLES) {
                    val db = from[i] + (to[i] - from[i]) * t
                    val x = size.width * i / (RESPONSE_SAMPLES - 1)
                    val y = yOf(db)
                    if (i == 0) {
                        path.moveTo(x, y)
                        fill.moveTo(x, size.height / 2f)
                        fill.lineTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                }
                fill.lineTo(size.width, size.height / 2f)
                fill.close()
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.34f * glow), accent.copy(alpha = 0.04f * glow), accent.copy(alpha = 0.34f * glow)),
                    ),
                )
                drawPath(
                    path,
                    accent.copy(alpha = 0.35f + 0.65f * glow),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("20", "100", "1k", "10k", "20k").forEachIndexed { index, label ->
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            4 -> TextAlign.End
                            else -> TextAlign.Center
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private const val RESPONSE_SAMPLES = 72

private fun sampleHz(i: Int): Float = (20.0 * 1_000.0.pow(i.toDouble() / (RESPONSE_SAMPLES - 1))).toFloat()

private fun xFraction(hz: Float): Float = ((log10(hz) - log10(20f)) / 3f).coerceIn(0f, 1f)

// ---- Mode ------------------------------------------------------------------------------------

/** Dynamic | Manual, as one capsule with a thumb that slides between the two. */
@Composable
private fun ModeSwitch(
    selected: SoundEqMode,
    onSelect: (SoundEqMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.08f))
            .padding(3.dp),
    ) {
        val half = maxWidth / 2
        val thumbX by animateDpAsState(
            if (selected == SoundEqMode.DYNAMIC) 0.dp else half,
            spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "eqModeThumb",
        )
        Box(
            Modifier
                .offset(x = thumbX)
                .width(half)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Row(Modifier.fillMaxWidth()) {
            SoundEqMode.entries.forEach { option ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(enabled = enabled) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (option == SoundEqMode.DYNAMIC) "Dynamic" else "Manual",
                        fontSize = 14.sp,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (option == selected) ink else ink.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}

// ---- Tone pad --------------------------------------------------------------------------------

/**
 * The dot grid and its puck, drawn in one canvas. The puck snaps to whole steps with a tick at
 * each and springs between them, and the axes through the centre are drawn up so the origin can be
 * found without a reading.
 */
@Composable
private fun TonePad(
    x: Int,
    y: Int,
    enabled: Boolean,
    onChange: (Int, Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val dot = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val puck = MaterialTheme.colorScheme.surfaceContainerHighest
    val steps = EqLayout.TONE_STEPS
    val latestX by rememberUpdatedState(x)
    val latestY by rememberUpdatedState(y)
    val onChangeLatest by rememberUpdatedState(onChange)
    val animatedX by animateFloatAsState(x.toFloat(), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessHigh), label = "padX")
    val animatedY by animateFloatAsState(y.toFloat(), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessHigh), label = "padY")

    Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var lastX = latestX
                        var lastY = latestY
                        fun report(offset: Offset) {
                            val (nx, ny) = stepAt(offset, size.toSize(), PUCK_RADIUS.toPx(), steps)
                            if (nx != lastX || ny != lastY) {
                                lastX = nx
                                lastY = ny
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onChangeLatest(nx, ny)
                            }
                        }
                        report(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                report(change.position)
                            }
                        }
                    }
                },
        ) {
            val inset = PUCK_RADIUS.toPx()
            val w = size.width - inset * 2
            val h = size.height - inset * 2
            val columns = steps * 2
            val centre = Offset(inset + w * (animatedX + steps) / columns, inset + h * (steps - animatedY) / columns)
            // A pool of the accent around the puck, so where it sits reads from across the room.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    center = centre,
                    radius = size.minDimension * 0.45f,
                ),
                radius = size.minDimension * 0.45f,
                center = centre,
            )
            for (column in 0..columns) {
                for (row in 0..columns) {
                    val onAxis = column == steps || row == steps
                    val p = Offset(inset + w * column / columns, inset + h * row / columns)
                    // Dots near the puck swell a little, as if it were pressing into the grid.
                    val near = (1f - ((p - centre).getDistance() / (inset * 5f))).coerceIn(0f, 1f)
                    drawCircle(
                        color = dot.copy(alpha = (if (onAxis) 0.55f else 0.28f) + 0.3f * near),
                        radius = 2.5.dp.toPx() * (1f + 0.6f * near),
                        center = p,
                    )
                }
            }
            for (ring in 3 downTo 1) {
                drawCircle(Color.Black.copy(alpha = 0.16f / ring), inset + ring * 2f, centre.copy(y = centre.y + ring))
            }
            drawCircle(puck, inset, centre)
            drawCircle(accent, inset * 0.42f, centre)
        }
        // The four directions, in the corners they point to.
        val label = MaterialTheme.colorScheme.onSurfaceVariant
        Text("Forward", fontSize = 11.sp, color = label, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-12).dp))
        Text("Scooped", fontSize = 11.sp, color = label, modifier = Modifier.align(Alignment.BottomCenter).offset(y = 12.dp))
        Text("Warmer", fontSize = 11.sp, color = label, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-6).dp))
        Text("Brighter", fontSize = 11.sp, color = label, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 6.dp))
    }
}

private fun stepAt(offset: Offset, size: Size, inset: Float, steps: Int): Pair<Int, Int> {
    val w = (size.width - inset * 2).coerceAtLeast(1f)
    val h = (size.height - inset * 2).coerceAtLeast(1f)
    val fx = ((offset.x - inset) / w).coerceIn(0f, 1f)
    val fy = ((offset.y - inset) / h).coerceIn(0f, 1f)
    return ((fx * steps * 2).roundToInt() - steps) to (steps - (fy * steps * 2).roundToInt())
}

@Composable
private fun ToneReadout(x: Int, y: Int) {
    Text(
        text = toneSummary(x, y),
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp),
    )
}

@Composable
private fun ChoiceRow(label: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(detail, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// ---- Manual ----------------------------------------------------------------------------------

/** Every preset as a capsule with its own little curve, in one row that scrolls. */
@Composable
private fun PresetStrip(selected: SoundPreset, enabled: Boolean, onSelect: (SoundPreset) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Column {
        Text(
            text = "Presets",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoundPreset.entries.filter { it != SoundPreset.CUSTOM || selected == SoundPreset.CUSTOM }.forEach { preset ->
                val chosen = preset == selected
                val shape = CircleShape
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (chosen) accent else ink.copy(alpha = 0.08f))
                        .clickable(enabled = enabled && preset != SoundPreset.CUSTOM) { onSelect(preset) }
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    val lineColor = if (chosen) MaterialTheme.colorScheme.onPrimary else accent
                    Canvas(Modifier.size(width = 22.dp, height = 14.dp)) {
                        if (preset.bandsDb.isEmpty()) return@Canvas
                        val path = Path()
                        preset.bandsDb.forEachIndexed { i, db ->
                            val px = size.width * i / (preset.bandsDb.size - 1)
                            val py = size.height / 2f - db / 8f * size.height / 2f
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(path, lineColor, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = preset.label,
                        fontSize = 14.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (chosen) MaterialTheme.colorScheme.onPrimary else ink,
                    )
                }
            }
        }
    }
}

/** Seven vertical faders, drawn, filling from the centre because the value is signed. */
@Composable
private fun BandFaders(bands: List<Float>, enabled: Boolean, onChange: (List<Float>) -> Unit) {
    val bandsLatest by rememberUpdatedState(bands)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        EqLayout.MANUAL_BANDS_HZ.forEachIndexed { index, hz ->
            val value = bands.getOrElse(index) { 0f }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatGain(value),
                    fontSize = 11.sp,
                    color = if (abs(value) < 0.05f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(8.dp))
                BandFader(
                    value = value,
                    enabled = enabled,
                    onChange = { updated -> onChange(bandsLatest.toMutableList().also { it[index] = updated }) },
                )
                Spacer(Modifier.height(8.dp))
                Text(formatFrequency(hz), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun BandFader(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.primary
    val knob = MaterialTheme.colorScheme.surfaceContainerHighest
    val range = EqLayout.MANUAL_RANGE_DB
    val latest by rememberUpdatedState(value)
    val onChangeLatest by rememberUpdatedState(onChange)
    Canvas(
        modifier = Modifier
            .width(34.dp)
            .height(160.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var last = latest
                    fun report(offset: Offset) {
                        val inset = KNOB_RADIUS.toPx()
                        val usable = (size.height - inset * 2).coerceAtLeast(1f)
                        val fraction = ((offset.y - inset) / usable).coerceIn(0f, 1f)
                        val snapped = snapGain((1f - fraction * 2f) * range, range)
                        if (abs(snapped - last) > 0.001f) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            last = snapped
                            onChangeLatest(snapped)
                        }
                    }
                    report(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            report(change.position)
                        }
                    }
                }
            },
    ) {
        val inset = KNOB_RADIUS.toPx()
        val usable = size.height - inset * 2
        val centreY = inset + usable / 2
        val trackWidth = 5.dp.toPx()
        val cx = size.width / 2
        val knobY = inset + usable * (1f - (value / range + 1f) / 2f)
        drawRoundRect(track, Offset(cx - trackWidth / 2, inset), Size(trackWidth, usable), CornerRadius(trackWidth / 2))
        if (abs(value) > 0.05f) {
            drawRoundRect(
                fill,
                Offset(cx - trackWidth / 2, minOf(centreY, knobY)),
                Size(trackWidth, abs(centreY - knobY)),
                CornerRadius(trackWidth / 2),
            )
        }
        drawLine(track.copy(alpha = 0.5f), Offset(cx - trackWidth, centreY), Offset(cx + trackWidth, centreY), 1.dp.toPx())
        drawCircle(Color.Black.copy(alpha = 0.18f), inset + 1.5f, Offset(cx, knobY + 1.5f))
        drawCircle(knob, inset, Offset(cx, knobY))
    }
}

/** Half-decibel steps, with a detent at zero — the value a band is dragged back to most. */
private fun snapGain(raw: Float, range: Float): Float {
    if (abs(raw) < 0.6f) return 0f
    return ((raw * 2f).roundToInt() / 2f).coerceIn(-range, range)
}

private fun formatGain(value: Float): String = when {
    abs(value) < 0.05f -> "0"
    value > 0 -> "+" + trimGain(value)
    else -> trimGain(value)
}

private fun trimGain(value: Float): String =
    if (abs(value - value.roundToInt()) < 0.05f) value.roundToInt().toString()
    else String.format(Locale.getDefault(), "%.1f", value)

private fun formatFrequency(hz: Float): String = when {
    hz < 1_000f -> hz.roundToInt().toString()
    hz % 1_000f == 0f -> "${(hz / 1_000f).roundToInt()}k"
    else -> String.format(Locale.getDefault(), "%.1fk", hz / 1_000f)
}

// ---- Balance ---------------------------------------------------------------------------------

/** L and R with the trim between, and a detent at centre, where it spends its life. */
@Composable
private fun BalanceControl(balance: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val fill = MaterialTheme.colorScheme.primary
    val knob = MaterialTheme.colorScheme.surfaceContainerHighest
    val latest by rememberUpdatedState(balance)
    val onChangeLatest by rememberUpdatedState(onChange)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("L", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = when {
                    abs(balance) < 0.005f -> "Centre"
                    balance < 0 -> "Left ${(abs(balance) * 100).roundToInt()}%"
                    else -> "Right ${(balance * 100).roundToInt()}%"
                },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text("R", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(10.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(KNOB_RADIUS * 2 + 8.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        var last = latest
                        fun report(offset: Offset) {
                            val inset = KNOB_RADIUS.toPx()
                            val usable = (size.width - inset * 2).coerceAtLeast(1f)
                            val raw = ((offset.x - inset) / usable).coerceIn(0f, 1f) * 2f - 1f
                            val snapped = if (abs(raw) < 0.04f) 0f else raw
                            if (abs(snapped - last) > 0.004f) {
                                if (snapped == 0f && last != 0f) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                last = snapped
                                onChangeLatest(snapped)
                            }
                        }
                        report(down.position)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            if (change.positionChanged()) {
                                change.consume()
                                report(change.position)
                            }
                        }
                    }
                },
        ) {
            val inset = KNOB_RADIUS.toPx()
            val usable = size.width - inset * 2
            val cy = size.height / 2
            val cx = inset + usable / 2
            val trackHeight = 5.dp.toPx()
            val knobX = inset + usable * (balance + 1f) / 2f
            drawRoundRect(track, Offset(inset, cy - trackHeight / 2), Size(usable, trackHeight), CornerRadius(trackHeight / 2))
            if (abs(balance) > 0.004f) {
                drawRoundRect(
                    fill,
                    Offset(minOf(cx, knobX), cy - trackHeight / 2),
                    Size(abs(cx - knobX), trackHeight),
                    CornerRadius(trackHeight / 2),
                )
            }
            drawCircle(Color.Black.copy(alpha = 0.18f), inset + 1.5f, Offset(knobX, cy + 1.5f))
            drawCircle(knob, inset, Offset(knobX, cy))
        }
    }
}

private val PUCK_RADIUS = 19.dp
private val KNOB_RADIUS = 10.dp
