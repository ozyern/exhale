/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.desktop.appIcon
import com.ozyern.exhale.desktop.data.AppInfo
import com.ozyern.exhale.desktop.data.AppSettings
import com.ozyern.exhale.desktop.data.AppStorage
import com.ozyern.exhale.desktop.data.LocalSettings
import com.ozyern.exhale.desktop.data.ReleaseInfo
import com.ozyern.exhale.desktop.data.SystemShell
import com.ozyern.exhale.desktop.data.UpdateChecker
import com.ozyern.exhale.desktop.data.UpdateDownload
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.feedIntro
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.lerp as lerpColor

private enum class UpdateStatus { Checking, UpToDate, Available, Failed }

/** System Settings' palette, light and dark: grouped boxes, hairlines, system blue. */
private class MacPalette(
    val group: Color,
    val groupBorder: Color,
    val separator: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val buttonFill: Color,
    val buttonBorder: Color,
    val green: Color,
    val red: Color,
    val sheet: Color,
    val scrim: Color,
    val track: Color,
    val switchOff: Color,
    val hover: Color,
)

private val DarkMac = MacPalette(
    group = Color.White.copy(alpha = 0.055f),
    groupBorder = Color.White.copy(alpha = 0.07f),
    separator = Color.White.copy(alpha = 0.08f),
    label = Color(0xFFF5F5F7),
    secondary = Color(0xFF98989D),
    tertiary = Color(0xFF6E6E73),
    accent = Color(0xFF0A84FF),
    buttonFill = Color.White.copy(alpha = 0.14f),
    buttonBorder = Color.White.copy(alpha = 0.05f),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    sheet = Color(0xFF28282B),
    scrim = Color.Black.copy(alpha = 0.38f),
    track = Color.White.copy(alpha = 0.12f),
    switchOff = Color.White.copy(alpha = 0.16f),
    hover = Color.White.copy(alpha = 0.04f),
)

private val LightMac = MacPalette(
    group = Color.White.copy(alpha = 0.78f),
    groupBorder = Color.Black.copy(alpha = 0.06f),
    separator = Color.Black.copy(alpha = 0.08f),
    label = Color(0xFF1D1D1F),
    secondary = Color(0xFF6E6E73),
    tertiary = Color(0xFF8E8E93),
    accent = Color(0xFF007AFF),
    buttonFill = Color.White,
    buttonBorder = Color.Black.copy(alpha = 0.12f),
    green = Color(0xFF28CD41),
    red = Color(0xFFFF3B30),
    sheet = Color(0xFFF6F6F8),
    scrim = Color.Black.copy(alpha = 0.18f),
    track = Color.Black.copy(alpha = 0.08f),
    switchOff = Color.Black.copy(alpha = 0.09f),
    hover = Color.Black.copy(alpha = 0.03f),
)

private val LocalMac = compositionLocalOf { DarkMac }

