/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.ozyern.exhale.ui.ios.compat.DropdownMenu
import com.ozyern.exhale.ui.ios.compat.DropdownMenuItem
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.IconButton
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R

/**
 * One sort option: the value to select and the string shown for it.
 */
data class SortOption<T>(val value: T, val labelRes: Int)

/**
 * Compact sort control for list headers. Unlike [SortHeader], it uses one icon so list content
 * can start higher on screen. A checkmark shows the selected option; callers can also display
 * the current sort while the menu is closed.
 */
@Composable
fun <T> SortPopupButton(
    options: List<SortOption<T>>,
    selected: T,
    descending: Boolean,
    onSelectedChange: (T) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    /** Extra rows above the sort fields — grid density, view style, whatever the screen has. */
    leadingSection: (@Composable ColumnScope.() -> Unit)? = null,
    /** False for orders with no direction, such as a custom arrangement. */
    showDescending: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            SymbolIcon(symbol = sf("slider.horizontal.3"), contentDescription = stringResource(R.string.sort_by), size = 24.dp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (leadingSection != null) {
                leadingSection()
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            options.forEach { option ->
                SortMenuRow(
                    label = stringResource(option.labelRes),
                    checked = option.value == selected,
                    onClick = {
                        onSelectedChange(option.value)
                        expanded = false
                    },
                )
            }

            if (showDescending) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

                SortMenuRow(
                    label = stringResource(R.string.sort_descending),
                    checked = descending,
                    // Deliberately does NOT dismiss: direction is the one option people flip
                    // back and forth to compare, and reopening the menu for each flip is the
                    // kind of friction that makes a sort control feel heavy.
                    onClick = { onDescendingChange(!descending) },
                    trailingIcon = R.drawable.arrow_downward,
                    trailingRotated = !descending,
                )
            }
        }
    }
}

@Composable
private fun SortMenuRow(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    trailingIcon: Int? = null,
    trailingRotated: Boolean = false,
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = AppTheme.typography.bodyLarge,
                    color = if (checked) {
                        AppTheme.colorScheme.primary
                    } else {
                        AppTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(24.dp))
            }
        },
        trailingIcon = {
            when {
                trailingIcon != null -> Icon(
                    painter = painterResource(trailingIcon),
                    contentDescription = null,
                    tint = if (checked) {
                        AppTheme.colorScheme.primary
                    } else {
                        AppTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = if (trailingRotated) 180f else 0f },
                )

                checked -> SymbolIcon(symbol = sf("checkmark"), contentDescription = null, size = 18.dp, tint = AppTheme.colorScheme.primary)
            }
        },
        onClick = onClick,
    )
}
