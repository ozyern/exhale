/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.desktop.data.UpdateChecker
import com.ozyern.exhale.desktop.data.UpdateDownload
import kotlinx.coroutines.launch

/**
 * A newer version, offered in the window, as the Android app offers its updates: a small card at the
 * top right with Update and Later. Update downloads the installer here (its progress on the card),
 * checks it against the release's published checksum, and then offers Restart Now, which closes
 * Exhale and runs the installer. Later puts it away until the next launch.
 *
 * It appears only when the launch check found a newer release that carries a Windows installer
 * (see UpdateChecker); Settings → Updates has the same steps with the release notes.
 */
@Composable
fun UpdatePrompt(modifier: Modifier = Modifier) {
    val available by UpdateChecker.available.collectAsState()
    val download by UpdateChecker.download.collectAsState()
    var dismissed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val release = available
    val shown = release != null && release.installer != null && dismissed != release.tag

    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.94f, transformOrigin = TransformOrigin(1f, 0f)),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.96f, transformOrigin = TransformOrigin(1f, 0f)),
        modifier = modifier,
    ) {
        if (release == null) return@AnimatedVisibility
        val ink = MaterialTheme.colorScheme.onSurface
        val accent = MaterialTheme.colorScheme.primary
        val shape = RoundedCornerShape(14.dp)
        val state = download.takeIf { it !is UpdateDownload.Idle && it.releaseTag() == release.tag } ?: UpdateDownload.Idle
        Column(
            Modifier
                .width(300.dp)
                .shadow(18.dp, shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(0.5.dp, ink.copy(alpha = 0.12f), shape)
                .padding(16.dp),
        ) {
            val version = release.tag.trimStart('v', 'V')
            Text("Exhale $version is available", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink)
            Spacer(Modifier.height(3.dp))
            Text(
                when (state) {
                    is UpdateDownload.Downloading -> {
                        val percent = if (state.total > 0) (state.bytes * 100 / state.total).toInt().coerceIn(0, 100) else 0
                        "Downloading… $percent%"
                    }
                    is UpdateDownload.Verifying -> "Checking the download…"
                    is UpdateDownload.Ready -> "Ready to install. Exhale will close while the installer runs."
                    is UpdateDownload.Failed -> state.message
                    UpdateDownload.Idle -> "A new version of Exhale for Windows. Your library and settings stay as they are."
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = if (state is UpdateDownload.Failed) MaterialTheme.colorScheme.error else ink.copy(alpha = 0.6f),
            )
            if (state is UpdateDownload.Downloading && state.total > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(ink.copy(alpha = 0.12f))) {
                    // The server's length can be the compressed one: never past full, which would throw.
                    Box(Modifier.fillMaxWidth((state.bytes.toFloat() / state.total).coerceIn(0f, 1f)).height(4.dp).background(accent))
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)) {
                if (state !is UpdateDownload.Downloading && state !is UpdateDownload.Verifying) {
                    PromptButton("Later", primary = false) { dismissed = release.tag }
                }
                when (state) {
                    is UpdateDownload.Ready -> PromptButton("Restart Now", primary = true) { UpdateChecker.install(state.file) }
                    is UpdateDownload.Downloading, is UpdateDownload.Verifying -> Unit
                    is UpdateDownload.Failed -> PromptButton("Try Again", primary = true) { scope.launch { UpdateChecker.downloadInstaller(release) } }
                    UpdateDownload.Idle -> PromptButton("Update", primary = true) { scope.launch { UpdateChecker.downloadInstaller(release) } }
                }
            }
        }
    }
}

private fun UpdateDownload.releaseTag(): String? = when (this) {
    is UpdateDownload.Downloading -> release.tag
    is UpdateDownload.Verifying -> release.tag
    is UpdateDownload.Ready -> release.tag
    is UpdateDownload.Failed -> release.tag
    UpdateDownload.Idle -> null
}

/** A small rounded button: the accent filled for the action, quiet grey for the other. */
@Composable
private fun PromptButton(text: String, primary: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (primary) accent else ink.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (primary) Color.White else ink)
    }
}
