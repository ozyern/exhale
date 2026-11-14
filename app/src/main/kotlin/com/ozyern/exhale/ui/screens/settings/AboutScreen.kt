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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozyern.exhale.BuildConfig
import com.ozyern.exhale.R
import com.ozyern.exhale.core.updater.UpdatePhase
import com.ozyern.exhale.core.updater.UpdateUiState
import com.ozyern.exhale.ui.component.ColorOsVersionText
import com.ozyern.exhale.ui.component.ExhaleGlowWordmark
import com.ozyern.exhale.ui.component.OppoSans
import com.ozyern.exhale.ui.component.frostedGlass
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import com.ozyern.exhale.ui.component.IosSettingsGroup
import com.ozyern.exhale.ui.component.IosSettingsItem
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
            status = updateStatusText(updateState),
            onClick = { navController.navigate("update") },
            modifier = Modifier.widthIn(max = 520.dp).align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(16.dp))

        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_installed)) },
                    trailingContent = { AboutRowValue(BuildConfig.VERSION_NAME) },
                    onClick = { navController.navigate("update") },
                ),
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_view_changelog)) },
                    onClick = { navController.navigate("settings/changelog") },
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))

        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    title = { AboutRowTitle("Exhale") },
                    description = { AboutRowSubtitle(buildLine) },
                ),
                IosSettingsItem(title = { AboutSpec(stringResource(R.string.exhale_about_architecture), "${BuildConfig.ARCHITECTURE} · $exhaleBuildFlavor") }),
                IosSettingsItem(title = { AboutSpec(stringResource(R.string.exhale_about_package), context.packageName) }),
                IosSettingsItem(title = { AboutSpec(stringResource(R.string.exhale_about_android), android) }),
                IosSettingsItem(title = { AboutSpec(stringResource(R.string.exhale_about_device), device) }),
                IosSettingsItem(title = { AboutSpec(stringResource(R.string.exhale_about_installed_on), installedOn) }),
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_copy_build_info)) },
                    onClick = {
                        val info = buildString {
                            appendLine("Exhale $buildLine")
                            appendLine("${BuildConfig.ARCHITECTURE} · $exhaleBuildFlavor · ${context.packageName} · ${BuildConfig.GIT_COMMIT}")
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
        Spacer(Modifier.height(16.dp))

        IosSettingsGroup(
            items = listOf(
                IosSettingsItem(
                    title = { AboutRowTitle("ozyern") },
                    description = { AboutRowSubtitle(stringResource(R.string.exhale_about_lead_developer)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern") },
                ),
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_source)) },
                    description = { AboutRowSubtitle(stringResource(R.string.exhale_about_source_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern/exhale") },
                ),
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_built_on)) },
                    description = { AboutRowSubtitle(stringResource(R.string.exhale_about_built_on_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/cosmictaserdev-creator/Convx") },
                ),
                IosSettingsItem(
                    title = { AboutRowTitle(stringResource(R.string.exhale_about_license)) },
                    description = { AboutRowSubtitle(stringResource(R.string.exhale_about_license_desc)) },
                    onClick = { uriHandler.safeOpenUri(context, "https://github.com/ozyern/exhale/blob/master/LICENSE") },
                ),
            ),
        )
        Spacer(Modifier.height(32.dp))
    }
}

// Exhale's About rows, lettered as the OPPO Sans build lettered them.
@Composable
private fun AboutRowTitle(text: String) =
    Text(text, fontFamily = OppoSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)

@Composable
private fun AboutRowSubtitle(text: String) =
    Text(text, fontFamily = OppoSans, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun AboutRowValue(text: String) =
    Text(text, fontFamily = OppoSans, fontSize = 15.sp, maxLines = 1)

/** A spec line: the label small and grey, the value under it in bold. */
@Composable
private fun AboutSpec(label: String, value: String) {
    Column {
        Text(label, fontFamily = OppoSans, fontSize = 14.sp, color = IosTheme.colors.secondaryLabel)
        Spacer(Modifier.height(2.dp))
        Text(value, fontFamily = OppoSans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The square card at the head of About: Exhale's artwork, the drawn wordmark and the version, and
 * a frosted pill saying whether this is the newest Exhale. It lights up as the page opens, the
 * artwork sweeping in from the lower left. Tapping it opens Software update.
 */
@Composable
private fun AboutHero(status: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(120)
        reveal.animateTo(1f, tween(durationMillis = 1500, easing = CubicBezierEasing(0.33f, 0f, 0.2f, 1f)))
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .background(Color(0xFF0B0A22))
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .bounceClick(onClick = onClick),
    ) {
        val art = rememberLayerBackdrop()
        Box(Modifier.matchParentSize().layerBackdrop(art)) {
            Image(
                painter = painterResource(R.drawable.exhale_about_card),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val p = reveal.value
                        val zoom = 1.14f - 0.14f * p
                        scaleX = zoom
                        scaleY = zoom
                        alpha = 0.25f + 0.75f * p
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val p = reveal.value
                        if (p < 1f) {
                            val lead = (p * 1.4f).coerceIn(0.002f, 1f)
                            val tail = (p * 1.4f - 0.4f).coerceIn(0f, lead - 0.001f)
                            drawRect(
                                brush = Brush.linearGradient(
                                    0f to Color.Black,
                                    tail to Color.Black,
                                    lead to Color.Transparent,
                                    1f to Color.Transparent,
                                    start = Offset(0f, size.height),
                                    end = Offset(size.width, 0f),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        }
                    },
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f)),
                ),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Spacer(Modifier.weight(1f))
            ExhaleGlowWordmark(fontSize = 58.sp)
            ColorOsVersionText(BuildConfig.VERSION_NAME, fontSize = 19.sp, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.weight(1f))
            val pillShape = RoundedCornerShape(50)
            AnimatedContent(
                targetState = status,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "aboutStatus",
            ) { text ->
                Text(
                    text,
                    fontFamily = OppoSans,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .frostedGlass(art, pillShape, tint = Color.White.copy(alpha = 0.13f), blur = 16.dp, rim = 0.32f)
                        .padding(horizontal = 22.dp, vertical = 10.dp),
                )
            }
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
