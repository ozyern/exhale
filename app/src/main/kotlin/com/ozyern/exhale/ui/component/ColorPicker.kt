/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.window.Dialog
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.IosTextField
import com.ozyern.exhale.ui.ios.compat.SegmentedButton
import com.ozyern.exhale.ui.ios.compat.SingleChoiceSegmentedButtonRow
import com.ozyern.exhale.ui.ios.component.IosSlider
import com.ozyern.exhale.ui.ios.component.IosText
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R

/**
 * Pure HSV/hex color conversions used by [ColorPickerDialog].
 * Kept free of android.* dependencies so they are unit-testable on the JVM.
 */
object ColorPickerConversions {

    /** Converts HSV components (hue 0..360, saturation/value 0..1) to an opaque [Color]. */
    fun hsvToColor(hue: Float, saturation: Float, value: Float): Color {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)

        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c

        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(r + m, g + m, b + m)
    }

    /** Extracts HSV components (hue 0..360, saturation/value 0..1) from [color]. */
    fun colorToHsv(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * ((b - r) / delta + 2f)
            else -> 60f * ((r - g) / delta + 4f)
        }.let { ((it % 360f) + 360f) % 360f }
        val saturation = if (max == 0f) 0f else delta / max
        return Triple(hue, saturation, max)
    }

    /** Formats [color] as an uppercase RRGGBB hex string without the leading '#'. */
    fun colorToHex(color: Color): String {
        val argb = color.toArgb()
        return "%06X".format(argb and 0xFFFFFF)
    }

    /**
     * Parses a RRGGBB hex string (optionally prefixed with '#') to an opaque [Color],
     * or null if the input is not a valid 6-digit hex color.
     */
    fun parseHexColor(hex: String): Color? {
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return null
        val rgb = cleaned.toIntOrNull(16) ?: return null
        return Color(0xFF000000.toInt() or rgb)
    }
}

/**
 * The swatch grid iOS shows on its Colours tab: hue across, lightness down, with a
 * neutral row on top. Generated rather than written out — 72 hard-coded hex literals
 * would say nothing that these two loops do not.
 */
private const val GridColumns = 12

private val gridColors: List<Color> = buildList {
    // iOS puts the neutrals first: white through black across the top row.
    repeat(GridColumns) { i ->
        val level = 1f - i / (GridColumns - 1f)
        add(Color(level, level, level))
    }
    // Then bands of the hue wheel, darkest to palest.
    val bands = listOf(0.45f to 1f, 0.65f to 1f, 0.85f to 1f, 1f to 0.7f, 1f to 0.35f)
    bands.forEach { (v, sat) ->
        repeat(GridColumns) { i ->
            add(ColorPickerConversions.hsvToColor(i * 360f / GridColumns, sat, v))
        }
    }
}

private enum class PickerTab(val label: String) {
    Grid("Grid"),
    Spectrum("Spectrum"),
    Sliders("Sliders"),
}