@Composable
private fun macText(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
    MaterialTheme.typography.bodyMedium.copy(fontSize = size.sp, fontWeight = weight, lineHeight = (size * 1.38f).sp, letterSpacing = 0.sp)

/**
 * Software Update, the way macOS lays it out in System Settings: the app, its version and one clear
 * status in a grouped box; the automatic check as a row with a switch; release history as a list, and
 * each version's notes in a sheet. Behind it, the same checker: newer Windows releases on GitHub,
 * downloaded, checked against their published SHA-256, and handed to the installer.
 */
@Composable
internal fun UpdatesPage(progress: () -> Float) {
    val mac = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkMac else LightMac
    val scope = rememberCoroutineScope()
    val settings = LocalSettings.current
    val available by UpdateChecker.available.collectAsState()
    val lastChecked by UpdateChecker.lastChecked.collectAsState()
    val download by UpdateChecker.download.collectAsState()
    var releases by remember { mutableStateOf<List<ReleaseInfo>?>(null) }
    var checking by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var notesFor by remember { mutableStateOf<ReleaseInfo?>(null) }

    fun check() {
        if (checking) return
        checking = true
        failure = null
        scope.launch {
            UpdateChecker.releases()
                .onSuccess { list ->
                    releases = list
                    UpdateChecker.available.value = list.firstOrNull()?.takeIf { UpdateChecker.isNewer(it.tag) }
                }
                .onFailure { failure = it.message ?: "GitHub couldn't be reached." }
            checking = false
        }
    }
    LaunchedEffect(Unit) { check() }

    val update = available
    val status = when {
        checking -> UpdateStatus.Checking
        update != null -> UpdateStatus.Available
        failure != null -> UpdateStatus.Failed
        releases != null -> UpdateStatus.UpToDate
        else -> UpdateStatus.Checking
    }
    val installed = releases?.firstOrNull { it.tag.trimStart('v', 'V') == AppInfo.VERSION }

    CompositionLocalProvider(LocalMac provides mac) {
        Column(Modifier.widthIn(max = 780.dp).fillMaxWidth().padding(top = 6.dp)) {
            Text(
                "Software Update",
                style = macText(26f, FontWeight.Bold).copy(letterSpacing = (-0.02).em),
                color = mac.label,
                modifier = Modifier.padding(start = 2.dp).feedIntro(0f, progress),
            )
            Spacer(Modifier.height(16.dp))

            MacGroup(Modifier.feedIntro(0.6f, progress)) {
                StatusBlock(
                    status = status,
                    update = update,
                    installed = installed,
                    failure = failure,
                    download = download,
                    onCheck = ::check,
                    onDownload = { release -> downloadJob = scope.launch { UpdateChecker.downloadInstaller(release) } },
                    onCancel = { downloadJob?.cancel() },
                    onNotes = { notesFor = it },
                )
            }
            Footnote("Exhale checks github.com/ozyern/exhale for Windows releases. Updates install to your user folder, so no administrator password is needed, and your library and settings stay as they are.")

            Spacer(Modifier.height(22.dp))
            MacGroup(Modifier.feedIntro(1.2f, progress)) {
                MacRow(
                    title = "Check for updates automatically",
                    subtitle = "Look for a new version each time Exhale opens.",
                    trailing = { MacSwitch(settings.checkUpdates) { value -> AppSettings.update { it.copy(checkUpdates = value) } } },
                )
                MacSeparator()
                MacRow(title = "Update channel", trailing = { Text("Stable", style = macText(13.5f), color = mac.secondary) })
                MacSeparator()
                MacRow(
                    title = "Last checked",
                    subtitle = relativeCheck(lastChecked),
                    trailing = { MacButton(if (checking) "Checking…" else "Check Now", enabled = !checking, onClick = ::check) },
                )
            }

            Spacer(Modifier.height(26.dp))
            MacSectionLabel("Release History", Modifier.feedIntro(1.8f, progress))
            MacGroup(Modifier.feedIntro(1.8f, progress)) {
                HistoryRows(releases, onOpen = { notesFor = it })
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(start = 4.dp).feedIntro(2.2f, progress)) {
                MacLink("All releases on GitHub…") { SystemShell.open(AppInfo.RELEASES_URL) }
            }
        }

        notesFor?.let { release ->
            NotesSheet(
                release = release,
                isUpdate = release.tag == update?.tag,
                isInstalled = release.tag.trimStart('v', 'V') == AppInfo.VERSION,
                onDismiss = { notesFor = null },
            )
        }
    }
}

private fun relativeCheck(at: Long?): String {
    if (at == null) return "Not checked yet"
    val minutes = (System.currentTimeMillis() - at) / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        else -> DateTimeFormatter.ofPattern("d MMM 'at' HH:mm", Locale.getDefault()).format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
    }
}

private fun releaseDate(at: Instant?): String? =
    at?.let { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()).format(it.atZone(ZoneId.systemDefault())) }

private fun ReleaseInfo.version(): String = tag.trimStart('v', 'V')

