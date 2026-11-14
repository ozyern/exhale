/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.player

import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.SymbolIcon
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.AlertDialog
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.compat.TextButton
import kotlin.math.roundToInt

@Composable
fun SleepTimerDialog(
    sleepTimerValue: Float,
    onSleepTimerValueChange: (Float) -> Unit,
    onConfirm: (minutes: Int) -> Unit,
    onEndOfSong: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        icon = {
            SymbolIcon(symbol = sf("moon.fill"), contentDescription = null)
        },
        title = { Text(stringResource(R.string.sleep_timer)) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sleepTimerValue.roundToInt()) },
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pluralStringResource(
                        R.plurals.minute,
                        sleepTimerValue.roundToInt(),
                        sleepTimerValue.roundToInt()
                    ),
                    style = AppTheme.typography.bodyLarge,
                )

                Slider(
                    value = sleepTimerValue,
                    onValueChange = onSleepTimerValueChange,
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                )

                OutlinedIconButton(
                    onClick = onEndOfSong,
                ) {
                    Text(stringResource(R.string.end_of_song))
                }
            }
        },
    )
}
