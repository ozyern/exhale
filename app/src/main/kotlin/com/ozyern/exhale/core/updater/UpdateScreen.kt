@file:OptIn(zone.ien.hig.ExperimentalCupertinoApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ozyern.exhale.core.updater

import android.content.Intent
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.ColorOsVersionText
import com.ozyern.exhale.ui.component.ExhaleGlowWordmark
import com.ozyern.exhale.ui.component.GlassCircleButton
import com.ozyern.exhale.ui.component.OppoSans
import com.ozyern.exhale.ui.component.UpdateArtwork
import com.ozyern.exhale.ui.component.frostedGlass
import androidx.compose.foundation.shape.RoundedCornerShape
import zone.ien.hig.CupertinoActivityIndicator
import zone.ien.hig.CupertinoButtonSize
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.screens.settings.exhaleBuildFlavor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.MenuItemChrome
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.viewmodels.UpdateViewModel
import zone.ien.hig.CupertinoButton
import zone.ien.hig.CupertinoButtonDefaults

@Composable
fun UpdateScreen(navController: NavHostController, viewModel: UpdateViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.check(manual = true) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val release = state.release
    val artwork = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        UpdateArtwork(artwork)
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassCircleButton(onClick = { navController.navigateUp() }, size = 44.dp) {
                    SymbolIcon(symbol = sf("chevron.left"), contentDescription = stringResource(R.string.back_content_desc))
                }
                Text(
                    stringResource(R.string.exhale_update_title),
                    color = Color.White,
                    fontFamily = OppoSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    modifier = Modifier.semantics { heading() },
                )
            }

            val checking = state.enabled && state.phase == UpdatePhase.CHECKING && release == null
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(if (checking) 1.1f else 0.35f))
                ExhaleGlowWordmark(fontSize = 66.sp)
                ColorOsVersionText(BuildConfig.VERSION_NAME, fontSize = 22.sp, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.weight(1f))

                if (checking) {
                    Row(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth().height(56.dp)
                            .frostedGlass(artwork, RoundedCornerShape(28.dp))
                            .padding(start = 22.dp, end = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.exhale_update_checking), fontFamily = OppoSans, fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.92f), modifier = Modifier.weight(1f))
                        CupertinoActivityIndicator(color = Color.White.copy(alpha = 0.85f))
                    }
                } else {
                    Column(
                        Modifier.widthIn(max = 560.dp).fillMaxWidth()
                            .frostedGlass(artwork, RoundedCornerShape(32.dp))
                            .padding(horizontal = 22.dp, vertical = 24.dp)
                            .animateContentSize(),
                    ) {
                        val title = when {
                            release != null -> stringResource(R.string.exhale_update_available, release.version)
                            state.error != null -> stringResource(R.string.exhale_update_failed)
                            else -> stringResource(R.string.exhale_update_up_to_date)
                        }
                        Text(title, fontFamily = OppoSans, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        val subtitle = if (release != null) {
                            stringResource(R.string.exhale_update_size_date, "%.1f".format(release.asset.size / 1048576.0), release.publishedAt.take(10))
                        } else "Exhale_${BuildConfig.VERSION_NAME}_${BuildConfig.ARCHITECTURE}(${BuildConfig.GIT_COMMIT})"
                        Text(subtitle, fontFamily = OppoSans, fontSize = 15.sp, color = Color.White.copy(alpha = 0.7f))

                        if (release != null) {
                            androidx.compose.animation.AnimatedVisibility(showNotes) {
                                Column(Modifier.padding(top = 14.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                                    CompositionLocalProvider(LocalContentColor provides Color.White) {
                                        if (release.description.isNotBlank()) Text(release.description, fontFamily = OppoSans, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                                        UpdateChangelog(release)
                                    }
                                }
                            }
                            if (state.phase != UpdatePhase.AVAILABLE && state.phase != UpdatePhase.IDLE || state.error != null) {
                                Spacer(Modifier.height(10.dp))
                                CompositionLocalProvider(LocalContentColor provides Color.White) { UpdateStatus(state) }
                            }
                        } else state.error?.let {
                            Spacer(Modifier.height(4.dp))
                            CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = 0.55f)) { UpdateStatus(state) }
                        }

                        Spacer(Modifier.height(20.dp))
                        val busy = UpdatePolicy.isTransfer(state.phase)
                        when {
                            release != null -> {
                                UpdateCardButton(
                                    stringResource(if (state.phase == UpdatePhase.READY) R.string.exhale_update_install else R.string.exhale_update_download),
                                    primary = true,
                                    enabled = state.enabled && !busy && state.phase != UpdatePhase.CHECKING && state.phase != UpdatePhase.INSTALLED,
                                ) {
                                    if (state.phase == UpdatePhase.READY) context.startActivity(Intent(context, UpdateInstallActivity::class.java)) else viewModel.download()
                                }
                                Spacer(Modifier.height(12.dp))
                                if (state.phase in setOf(UpdatePhase.QUEUED, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) {
                                    UpdateCardButton(stringResource(R.string.cancel), primary = false, onClick = viewModel::cancel)
                                } else {
                                    UpdateCardButton(stringResource(if (showNotes) R.string.exhale_update_hide_notes else R.string.exhale_update_notes), primary = false) { showNotes = !showNotes }
                                }
                            }
                            else -> {
                                if (state.error != null && state.enabled) {
                                    UpdateCardButton(stringResource(R.string.exhale_update_try_again), primary = true) { viewModel.check(manual = true) }
                                } else {
                                    UpdateCardButton(stringResource(R.string.exhale_update_highlights), primary = true) { navController.navigate("settings/changelog") }
                                }
                                Spacer(Modifier.height(12.dp))
                                UpdateCardButton(stringResource(R.string.exhale_update_notes), primary = false) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ozyern/exhale/releases")))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The two buttons on the update card, as Convx draws its large buttons: a light one and a glass one. */
@Composable
private fun UpdateCardButton(text: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    CupertinoButton(
        onClick = onClick,
        enabled = enabled,
        size = CupertinoButtonSize.Large,
        colors = CupertinoButtonDefaults.filledButtonColors(
            contentColor = if (primary) Color(0xFF141414) else Color.White,
            containerColor = if (primary) Color(0xFFF1ECE4) else Color.White.copy(alpha = 0.14f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, fontFamily = OppoSans, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
            color = if (primary) Color(0xFF141414) else Color.White)
    }
}

@Composable
internal fun UpdateSummary(release: UpdateRelease, compact: Boolean = false) {
    val shape = ContinuousRoundedRectangle(20.dp)
    if (!compact) {
        if (release.imageUrl != null) AsyncImage(model = release.imageUrl, contentDescription = null,
            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(16f / 9f).clip(shape), contentScale = ContentScale.Fit)
        else Box(Modifier.fillMaxWidth().height(100.dp).clip(shape).background(AppTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            SymbolIcon(symbol = sf("arrow.down.circle"), contentDescription = null, size = 48.dp)
        }
        Spacer(Modifier.height(16.dp))
    }
    val dark = AppTheme.colorScheme.surface.luminance() < 0.5f
    Text(stringResource(R.string.update_to_v, release.version), fontWeight = FontWeight.Bold,
        style = if (compact) AppTheme.typography.titleLarge else AppTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() }.additiveChrome(compact && dark, strength = MenuItemChrome))
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.updater_release_details, release.publishedAt.take(10), "%.1f".format(release.asset.size / 1048576.0)),
        style = AppTheme.typography.bodySmall, color = AppTheme.colorScheme.onSurfaceVariant)
    if (release.description.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(if (compact) release.description.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(220) else release.description,
            style = AppTheme.typography.bodyMedium, maxLines = if (compact) 3 else Int.MAX_VALUE)
    }
}

@Composable
private fun UpdateChangelog(release: UpdateRelease) {
    for (section in release.changelog) {
        Text(section.title, style = AppTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() })
        section.items.forEach { item -> Text("• $item", modifier = Modifier.padding(bottom = 8.dp), style = AppTheme.typography.bodyMedium) }
    }
}

@Composable
internal fun ReleaseLink(release: UpdateRelease) {
    val context = LocalContext.current
    if (release.releaseUrl.isNotBlank()) {
        CupertinoButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl))) },
            colors = CupertinoButtonDefaults.plainButtonColors()) {
            Text(stringResource(R.string.updater_view_release))
        }
    }
}

