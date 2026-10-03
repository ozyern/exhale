/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.screens.OptionStats

@Composable
fun <E> ChipsRow(
    chips: List<Pair<E, String>>,
    currentValue: E,
    onValueUpdate: (E) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    icons: Map<E, Int> = emptyMap(),
    segmented: Boolean = chips.size in 2..4 && chips.all { it.second.length <= 10 },
) {
    // A handful of short, fixed choices reads as one control rather than a row of buttons, so it
    // gets the sliding glass thumb you can drag between them. Long or open-ended lists stay chips.
    if (segmented) {
        com.ozyern.exhale.ui.component.liquid.LiquidSegmentedTabs(
            labels = chips.map { it.second },
            selectedIndex = chips.indexOfFirst { it.first == currentValue }.coerceAtLeast(0),
            onSelect = { index -> chips.getOrNull(index)?.let { onValueUpdate(it.first) } },
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.width(12.dp))

        chips.forEach { (value, label) ->
            val isSelected = currentValue == value
            val iconRes = icons[value]

            GlassChip(
                label = label,
                selected = isSelected,
                iconRes = iconRes,
                onClick = { onValueUpdate(value) },
            )

            Spacer(Modifier.width(8.dp))
        }
    }
}

/**
 * A filter chip on the app's own glass.
 *
 * `FilterChip` is the most recognisable Material control there is: a grey tonal capsule that grows
 * a check mark on the left when it is chosen, shifting the label sideways as it does. On a library
 * page whose every other surface is frosted, three of them in a row were the thing that made the
 * page look like a different app - and the shifting label is a layout jump on every tap.
 *
 * So: one capsule of the same glass as the dock, which fills with the accent when it is on. The
 * leading glyph is the chip's own icon and it stays put; selection is carried by colour, which is
 * how iOS marks a chosen segment.
 */
@Composable
private fun GlassChip(
    label: String,
    selected: Boolean,
    iconRes: Int?,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(percent = 50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "glassChipPress",
    )
    val fill by animateColorAsState(
        targetValue = if (selected) accent else Color.Transparent,
        animationSpec = tween(200),
        label = "glassChipFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
        },
        animationSpec = tween(200),
        label = "glassChipInk",
    )

    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(38.dp)
            // In-content glass, NOT `rememberChromeGlassModifier`.
            //
            // That one samples `LocalAppBackdrop` - the layer the whole page is drawn into - which
            // is only legal for chrome composed *outside* the NavHost, like the dock. Used on a
            // chip that lives inside the page, it makes the backdrop contain something that reads
            // the backdrop, and hwui walks that render tree until the native stack runs out: the
            // library tab took the whole process down with a `prepareTreeImpl` overflow.
            .liquidGlassSurface(shape)
            .clip(shape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun <Int> ChoiceChipsRow(
    chips: List<Pair<Int, String>>,
    options: List<Pair<OptionStats, String>>,
    selectedOption: OptionStats,
    onSelectionChange: (OptionStats) -> Unit,
    currentValue: Int,
    onValueUpdate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    var expandIconDegree by remember { mutableFloatStateOf(0f) }
    val rotationAnimation by animateFloatAsState(
        targetValue = expandIconDegree,
        animationSpec = tween(durationMillis = 400),
        label = "",
    )

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }

        Column {
            AssistChip(
                onClick = {
                    expanded = !expanded
                    expandIconDegree -= 180
                },
                label = {
                    Text(
                        text =
                        when (selectedOption) {
                            OptionStats.WEEKS -> stringResource(id = R.string.weeks)
                            OptionStats.MONTHS -> stringResource(id = R.string.months)
                            OptionStats.YEARS -> stringResource(id = R.string.years)
                            OptionStats.CONTINUOUS -> stringResource(id = R.string.continuous)
                        },
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation),
                    )
                },
                shape = RoundedCornerShape(16.dp),
                border = null,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = containerColor,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandIn() + fadeIn(),
                exit = shrinkOut() + fadeOut(),
            ) {
                DropdownMenu(
                    modifier = Modifier.padding(start = 12.dp),
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                        expandIconDegree -= 180
                    },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.second) },
                            onClick = {
                                onSelectionChange(option.first)
                                expandIconDegree -= 180
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = selectedOption,
            transitionSpec = { slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut() },
            label = "",
        ) {
            Row(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                chips.forEach { (value, label) ->
                    Spacer(Modifier.width(8.dp))

                    FilterChip(
                        label = { Text(label) },
                        selected = currentValue == value,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = containerColor,
                        ),
                        onClick = { onValueUpdate(value) },
                        shape = RoundedCornerShape(16.dp),
                        border = null
                    )
                }
            }
        }
    }
}
