/*
 * Exhale Project Original (2026)
 * Licensed Under GPL-3.0
 */

package com.ozyern.exhale.ui.screens.settings.lossless

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.lossless.sources.SourceCheckResult
import com.ozyern.exhale.playback.lossless.sources.SourceCheckStatus
import com.ozyern.exhale.playback.lossless.sources.constants.AppleMusicQuality
import com.ozyern.exhale.playback.lossless.sources.constants.AudioSourceType
import com.ozyern.exhale.playback.lossless.sources.constants.DeezerAudioQuality
import com.ozyern.exhale.playback.lossless.sources.constants.QobuzAudioQuality
import com.ozyern.exhale.playback.lossless.sources.constants.TidalAudioQuality
import com.ozyern.exhale.ui.component.EnumListPreference
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.PreferenceEntry
import com.ozyern.exhale.ui.component.PreferenceGroup
import com.ozyern.exhale.ui.component.PreferenceGroupDivider
import com.ozyern.exhale.ui.component.PreferenceGroupTitle
import com.ozyern.exhale.ui.component.SwitchPreference
import com.ozyern.exhale.ui.utils.backToMain

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LosslessSourcesSettingsScreen(
    navController: NavController,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LosslessSourcesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val poolCounts by viewModel.poolCounts.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val checkResults by viewModel.checkResults.collectAsState()
    val poolCheck by viewModel.poolCheckResult.collectAsState()
    val message by viewModel.lastMessage.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    var resultDialog by remember { mutableStateOf<Pair<String, SourceCheckResult>?>(null) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var showPoolKeyDialog by remember { mutableStateOf(false) }
    var showPoolUrlDialog by remember { mutableStateOf(false) }
    var showTidalInstancesDialog by remember { mutableStateOf(false) }
    var showQobuzTokensDialog by remember { mutableStateOf(false) }
    var showQobuzInstancesDialog by remember { mutableStateOf(false) }
    var showDeezerArlDialog by remember { mutableStateOf(false) }
    var showAppleTokensDialog by remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Lossless Sources",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // ── Master & Routing ──────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Lossless Streaming")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable lossless streaming") },
                description = "Stream high-resolution FLAC audio from Tidal, Qobuz, Deezer, Apple Music & Monochrome.",
                icon = { Icon(Icons.Outlined.HighQuality, null) },
                checked = state.losslessEnabled,
                onCheckedChange = viewModel::setLosslessEnabled,
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Source priority order") },
                description = state.sourceOrder.joinToString(" → ") { displayName(it) },
                icon = { Icon(Icons.Outlined.SwapVert, null) },
                onClick = { showOrderDialog = true },
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Clear resolved stream cache") },
                description = "Forget cached URLs so subsequent playback re-queries sources.",
                icon = { Icon(Icons.Outlined.Refresh, null) },
                onClick = viewModel::clearResolvedCache,
            )
        }

        // ── Source Pool ──────────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Source Pool")
        PreferenceGroup {
            val poolSubtitle = buildString {
                if (poolCounts.total > 0) {
                    append("Active: ${poolCounts.total} accounts (")
                    val items = mutableListOf<String>()
                    if (poolCounts.tidal > 0) items += "Tidal: ${poolCounts.tidal}"
                    if (poolCounts.qobuz > 0) items += "Qobuz: ${poolCounts.qobuz}"
                    if (poolCounts.deezer > 0) items += "Deezer: ${poolCounts.deezer}"
                    if (poolCounts.apple > 0) items += "Apple: ${poolCounts.apple}"
                    append(items.joinToString(" · "))
                    append(")")
                } else {
                    append("Community account pool for Tidal, Qobuz, Deezer and Apple Music.")
                }
            }

            PreferenceEntry(
                title = { Text("Pool API key") },
                description = if (state.poolApiKey.isNotBlank()) "Key configured (${state.poolApiKey.take(8)}…)" else if (state.poolBuildTimeKeyPresent) "Using built-in key" else "Tap to enter pool read key (atp_…)",
                icon = { Icon(Icons.Outlined.Key, null) },
                onClick = { showPoolKeyDialog = true },
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Pool base URL") },
                description = if (state.poolBaseUrl.isNotBlank()) state.poolBaseUrl else state.poolEffectiveBaseUrl.ifBlank { "https://archivepool.vercel.app" },
                icon = { Icon(painterResource(R.drawable.storage), null) },
                onClick = { showPoolUrlDialog = true },
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Refresh pool accounts") },
                description = poolSubtitle,
                icon = {
                    if ("pool" in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, null)
                    }
                },
                onClick = { viewModel.refreshPool(force = true) },
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Check pool connection") },
                description = poolCheck?.summary ?: "Test connectivity to the Source Pool feed.",
                icon = {
                    val status = poolCheck?.status
                    when (status) {
                        SourceCheckStatus.READY -> Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        SourceCheckStatus.DEGRADED, SourceCheckStatus.UNREACHABLE -> Icon(Icons.Outlined.Error, null, tint = MaterialTheme.colorScheme.error)
                        else -> Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkPool() },
            )
        }

        // ── Monochrome (Free FLAC) ────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Monochrome (Free FLAC)")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable Monochrome") },
                description = "Free FLAC streams from the Monochrome mirrors. No account needed.",
                icon = { Icon(Icons.Outlined.GraphicEq, null) },
                checked = state.monochromeEnabled,
                onCheckedChange = viewModel::setMonochromeEnabled,
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            val check = checkResults[AudioSourceType.MONOCHROME]
            PreferenceEntry(
                title = { Text("Check Monochrome connection") },
                description = check?.summary ?: "Test connectivity to Monochrome API.",
                icon = {
                    if (AudioSourceType.MONOCHROME.name in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (check?.status == SourceCheckStatus.READY) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkSource(AudioSourceType.MONOCHROME) },
            )
        }

        // ── Tidal ────────────────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Tidal")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable Tidal") },
                description = "Stream lossless FLAC via personal account, pool accounts or public instances.",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.tidalEnabled,
                onCheckedChange = viewModel::setTidalEnabled,
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text("Tidal audio quality") },
                icon = { Icon(painterResource(R.drawable.waves), null) },
                selectedValue = state.tidalQuality,
                onValueSelected = viewModel::setTidalQuality,
                valueText = {
                    when (it) {
                        TidalAudioQuality.HI_RES_LOSSLESS -> "Hi-Res Lossless (up to 24-bit/192kHz)"
                        TidalAudioQuality.FLAC -> "FLAC (16-bit/44.1kHz)"
                        TidalAudioQuality.AAC_320 -> "High (320 kbps AAC)"
                    }
                },
                isEnabled = state.losslessEnabled && state.tidalEnabled,
            )

            PreferenceGroupDivider()
            if (state.tidalSignedIn) {
                PreferenceEntry(
                    title = { Text("Signed in: ${state.tidalAccountName.ifBlank { "Tidal User" }}") },
                    description = "Tap to sign out of this Tidal account.",
                    icon = { Icon(painterResource(R.drawable.account), null) },
                    onClick = { viewModel.signOutTidal() },
                    isEnabled = state.losslessEnabled && state.tidalEnabled,
                )
            } else {
                PreferenceEntry(
                    title = { Text("Sign in to Tidal") },
                    description = "Log in via browser session to stream from your personal account.",
                    icon = { Icon(painterResource(R.drawable.login), null) },
                    onClick = { navController.navigate("settings/player/lossless_sources/tidal_login") },
                    isEnabled = state.losslessEnabled && state.tidalEnabled,
                )
            }

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Public Tidal instances") },
                description = if (state.tidalInstances.isBlank()) "None configured (auto-discovery active)" else "${state.tidalInstances.lines().count()} custom instance(s)",
                icon = { Icon(painterResource(R.drawable.storage), null) },
                onClick = { showTidalInstancesDialog = true },
                isEnabled = state.losslessEnabled && state.tidalEnabled,
            )

            PreferenceGroupDivider()
            val check = checkResults[AudioSourceType.TIDAL]
            PreferenceEntry(
                title = { Text("Check Tidal connection") },
                description = check?.summary ?: "Test resolving playback via Tidal.",
                icon = {
                    if (AudioSourceType.TIDAL.name in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (check?.status == SourceCheckStatus.READY) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkSource(AudioSourceType.TIDAL) },
            )
        }

        // ── Qobuz ────────────────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Qobuz")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable Qobuz") },
                description = "Stream studio quality Hi-Res FLAC up to 24-bit/192kHz.",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.qobuzEnabled,
                onCheckedChange = viewModel::setQobuzEnabled,
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text("Qobuz audio quality") },
                icon = { Icon(painterResource(R.drawable.waves), null) },
                selectedValue = state.qobuzQuality,
                onValueSelected = viewModel::setQobuzQuality,
                valueText = {
                    when (it) {
                        QobuzAudioQuality.MAX -> "Max (up to 24-bit/192kHz)"
                        QobuzAudioQuality.HI_RES -> "Hi-Res (up to 24-bit/96kHz)"
                        QobuzAudioQuality.FLAC -> "FLAC (16-bit/44.1kHz)"
                    }
                },
                isEnabled = state.losslessEnabled && state.qobuzEnabled,
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Sign in to Qobuz") },
                description = if (state.qobuzTokens.isNotEmpty()) "${state.qobuzTokens.size} token(s) configured" else "Log in via web player to capture credentials",
                icon = { Icon(painterResource(R.drawable.login), null) },
                onClick = { navController.navigate("settings/player/lossless_sources/qobuz_login") },
                isEnabled = state.losslessEnabled && state.qobuzEnabled,
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Add Qobuz tokens manually") },
                description = "Paste user auth token, token triples or JSON list.",
                icon = { Icon(Icons.Outlined.Key, null) },
                onClick = { showQobuzTokensDialog = true },
                isEnabled = state.losslessEnabled && state.qobuzEnabled,
            )

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Public Qobuz instances") },
                description = if (state.qobuzInstances.isBlank()) "None configured" else "${state.qobuzInstances.lines().count()} custom instance(s)",
                icon = { Icon(painterResource(R.drawable.storage), null) },
                onClick = { showQobuzInstancesDialog = true },
                isEnabled = state.losslessEnabled && state.qobuzEnabled,
            )

            PreferenceGroupDivider()
            val check = checkResults[AudioSourceType.QOBUZ]
            PreferenceEntry(
                title = { Text("Check Qobuz connection") },
                description = check?.summary ?: "Test resolving playback via Qobuz.",
                icon = {
                    if (AudioSourceType.QOBUZ.name in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (check?.status == SourceCheckStatus.READY) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkSource(AudioSourceType.QOBUZ) },
            )
        }

        // ── Deezer ───────────────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Deezer")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable Deezer") },
                description = "Stream CD-quality 16-bit FLAC audio using in-flight Blowfish decryption.",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.deezerEnabled,
                onCheckedChange = viewModel::setDeezerEnabled,
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text("Deezer audio quality") },
                icon = { Icon(painterResource(R.drawable.waves), null) },
                selectedValue = state.deezerQuality,
                onValueSelected = viewModel::setDeezerQuality,
                valueText = {
                    when (it) {
                        DeezerAudioQuality.FLAC -> "FLAC (16-bit 1411 kbps)"
                        DeezerAudioQuality.MP3_320 -> "MP3 (320 kbps HQ)"
                        DeezerAudioQuality.MP3_128 -> "MP3 (128 kbps)"
                    }
                },
                isEnabled = state.losslessEnabled && state.deezerEnabled,
            )

            PreferenceGroupDivider()
            if (state.deezerSignedIn) {
                PreferenceEntry(
                    title = { Text("Signed in: ${state.deezerAccountName.ifBlank { "Deezer Account" }}") },
                    description = if (state.deezerAccountPremium) "Premium/HiFi active · Tap to sign out" else "Free account · Tap to sign out",
                    icon = { Icon(painterResource(R.drawable.account), null) },
                    onClick = { viewModel.signOutDeezer() },
                    isEnabled = state.losslessEnabled && state.deezerEnabled,
                )
            } else {
                PreferenceEntry(
                    title = { Text("Sign in to Deezer") },
                    description = "Sign in via web player to capture session cookie.",
                    icon = { Icon(painterResource(R.drawable.login), null) },
                    onClick = { navController.navigate("settings/player/lossless_sources/deezer_login") },
                    isEnabled = state.losslessEnabled && state.deezerEnabled,
                )
            }

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Enter ARL cookie manually") },
                description = "Paste the arl cookie value from any browser.",
                icon = { Icon(Icons.Outlined.Key, null) },
                onClick = { showDeezerArlDialog = true },
                isEnabled = state.losslessEnabled && state.deezerEnabled,
            )

            PreferenceGroupDivider()
            val check = checkResults[AudioSourceType.DEEZER]
            PreferenceEntry(
                title = { Text("Check Deezer connection") },
                description = check?.summary ?: "Test resolving playback via Deezer.",
                icon = {
                    if (AudioSourceType.DEEZER.name in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (check?.status == SourceCheckStatus.READY) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkSource(AudioSourceType.DEEZER) },
            )
        }

        // ── Apple Music ──────────────────────────────────────────────────────────────────
        PreferenceGroupTitle(title = "Apple Music")
        PreferenceGroup {
            SwitchPreference(
                title = { Text("Enable Apple Music") },
                description = "Stream ALAC lossless audio with Widevine L3 DRM decryption.",
                icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
                checked = state.appleEnabled,
                onCheckedChange = viewModel::setAppleEnabled,
                isEnabled = state.losslessEnabled,
            )

            PreferenceGroupDivider()
            EnumListPreference(
                title = { Text("Apple Music audio quality") },
                icon = { Icon(painterResource(R.drawable.waves), null) },
                selectedValue = state.appleQuality,
                onValueSelected = viewModel::setAppleQuality,
                valueText = {
                    when (it) {
                        AppleMusicQuality.HI_RES_LOSSLESS -> "Hi-Res Lossless (up to 24-bit/192kHz)"
                        AppleMusicQuality.LOSSLESS -> "Lossless ALAC (up to 24-bit/48kHz)"
                        AppleMusicQuality.AAC -> "High (256 kbps AAC)"
                    }
                },
                isEnabled = state.losslessEnabled && state.appleEnabled,
            )

            PreferenceGroupDivider()
            if (state.appleMediaUserToken.isNotBlank()) {
                PreferenceEntry(
                    title = { Text("Apple Music tokens configured") },
                    description = "Music User Token active · Tap to clear",
                    icon = { Icon(painterResource(R.drawable.account), null) },
                    onClick = { viewModel.signOutApple() },
                    isEnabled = state.losslessEnabled && state.appleEnabled,
                )
            } else {
                PreferenceEntry(
                    title = { Text("Sign in to Apple Music") },
                    description = "Sign in via Apple web player to capture session token.",
                    icon = { Icon(painterResource(R.drawable.login), null) },
                    onClick = { navController.navigate("settings/player/lossless_sources/applemusic_login") },
                    isEnabled = state.losslessEnabled && state.appleEnabled,
                )
            }

            PreferenceGroupDivider()
            PreferenceEntry(
                title = { Text("Enter Apple Music tokens manually") },
                description = "Paste Media-User-Token and optional Developer Token.",
                icon = { Icon(Icons.Outlined.Key, null) },
                onClick = { showAppleTokensDialog = true },
                isEnabled = state.losslessEnabled && state.appleEnabled,
            )

            PreferenceGroupDivider()
            val check = checkResults[AudioSourceType.APPLE]
            PreferenceEntry(
                title = { Text("Check Apple Music connection") },
                description = check?.summary ?: "Test resolving playback via Apple Music.",
                icon = {
                    if (AudioSourceType.APPLE.name in busy) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (check?.status == SourceCheckStatus.READY) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(painterResource(R.drawable.bolt), null)
                    }
                },
                onClick = { viewModel.checkSource(AudioSourceType.APPLE) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Dialogs ──────────────────────────────────────────────────────────────────────────

    // 1. Source priority reordering dialog
    if (showOrderDialog) {
        var currentOrder by remember(state.sourceOrder) { mutableStateOf(state.sourceOrder) }
        AlertDialog(
            onDismissRequest = { showOrderDialog = false },
            title = { Text("Source Priority Order") },
            text = {
                Column {
                    Text(
                        "Sources are queried in order from top to bottom; the first accepted match plays.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    currentOrder.forEachIndexed { index, source ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${index + 1}. ${displayName(source)}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Row {
                                IconButton(
                                    enabled = index > 0,
                                    onClick = {
                                        val mutable = currentOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index - 1]
                                        mutable[index - 1] = temp
                                        currentOrder = mutable
                                    }
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, null)
                                }
                                IconButton(
                                    enabled = index < currentOrder.size - 1,
                                    onClick = {
                                        val mutable = currentOrder.toMutableList()
                                        val temp = mutable[index]
                                        mutable[index] = mutable[index + 1]
                                        mutable[index + 1] = temp
                                        currentOrder = mutable
                                    }
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSourceOrder(currentOrder)
                    showOrderDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOrderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. Pool API Key dialog
    if (showPoolKeyDialog) {
        var text by remember(state.poolApiKey) { mutableStateOf(state.poolApiKey) }
        AlertDialog(
            onDismissRequest = { showPoolKeyDialog = false },
            title = { Text("Pool API Key") },
            text = {
                Column {
                    Text(
                        "Enter the atp_... read key provided by your ArchivePool deployment.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("API Key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePoolApiKey(text)
                    showPoolKeyDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPoolKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. Pool Base URL dialog
    if (showPoolUrlDialog) {
        var text by remember(state.poolBaseUrl) { mutableStateOf(state.poolBaseUrl) }
        AlertDialog(
            onDismissRequest = { showPoolUrlDialog = false },
            title = { Text("Pool Base URL") },
            text = {
                Column {
                    Text(
                        "URL of the ArchivePool server (defaults to https://archivepool.vercel.app).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Base URL") },
                        placeholder = { Text("https://archivepool.vercel.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.savePoolBaseUrl(text)
                    showPoolUrlDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPoolUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 4. Tidal Instances dialog
    if (showTidalInstancesDialog) {
        var text by remember(state.tidalInstances) { mutableStateOf(state.tidalInstances) }
        AlertDialog(
            onDismissRequest = { showTidalInstancesDialog = false },
            title = { Text("Public Tidal Instances") },
            text = {
                Column {
                    Text(
                        "Enter one instance base URL per line (e.g. https://tidal.example.com). Leave empty to use auto-discovered instances.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTidalInstances(text)
                    showTidalInstancesDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTidalInstancesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 5. Qobuz Tokens dialog
    if (showQobuzTokensDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQobuzTokensDialog = false },
            title = { Text("Add Qobuz Tokens") },
            text = {
                Column {
                    Text(
                        "Paste a Qobuz user auth token, token:appId:secret triple, or JSON array.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) viewModel.addQobuzTokens(text)
                    showQobuzTokensDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQobuzTokensDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 6. Qobuz Instances dialog
    if (showQobuzInstancesDialog) {
        var text by remember(state.qobuzInstances) { mutableStateOf(state.qobuzInstances) }
        AlertDialog(
            onDismissRequest = { showQobuzInstancesDialog = false },
            title = { Text("Public Qobuz Instances") },
            text = {
                Column {
                    Text(
                        "Enter one instance URL per line. Leave empty for default.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setQobuzInstances(text)
                    showQobuzInstancesDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQobuzInstancesDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 7. Deezer ARL dialog
    if (showDeezerArlDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDeezerArlDialog = false },
            title = { Text("Deezer ARL Cookie") },
            text = {
                Column {
                    Text(
                        "If Deezer web login is blocked in your region, sign in from any browser, copy the arl cookie value, and paste it here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("ARL cookie") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        com.ozyern.exhale.playback.lossless.sources.deezer.DeezerAudioProvider.setManualArl(text.trim(), premium = true)
                        Toast.makeText(context, "Saved ARL cookie", Toast.LENGTH_SHORT).show()
                    }
                    showDeezerArlDialog = false
                }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeezerArlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 8. Apple Music Tokens dialog
    if (showAppleTokensDialog) {
        var mediaToken by remember(state.appleMediaUserToken) { mutableStateOf(state.appleMediaUserToken) }
        var devToken by remember(state.appleDevToken) { mutableStateOf(state.appleDevToken) }
        AlertDialog(
            onDismissRequest = { showAppleTokensDialog = false },
            title = { Text("Apple Music Tokens") },
            text = {
                Column {
                    Text(
                        "Enter your Media-User-Token (required) and Developer Token (optional; scraped automatically if omitted).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = mediaToken,
                        onValueChange = { mediaToken = it },
                        label = { Text("Media-User-Token") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = devToken,
                        onValueChange = { devToken = it },
                        label = { Text("Developer Token (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveAppleTokens(mediaToken, devToken)
                    showAppleTokensDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAppleTokensDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 9. Details Dialog
    resultDialog?.let { (title, result) ->
        AlertDialog(
            onDismissRequest = { resultDialog = null },
            title = { Text("$title Check Result") },
            text = {
                Column {
                    Text("Status: ${result.status.name}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(result.summary)
                }
            },
            confirmButton = {
                TextButton(onClick = { resultDialog = null }) {
                    Text("OK")
                }
            }
        )
    }
}

private fun displayName(source: AudioSourceType): String =
    when (source) {
        AudioSourceType.MONOCHROME -> "Monochrome"
        AudioSourceType.TIDAL -> "Tidal"
        AudioSourceType.QOBUZ -> "Qobuz"
        AudioSourceType.DEEZER -> "Deezer"
        AudioSourceType.APPLE -> "Apple Music"
        AudioSourceType.RADIOPARADISE -> "Radio Paradise"
        AudioSourceType.QOBUZ_BACKUP -> "Qobuz Backup"
        AudioSourceType.AMAZON -> "Amazon"
        AudioSourceType.JIOSAAVN -> "JioSaavn"
        AudioSourceType.YOUTUBE -> "YouTube Music"
    }
