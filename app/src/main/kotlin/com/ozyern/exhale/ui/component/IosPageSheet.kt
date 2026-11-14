/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import kotlinx.coroutines.launch

/**
 * The full-screen iOS page sheet the New Playlist sheet uses: edge to edge, just under the
 * status bar, a close button and a centred title, rising on a critically damped spring.
 */
@Composable
fun IosPageSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = IosTheme.colors
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(1f) }
    LaunchedEffect(Unit) { offset.animateTo(0f, spring(dampingRatio = 1f, stiffness = 380f)) }
    fun close() {
        scope.launch {
            offset.animateTo(1f, tween(220))
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = ::close)
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetColor = if (colors.isDark) colors.secondarySystemBackground else colors.systemGroupedBackground
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * (1f - offset.value))),
        ) {
            val travel = with(LocalDensity.current) { maxHeight.toPx() }
            Column(
                Modifier
                    .padding(top = statusTop + 10.dp)
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value * travel }
                    .clip(ContinuousRoundedRectangle(topStart = 38.dp, topEnd = 38.dp, bottomEnd = 0.dp, bottomStart = 0.dp))
                    .background(sheetColor),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
                        .height(44.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.tertiarySystemFill)
                            .clickable(onClick = ::close),
                    ) {
                        SymbolIcon(symbol = sf("SFXmark"), contentDescription = stringResource(R.string.close), size = 16.dp, tint = colors.label)
                    }
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.label,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), content = content)
            }
        }
    }
}
