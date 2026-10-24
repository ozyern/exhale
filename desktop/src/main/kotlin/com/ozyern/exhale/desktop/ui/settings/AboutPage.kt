/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import com.ozyern.exhale.desktop.ui.pressScale

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AppInfo
import com.ozyern.exhale.desktop.data.DesktopPrefs
import com.ozyern.exhale.desktop.data.SystemShell
import com.ozyern.exhale.desktop.player.AudioOutputStatus
import com.ozyern.exhale.desktop.ui.Motion
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.feedIntro
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ─── People and links ─────────────────────────────────────────────────────────

private const val LeadDeveloperName = "Aditya Jha"
private const val LeadDeveloperHandle = "ozyern"

/** GitHub serves every avatar at github.com/<handle>.png, so the picture follows the profile. */
private const val LeadDeveloperAvatar = "https://github.com/$LeadDeveloperHandle.png"
private const val LeadDeveloperUrl = "https://github.com/$LeadDeveloperHandle"

private data class SocialLink(val icon: ImageVector, val label: String, val handle: String, val url: String, val accent: Color)

private val SocialLinks = listOf(
    SocialLink(NavIcons.GitHub, "GitHub", "@ozyern", "https://github.com/ozyern", Color(0xFFD7DEE6)),
    SocialLink(NavIcons.Send, "Telegram", "@ozyern", "https://t.me/ozyern", Color(0xFF29A9EB)),
    SocialLink(NavIcons.Camera, "Instagram", "@imozyern", "https://www.instagram.com/imozyern/", Color(0xFFE1306C)),
)

private data class StackEntry(val name: String, val role: String)

/** What Exhale for Windows is built on. */
private val Stack = listOf(
    StackEntry("libmpv", "Audio engine · WASAPI"),
    StackEntry("Compose Multiplatform", "Interface"),
    StackEntry("JetBrains Runtime", "Window and title bar"),
    StackEntry("Kyant Backdrop", "Liquid glass"),
    StackEntry("m3color", "Material colour from artwork"),
    StackEntry("NewPipe Extractor", "Stream deciphering"),
    StackEntry("BetterLyrics · LRCLIB · KuGou", "Synced lyrics"),
    StackEntry("Kizzy", "Discord presence"),
)

// ─── Build facts ──────────────────────────────────────────────────────────────

/** Everything the page states about the build, once — so the tiles and "Copy" can't disagree. */
private data class BuildFacts(
    val version: String,
    val commit: String,
    val windows: String,
    val architecture: String,
    val runtime: String,
    val graphics: String,
    val audio: String,
    val dataFolder: String,
) {
    fun asReport(): String = buildString {
        appendLine("Exhale for Windows $version")
        appendLine("Commit:       $commit")
        appendLine("Windows:      $windows")
        appendLine("Architecture: $architecture")
        appendLine("Runtime:      $runtime")
        appendLine("Graphics:     $graphics")
        append("Audio:        $audio")
    }
}

private fun architectureName(): String = when (System.getProperty("os.arch").lowercase(Locale.US)) {
    "amd64", "x86_64" -> "x64"
    "aarch64", "arm64" -> "ARM64"
    "x86" -> "x86"
    else -> System.getProperty("os.arch")
}

private fun graphicsName(): String = when (System.getProperty("skiko.renderApi")?.uppercase(Locale.US)) {
    "DIRECT3D" -> "Direct3D"
    "OPENGL" -> "OpenGL"
    "SOFTWARE", "SOFTWARE_FAST", "SOFTWARE_COMPAT" -> "Software"
    null -> "Default"
    else -> System.getProperty("skiko.renderApi")
}

/** Taps on the wordmark that open the easter egg — seven, like Android's version-tap egg. */
private const val SecretTapCount = 7

/**
 * About, laid out for a wide window: a banner across the whole pane — the mark, the name set large,
 * the build in pills, the actions people come here for — over a slow accent aurora and a spectrum;
 * then cards side by side for who made it, this build, what it's built on, and the licence.
 */
@Composable
internal fun AboutPage(onOpenUpdates: () -> Unit, progress: () -> Float) {
    var showEgg by remember { mutableStateOf(false) }
    val output by AudioOutputStatus.state.collectAsState()
    val facts = remember(output) {
        BuildFacts(
            version = AppInfo.VERSION,
            commit = AppInfo.commit,
            windows = "${System.getProperty("os.name")} (${System.getProperty("os.version")})",
            architecture = architectureName(),
            runtime = "Java ${System.getProperty("java.runtime.version")}",
            graphics = graphicsName(),
            audio = listOfNotNull("libmpv", output?.describe()).joinToString(" · "),
            dataFolder = DesktopPrefs.dataDir.toString(),
        )
    }

    if (showEgg) ExhaleBreathingEgg(onDismiss = { showEgg = false })

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AboutHero(facts, onSecretUnlocked = { showEgg = true }, modifier = Modifier.feedIntro(0f, progress))
        Spacer(Modifier.height(8.dp))
        AboutActions(facts, onOpenUpdates, Modifier.feedIntro(0.6f, progress))
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SpecTable(facts, Modifier.feedIntro(1.2f, progress))
            MakerCard(Modifier.feedIntro(1.6f, progress))
            StackCard(Modifier.feedIntro(2.0f, progress))
            LicenceCard(Modifier.feedIntro(2.4f, progress))
        }
    }
}

