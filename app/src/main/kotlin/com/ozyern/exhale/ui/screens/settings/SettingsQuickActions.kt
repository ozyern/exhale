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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.settingsIconPuck

/**
 * The primary settings destinations (Appearance, Player, Storage, Privacy), as a grid of tiles.
 *
 * These were full-width rows in a grouped card, identical to every other section on the page. That
 * is the thing that made Settings tiring to look at: hero, then five consecutive blocks of the same
 * icon-title-chevron row, with nothing but a caption to tell you which block you were in and no
 * visual anchor anywhere below the top of the page.
 *
 * Tiles here give the page a rhythm — hero, then a grid, then the lists — and it is the same rhythm
 * About uses (statement card, stat pair, table), so the two screens now read as one design rather
 * than two. It also earns the "essentials" label: these four are meant to be the shortcuts, and a
 * shortcut that looks exactly like everything else is not one.
 */
@Composable
fun SettingsQuickActionsSection(
    actions: List<SettingsQuickAction>,
    columns: Int = SettingsDimensions.CompactColumns,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    val perRow = columns.coerceAtLeast(1)

    Column(modifier = modifier) {
        SettingsSectionHeader(stringResource(R.string.settings_group_essentials))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Chunked Rows rather than a LazyVerticalGrid: this whole section is a single item
            // inside the settings LazyColumn, and nesting a lazy scrollable in a lazy scrollable
            // along the same axis throws at measure time.
            actions.chunked(perRow).forEach { rowActions ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowActions.forEach { action ->
                        QuickActionTile(
                            action = action,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a short final row's tiles the same width as a full one instead of
                    // letting a lone tile stretch across the screen.
                    repeat(perRow - rowActions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    action: SettingsQuickAction,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SettingsAnimations.TilePressScale else 1f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "quickActionTileScale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .settingsGlassGroup(RoundedCornerShape(SettingsDimensions.QuickActionCardCornerRadius))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = action.onClick,
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(SettingsDimensions.QuickActionIconSize)
                .settingsIconPuck(
                    action.accentColor,
                    RoundedCornerShape(SettingsDimensions.RowIconCornerRadius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = action.icon,
                contentDescription = action.label,
                tint = if (action.coloredIcon) androidx.compose.ui.graphics.Color.Unspecified else action.accentColor,
                modifier = Modifier.size(SettingsDimensions.QuickActionIconInnerSize),
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = action.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickActionRow(
    action: SettingsQuickAction,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.09f else 0f,
        animationSpec = SettingsAnimations.pressSpring(),
        label = "quickActionRowBg",
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
                    contentDescription = action.label,
                    tint = if (action.coloredIcon) androidx.compose.ui.graphics.Color.Unspecified else action.accentColor,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
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