/** The app, where it stands, and the one thing to do about it. */
@Composable
private fun StatusBlock(
    status: UpdateStatus,
    update: ReleaseInfo?,
    installed: ReleaseInfo?,
    failure: String?,
    download: UpdateDownload,
    onCheck: () -> Unit,
    onDownload: (ReleaseInfo) -> Unit,
    onCancel: () -> Unit,
    onNotes: (ReleaseInfo) -> Unit,
) {
    val mac = LocalMac.current
    val state = when {
        update == null -> UpdateDownload.Idle
        download is UpdateDownload.Downloading && download.release.tag == update.tag -> download
        download is UpdateDownload.Verifying && download.release.tag == update.tag -> download
        download is UpdateDownload.Ready && download.release.tag == update.tag -> download
        download is UpdateDownload.Failed && download.release.tag == update.tag -> download
        else -> UpdateDownload.Idle
    }
    var failedToStart by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIconTile(62.dp)
        Spacer(Modifier.width(16.dp))
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.weight(1f),
            label = "mac_update_status",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (shown) {
                    UpdateStatus.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        MacSpinner(15.dp, mac.secondary)
                        Spacer(Modifier.width(10.dp))
                        Text("Checking for updates…", style = macText(14f), color = mac.label)
                    }
                    UpdateStatus.UpToDate -> {
                        Text("Exhale is up to date", style = macText(15f, FontWeight.SemiBold), color = mac.label)
                        Text("Exhale ${AppInfo.VERSION} (${AppInfo.commit})", style = macText(12.5f), color = mac.secondary)
                        installed?.let { MacLink("More Info…") { onNotes(it) } }
                    }
                    UpdateStatus.Failed -> {
                        Text("Unable to check for updates", style = macText(15f, FontWeight.SemiBold), color = mac.label)
                        Text(failure.orEmpty(), style = macText(12.5f), color = mac.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    UpdateStatus.Available -> update?.let { release ->
                        Text("Exhale ${release.version()}", style = macText(15f, FontWeight.SemiBold), color = mac.label)
                        Text(
                            listOfNotNull(release.installer?.let { AppStorage.format(it.size) }, releaseDate(release.publishedAt)).joinToString(" — "),
                            style = macText(12.5f),
                            color = mac.secondary,
                        )
                        MacLink("More Info…") { onNotes(release) }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            status == UpdateStatus.Failed -> MacButton("Try Again", onClick = onCheck)
            status == UpdateStatus.Available && update != null -> when (state) {
                UpdateDownload.Idle -> MacButton("Update Now", primary = true) { onDownload(update) }
                is UpdateDownload.Failed -> MacButton("Try Again", primary = true) { onDownload(update) }
                is UpdateDownload.Ready -> MacButton("Restart Now", primary = true) { failedToStart = !UpdateChecker.install(state.file) }
                else -> Unit
            }
            else -> Unit
        }
    }

    // Download progress sits under the status, the way a macOS update reports it.
    if (status == UpdateStatus.Available) {
        when (state) {
            is UpdateDownload.Downloading -> {
                MacSeparator(inset = 0.dp)
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    val fraction = if (state.total > 0) (state.bytes.toFloat() / state.total).coerceIn(0f, 1f) else 0f
                    Text("Downloading Exhale ${state.release.version()}…", style = macText(13.5f), color = mac.label)
                    Spacer(Modifier.height(9.dp))
                    MacProgressBar(fraction)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val remaining = if (state.bytesPerSecond > 0 && state.total > 0) (state.total - state.bytes) / state.bytesPerSecond else null
                        Text(
                            buildString {
                                append("${AppStorage.format(state.bytes)} of ${AppStorage.format(state.total)}")
                                remaining?.let {
                                    append(" — ")
                                    append(if (it < 60) "About $it seconds remaining" else "About ${(it + 30) / 60} minutes remaining")
                                }
                            },
                            style = macText(12.5f),
                            color = mac.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        MacButton("Cancel", onClick = onCancel)
                    }
                }
            }
            is UpdateDownload.Verifying -> {
                MacSeparator(inset = 0.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    MacSpinner(14.dp, mac.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying the download against its published checksum…", style = macText(13.5f), color = mac.label)
                }
            }
            is UpdateDownload.Ready -> {
                MacSeparator(inset = 0.dp)
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(NavIcons.Check, contentDescription = null, tint = mac.green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.verified) "Downloaded and verified. Exhale will close while the installer runs." else "Downloaded. Exhale will close while the installer runs.",
                            style = macText(13.5f),
                            color = mac.label,
                        )
                    }
                    if (failedToStart) {
                        Spacer(Modifier.height(6.dp))
                        Text("Windows wouldn't start the installer. Try again, or run it from GitHub.", style = macText(12.5f), color = mac.red)
                    }
                }
            }
            is UpdateDownload.Failed -> {
                MacSeparator(inset = 0.dp)
                Text(state.message, style = macText(13f), color = mac.red, modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp))
            }
            UpdateDownload.Idle -> Unit
        }
    }
}