/**
 * Who this is, the way a desktop says it: the mark, the name, the version, centred and quiet. No
 * banner, no aurora — what someone opens this page for is the version number and a way to check for
 * a newer one, and everything else here is in the way of that.
 */
@Composable
private fun AboutHero(facts: BuildFacts, onSecretUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    var taps by remember { mutableIntStateOf(0) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(118.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    taps++
                    if (taps >= SecretTapCount) {
                        taps = 0
                        onSecretUnlocked()
                    }
                },
        ) {
            Image(appIcon, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(14.dp))
        Text("Exhale", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Version ${facts.version} (${facts.commit})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The machine's side of it: labels right-aligned against their values, as a spec sheet reads. */
@Composable
private fun SpecTable(facts: BuildFacts, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("This build")
        Column(Modifier.fillMaxWidth().settingsGroup().padding(vertical = 2.dp)) {
        val rows = listOf(
            "Windows" to facts.windows,
            "Architecture" to facts.architecture,
            "Runtime" to facts.runtime,
            "Graphics" to facts.graphics,
            "Audio" to facts.audio,
            "Data folder" to facts.dataFolder,
        )
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) RowDivider(14.dp)
            SpecRow(label, value)
        }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(150.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutActions(facts: BuildFacts, onOpenUpdates: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_600)
            copied = false
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BannerButton("Check for Updates…", filled = true, onClick = onOpenUpdates)
        BannerButton(if (copied) "Copied" else "Copy report", filled = false) {
            clipboard.setText(AnnotatedString(facts.asReport()))
            copied = true
        }
        BannerButton("GitHub", filled = false) { SystemShell.open(LeadDeveloperRepo) }
    }
}

@Composable
private fun BannerButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.96f, hoverScale = 1.02f)
    val accent = MaterialTheme.colorScheme.primary
    val background by animateColorAsState(
        when {
            filled -> accent
            hovered -> Color.White.copy(alpha = 0.16f)
            else -> Color.White.copy(alpha = 0.09f)
        },
        label = "banner_button_bg",
    )
    val content = if (filled && accent.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .border(0.7.dp, Color.White.copy(alpha = if (filled) 0f else 0.12f), RoundedCornerShape(9.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = content, maxLines = 1)
    }
}

// ─── Lists ────────────────────────────────────────────────────────────────────

private const val LeadDeveloperRepo = "https://github.com/ozyern/Exhale"

/** One row that opens something: an icon or picture, a name, a quiet detail and a chevron. */
@Composable
private fun LinkRow(title: String, detail: String?, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = if (hovered) 0.045f else 0f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Chevron()
    }
}

@Composable
private fun MakerCard(modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Made by")
        Column(Modifier.fillMaxWidth().settingsGroup()) {
            LinkRow(LeadDeveloperName, "Lead developer · @$LeadDeveloperHandle", { SystemShell.open(LeadDeveloperUrl) }) {
                AsyncImage(
                    LeadDeveloperAvatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                )
            }
            SocialLinks.forEach { link ->
                RowDivider(60.dp)
                LinkRow(link.label, link.handle, { SystemShell.open(link.url) }) {
                    Icon(link.icon, contentDescription = null, tint = link.accent, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
private fun StackCard(modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Built with")
        Column(Modifier.fillMaxWidth().settingsGroup().padding(vertical = 2.dp)) {
            Stack.forEachIndexed { index, entry ->
                if (index > 0) RowDivider(14.dp)
                SpecRow(entry.name, entry.role)
            }
        }
    }
}

@Composable
private fun LicenceCard(modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        SectionLabel("Licence")
        Column(Modifier.fillMaxWidth().settingsGroup()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Text("GNU General Public License v3.0", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Exhale is free and open-source software. You can use, study, share and change it under the terms of the GPL.",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowDivider(14.dp)
            LinkRow("Read the licence", null, { SystemShell.open("$LeadDeveloperRepo/blob/master/LICENSE") }) {}
            RowDivider(14.dp)
            LinkRow("Source code", null, { SystemShell.open(LeadDeveloperRepo) }) {}
            RowDivider(14.dp)
            LinkRow("Releases", null, { SystemShell.open("$LeadDeveloperRepo/releases") }) {}
        }
    }
}
