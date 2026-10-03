/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ozyern.exhale.ui.component.settingsGlassGroup
import com.ozyern.exhale.utils.DeviceNames
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.ozyern.exhale.ui.component.LoadingRing
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import com.ozyern.exhale.constants.EnableUpdateNotificationKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.component.AuroraBackdrop
import com.ozyern.exhale.ui.component.IconButton
import com.ozyern.exhale.ui.component.LiquidGlassSheet
import com.ozyern.exhale.ui.component.SettingsDividerThickness
import com.ozyern.exhale.ui.component.SettingsGroupCornerRadius
import com.ozyern.exhale.ui.component.liquidGlassSurface
import com.ozyern.exhale.ui.component.liquid.LiquidButton
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import com.ozyern.exhale.ui.component.settingsDividerColor
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.BundledChangelog
import com.ozyern.exhale.utils.DownloadProgress
import com.ozyern.exhale.utils.UpdateInfo
import com.ozyern.exhale.utils.UpdateNotificationManager
import com.ozyern.exhale.utils.Updater
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import android.text.format.DateUtils
import com.ozyern.exhale.constants.LastUpdateCheckKey

// ─── Update check state ───────────────────────────────────────────────────────

private sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Loading : UpdateCheckState
    data object UpToDate : UpdateCheckState
    data class UpdateAvailable(val info: UpdateInfo) : UpdateCheckState
    data class Error(val message: String) : UpdateCheckState
}

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * Software Update.
 *
 * Deliberately a *short* page. It used to carry a stable/nightly channel picker and a scrolling
 * feed of raw git commits; the picker offered a choice between one real channel and one that is
 * not published, and the commit feed answered a question ("what changed in the source this week")
 * that nobody standing on an update screen is asking. Both are gone. What is left is the only
 * thing the page is for: which version you are on, whether there is a newer one, and one button
 * that fetches it.
 *
 * The changelog still exists in full behind the last row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    autoStart: Boolean = false,
) {
    // The whole page in OPPO Sans, as ColorOS sets its own update page.
    com.ozyern.exhale.ui.component.ColorOsType {
        UpdateScreenContent(navController, scrollBehavior, autoStart)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateScreenContent(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    /**
     * Arrive checking, and start the transfer as soon as there is something to fetch.
     *
     * Set by the launch prompt's "Update Now", which navigates here instead of throwing the user
     * out to a browser. Without it the prompt would land you on a page with the same button you
     * just pressed, which is the kind of hand-off that makes people give up halfway.
     */
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    val (enableUpdateNotification, onEnableUpdateNotificationChange) = rememberPreference(
        EnableUpdateNotificationKey, defaultValue = false
    )

    // Written by the background worker after every automatic check, and now by the manual one
    // too. It was already being recorded and never shown — the page could tell you a newer build
    // existed but not whether it had looked in the last minute or the last fortnight, which is
    // the difference between "up to date" meaning something and meaning nothing.
    val (lastCheckedAt, onLastCheckedAtChange) = rememberPreference(LastUpdateCheckKey, 0L)

    var latestVersion by remember { mutableStateOf<String?>(null) }
    var updateCheckState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var whatsNewExpanded by remember { mutableStateOf(false) }
    var pendingUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            onEnableUpdateNotificationChange(true)
            UpdateNotificationManager.schedulePeriodicUpdateCheck(context)
        }
    }

    fun checkForUpdate() {
        if (updateCheckState == UpdateCheckState.Loading) return
        coroutineScope.launch {
            updateCheckState = UpdateCheckState.Loading
            onLastCheckedAtChange(System.currentTimeMillis())
            // Always from the network: this screen is someone asking. See Updater.checkForUpdate.
            Updater.checkForUpdate(BuildConfig.VERSION_NAME, forceRefresh = true)
                .onSuccess { info ->
                    if (info == null) {
                        updateCheckState = UpdateCheckState.UpToDate
                    } else {
                        pendingUpdateInfo = info
                        updateCheckState = UpdateCheckState.UpdateAvailable(info)
                        showUpdateDialog = true
                    }
                }
                .onFailure { err ->
                    updateCheckState = UpdateCheckState.Error(err.message ?: "Check failed")
                }
        }
    }

    LaunchedEffect(autoStart) {
        if (autoStart) checkForUpdate()
    }

    if (showUpdateDialog) {
        pendingUpdateInfo?.let { info ->
            UpdateAvailableSheet(
                info = info,
                autoStart = autoStart,
                onViewRelease = { showUpdateDialog = false; uriHandler.openUri(info.releasePageUrl) },
                onDismiss = { showUpdateDialog = false }
            )
        }
    }

    LaunchedEffect(Unit) {
        Updater.getLatestVersionName().onSuccess { latestVersion = it }
    }

    val updateAvailable = latestVersion?.let { Updater.hasUpdate(it, BuildConfig.VERSION_NAME) } == true

    // The bar is shared with the page that linked here, and it arrives collapsed if that page was
    // scrolled down - which the About page, the only way in, almost always is. This page is one
    // card tall and has nothing to scroll back, so the title and the back button would simply
    // never reappear. Every other settings page recovers by being scrollable; this one has to ask.
    LaunchedEffect(Unit) {
        scrollBehavior.state.heightOffset = 0f
        scrollBehavior.state.contentOffset = 0f
    }

    val statusTitle = when (val state = updateCheckState) {
        UpdateCheckState.Loading -> stringResource(R.string.update_status_checking)
        UpdateCheckState.UpToDate -> "Version up to date"
        is UpdateCheckState.UpdateAvailable -> "Exhale ${state.info.versionName} is available"
        is UpdateCheckState.Error -> "Couldn't check for updates"
        UpdateCheckState.Idle -> if (updateAvailable) "Update available" else "Version up to date"
    }
    val canDownload = updateAvailable || pendingUpdateInfo?.let { Updater.hasUpdate(it.versionName, BuildConfig.VERSION_NAME) } == true

    // ColorOS 17's software update page: the artwork is the whole screen, the release mark sits
    // large in its upper half, and everything you can read or press is in one frosted card at
    // the foot, where the thumb already is.
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.exhale_update_bg),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.25f),
                    ),
                ),
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Software update",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    },
                    navigationIcon = {
                        LiquidBackButton(
                            onClick = navController::navigateUp,
                            onLongClick = navController::backToMain,
                            icon = R.drawable.chevron_back,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    scrollBehavior = scrollBehavior
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(0.5f))
                com.ozyern.exhale.ui.component.ExhaleGlowWordmark(fontSize = 66.sp)
                com.ozyern.exhale.ui.component.ColorOsVersionText(BuildConfig.VERSION_NAME, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.weight(1f))

                // The card: what state you are in, the build, and the two things you can do.
                val cardShape = RoundedCornerShape(32.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(Color(0xCC2A1C08), Color(0xE60E0A05)),
                            ),
                        )
                        .border(0.8.dp, Color.White.copy(alpha = 0.14f), cardShape)
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                ) {
                    Text(
                        text = statusTitle,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Exhale_${BuildConfig.VERSION_NAME}_${BuildConfig.ARCHITECTURE}(${BuildConfig.GIT_COMMIT})",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    (updateCheckState as? UpdateCheckState.Error)?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.message, fontSize = 13.sp, color = Color.White.copy(alpha = 0.55f))
                    }
                    Spacer(Modifier.height(22.dp))
                    UpdatePill(
                        label = if (canDownload) "Download & install" else "Check for updates",
                        loading = updateCheckState == UpdateCheckState.Loading,
                        filled = true,
                        onClick = {
                            val pending = pendingUpdateInfo
                            if (pending != null && Updater.hasUpdate(pending.versionName, BuildConfig.VERSION_NAME)) {
                                showUpdateDialog = true
                            } else {
                                checkForUpdate()
                            }
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    UpdatePill(
                        label = "View update notes",
                        loading = false,
                        filled = false,
                        onClick = { navController.navigate("settings/update/notes") },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** ColorOS's two card buttons: a bright pill for the action, a dark one for the notes. */
@Composable
private fun UpdatePill(label: String, loading: Boolean, filled: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.75f, stiffness = 900f), label = "updatePill")
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(
                if (filled) {
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFFF7F3EC), Color(0xFFD9D4CC)))
                } else {
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.10f)))
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = !loading) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        if (loading) {
            LoadingRing(modifier = Modifier.size(20.dp), stroke = 2.dp, color = if (filled) Color.Black else Color.White)
        } else {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = if (filled) Color(0xFF141414) else Color.White)
        }
    }
}

