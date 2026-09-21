/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.UiScaleKey
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.ui.utils.backToMain
import com.ozyern.exhale.utils.UiScaleDefault
import com.ozyern.exhale.utils.usableUiScaleSteps
import com.ozyern.exhale.utils.rememberPreference
import com.ozyern.exhale.utils.uiScaleStepIndex
import com.ozyern.exhale.utils.writeUiScale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Settings → Appearance → Display → Interface scale.
 *
 * This replaces a slider in a dialog. The dialog had two problems that no amount of polish fixes:
 * the thing being changed was the whole app, and a dialog can only ever show you a corner of it;
 * and the control was continuous, so most of its travel was differences nobody can see.
 *
 * So: a page, a scale model of a real screen that redraws as you move, and ten discrete steps.
 *
 * The commit is explicit. Interface scale is a Configuration override applied in
 * `MainActivity.attachBaseContext` (see `utils/UiScale.kt` for why it has to be), and changing it
 * means recreating the Activity — which is not something to do on every tick of a slider while the
 * user is still deciding. The preview carries the "live" feeling; the button carries the change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UiScaleScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // The applied value — what the Activity was actually built with. Read from the preference
    // rather than from the live density so the "Apply" button knows whether there is anything
    // left to apply.
    val (appliedScale) = rememberPreference(UiScaleKey, defaultValue = UiScaleDefault)

    // Only the sizes this screen can actually render.
    //
    // Scaling up makes the app believe the phone is *smaller*, and past a point that is a layout
    // this app does not have — so a small handset is not offered a step that would break the page
    // offering it. A large phone gets the whole range. See usableUiScaleSteps.
    val configuration = LocalConfiguration.current
    val steps = remember(configuration.screenWidthDp, appliedScale) {
        usableUiScaleSteps(configuration.screenWidthDp * appliedScale)
    }

    var index by rememberSaveable { mutableIntStateOf(uiScaleStepIndex(appliedScale)) }
    // If the preference changes underneath us (the Activity was recreated after an Apply), follow
    // it, so coming back to this page never shows a pending change that has already happened.
    LaunchedEffect(appliedScale, steps) {
        index = steps.indexOfFirst { abs(it - appliedScale) < 0.001f }
            .takeIf { it >= 0 }
            ?: steps.indexOfFirst { abs(it - UiScaleDefault) < 0.001f }.coerceAtLeast(0)
    }

    val selected = steps[index.coerceIn(steps.indices)]
    val pending = abs(selected - appliedScale) > 0.001f

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(8.dp))

        UiScalePreviewFrame(
            scale = selected,
            appliedScale = appliedScale,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = uiScaleLabel(selected),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.ui_scale_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        )

        Spacer(Modifier.height(16.dp))

        UiScaleStepper(
            index = index,
            count = steps.size,
            onIndexChange = {
                if (it != index) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    index = it
                }
            },
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(24.dp))

        // The button appears only when there is something to apply, and it says what it does.
        // "Apply" alone would be a button that restarts the app without warning.
        AnimatedVisibility(
            visible = pending,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            writeUiScale(context, selected)
                            context.findActivity()?.recreate()
                        }
                    },
                    modifier = Modifier.padding(horizontal = 24.dp),
                ) {
                    Text(stringResource(R.string.ui_scale_apply))
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.ui_scale_apply_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }

        val defaultIndex = steps.indexOfFirst { abs(it - UiScaleDefault) < 0.001f }.coerceAtLeast(0)

        AnimatedVisibility(
            visible = index != defaultIndex,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = {
                        if (defaultIndex != index) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            index = defaultIndex
                        }
                    },
                ) {
                    Text(stringResource(R.string.ui_scale_use_default))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    SettingsTopAppBar(
        title = { Text(stringResource(R.string.ui_scale)) },
        navigationIcon = {
            LiquidBackButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
                icon = R.drawable.chevron_back,
            )
        },
    )
}

/**
 * A scale model of the screen, drawn at the candidate size.
 *
 * The trick that makes it honest is the extra shrink factor: the frame is as many dp wide as it
 * is, but the density inside it is scaled by `frameWidth / screenWidth` on top of the user's
 * setting, so the frame stands in for the whole phone. Without that the preview is just a card
 * whose contents grow — with it, choosing a larger size visibly fits *less* on the screen, which
 * is the actual trade the user is making.
 *
 * The frame is shorter than a phone on purpose. A true-aspect model of a 20:9 display is 750dp
 * tall and pushes the control being adjusted off the bottom of the page; this is the top of that
 * screen, cropped, which is where everything worth previewing lives anyway.
 */
@Composable
private fun UiScalePreviewFrame(
    scale: Float,
    appliedScale: Float,
    modifier: Modifier = Modifier,
) {
    val currentDensity = LocalDensity.current
    val configuration = LocalConfiguration.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                RoundedCornerShape(28.dp),
            ),
    ) {
        val frameWidth = maxWidth
        val miniature = (frameWidth.value / configuration.screenWidthDp.coerceAtLeast(1))
            .coerceIn(0.3f, 1f)

        // Relative to what is already applied, not to the system.
        //
        // `LocalDensity.current` here is the app's *current* size, which is the setting the user
        // is about to change — so previewing 100% while sitting at 130% has to divide the applied
        // scale back out, or the preview shows 130% of 100% and never moves off the value the
        // page opened on.
        val relative = scale / appliedScale.coerceAtLeast(0.01f)

        val previewDensity = remember(currentDensity, relative, miniature) {
            Density(
                density = currentDensity.density * relative * miniature,
                fontScale = currentDensity.fontScale,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(frameWidth * 1.12f),
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalDensity provides previewDensity) {
                PreviewLibraryPage()
            }
        }
    }
}

