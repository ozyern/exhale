/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.settingsIconPuck

/**
 * Integrations rendered as a true the iOS music app inset grouped list — full-width rows
 * stacked vertically inside one block clipped to 16dp over a frosted surface, with a
 * thin divider BETWEEN rows only (never top/bottom). The horizontal pill carousel is
 * gone: iOS's Settings never scroll sideways.
 */
@Composable
fun SettingsIntegrationsSection(
    integrations: List<SettingsIntegrationAction>,
    modifier: Modifier = Modifier,
) {
    if (integrations.isEmpty()) return

    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.integrations))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.GroupCardCornerRadius)),
        ) {
            integrations.forEachIndexed { index, action ->
                IntegrationRow(
                    action = action,
                    showDivider = index < integrations.size - 1,
                )
            }
        }
    }
}

@Composable
private fun IntegrationRow(
    action: SettingsIntegrationAction,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.09f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "integrationRowBg",
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = action.onClick,
                )
                .padding(
                    horizontal = SettingsDimensions.RowHorizontalPadding,
                    vertical = SettingsDimensions.RowVerticalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(SettingsDimensions.RowIconSize)
                    .settingsIconPuck(
                        action.accentColor,
                        RoundedCornerShape(SettingsDimensions.RowIconCornerRadius),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = action.icon,
                    contentDescription = null,
                    tint = action.accentColor,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(SettingsDimensions.ChevronSize),
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = SettingsDimensions.DividerStartIndent),
                thickness = SettingsDimensions.DividerThickness,
                color = SettingsDimensions.dividerColor(),
            )
        }
    }
}
