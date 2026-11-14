/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text

@Composable
fun LocalSectionDivider(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        HorizontalDivider(color = AppTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.filter_local),
            style = AppTheme.typography.titleSmall,
            color = AppTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
