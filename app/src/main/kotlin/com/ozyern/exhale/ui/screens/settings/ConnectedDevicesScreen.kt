/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozyern.exhale.R
import com.ozyern.exhale.connect.Beacon
import com.ozyern.exhale.connect.ConnectAction
import com.ozyern.exhale.connect.ConnectMessage
import com.ozyern.exhale.connect.ConnectStatus
import com.ozyern.exhale.phoneconnect.PhoneConnect
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import kotlinx.coroutines.launch

/**
 * Settings → Connected devices: pair once with Exhale for Windows on the same Wi-Fi, after which
 * the phone reconnects by itself. While connected, the PC can be controlled from here and what
 * plays on the phone can be handed to it.
 */
@Composable
fun ConnectedDevicesScreen(navController: NavController) {
    val status by PhoneConnect.status.collectAsStateWithLifecycle()
    val devices by PhoneConnect.devices.collectAsStateWithLifecycle()
    val pc by PhoneConnect.pc.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var searching by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<Beacon>?>(null) }
    var pairingWith by remember { mutableStateOf<Beacon?>(null) }
    var code by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { PhoneConnect.nudge() }

    fun search() {
        searching = true
        message = null
        scope.launch {
            found = PhoneConnect.discover()
            searching = false
            if (found.isNullOrEmpty()) {
                message = "No PC answered. Open Exhale on the PC, make sure both are on the same Wi-Fi, and allow Exhale through the Windows firewall if it asked."
            }
        }
    }

    SettingsPage(navController = navController, title = "Connected devices", horizontalPadding = 16.dp) {
        Text(
            text = "Pair once with Exhale for Windows on the same Wi-Fi. After that they connect by themselves: control one from the other, and hand a song over to carry on there.",
            style = IosTheme.typography.body,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))

        val statusText = when (val s = status) {
            is ConnectStatus.Connected -> "Connected to ${s.peer.name}"
            ConnectStatus.Searching -> "Looking for your PC on this Wi-Fi…"
            ConnectStatus.Unpaired -> "Not paired with a PC yet"
        }
        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(if (status is ConnectStatus.Connected) R.drawable.cast_connected else R.drawable.cast),
                    title = { Text(statusText) },
                ),
            ),
        )

        // The PC's remote, while connected.
        val now: ConnectMessage.NowPlaying? = pc
        if (status is ConnectStatus.Connected) {
            Spacer(Modifier.height(16.dp))
            val track = now?.track
            IosSettingsGroup(
                title = "On your PC",
                items = listOfNotNull(
                    IosSettingsItem(
                        icon = painterResource(R.drawable.music_note),
                        title = { Text(track?.title ?: "Nothing playing") },
                        description = track?.let { { Text(it.artists) } },
                    ),
                    track?.let {
                        IosSettingsItem(
                            icon = painterResource(R.drawable.player_ic_player_previous),
                            title = { Text("Previous") },
                            onClick = { PhoneConnect.commandPc(ConnectAction.PREVIOUS) },
                        )
                    },
                    track?.let {
                        IosSettingsItem(
                            icon = painterResource(if (now?.playing == true) R.drawable.pause else R.drawable.play),
                            title = { Text(if (now?.playing == true) "Pause" else "Play") },
                            onClick = { PhoneConnect.commandPc(ConnectAction.TOGGLE) },
                        )
                    },
                    track?.let {
                        IosSettingsItem(
                            icon = painterResource(R.drawable.player_ic_player_next),
                            title = { Text("Next") },
                            onClick = { PhoneConnect.commandPc(ConnectAction.NEXT) },
                        )
                    },
                    IosSettingsItem(
                        icon = painterResource(R.drawable.cast),
                        title = { Text("Continue on PC") },
                        description = { Text("Plays what's on this phone on the PC, from where it is, and pauses here.") },
                        onClick = {
                            message = if (PhoneConnect.handOffToPc()) null else "Nothing is playing on the phone to hand over."
                        },
                    ),
                ),
            )
        }

        // Pairing: find the PC, then type the code it shows.
        Spacer(Modifier.height(16.dp))
        val target = pairingWith
        if (target == null) {
            IosSettingsGroup(
                title = "Pair with a PC",
                items = buildList {
                    add(
                        IosSettingsItem(
                            icon = painterResource(R.drawable.refresh),
                            title = { Text(if (searching) "Looking…" else "Find PCs on this Wi-Fi") },
                            description = { Text("On the PC: Settings → Connected devices → Pair.") },
                            enabled = !searching,
                            onClick = ::search,
                        ),
                    )
                    found.orEmpty().forEach { beacon ->
                        add(
                            IosSettingsItem(
                                icon = painterResource(R.drawable.link),
                                title = { Text(beacon.name) },
                                description = { Text(beacon.host) },
                                onClick = { pairingWith = beacon; code = ""; message = null },
                            ),
                        )
                    }
                },
            )
        } else {
            Text(
                text = "Enter the code shown on ${target.name}",
                style = IosTheme.typography.headline,
                color = IosTheme.colors.label,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                singleLine = true,
                placeholder = { Text("123 456") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            IosSettingsGroup(
                items = listOf(
                    IosSettingsItem(
                        icon = painterResource(R.drawable.link),
                        title = { Text(if (pairing) "Pairing…" else "Pair") },
                        enabled = code.length == 6 && !pairing,
                        onClick = {
                            pairing = true
                            scope.launch {
                                val reason = PhoneConnect.pair(target, code)
                                pairing = false
                                message = when (reason) {
                                    null -> { pairingWith = null; found = null; "Paired with ${target.name}. They'll connect by themselves from now on." }
                                    ConnectMessage.Rejected.Reason.WRONG_CODE -> "That code didn't match. Check the PC and try again."
                                    ConnectMessage.Rejected.Reason.NOT_PAIRING -> "The PC isn't pairing right now. Press Pair on the PC to show a new code."
                                    ConnectMessage.Rejected.Reason.VERSION -> "Update Exhale on the phone and the PC to the same version."
                                    else -> "Couldn't pair. Try again."
                                }
                            }
                        },
                    ),
                    IosSettingsItem(
                        icon = painterResource(R.drawable.close),
                        title = { Text("Cancel") },
                        onClick = { pairingWith = null; code = "" },
                    ),
                ),
            )
        }

        message?.let {
            Text(
                text = it,
                style = IosTheme.typography.footnote,
                color = IosTheme.colors.secondaryLabel,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }

        if (devices.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            IosSettingsGroup(
                title = "Paired PCs",
                items = devices.sortedByDescending { it.pairedAtMs }.map { device ->
                    val online = (status as? ConnectStatus.Connected)?.peer?.id == device.id
                    IosSettingsItem(
                        icon = painterResource(R.drawable.cast),
                        title = { Text(device.name) },
                        description = { Text(if (online) "Connected" else "Connects by itself on the same Wi-Fi") },
                        trailingContent = {
                            Text(
                                "Forget",
                                color = IosTheme.colors.systemRed,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        },
                        onClick = { PhoneConnect.forget(device.id) },
                    )
                },
            )
        }
    }
}