private const val GitHubReleasesUrl = "https://github.com/ozyern/Exhale/releases"

// ─── Hero ─────────────────────────────────────────────────────────────────────

@Composable
private fun UpdateHero(
    state: UpdateCheckState,
    updateAvailable: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "updateHero")

    // One phase drives both rings; the second reads it offset by half a cycle, so they pulse
    // alternately off a single animation.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "haloPhase",
    )

    // Only spun while a check is actually running. It used to run permanently and was simply
    // multiplied by zero when idle — an animation nobody could see, costing a frame callback
    // for the entire time the page was on screen.
    val spinTransition = rememberInfiniteTransition(label = "updateGlyph")
    val spin by if (state == UpdateCheckState.Loading) {
        spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "glyphSpin",
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val accent = when (state) {
        is UpdateCheckState.Error -> MaterialTheme.colorScheme.error
        UpdateCheckState.UpToDate -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val accentColor by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(400),
        label = "heroAccent",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(148.dp),
            contentAlignment = Alignment.Center,
        ) {
            Halo(phase = { phase }, color = accentColor)
            Halo(phase = { (phase + 0.5f) % 1f }, color = accentColor)

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .liquidGlassSurface(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(
                        when (state) {
                            UpdateCheckState.UpToDate -> R.drawable.done
                            else -> R.drawable.update
                        }
                    ),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(42.dp)
                        .graphicsLayer {
                            rotationZ = if (state == UpdateCheckState.Loading) spin else 0f
                        },
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(6.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 3 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically { -it / 3 })
            },
            label = "updateStatus",
        ) { s ->
            val message = when (s) {
                UpdateCheckState.Loading -> stringResource(R.string.update_status_checking)
                UpdateCheckState.UpToDate -> stringResource(R.string.update_status_up_to_date)
                is UpdateCheckState.UpdateAvailable ->
                    stringResource(R.string.update_version_label, s.info.versionName)

                is UpdateCheckState.Error ->
                    stringResource(R.string.update_status_failed, s.message)

                UpdateCheckState.Idle ->
                    if (updateAvailable) {
                        stringResource(R.string.update_status_available)
                    } else {
                        stringResource(R.string.update_version_label, BuildConfig.VERSION_NAME)
                    }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = when (s) {
                    is UpdateCheckState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** One expanding, fading ring. [phase] runs 0→1 and restarts. */
@Composable
private fun Halo(
    // A lambda, not a Float. The phase ticks every frame forever; taking it by value meant
    // reading it in the CALLER's composition, so the whole hero — glass circle, halos, title,
    // status line — recomposed 60 times a second for as long as the page was open, whether or
    // not anything was happening. Read inside graphicsLayer it is a draw-phase read, and the
    // pulse costs one GPU transform per frame and nothing else.
    phase: () -> Float,
    color: Color,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .graphicsLayer {
                val p = phase()
                val scale = 1f + p * 0.52f
                scaleX = scale
                scaleY = scale
                alpha = (1f - p) * 0.28f
            }
            .clip(CircleShape)
            .background(color),
    )
}

// ─── Grouped rows ─────────────────────────────────────────────────────────────

@Composable
private fun UpdateGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
    )
}

@Composable
private fun UpdateGlassGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlassSurface(RoundedCornerShape(SettingsGroupCornerRadius)),
        content = content,
    )
}

