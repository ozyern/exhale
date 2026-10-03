/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.core.animateFloatAsState
import com.ozyern.exhale.ui.theme.MotionTokens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import me.saket.squiggles.SquigglySlider
import kotlin.math.roundToInt

val LocalPreferenceInGroup = compositionLocalOf { false }

/** Icon-puck geometry, shared with the top-level Settings rows. */
private val PreferenceIconSize = 30.dp

/**
 * iOS's system colours, the ones Settings paints its row icons with. Grey is deliberately absent:
 * the pick is a hash, and a one-in-nine chance of grey means a page can come up with three pale
 * squares in a row, which reads as icons that failed to load.
 */
private val PreferenceIconColors = listOf(
    Color(0xFF0A84FF), // blue
    Color(0xFF30D158), // green
    Color(0xFFFF9F0A), // orange
    Color(0xFFFF375F), // pink
    Color(0xFFBF5AF2), // purple
    Color(0xFF5E5CE6), // indigo
    Color(0xFFFF453A), // red
    Color(0xFF64D2FF), // cyan
)

/** A stable colour for [key], so a row is the same colour every time the page is opened. */
private fun preferenceIconColor(key: String): Color =
    PreferenceIconColors[(key.hashCode() and 0x7fffffff) % PreferenceIconColors.size]

/**
 * Hands each row in a group the next colour in the palette.
 *
 * A hash gives every row *a* colour and no row a colour that relates to its neighbours, so a page
 * of ten settings came up with three reds in a row and read as random - which is what it was.
 * Walking the palette instead means consecutive rows never repeat, and the page looks chosen. Each
 * row takes its slot once, in `remember`, so it keeps that colour for as long as it is on screen.
 */
@Stable
class PreferenceIconSequence {
    private var next = 0
    fun take(): Int = next++
}

private val LocalPreferenceIconSequence = compositionLocalOf<PreferenceIconSequence?> { null }

@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    /**
     * What the icon's colour is picked from. Defaults to the row's own position in the tree, which
     * is stable for a given page; pass a name to tie two rows to the same colour.
     */
    iconTintKey: String = currentCompositeKeyHashCode.toString(),
    subtitle: (@Composable () -> Unit)? = null,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    /** The current choice, drawn at the right before the chevron. */
    value: String? = null,
) {
    val inGroup = LocalPreferenceInGroup.current
    val sequence = LocalPreferenceIconSequence.current
    val slot = remember(sequence) { sequence?.take() }
    val iconColor = if (slot != null) {
        PreferenceIconColors[slot % PreferenceIconColors.size]
    } else {
        preferenceIconColor(iconTintKey)
    }
    // Subtle premium haptic tick on every preference tap (respects the user's haptics setting
    // via the app-wide LocalHapticFeedback provider).
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !inGroup) MotionTokens.PressedScale else 1f,
        animationSpec = MotionTokens.PressSpring,
        label = "prefScale",
    )

    val rowContent: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                // Grouped rows bounce individually; standalone rows bounce via the Card.
                // Instant (ACTION_DOWN) observer — avoids the ~100ms scroll tap-delay.
                .then(if (inGroup) Modifier.pressScaleContainer() else Modifier)
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (inGroup) LocalIndication.current else null,
                    enabled = isEnabled && onClick != null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick?.invoke()
                    },
                )
                .alpha(if (isEnabled) 1f else 0.45f)
                // The rows breathe: taller, and the title a size up.
                .heightIn(min = 60.dp)
                .padding(horizontal = 18.dp, vertical = 15.dp),
        ) {
            if (icon != null) {
                // The same puck the top-level Settings list uses: a solid iOS system colour with a
                // white glyph on it, picked from the row's own key so a row keeps its colour
                // between visits. Sub-pages used to draw the glyph in the brand accent on a 12%
                // wash of the same accent, which on a page of ten rows is ten near-identical
                // pastel squares - and it read as a different app from the Settings page that
                // linked to it.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .size(PreferenceIconSize),
                    contentAlignment = Alignment.Center,
                ) {
                    // The glyph carries the colour itself - see the note on the Settings row.
                    // One quiet ink for every glyph: a page of rows in eight
                    // colours read as noise, and the glyph is a signpost, not decoration.
                    CompositionLocalProvider(
                        LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                    ) {
                        icon()
                    }
                }
                Spacer(Modifier.width(14.dp))
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    title()
                }

                subtitle?.let {
                    Spacer(Modifier.height(2.dp))
                    ProvideTextStyle(
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        it()
                    }
                }

                if (description != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                content?.invoke()
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    trailingContent()
                }
            } else if (value != null || onClick != null) {
                // A row that opens something says so: its current value, then a chevron.
                Spacer(Modifier.width(12.dp))
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 150.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Icon(
                    painter = painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    if (inGroup) {
        // Every row but the first draws the hairline above it.
        //
        // This used to be the page's job: twenty screens each placing `PreferenceGroupDivider()`
        // by hand between rows, and a page that forgot - Appearance forgot five times - showed a
        // group whose rows ran together with one stray line in the middle of it. A row knows
        // whether it is the first in its group, so it can rule itself.
        if (slot != null && slot > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(start = SettingsDividerStartIndent),
                thickness = SettingsDividerThickness,
                color = settingsDividerColor(),
            )
        }
        rowContent()
    } else {
        // Apple Music-style inset row: NO Material Card (no elevation, no border, no tonal
        // tint). A 16dp clip over the SOLID group surface — the same colour the grouped cards
        // use — so a standalone row and a row inside a group are visibly the same material.
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
        ) {
            rowContent()
        }
    }
}

