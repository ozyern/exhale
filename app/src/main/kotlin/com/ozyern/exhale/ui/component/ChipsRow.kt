/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.unit.Dp
import com.ozyern.exhale.ui.utils.bleedStart
import android.annotation.SuppressLint
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import com.ozyern.exhale.ui.ios.compat.DropdownMenu
import com.ozyern.exhale.ui.ios.compat.DropdownMenuItem
import com.ozyern.exhale.ui.ios.compat.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFChevronDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import com.ozyern.exhale.LocalAmbientBackgroundColor
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Tune
import com.ozyern.exhale.ui.ios.compat.VerticalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.screens.OptionStats
import com.ozyern.exhale.ui.theme.LocalPageMetrics

@Composable
fun <T> ChipsRow(
    chips: List<Pair<T, String>>,
    currentValue: T,
    onValueUpdate: (T) -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = AppTheme.colorScheme.surfaceContainer,
    // Callers over blurred hero backdrops pass tint/transparent colors so chips read as glass, not a card.
    selectedContainerColor: Color = AppTheme.colorScheme.secondaryContainer,
    labelColor: Color = Color.Unspecified,
    selectedLabelColor: Color = Color.Unspecified,
    scrollState: ScrollState = rememberScrollState(),
    /** Width of the floating side bar the chips may scroll under; the first chip still starts clear of it. */
    sideInset: Dp = 0.dp,
) {
    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val ambientColor = LocalAmbientBackgroundColor.current.value
    val defaultContainerColor = com.ozyern.exhale.ui.theme.GlassTintTokens.baseSurfaceColor(isDark).copy(
        alpha = if (isDark) com.ozyern.exhale.ui.theme.GlassTintTokens.NavPillFillOpacityDark else com.ozyern.exhale.ui.theme.GlassTintTokens.NavPillFillOpacityLight
    )
    val defaultSelectedContainerColor = com.ozyern.exhale.ui.theme.GlassTintTokens.baseSurfaceColor(isDark).copy(
        alpha = if (isDark) com.ozyern.exhale.ui.theme.GlassTintTokens.PillSelectedFillOpacityDark else com.ozyern.exhale.ui.theme.GlassTintTokens.PillSelectedFillOpacityLight
    )
    val defaultLabelColor = com.ozyern.exhale.ui.theme.GlassTintTokens.glassContentColor(
        isDark = isDark,
        activeTint = ambientColor,
        isSelected = false,
    )
    val defaultSelectedLabelColor = com.ozyern.exhale.ui.theme.GlassTintTokens.glassContentColor(
        isDark = isDark,
        activeTint = ambientColor,
        isSelected = false, // Ambient tint for applied filter chip
    )
    val pillBorder = androidx.compose.foundation.BorderStroke(1.dp, com.ozyern.exhale.ui.theme.GlassTintTokens.borderColor(isDark))

    val effectiveContainerColor = if (containerColor != AppTheme.colorScheme.surfaceContainer) containerColor else defaultContainerColor
    val effectiveSelectedContainerColor = if (selectedContainerColor != AppTheme.colorScheme.secondaryContainer) selectedContainerColor else defaultSelectedContainerColor
    val effectiveLabelColor = if (labelColor.isSpecified) labelColor else defaultLabelColor
    val effectiveSelectedLabelColor = if (selectedLabelColor.isSpecified) selectedLabelColor else defaultSelectedLabelColor

    Row(
        modifier =
        modifier
            .fillMaxWidth()
            .bleedStart(sideInset)
            .horizontalScroll(scrollState)
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
            .padding(vertical = 8.dp),
    ) {
        Spacer(Modifier.width(LocalPageMetrics.current.gutter + sideInset))

        chips.forEachIndexed { index, (value, label) ->
            val isSelected = currentValue == value

            val cornerRadius by animateDpAsState(
                // Selected chips expand into a full pill.
                targetValue = if (isSelected) 100.dp else 8.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "corner_radius"
            )

            FilterChip(
                label = { Text(label) },
                selected = isSelected,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = effectiveContainerColor,
                    selectedContainerColor = effectiveSelectedContainerColor,
                    labelColor = effectiveLabelColor,
                    selectedLabelColor = effectiveSelectedLabelColor,
                ),
                onClick = { onValueUpdate(value) },
                shape = RoundedCornerShape(cornerRadius),
                border = pillBorder,
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            )

            Spacer(Modifier.width(8.dp))
        }
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
    containerColor: Color = AppTheme.colorScheme.surfaceContainer,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    var expandIconDegree by remember { mutableFloatStateOf(0f) }
    val rotationAnimation by animateFloatAsState(
        targetValue = expandIconDegree,
        animationSpec = tween(durationMillis = 400),
        label = "rotation",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        ) {
            Spacer(Modifier.width(12.dp))
            Box(contentAlignment = Alignment.Center) {
                FilterChip(
                    selected = false,
                    modifier = Modifier
                        .padding(horizontal = 4.dp),
                    onClick = {
                        menuExpanded = !menuExpanded
                        expandIconDegree -= 180
                    },
                    label = {
                        Text(
                            text = when (selectedOption) {
                                OptionStats.WEEKS -> stringResource(id = R.string.weeks)
                                OptionStats.MONTHS -> stringResource(id = R.string.months)
                                OptionStats.YEARS -> stringResource(id = R.string.years)
                                OptionStats.CONTINUOUS -> stringResource(id = R.string.continuous)
                            }
                        )
                    },
                    leadingIcon = {
                        SymbolIcon(symbol = sf("slider.horizontal.3"), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                    },
                    trailingIcon = {
                        SymbolIcon(symbol = sf("SFChevronDown"), contentDescription = null, size = 14.dp, modifier = Modifier
                                .graphicsLayer(rotationZ = rotationAnimation))
                    },
                    shape = RoundedCornerShape(16.dp),
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = containerColor,
                        selectedContainerColor = AppTheme.colorScheme.primaryContainer,
                        labelColor = AppTheme.colorScheme.onSurface,
                        selectedLabelColor = AppTheme.colorScheme.onPrimaryContainer
                    )
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = {
                        menuExpanded = false
                        expandIconDegree += 180
                    },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option.second) },
                            onClick = {
                                onSelectionChange(option.first)
                                expandIconDegree += 180
                                menuExpanded = false
                            },
                        )
                    }
                }
            }

            Box(
                Modifier
                    .height(FilterChipDefaults.Height)
                    .padding(horizontal = 4.dp)
                    .align(Alignment.CenterVertically)
            ) {
                VerticalDivider()
            }

            chips.forEach { (value, label) ->
                val isSelected = currentValue == value

                val cornerRadius by animateDpAsState(
                    targetValue = if (isSelected) 20.dp else 8.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "corner_radius"
                )

                FilterChip(
                    label = { Text(label) },
                    selected = isSelected,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = containerColor,
                    ),
                    onClick = { onValueUpdate(value) },
                    shape = RoundedCornerShape(cornerRadius),
                    border = null,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                )
            }
        }
    }
}
