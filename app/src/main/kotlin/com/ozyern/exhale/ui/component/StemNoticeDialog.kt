package com.ozyern.exhale.ui.component

import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text

@Composable
fun StemNoticeDialog(bytes: Long, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    DefaultDialog(
        onDismiss = onDismiss,
        content = {
            Text(
                text = stringResource(R.string.split_stems_title),
                style = AppTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Text(
                text = stringResource(R.string.split_stems_notice, android.text.format.Formatter.formatShortFileSize(context, bytes)),
                style = AppTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            )
        },
        buttons = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.split_stems_start)) }
        },
    )
}