/** Hairline between rows. Never at a group's top or bottom edge — the caller places these. */
@Composable
private fun UpdateRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp),
        thickness = SettingsDividerThickness,
        color = settingsDividerColor(),
    )
}

/**
 * One grouped-list row: tinted glyph, title over optional subtitle, and either a value string or a
 * trailing composable on the right.
 */
@Composable
private fun UpdateRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    valueBold: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(14.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Medium,
                color = valueColor,
            )
        }

        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            trailing()
        }
    }
}

// ─── UpdateAvailableSheet ─────────────────────────────────────────────────────

/** What the sheet's primary action is currently doing. */
private sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val progress: DownloadProgress) : DownloadState
    data object Installing : DownloadState
    data object NeedsPermission : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * The "Software Update" sheet — Apple's presentation of an available update, in our glass.
 *
 * Replaces an `AlertDialog` whose only action kicked the user out to a browser to download the APK
 * by hand. The download now happens in place, which is the whole point of the ported backend: the
 * sheet owns the transfer, shows real byte progress, and hands the finished file straight to the
 * system installer.
 */
@Composable
private fun UpdateAvailableSheet(
    info: UpdateInfo,
    onViewRelease: () -> Unit,
    onDismiss: () -> Unit,
    /** Begin the transfer on first frame — see [UpdateScreen]'s parameter of the same name. */
    autoStart: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val isBusy = state is DownloadState.Downloading || state is DownloadState.Installing

    fun startDownload() {
        coroutineScope.launch {
            // Fixed filename in the cache dir: it is covered by the <cache-path> entry in
            // provider_paths.xml (so FileProvider can hand it to the installer), and reusing one
            // name means a previous abandoned download cannot accumulate copies of a ~100 MB APK.
            val destination = File(context.cacheDir, "update.apk")
            state = DownloadState.Downloading(DownloadProgress(0L, -1L))
            runCatching {
                Updater.downloadApk(info.downloadUrl, destination).collect { progress ->
                    state = DownloadState.Downloading(progress)
                }
            }.onSuccess {
                state = DownloadState.Installing
                state = if (Updater.installApk(context, destination)) {
                    DownloadState.Installing
                } else {
                    DownloadState.NeedsPermission
                }
            }.onFailure { error ->
                state = DownloadState.Failed(error.message ?: "Download failed")
            }
        }
    }

    // Once, on arrival. Keyed on Unit rather than on `autoStart` so a recomposition that
    // re-reads the flag cannot kick off a second download over the first.
    LaunchedEffect(Unit) {
        if (autoStart && state is DownloadState.Idle) startDownload()
    }

    LiquidGlassSheet(
        onDismiss = onDismiss,
        // A half-written APK is worse than no APK, so the sheet refuses every dismissal route
        // while bytes are moving — scrim tap, back press and the grab handle all go away.
        dismissible = !isBusy,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 28.dp),
        ) {
            UpdateSheetHeader(versionName = info.versionName)

            Spacer(Modifier.height(22.dp))

            // Inset grouped table, the same language as Settings: two rows, one hairline between
            // them, never at the card's outer edges.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(SettingsGroupCornerRadius))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            ) {
                UpdateVersionRow(
                    label = stringResource(R.string.update_installed_version),
                    value = BuildConfig.VERSION_NAME,
                    highlight = false,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = SettingsDividerThickness,
                    color = settingsDividerColor(),
                )
                UpdateVersionRow(
                    label = stringResource(R.string.update_new_version),
                    value = info.versionName,
                    highlight = true,
                )
            }

            if (!info.releaseNotes.isNullOrBlank()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = stringResource(R.string.update_whats_new),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(SettingsGroupCornerRadius))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text(
                        text = info.releaseNotes.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            UpdateSheetActions(
                state = state,
                onDownload = ::startDownload,
                onGrantPermission = {
                    Updater.requestInstallPermission(context)
                    state = DownloadState.Idle
                },
                onViewRelease = onViewRelease,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Hero block: a large circular glyph over bold identity text, centred — the iOS sheet opening. */
@Composable
private fun UpdateSheetHeader(versionName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.update),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.update_available_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.update_version_label, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UpdateVersionRow(
    label: String,
    value: String,
    highlight: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun UpdateSheetActions(
    state: DownloadState,
    onDownload: () -> Unit,
    onGrantPermission: () -> Unit,
    onViewRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedContent(
            targetState = state,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "updateAction",
        ) { s ->
            when (s) {
                is DownloadState.Downloading -> UpdateProgressBlock(s.progress)

                DownloadState.Installing -> Text(
                    text = stringResource(R.string.update_opening_installer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                DownloadState.NeedsPermission -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.update_install_permission_rationale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onGrantPermission,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_open_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                is DownloadState.Failed -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onDownload,
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.update_retry),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // A solid, filled capsule with a two-word label and no icon.
                //
                // It was a translucent glass button reading "Download and Install" behind a
                // download glyph. Three problems, all of them the same problem: a primary action
                // should not be see-through (the one control on the sheet that must be
                // unmissable was the one made of the same material as the background), should not
                // need an icon to explain a verb, and should not describe its own implementation.
                // Nobody is choosing between downloading and installing.
                DownloadState.Idle -> Button(
                    onClick = onDownload,
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text(
                        text = stringResource(R.string.update_now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Secondary actions stay hidden while the transfer is in flight — there is nothing safe
        // to do at that point except wait.
        AnimatedVisibility(
            visible = state !is DownloadState.Downloading && state != DownloadState.Installing,
        ) {
            // Accent text, not the muted grey, and directly under the primary rather than beside
            // it. A greyed-out alternative reads as disabled and people stop seeing it; a pair of
            // equal-weight buttons in a row makes a person stop and choose. Same accent, less
            // weight, second position - the hierarchy is carried by everything except colour.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    onClick = onViewRelease,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.update_view_release))
                }
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(stringResource(R.string.update_not_now))
                }
            }
        }
    }
}

/**
 * Apple-style transfer readout: a hairline capsule track with a spring-eased fill, the byte count
 * underneath, and the percentage on the right.
 *
 * The fill is animated rather than snapped to each emission — progress arrives every 512 KB, so a
 * raw binding would visibly step. Falls back to an indeterminate bar when the server withheld a
 * Content-Length.
 */
@Composable
private fun UpdateProgressBlock(progress: DownloadProgress) {
    val fraction = progress.fraction
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = spring(
            dampingRatio = AquamorphicDampingRatio,
            stiffness = AquamorphicStiffness,
        ),
        label = "downloadFill",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            if (fraction == null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedFraction)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (progress.totalBytes > 0L) {
                    stringResource(
                        R.string.update_downloaded_of,
                        formatBytes(progress.downloadedBytes),
                        formatBytes(progress.totalBytes),
                    )
                } else {
                    formatBytes(progress.downloadedBytes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fraction != null) {
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