@Composable
internal fun UpdateStatus(state: UpdateUiState) {
    state.error?.let { Text(stringResource(it.messageResource()), color = AppTheme.colorScheme.error, style = AppTheme.typography.bodyMedium) }
    val label = when (state.phase) {
        UpdatePhase.QUEUED -> stringResource(R.string.updater_waiting)
        UpdatePhase.DOWNLOADING -> state.progress?.let { stringResource(R.string.downloading_percent, (it * 100).toInt()) } ?: stringResource(R.string.downloading_update)
        UpdatePhase.VERIFYING -> stringResource(R.string.updater_verifying)
        UpdatePhase.INSTALLING -> stringResource(R.string.updater_installing)
        UpdatePhase.AWAITING_APPROVAL -> stringResource(R.string.updater_approval)
        UpdatePhase.READY -> stringResource(R.string.update_ready)
        UpdatePhase.INSTALLED -> stringResource(R.string.updater_installed)
        else -> null
    }
    if (label != null) Text(label, style = AppTheme.typography.bodyMedium)
    if (state.phase in setOf(UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING, UpdatePhase.QUEUED)) {
        val progress = state.progress?.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(5.dp).clip(ContinuousRoundedRectangle(3.dp)).background(AppTheme.colorScheme.surfaceContainerHigh)
            .semantics { progressBarRangeInfo = progress?.let { ProgressBarRangeInfo(it, 0f..1f) } ?: ProgressBarRangeInfo.Indeterminate }) {
            if (progress != null) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(AppTheme.colorScheme.primary))
        }
    }
}

