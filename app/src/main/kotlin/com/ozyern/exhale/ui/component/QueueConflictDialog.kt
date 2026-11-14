/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import com.ozyern.exhale.ui.ios.compat.TextButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AskBeforeReplacingQueueKey
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Asks whether starting something new should keep the queue that is already playing or clear
 * it. Hosted once at the root: [PlayerConnection.playQueue] parks the requested queue in
 * `pendingQueue` instead of handing it to the service, and this is what resolves it, so every
 * "play this" in the app is covered by one dialog rather than a prompt per call site.
 *
 * Silent when nothing is parked, and never shown at all while
 * [AskBeforeReplacingQueueKey] is off.
 */
@Composable
fun QueueConflictDialog(playerConnection: PlayerConnection?) {
    val connection = playerConnection ?: return
    val pending by connection.pendingQueue.collectAsStateWithLifecycle()
    val queue = pending ?: return

    DefaultDialog(
        onDismiss = { connection.pendingQueue.value = null },
        title = {
            Text(
                text = stringResource(R.string.queue_conflict_title),
                style = IosTheme.typography.headline,
            )
        },
        buttons = {
            TextButton(
                onClick = {
                    connection.pendingQueue.value = null
                    connection.enqueueQueue(queue)
                }
            ) {
                Text(stringResource(R.string.queue_conflict_keep))
            }
            TextButton(
                onClick = {
                    connection.pendingQueue.value = null
                    connection.playQueue(queue, force = true)
                }
            ) {
                Text(stringResource(R.string.queue_conflict_replace))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.queue_conflict_message),
            style = IosTheme.typography.subheadline,
        )
    }
}

/**
 * Offers to undo a queue replace that happened without asking (the
 * [AskBeforeReplacingQueueKey] toggle is off). Hosted once at the root, same reasoning as
 * [QueueConflictDialog]: [PlayerConnection.playQueue] is the single choke point.
 */
@Composable
fun QueueReplacedUndoSnackbar(
    playerConnection: PlayerConnection?,
    snackbarHostState: SnackbarHostState,
) {
    val connection = playerConnection ?: return
    val replaced by connection.queueReplacedForUndo.collectAsStateWithLifecycle()
    val message = stringResource(R.string.queue_replaced_message)
    val undoLabel = stringResource(R.string.undo)

    LaunchedEffect(replaced) {
        val previousQueue = replaced ?: return@LaunchedEffect
        connection.queueReplacedForUndo.value = null
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            connection.playQueue(previousQueue, force = true)
        }
    }
}