/**
 * The contents of the preview: a stripped Library page.
 *
 * Library rather than an invented layout, because the point of the preview is recognition — the
 * user has to be able to map what they are looking at onto a screen they use. Everything here is
 * measured in the same dp the real page uses, so the model is to scale.
 */
@Composable
private fun PreviewLibraryPage() {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Text(
            text = stringResource(R.string.filter_library),
            style = MaterialTheme.typography.displaySmall.copy(letterSpacing = (-0.03).em),
            fontWeight = FontWeight.Bold,
            color = onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))

        listOf(
            R.string.filter_playlists to R.drawable.queue_music,
            R.string.filter_artists to R.drawable.person,
            R.string.filter_albums to R.drawable.album,
        ).forEach { (label, icon) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.chevron_right),
                    contentDescription = null,
                    tint = onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.library_recently_added),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            maxLines = 1,
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(onSurface.copy(alpha = 0.25f)),
                    )
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(onSurfaceVariant.copy(alpha = 0.18f)),
                    )
                }
            }
        }
    }
}

/**
 * The size control: a small A, a large A, and the steps between them.
 *
 * Discrete by construction rather than a slider that happens to snap — the thumb can only ever sit
 * on a tick, tapping anywhere on the track jumps to the nearest one, and dragging walks them. The
 * two glyphs at the ends are the label; a row that read "0.85" to "1.30" would be describing the
 * implementation.
 */
@Composable
private fun UiScaleStepper(
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val thumbColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = "A",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )

        UiScaleStepperTrack(
            index = index,
            count = count,
            onIndexChange = onIndexChange,
            trackColor = trackColor,
            tickColor = tickColor,
            thumbColor = thumbColor,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "A",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = onSurface,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun UiScaleStepperTrack(
    index: Int,
    count: Int,
    onIndexChange: (Int) -> Unit,
    trackColor: Color,
    tickColor: Color,
    thumbColor: Color,
    modifier: Modifier = Modifier,
) {
    val thumbTravel: Dp = 8.dp
    val height: Dp = 56.dp

    // Animated so a tap two ticks away slides rather than teleports — the movement is what tells
    // the eye that the ticks are one scale and not eleven buttons.
    val animatedIndex by animateFloatAsState(index.toFloat(), label = "uiScaleThumb")

    BoxWithConstraints(
        modifier = modifier
            .height(height)
            .pointerInput(count) {
                detectTapGestures { offset ->
                    onIndexChange(indexAt(offset.x, size.width.toFloat(), count))
                }
            }
            .pointerInput(count) {
                detectHorizontalDragGestures { change, _ ->
                    onIndexChange(indexAt(change.position.x, size.width.toFloat(), count))
                }
            },
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val inset = with(LocalDensity.current) { thumbTravel.toPx() }
        val usable = (widthPx - inset * 2).coerceAtLeast(1f)

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()

            drawRoundRectTrack(trackColor, centerY, trackHeight)

            for (i in 0 until count) {
                val x = inset + usable * (i / (count - 1).toFloat())
                drawCircle(
                    color = tickColor,
                    radius = 2.dp.toPx(),
                    center = Offset(x, centerY),
                )
            }

            val thumbX = inset + usable * (animatedIndex / (count - 1).toFloat())
            drawCircle(
                color = thumbColor.copy(alpha = 0.18f),
                radius = 18.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
            drawCircle(
                color = thumbColor,
                radius = 11.dp.toPx(),
                center = Offset(thumbX, centerY),
            )
        }
    }
}

private fun DrawScope.drawRoundRectTrack(color: Color, centerY: Float, trackHeight: Float) {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, centerY - trackHeight / 2f),
        size = Size(size.width, trackHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f),
    )
}

/**
 * The Activity behind a Compose context.
 *
 * `LocalContext.current` is usually the Activity outright, but it is a ContextWrapper often enough
 * — a themed overlay, a dialog window — that assuming it costs the whole feature when the cast
 * fails: no recreate, no size change, no error.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Nearest tick to a touch, so the control can never land between two sizes. */
private fun indexAt(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

/**
 * "Default", or a percentage. Never "1.0".
 *
 * A raw multiplier is an implementation detail leaking into a settings row — nobody thinks of
 * their interface as being at 1.15. And the system size gets a word rather than "100%" because it
 * is a distinct state, not a coincidental value: it is what the row says when the app is doing
 * whatever the phone told it to.
 */
@Composable
internal fun uiScaleLabel(scale: Float): String =
    if (abs(scale - UiScaleDefault) < 0.001f) {
        stringResource(R.string.ui_scale_default)
    } else {
        "${(scale * 100).roundToInt()}%"
    }