@Composable
private fun HistoryRows(releases: List<ReleaseInfo>?, onOpen: (ReleaseInfo) -> Unit) {
    val mac = LocalMac.current
    when {
        releases == null -> MacRow(title = "Loading release history…", trailing = { MacSpinner(14.dp, mac.secondary) })
        releases.isEmpty() -> MacRow(title = "No Windows releases have been published yet.")
        else -> releases.take(8).forEachIndexed { index, release ->
            if (index > 0) MacSeparator()
            val current = release.version() == AppInfo.VERSION
            MacRow(
                title = "Exhale ${release.version()}",
                subtitle = releaseDate(release.publishedAt),
                onClick = { onOpen(release) },
                trailing = {
                    if (current) {
                        Icon(NavIcons.Check, contentDescription = null, tint = mac.green, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Installed", style = macText(12.5f), color = mac.secondary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Icon(NavIcons.ChevronRight, contentDescription = null, tint = mac.tertiary, modifier = Modifier.size(16.dp))
                },
            )
        }
    }
}

/** The app icon as macOS draws one: a rounded square with a soft drop shadow. */
@Composable
private fun AppIconTile(size: Dp) {
    val shape = RoundedCornerShape(size * 0.23f)
    Box(Modifier.size(size).shadow(size * 0.09f, shape).clip(shape).background(Color(0xFF141116)), contentAlignment = Alignment.Center) {
        Image(appIcon, contentDescription = "Exhale", modifier = Modifier.size(size * 0.86f).clip(CircleShape))
    }
}

@Composable
private fun MacGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val mac = LocalMac.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 1f, stiffness = 500f))
            .clip(shape)
            .background(mac.group)
            .border(0.5.dp, mac.groupBorder, shape),
        content = content,
    )
}

@Composable
private fun MacRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val mac = LocalMac.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .background(if (hovered) mac.hover else Color.Transparent)
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = macText(13.5f), color = mac.label)
            subtitle?.let { Text(it, style = macText(12f), color = mac.secondary) }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
private fun MacSeparator(inset: Dp = 16.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(LocalMac.current.separator))
}

@Composable
private fun MacSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = macText(13f, FontWeight.SemiBold), color = LocalMac.current.label, modifier = modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
private fun Footnote(text: String) {
    Text(text, style = macText(12f), color = LocalMac.current.tertiary, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp))
}

@Composable
private fun MacLink(text: String, onClick: () -> Unit) {
    val mac = LocalMac.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text,
        style = macText(12.5f).copy(textDecoration = if (hovered) TextDecoration.Underline else TextDecoration.None),
        color = mac.accent,
        modifier = Modifier
            .padding(top = 2.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    )
}

/** A push button: system blue for the default action, a quiet grey plate otherwise. */
@Composable
private fun MacButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val mac = LocalMac.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(6.dp)
    val base = if (primary) mac.accent else mac.buttonFill
    val fill by animateColorAsState(if (pressed) lerpColor(base, Color.Black, 0.14f) else base, tween(90), label = "mac_button")
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .shadow(if (primary) 1.5.dp else 0.6.dp, shape)
            .clip(shape)
            .background(if (primary) Brush.verticalGradient(listOf(lerpColor(fill, Color.White, 0.1f), fill)) else Brush.verticalGradient(listOf(fill, fill)))
            .border(0.5.dp, if (primary) Color.Transparent else mac.buttonBorder, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
            .heightIn(min = 26.dp)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = macText(13f, FontWeight.Medium), color = if (primary) Color.White else mac.label, maxLines = 1)
    }
}