/**
 * iOS colour picker: segmented Grid/Spectrum/Sliders over a swatch grid, HSB area,
 * or RGB sliders, with the chosen colour and hex below. Signature is unchanged from
 * the Material version, so call sites are untouched.
 */
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
    defaultColor: Color? = null,
    onReset: (() -> Unit)? = null,
) {
    val initialHsv = remember(initialColor) { ColorPickerConversions.colorToHsv(initialColor) }
    var hue by rememberSaveable(initialColor) { mutableStateOf(initialHsv.first) }
    var saturation by rememberSaveable(initialColor) { mutableStateOf(initialHsv.second) }
    var value by rememberSaveable(initialColor) { mutableStateOf(initialHsv.third) }
    var tab by rememberSaveable(initialColor) { mutableStateOf(PickerTab.Grid) }

    val selectedColor = ColorPickerConversions.hsvToColor(hue, saturation, value)
    var hexInput by rememberSaveable(initialColor) {
        mutableStateOf(ColorPickerConversions.colorToHex(initialColor))
    }
    var hexError by rememberSaveable { mutableStateOf(false) }

    fun selectColor(color: Color) {
        val (h, s, v) = ColorPickerConversions.colorToHsv(color)
        hue = h
        saturation = s
        value = v
        hexInput = ColorPickerConversions.colorToHex(color)
        hexError = false
    }

    fun syncHex() {
        hexInput = ColorPickerConversions.colorToHex(
            ColorPickerConversions.hsvToColor(hue, saturation, value)
        )
        hexError = false
    }

    val iosColors = IosTheme.colors

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(ContinuousRoundedRectangle(30.dp))
                .background(iosColors.systemBackground)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IosText(
                    text = title,
                    style = IosTheme.typography.headline,
                    color = iosColors.label,
                    modifier = Modifier.weight(1f),
                )
                IosText(
                    text = stringResource(android.R.string.cancel),
                    style = IosTheme.typography.body,
                    color = IosTheme.tint,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
            }

            Spacer(Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                PickerTab.entries.forEach { entry ->
                    SegmentedButton(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        label = { IosText(entry.label) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when (tab) {
                PickerTab.Grid -> ColorGrid(selectedColor) { selectColor(it) }

                PickerTab.Spectrum -> {
                    SaturationValuePanel(hue, saturation, value) { s, v ->
                        saturation = s
                        value = v
                        syncHex()
                    }
                    Spacer(Modifier.height(16.dp))
                    HueSlider(hue) { h ->
                        hue = h
                        syncHex()
                    }
                }

                PickerTab.Sliders -> {
                    ChannelSlider("Red", selectedColor.red, Color.Red) {
                        selectColor(selectedColor.copy(red = it))
                    }
                    ChannelSlider("Green", selectedColor.green, Color.Green) {
                        selectColor(selectedColor.copy(green = it))
                    }
                    ChannelSlider("Blue", selectedColor.blue, Color.Blue) {
                        selectColor(selectedColor.copy(blue = it))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(ContinuousCapsule())
                        .background(selectedColor)
                        .border(1.dp, iosColors.separator, ContinuousCapsule())
                )
                Spacer(Modifier.width(12.dp))
                IosTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        hexInput = input.take(7)
                        val parsed = ColorPickerConversions.parseHexColor(input)
                        hexError = parsed == null
                        if (parsed != null) {
                            val (h, sat, v) = ColorPickerConversions.colorToHsv(parsed)
                            hue = h
                            saturation = sat
                            value = v
                        }
                    },
                    placeholder = stringResource(R.string.color_picker_hex),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (defaultColor != null || onReset != null) {
                    PickerAction(
                        label = stringResource(R.string.reset),
                        prominent = false,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (onReset != null) onReset() else defaultColor?.let { selectColor(it) }
                    }
                }
                PickerAction(
                    label = stringResource(android.R.string.ok),
                    prominent = true,
                    modifier = Modifier.weight(1f),
                ) { onConfirm(selectedColor) }
            }
        }
    }
}

@Composable
private fun PickerAction(
    label: String,
    prominent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iosColors = IosTheme.colors
    Box(
        modifier = modifier
            .clip(ContinuousCapsule())
            .background(if (prominent) IosTheme.tint else iosColors.secondarySystemFill)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        IosText(
            text = label,
            style = IosTheme.typography.headline,
            color = if (prominent) iosColors.onAccent else iosColors.label,
        )
    }
}

@Composable
private fun ColorGrid(selectedColor: Color, onPick: (Color) -> Unit) {
    val selectedArgb = selectedColor.toArgb()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        gridColors.chunked(GridColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { swatch ->
                    val isSelected = swatch.toArgb() == selectedArgb
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(swatch)
                            // iOS rings the chosen swatch; it does not check it.
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.dp, IosTheme.colors.label, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onPick(swatch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    channel: Float,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Row {
            IosText(
                text = label,
                style = IosTheme.typography.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.weight(1f),
            )
            IosText(
                text = (channel * 255f).toInt().toString(),
                style = IosTheme.typography.footnote,
                color = IosTheme.colors.secondaryLabel,
            )
        }
        IosSlider(value = channel, onValueChange = onChange, tint = accent)
    }
}

@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
) {
    val hueColor = ColorPickerConversions.hsvToColor(hue, 1f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(hue) {
                detectTapGestures { offset ->
                    onChange(
                        (offset.x / size.width).coerceIn(0f, 1f),
                        1f - (offset.y / size.height).coerceIn(0f, 1f),
                    )
                }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange(
                        (change.position.x / size.width).coerceIn(0f, 1f),
                        1f - (change.position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))

            val thumbCenter = Offset(saturation * size.width, (1f - value) * size.height)
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = 10.dp.toPx(), center = thumbCenter)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = thumbCenter)
            drawCircle(
                ColorPickerConversions.hsvToColor(hue, saturation, value),
                radius = 6.dp.toPx(),
                center = thumbCenter,
            )
        }
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onChange: (hue: Float) -> Unit,
) {
    val hueColors = remember {
        List(7) { i -> ColorPickerConversions.hsvToColor(i * 60f, 1f, 1f) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.horizontalGradient(hueColors))

            val thumbCenter = Offset((hue / 360f) * size.width, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = 10.dp.toPx(), center = thumbCenter)
            drawCircle(Color.White, radius = 8.dp.toPx(), center = thumbCenter)
            drawCircle(
                ColorPickerConversions.hsvToColor(hue, 1f, 1f),
                radius = 6.dp.toPx(),
                center = thumbCenter,
            )
        }
    }
}
