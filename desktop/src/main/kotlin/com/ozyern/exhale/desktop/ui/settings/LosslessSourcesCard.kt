/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.desktop.data.SystemShell
import com.ozyern.exhale.desktop.player.LosslessKind
import com.ozyern.exhale.desktop.player.LosslessLibrary
import com.ozyern.exhale.desktop.player.LosslessSource
import com.ozyern.exhale.desktop.player.SourceHealth
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.typingField
import com.ozyern.exhale.desktop.ui.glass.glassPlate
import kotlinx.coroutines.launch

/**
 * Where lossless comes from. Exhale ships no sources and fetches no code for them: a source is a
 * folder on this PC, a Subsonic server, or an addon server — each one an address typed in here.
 */
@Composable
internal fun LosslessSourcesCard() {
    val scope = rememberCoroutineScope()
    val sources by LosslessLibrary.sources.collectAsState()
    val health by LosslessLibrary.health.collectAsState()
    val lastMatch by LosslessLibrary.lastMatch.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().glassPlate(RoundedCornerShape(20.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Lossless sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "FLAC and hi-res from music you own or a server you run. What a source doesn't have keeps playing from YouTube.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { adding = !adding }) { Text(if (adding) "Cancel" else "Add a source") }
        }

        if (sources.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Nothing here yet. Exhale already looked in your Music folder — if your music lives somewhere else, add that folder; if you run a music server at home, add its address. " +
                    "YouTube itself has no lossless, so until then everything plays at YouTube's own quality.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (sources.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        health = health[source.id] ?: SourceHealth.Unknown,
                        onToggle = { LosslessLibrary.setEnabled(source.id, it) },
                        onTest = { scope.launch { LosslessLibrary.check(source) } },
                        onRemove = { LosslessLibrary.remove(source.id) },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = adding,
            enter = fadeIn(tween(160)) + expandVertically(spring(dampingRatio = 1f, stiffness = 400f)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
        ) {
            AddSourceForm(
                onAdded = { adding = false },
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        lastMatch?.let {
            Spacer(Modifier.height(12.dp))
            Text("Last played from a source: $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Point Exhale at music you own or a server you're entitled to use. It never sends your library anywhere, and a source's password stays on this PC.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourceRow(
    source: LosslessSource,
    health: SourceHealth,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val icon = when (source.kind) {
        LosslessKind.FOLDER -> NavIcons.Folder
        LosslessKind.SUBSONIC, LosslessKind.PLEX, LosslessKind.JELLYFIN -> NavIcons.Storage
        LosslessKind.ADDON -> NavIcons.Link
        LosslessKind.ARCHIVE -> NavIcons.Backup
        LosslessKind.WIKIMEDIA -> NavIcons.Language
        LosslessKind.JAMENDO -> NavIcons.Radio
        LosslessKind.DLNA -> NavIcons.Chip
        LosslessKind.MONOCHROME, LosslessKind.TIDAL -> NavIcons.HighQuality
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(source.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${source.kindLabel} · ${source.location}",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HealthLine(health)
        }
        Spacer(Modifier.width(10.dp))
        TextButton(onClick = onTest) { Text("Test") }
        TextButton(onClick = onRemove) { Text("Remove", color = scheme.error) }
        com.ozyern.exhale.desktop.ui.glass.LiquidToggle(checked = source.enabled, onCheckedChange = onToggle, modifier = Modifier.graphicsLayer { scaleX = 0.82f; scaleY = 0.82f })
    }
}

@Composable
private fun HealthLine(health: SourceHealth) {
    val scheme = MaterialTheme.colorScheme
    val (dot, text) = when (health) {
        SourceHealth.Unknown -> scheme.onSurfaceVariant.copy(alpha = 0.4f) to "Not checked yet"
        SourceHealth.Checking -> scheme.secondary to "Checking…"
        is SourceHealth.Ok -> Color(0xFF5EE39A) to health.detail
        is SourceHealth.Failed -> scheme.error to health.message
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Adding one: pick what it is, say where, and it's only kept once it answers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddSourceForm(onAdded: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(LosslessKind.FOLDER) }
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Nothing to fill in for the public catalogues or the network sweep; the rest need an address.
    val needsAddress = kind in setOf(LosslessKind.FOLDER, LosslessKind.SUBSONIC, LosslessKind.ADDON, LosslessKind.PLEX, LosslessKind.JELLYFIN)
    val secretLabel = when (kind) {
        LosslessKind.SUBSONIC -> "Password"
        LosslessKind.PLEX -> "Plex token"
        LosslessKind.JELLYFIN -> "API key"
        else -> null
    }

    // Covers every field in the form at once: shortcuts stand down while any of them has focus.
    Column(modifier.fillMaxWidth().typingField(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LosslessKind.entries.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = {
                        kind = option
                        error = null
                    },
                    label = {
                        Text(
                            when (option) {
                                LosslessKind.FOLDER -> "Folder"
                                LosslessKind.SUBSONIC -> "Subsonic"
                                LosslessKind.ADDON -> "Addon"
                                LosslessKind.ARCHIVE -> "Internet Archive"
                                LosslessKind.WIKIMEDIA -> "Wikimedia"
                                LosslessKind.JAMENDO -> "Jamendo"
                                LosslessKind.PLEX -> "Plex"
                                LosslessKind.JELLYFIN -> "Jellyfin"
                                LosslessKind.DLNA -> "This network"
                                LosslessKind.MONOCHROME -> "Monochrome"
                                LosslessKind.TIDAL -> "Tidal"
                            },
                        )
                    },
                )
            }
        }
        Text(
            when (kind) {
                LosslessKind.FOLDER -> "Your own files. FLAC keeps its tags, sample rate and bit depth; other formats are matched on their file and folder names."
                LosslessKind.SUBSONIC -> "Navidrome, Airsonic, Gonic, or anything else speaking the Subsonic API. Files are sent as they are, with no transcoding."
                LosslessKind.ADDON -> "A server offering /manifest.json, /search and /stream. Exhale asks it for the song that's playing and nothing else."
                LosslessKind.ARCHIVE -> "archive.org: concerts taped with the band's blessing, and public-domain recordings, much of it FLAC. Free, no account, on by default."
                LosslessKind.WIKIMEDIA -> "Wikimedia Commons: public-domain and Creative Commons recordings, much of it FLAC or WAV. Free, no account, on by default."
                LosslessKind.JAMENDO -> "Jamendo's Creative Commons catalogue in FLAC. Needs a free client ID from developer.jamendo.com."
                LosslessKind.PLEX -> "Your own Plex server. The token is the X-Plex-Token from any Plex web URL, and it stays on this PC."
                LosslessKind.JELLYFIN -> "Your own Jellyfin server. Make an API key under Dashboard → API Keys."
                LosslessKind.DLNA -> "Media servers that answer on this network — a NAS, Plex's DLNA, Windows media sharing. Nothing to type, and nothing leaves your network."
                LosslessKind.MONOCHROME -> "Monochrome FLAC: Free CD-quality FLAC (44.1 kHz / 16-bit) streamed from public mirrors with no account needed. On by default."
                LosslessKind.TIDAL -> "Tidal: Free CD-quality & Hi-Res FLAC streamed from public Tidal instances with no account needed. On by default."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (needsAddress) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text(if (kind == LosslessKind.FOLDER) "My library" else "My server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(if (kind == LosslessKind.FOLDER) "Folder" else "Address") },
                    placeholder = {
                        Text(
                            when (kind) {
                                LosslessKind.FOLDER -> "D:\\Music"
                                LosslessKind.PLEX -> "http://192.168.1.10:32400"
                                LosslessKind.JELLYFIN -> "http://192.168.1.10:8096"
                                else -> "http://192.168.1.10:4533"
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (kind == LosslessKind.FOLDER) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { SystemShell.chooseFolder("Choose your music folder")?.let { location = it.absolutePath } }) { Text("Browse…") }
                }
            }
        }
        if (kind == LosslessKind.JAMENDO) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (secretLabel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (kind == LosslessKind.SUBSONIC) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(secretLabel) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = !busy && (!needsAddress && kind != LosslessKind.JAMENDO || location.isNotBlank() || username.isNotBlank()),
                onClick = {
                    busy = true
                    error = null
                    val source = LosslessSource(
                        id = LosslessLibrary.newId(),
                        kind = kind,
                        name = when {
                            name.isNotBlank() && needsAddress -> name
                            kind == LosslessKind.ARCHIVE -> "Internet Archive"
                            kind == LosslessKind.WIKIMEDIA -> "Wikimedia Commons"
                            kind == LosslessKind.JAMENDO -> "Jamendo"
                            kind == LosslessKind.DLNA -> "Servers on this network"
                            kind == LosslessKind.FOLDER -> location.trimEnd('\\', '/').substringAfterLast('\\').ifBlank { "Library" }
                            else -> "Server"
                        },
                        location = when (kind) {
                            LosslessKind.ARCHIVE -> "archive.org"
                            LosslessKind.WIKIMEDIA -> "commons.wikimedia.org"
                            LosslessKind.JAMENDO -> "api.jamendo.com"
                            LosslessKind.DLNA -> "this network"
                            else -> location.trim()
                        },
                        username = username.trim(),
                        password = password,
                    )
                    scope.launch {
                        when (val result = LosslessLibrary.check(source)) {
                            is SourceHealth.Ok -> {
                                LosslessLibrary.add(source)
                                name = ""
                                location = ""
                                username = ""
                                password = ""
                                onAdded()
                            }
                            is SourceHealth.Failed -> error = result.message
                            else -> Unit
                        }
                        busy = false
                    }
                },
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (busy) "Checking…" else "Test and add")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                when (kind) {
                    LosslessKind.FOLDER -> "A big library takes a moment to read the first time."
                    LosslessKind.ARCHIVE, LosslessKind.WIKIMEDIA -> "Free and open to everyone — live and public-domain music, not the current charts."
                    LosslessKind.DLNA -> "Exhale asks the network who's listening; this takes a couple of seconds."
                    else -> "Nothing is saved until it answers."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