/** The small toggle from System Settings: blue when on, the knob sliding over with a little give. */
@Composable
private fun MacSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val mac = LocalMac.current
    val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 650f), label = "mac_switch")
    val track by animateColorAsState(if (checked) mac.accent else mac.switchOff, tween(160), label = "mac_switch_track")
    Box(
        Modifier
            .width(38.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .size(18.dp)
                .graphicsLayer { translationX = 16.dp.toPx() * t }
                .shadow(1.5.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun MacProgressBar(fraction: Float) {
    val mac = LocalMac.current
    val shown by animateFloatAsState(fraction, spring(dampingRatio = 1f, stiffness = 200f), label = "mac_progress")
    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(mac.track)) {
        Box(Modifier.fillMaxWidth(shown).height(5.dp).clip(CircleShape).background(mac.accent))
    }
}

/** The twelve-spoke activity indicator. The spokes step rather than spin, as the real one does. */
@Composable
private fun MacSpinner(size: Dp, color: Color) {
    val step = rememberInfiniteTransition(label = "mac_spinner").animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "mac_spinner_step",
    )
    Canvas(Modifier.size(size)) {
        val lead = step.value.toInt() % 12
        val outer = this.size.minDimension / 2f
        val inner = outer * 0.48f
        val stroke = outer * 0.19f
        for (i in 0 until 12) {
            val age = ((lead - i) % 12 + 12) % 12
            val angle = (i * 30f - 90f) * (PI.toFloat() / 180f)
            drawLine(
                color = color.copy(alpha = color.alpha * (1f - age / 12f * 0.82f)),
                start = Offset(center.x + cos(angle) * inner, center.y + sin(angle) * inner),
                end = Offset(center.x + cos(angle) * (outer - stroke / 2f), center.y + sin(angle) * (outer - stroke / 2f)),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Covers the whole window, so the sheet can dim everything behind it. */
private object WholeWindow : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset = IntOffset.Zero
}

/** A version's notes in a sheet over the window, with Done as the default button. Esc or a click outside closes it. */
@Composable
private fun NotesSheet(release: ReleaseInfo, isUpdate: Boolean, isInstalled: Boolean, onDismiss: () -> Unit) {
    val mac = LocalMac.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f)) }
    val shape = RoundedCornerShape(14.dp)

    Popup(
        popupPositionProvider = WholeWindow,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onKeyEvent = {
            if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) {
                onDismiss()
                true
            } else {
                false
            }
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
                .background(mac.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 660.dp)
                    .graphicsLayer {
                        val a = appear.value
                        val s = 0.94f + 0.06f * a
                        scaleX = s
                        scaleY = s
                        translationY = (1f - a) * (-18).dp.toPx()
                    }
                    .shadow(30.dp, shape)
                    .clip(shape)
                    .background(mac.sheet)
                    .border(0.5.dp, mac.groupBorder, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconTile(46.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                isUpdate -> "What's New in Exhale ${release.version()}"
                                else -> "Exhale ${release.version()}"
                            },
                            style = macText(16f, FontWeight.SemiBold),
                            color = mac.label,
                        )
                        Text(
                            listOfNotNull(releaseDate(release.publishedAt), if (isInstalled) "Installed on this PC" else null).joinToString(" — "),
                            style = macText(12.5f),
                            color = mac.secondary,
                        )
                    }
                }
                MacSeparator(inset = 0.dp)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    if (release.notes.isBlank()) {
                        Text("No release notes were published with this version.", style = macText(13.5f), color = mac.secondary)
                    } else {
                        MarkdownText(release.notes)
                    }
                }
                MacSeparator(inset = 0.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MacButton("View on GitHub") { SystemShell.open(release.url) }
                    Spacer(Modifier.width(8.dp))
                    MacButton("Done", primary = true, onClick = onDismiss)
                }
            }
        }
    }
}

/** The release notes' Markdown: headings, bullet lists, bold, code and links. */
@Composable
private fun MarkdownText(markdown: String) {
    val mac = LocalMac.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))
                trimmed.startsWith("### ") -> Text(inline(trimmed.removePrefix("### "), mac.accent), style = macText(14f, FontWeight.SemiBold), color = mac.label, modifier = Modifier.padding(top = 8.dp))
                trimmed.startsWith("## ") -> Text(inline(trimmed.removePrefix("## "), mac.accent), style = macText(15f, FontWeight.Bold), color = mac.label, modifier = Modifier.padding(top = 10.dp))
                trimmed.startsWith("# ") -> Text(inline(trimmed.removePrefix("# "), mac.accent), style = macText(17f, FontWeight.Bold), color = mac.label, modifier = Modifier.padding(top = 10.dp))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val indent = (line.length - trimmed.length) / 2
                    Row(Modifier.padding(start = (indent * 18).dp)) {
                        Text("•", style = macText(13.5f, FontWeight.Bold), color = mac.secondary, modifier = Modifier.width(16.dp))
                        Text(inline(trimmed.drop(2), mac.accent), style = macText(13.5f), color = mac.label)
                    }
                }
                else -> Text(inline(trimmed, mac.accent), style = macText(13.5f), color = mac.label)
            }
        }
    }
}

private val InlineMarkdown = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)""")

private fun inline(text: String, accent: Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    InlineMarkdown.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val (bold, code, linkText) = match.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = accent.copy(alpha = 0.12f))) { append(code) }
            linkText.isNotEmpty() -> withStyle(SpanStyle(color = accent)) { append(linkText) }
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
