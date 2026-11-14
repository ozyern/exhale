/**
 * Exhale Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFArrowDownApp
import com.composables.sfsymbols.monochrome.SFCalendar
import com.composables.sfsymbols.monochrome.SFChevronLeftForwardslashChevronRight
import com.composables.sfsymbols.monochrome.SFCpu
import com.composables.sfsymbols.monochrome.SFHeart
import com.composables.sfsymbols.monochrome.SFInfoCircle
import com.composables.sfsymbols.monochrome.SFIphone
import com.composables.sfsymbols.monochrome.SFListBulletRectangle
import com.composables.sfsymbols.monochrome.SFPersonCropCircleBadgeExclamationmark
import com.composables.sfsymbols.monochrome.SFShippingbox
import com.composables.sfsymbols.monochrome.SFTextDocument
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.core.updater.UpdatePhase
import com.ozyern.exhale.core.updater.UpdateUiState
import com.ozyern.exhale.ui.component.ExhaleLightRibbons
import com.ozyern.exhale.ui.component.ExhaleVersionLockup
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.bounceClick
import com.ozyern.exhale.ui.utils.safeOpenUri
import com.ozyern.exhale.viewmodels.UpdateViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The release this build is, as the About page and Software update page both name it. */
internal val exhaleBuildFlavor: String
    get() = if (BuildConfig.CAST_AVAILABLE) "gms" else "foss"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: (() -> Unit)? = null,
    updateViewModel: UpdateViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updateViewModel.check() }

    val unknown = stringResource(R.string.unknown)
    val installedOn = remember {
        runCatching {
            val installed = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(installed))
        }.getOrDefault(unknown)
    }
    val device = remember { deviceName() }
    val android = remember { "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})" }
    val buildLine = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}"
    val copiedText = stringResource(R.string.exhale_about_build_info_copied)

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.about),
        onBack = { onBack?.invoke() ?: navController.navigateUp() },
        horizontalPadding = 16.dp,
    ) {
        Spacer(Modifier.height(8.dp))
        AboutHero(
            state = updateState,
            onClick = { navController.navigate("update") },
            modifier = Modifier.widthIn(max = 520.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(20.dp))

        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFArrowDownApp),
                    title = { Text(stringResource(R.string.exhale_about_installed)) },
                    trailingContent = { Text(BuildConfig.VERSION_NAME) },
                    onClick = { navController.navigate("update") },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFListBulletRectangle),
                    title = { Text(stringResource(R.string.exhale_about_view_changelog)) },
                    onClick = { navController.navigate("settings/changelog") },
                ),
            ),
        )
        Spacer(Modifier.height(20.dp))

        IosSettingsGroup(
            title = stringResource(R.string.app_name),
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFInfoCircle),
                    title = { Text(buildLine) },
                    description = { Text("${BuildConfig.ARCHITECTURE} · $exhaleBuildFlavor") },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFShippingbox),
                    title = { Text(stringResource(R.string.exhale_about_package)) },
                    description = { Text(context.packageName) },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFCpu),
                    title = { Text(stringResource(R.string.exhale_about_android)) },
                    description = { Text(android) },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFIphone),
                    title = { Text(stringResource(R.string.exhale_about_device)) },
                    description = { Text(device) },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFCalendar),
                    title = { Text(stringResource(R.string.exhale_about_installed_on)) },
                    description = { Text(installedOn) },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFTextDocument),
                    title = { Text(stringResource(R.string.exhale_about_copy_build_info)) },
                    onClick = {
                        val info = buildString {
                            appendLine("Exhale $buildLine")
                            appendLine("${BuildConfig.ARCHITECTURE} · $exhaleBuildFlavor · ${context.packageName}")
                            appendLine("Android $android")
                            append(device)
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Exhale build info", info))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    },
                ),
            ),
        )
        Spacer(Modifier.height(20.dp))

        IosSettingsGroup(
            title = stringResource(R.string.exhale_about_developer),
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFPersonCropCircleBadgeExclamationmark),
                    title = { Text("ozyern") },
                    description = { Text(stringResource(R.string.exhale_about_lead_developer)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern") },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFChevronLeftForwardslashChevronRight),
                    title = { Text(stringResource(R.string.exhale_about_source)) },
                    description = { Text(stringResource(R.string.exhale_about_source_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern/exhale") },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFHeart),
                    title = { Text(stringResource(R.string.exhale_about_built_on)) },
                    description = { Text(stringResource(R.string.exhale_about_built_on_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/cosmictaserdev-creator/Convx") },
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFTextDocument),
                    title = { Text(stringResource(R.string.exhale_about_license)) },
                    description = { Text(stringResource(R.string.exhale_about_license_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern/exhale/blob/master/LICENSE") },
                ),
            ),
        )
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The square card at the head of About: Exhale's light, the wordmark and version, and a glass
 * pill saying whether this is the newest Exhale. Tapping it opens Software update.
 */
@Composable
private fun AboutHero(state: UpdateUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = ContinuousRoundedRectangle(36.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .bounceClick(onClick = onClick),
    ) {
        ExhaleLightRibbons(Modifier.matchParentSize())
        ExhaleVersionLockup(
            version = BuildConfig.VERSION_NAME,
            fontSize = 60.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        val status = updateStatusText(state)
        val pillShape = ContinuousRoundedRectangle(28.dp)
        AnimatedContent(
            targetState = status,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "aboutStatus",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 28.dp)
                .clip(pillShape)
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), pillShape)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) { text ->
            Text(text, color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun updateStatusText(state: UpdateUiState): String = when {
    !state.enabled -> stringResource(R.string.exhale_about_status_unmanaged)
    state.phase == UpdatePhase.CHECKING -> stringResource(R.string.exhale_about_status_checking)
    state.release != null -> stringResource(R.string.exhale_about_status_available, state.release.version)
    state.error != null -> stringResource(R.string.exhale_about_status_failed)
    else -> stringResource(R.string.exhale_about_status_up_to_date)
}

private fun deviceName(): String {
    val maker = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
    return if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
}
