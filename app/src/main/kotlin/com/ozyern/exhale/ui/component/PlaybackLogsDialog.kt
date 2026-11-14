/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.Button
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Surface
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.utils.DebugLogs
import com.ozyern.exhale.utils.PlaybackLogEntry
import com.ozyern.exhale.utils.PlaybackLogLevel

@Composable
fun PlaybackLogsDialog(
    logs: List<PlaybackLogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }
    
    val context = LocalContext.current

    DefaultDialog(
        onDismiss = onDismiss,
        icon = { SymbolIcon(symbol = sf("ladybug"), contentDescription = null) },
        title = { Text(stringResource(R.string.playback_logs_title)) },
        buttons = {
            TextButton(
                onClick = {
                    val textToCopy = logs.joinToString("\n") { log ->
                        buildString {
                            append(log.timestamp)
                            append(" [")
                            append(log.level.name)
                            append("] ")
                            append(log.message)
                            log.details?.let { d -> append(" -- $d") }
                        }
                    }
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("PlaybackLogs", textToCopy)
                    cm.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                enabled = logs.isNotEmpty()
            ) {
                Text(stringResource(R.string.copy))
            }
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.clear))
            }
            TextButton(
                onClick = { DebugLogs.share(context, logs) },
                enabled = logs.isNotEmpty()
            ) {
                Text(stringResource(R.string.crash_share_logs))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_playback_logs),
                        color = AppTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(logs) { log ->
                        PlaybackLogEntryItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaybackLogEntryItem(log: PlaybackLogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = log.timestamp,
                style = AppTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = AppTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (log.level) {
                    PlaybackLogLevel.ERROR -> AppTheme.colorScheme.errorContainer
                    PlaybackLogLevel.WARNING -> Color(0xFFFFF3CD)
                    PlaybackLogLevel.BOT -> AppTheme.colorScheme.secondaryContainer
                    PlaybackLogLevel.INFO -> AppTheme.colorScheme.primaryContainer
                    PlaybackLogLevel.DEBUG -> AppTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = log.level.name,
                    style = AppTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = when (log.level) {
                        PlaybackLogLevel.ERROR -> AppTheme.colorScheme.onErrorContainer
                        PlaybackLogLevel.WARNING -> Color(0xFF856404)
                        PlaybackLogLevel.BOT -> AppTheme.colorScheme.onSecondaryContainer
                        PlaybackLogLevel.INFO -> AppTheme.colorScheme.onPrimaryContainer
                        PlaybackLogLevel.DEBUG -> AppTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        Text(
            text = log.message,
            style = AppTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (log.level == PlaybackLogLevel.BOT) androidx.compose.ui.text.font.FontWeight.Bold else null
        )
        log.details?.let { details ->
            Text(
                text = details,
                style = AppTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = AppTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