@Composable
internal fun UpdateActions(state: UpdateUiState, onDownload: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val busy = UpdatePolicy.isTransfer(state.phase)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CupertinoButton(onClick = {
            if (state.phase == UpdatePhase.READY) context.startActivity(Intent(context, UpdateInstallActivity::class.java)) else onDownload()
        }, enabled = state.enabled && !busy && state.phase != UpdatePhase.CHECKING && state.phase != UpdatePhase.INSTALLED) {
            Text(stringResource(if (state.phase == UpdatePhase.READY) R.string.install else R.string.update_available))
        }
        if (state.phase in setOf(UpdatePhase.QUEUED, UpdatePhase.DOWNLOADING, UpdatePhase.VERIFYING)) {
            CupertinoButton(onClick = onCancel, colors = CupertinoButtonDefaults.plainButtonColors()) { Text(stringResource(R.string.cancel)) }
        }
    }
}

private fun UpdateFailure.messageResource(): Int = when (this) {
    UpdateFailure.NETWORK -> R.string.check_internet_connection
    UpdateFailure.RATE_LIMIT -> R.string.updater_rate_limit
    UpdateFailure.STORAGE -> R.string.updater_storage
    UpdateFailure.VERIFICATION -> R.string.updater_verification_failed
    UpdateFailure.INCOMPATIBLE -> R.string.updater_incompatible
    UpdateFailure.INSTALL_BLOCKED -> R.string.updater_blocked
    UpdateFailure.INSTALL_CANCELLED -> R.string.updater_cancelled
    UpdateFailure.INSTALL_FAILED -> R.string.updater_install_failed
    UpdateFailure.UNAVAILABLE -> R.string.updater_unavailable
    UpdateFailure.METADATA -> R.string.updater_metadata_failed
}
