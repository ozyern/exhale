/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ozyern.exhale.desktop.integrations.DesktopConnect
import com.ozyern.exhale.desktop.player.DesktopPlayer

/**
 * Where the sound goes, in the app's one menu: each output with a tick on the one in use, and the
 * paired phone as one more place to play while it is connected (Exhale Connect). Shared by the
 * player bar and the full player, which both had their own Material copy of it.
 */
@Composable
fun AudioOutputMenu(
    player: DesktopPlayer,
    outputs: List<DesktopPlayer.AudioOutput>,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    OptionsMenu(expanded = expanded, onDismiss = onDismiss) {
        if (outputs.isEmpty()) {
            OptionsMenuRow("No outputs found", OptionsIcons.Speaker, enabled = false, onClick = onDismiss)
        }
        val current = player.currentAudioOutput()
        outputs.forEach { output ->
            OptionsMenuRow(output.description, OptionsIcons.Speaker, checked = output.name == current) {
                player.setAudioOutput(output.name)
                onDismiss()
            }
        }
        val connectedPhones by DesktopConnect.connected.collectAsState()
        if (connectedPhones.isNotEmpty()) {
            val phoneName = DesktopConnect.connectedPhoneName() ?: "phone"
            OptionsDivider()
            OptionsMenuRow("Continue on $phoneName", OptionsIcons.Phone) {
                DesktopConnect.handOffToPhone()
                onDismiss()
            }
        }
    }
}
