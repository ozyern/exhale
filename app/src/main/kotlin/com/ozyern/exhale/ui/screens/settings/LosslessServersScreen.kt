/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.MusicServer
import com.ozyern.exhale.playback.lossless.ServerHealth
import com.ozyern.exhale.playback.lossless.ServerKind
import com.ozyern.exhale.playback.lossless.ServerLibrary
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.utils.backToMain
import kotlinx.coroutines.launch

/**
 * The listener's own music servers — Navidrome and the rest of the Subsonic family, Jellyfin, Plex —
 * asked for a lossless copy of each song before the public catalogues and YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LosslessServersScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
) {
    val servers by ServerLibrary.servers.collectAsState()
    val health by ServerLibrary.health.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<MusicServer?>(null) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        if (servers.isNotEmpty()) {
            PreferenceGroup(title = "Your servers") {
                servers.forEachIndexed { index, server ->
                    if (index > 0) PreferenceGroupDivider()
                    val status = when (val h = health[server.id]) {
                        ServerHealth.Checking -> "Checking…"
                        is ServerHealth.Ok -> h.detail
                        is ServerHealth.Failed -> h.message
                        null -> "${server.kind.label} · ${server.url}"
                    }
                    SwitchPreference(
                        title = { Text(server.name) },
                        description = status,
                        icon = { Icon(painterResource(R.drawable.storage), null) },
                        checked = server.enabled,
                        onCheckedChange = { ServerLibrary.setEnabled(server.id, it) },
                    )
                    PreferenceEntry(
                        title = { Text("Edit ${server.name}") },
                        icon = { Icon(painterResource(R.drawable.edit), null) },
                        onClick = { editing = server },
                    )
                }
            }
        }

        PreferenceGroup {
            PreferenceEntry(
                title = { Text("Add a server") },
                description = "Navidrome, Airsonic, Gonic and other Subsonic servers, Jellyfin, or Plex",
                icon = { Icon(painterResource(R.drawable.add), null) },
                onClick = { editing = MusicServer(id = ServerLibrary.newId(), kind = ServerKind.SUBSONIC, name = "", url = "") },
            )
        }

        Text(
            text = "When a song starts, your servers are asked for the same recording at the same time as the " +
                "free catalogues. A lossless copy on your own server wins, and plays exactly as the file is — no " +
                "transcoding. Anything not found plays from YouTube Music as before. Passwords and keys are kept " +
                "encrypted on this phone and are only ever sent to the server they belong to.",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(24.dp))
    }

    editing?.let { server ->
        ServerDialog(
            initial = server,
            isNew = servers.none { it.id == server.id },
            onDismiss = { editing = null },
            onRemove = {
                ServerLibrary.remove(server.id)
                editing = null
            },
            onSave = { saved ->
                ServerLibrary.save(saved)
                editing = null
                scope.launch { ServerLibrary.check(saved) }
            },
        )
    }

    SettingsTopAppBar(
        title = { Text("Lossless servers") },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

@Composable
private fun ServerDialog(
    initial: MusicServer,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onSave: (MusicServer) -> Unit,
) {
    var kind by remember { mutableStateOf(initial.kind) }
    var name by remember { mutableStateOf(initial.name) }
    var url by remember { mutableStateOf(initial.url) }
    var username by remember { mutableStateOf(initial.username) }
    var secret by remember { mutableStateOf(initial.secret) }

    // An address typed without a scheme is taken as https, the safe default for a server on the internet.
    val cleanUrl = url.trim().trimEnd('/').let { if (it.isNotEmpty() && "://" !in it) "https://$it" else it }
    val ready = cleanUrl.isNotEmpty() && secret.isNotEmpty() && (kind != ServerKind.SUBSONIC || username.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add a server" else "Edit server") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    ServerKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label.substringBefore(" /")) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Address") },
                    placeholder = {
                        Text(
                            when (kind) {
                                ServerKind.SUBSONIC -> "https://music.example.com"
                                ServerKind.JELLYFIN -> "http://192.168.1.20:8096"
                                ServerKind.PLEX -> "http://192.168.1.20:32400"
                            },
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == ServerKind.SUBSONIC) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(kind.keyLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (kind) {
                        ServerKind.SUBSONIC -> "Your server's own username and password. Only a salted hash of the password is sent."
                        ServerKind.JELLYFIN -> "Create one in Jellyfin: Dashboard → API Keys."
                        ServerKind.PLEX -> "Your X-Plex-Token, from any item's \"Get Info → View XML\" link in Plex."
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!isNew) {
                    TextButton(onClick = onRemove) {
                        Text("Remove this server", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = {
                    val host = runCatching { java.net.URI(cleanUrl).host }.getOrNull().orEmpty()
                    onSave(
                        initial.copy(
                            kind = kind,
                            name = name.trim().ifBlank { host.ifBlank { kind.label } },
                            url = cleanUrl,
                            username = if (kind == ServerKind.SUBSONIC) username.trim() else "",
                            secret = secret,
                        ),
                    )
                },
            ) { Text("Save & test") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