@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showMenu by remember {
        mutableStateOf(false)
    }

    // The choice opens as a pull-down from this row, as iOS does it — not a dialog over the page.
    Box(modifier = modifier) {
        PreferenceEntry(
            title = title,
            value = valueText(selectedValue),
            icon = icon,
            onClick = { showMenu = true },
            isEnabled = isEnabled,
        )
        PullDownMenu(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            options = values,
            selected = selectedValue,
            label = valueText,
            onSelect = onValueSelected,
        )
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    ListPreference(
        modifier = modifier,
        title = title,
        icon = icon,
        selectedValue = selectedValue,
        values = enumValues<T>().toList(),
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    // Direct toggle drags fire their own haptic tick; row taps get theirs from PreferenceEntry.
    val haptic = LocalHapticFeedback.current
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        trailingContent = {
            LiquidToggle(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(it)
                },
                modifier = if (!isEnabled) Modifier.alpha(0.38f) else Modifier
            )
        },
        onClick = { if (isEnabled) onCheckedChange(!checked) },
        isEnabled = isEnabled
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            initialTextFieldValue =
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length),
                ),
            singleLine = singleLine,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value)
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue)
            },
            onCancel = {
                sliderValue = value
                showDialog = false
            },
            onReset = {
                sliderValue = 30f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.seconds,
                            sliderValue.roundToInt(),
                            sliderValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    SquigglySlider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 15f..60f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value.roundToInt().toString(),
        icon = icon,
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CrossfadeSliderPreference(
    modifier: Modifier = Modifier,
    value: Int,
    onValueChange: (Int) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember { mutableStateOf(false) }
    var localValue by remember { mutableFloatStateOf(value.toFloat()) }

    // Actualizar localValue cuando value cambia desde fuera
    androidx.compose.runtime.LaunchedEffect(value) {
        localValue = value.toFloat()
    }

    val displayValue = localValue.roundToInt().coerceIn(0, 10)
    val isCrossfadeEnabled = displayValue > 0

    val descriptionText = when (displayValue) {
        0 -> stringResource(R.string.crossfade_disabled_description)
        else -> pluralStringResource(R.plurals.seconds, displayValue, displayValue)
    }

    if (showDialog) {
        ActionPromptDialog(
            title = stringResource(R.string.audio_crossfade_title),
            onDismiss = { showDialog = false },
            onConfirm = {
                val finalValue = localValue.roundToInt().coerceIn(0, 10)
                onValueChange(finalValue)
                showDialog = false
            },
            onCancel = {
                localValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                localValue = 0f
            },
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Valor actual
                    Text(
                        text = if (displayValue == 0) {
                            stringResource(R.string.dark_theme_off)
                        } else {
                            pluralStringResource(R.plurals.seconds, displayValue, displayValue)
                        },
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCrossfadeEnabled)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(24.dp))

                    // Slider
                    SquigglySlider(
                        value = localValue,
                        onValueChange = {
                            localValue = it.roundToInt()
                                .coerceIn(0, 10)
                                .toFloat()
                        },
                        valueRange = 0f..10f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Marcas del slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0, 2, 4, 6, 8, 10).forEach { mark ->
                            Text(
                                text = "${mark}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (displayValue >= mark && mark > 0)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.audio_crossfade_title))
                if (isCrossfadeEnabled) {
                    CrossfadeBadge(duration = displayValue)
                }
            }
        },
        description = descriptionText,
        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
private fun CrossfadeBadge(duration: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier
            .padding(start = 8.dp)
            .size(width = 48.dp, height = 24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "${duration}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberPickerPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 10,
    valueText: (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    title()
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                sliderValue = rounded.toFloat()
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = minValue.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                    Text(
                        text = valueText(rounded),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Spacer(Modifier.height(16.dp))

                    SquigglySlider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it.roundToInt()
                                .coerceIn(minValue, maxValue)
                                .toFloat()
                        },
                        valueRange = minValue.toFloat()..maxValue.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = valueText(value),
        icon = icon,
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        if (title != null) {
            Text(
                text = title.uppercase(java.util.Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.7.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 30.dp, bottom = 10.dp),
            )
        }
        // The same plate the Settings page floats its groups on - `settingsGlassGroup`, not a
        // 5% wash of onSurface behind a 16dp clip. Two cards claiming to be the same grouped
        // table were being drawn two different ways: the landing page had a rim, a gradient and
        // a 22dp corner, and every page it linked to had a flat grey box.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsGlassGroup(RoundedCornerShape(SettingsGroupCornerRadius)),
        ) {
            val sequence = remember { PreferenceIconSequence() }
            CompositionLocalProvider(
                LocalPreferenceInGroup provides true,
                LocalPreferenceIconSequence provides sequence,
            ) {
                Column(content = content)
            }
        }
    }
}

@Composable
fun PreferenceGroupDivider(modifier: Modifier = Modifier) {
    // Nothing, inside a group: the rows rule themselves now - see PreferenceEntry. Kept because
    // twenty settings pages call it between their rows, and drawing it here as well would double
    // every hairline in the app.
    if (!LocalPreferenceInGroup.current) {
        HorizontalDivider(
            modifier = modifier.padding(start = SettingsDividerStartIndent),
            thickness = SettingsDividerThickness,
            color = settingsDividerColor(),
        )
    }
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    // A label, not a headline - the same one the Settings page sets over "Essentials".
    //
    // At headlineSmall/Bold this was larger and heavier than the row titles underneath it, so a
    // sub-page read as a stack of competing headlines with the settings squeezed between them,
    // and nothing on the page matched the page that linked to it.
    // The section caption: small capitals over the card, set in from its edge.
    Text(
        text = title.uppercase(java.util.Locale.getDefault()),
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.7.sp, fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 10.dp),
    )
}