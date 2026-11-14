/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.constants.SheetCornerRadiusKey
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.component.backdrop.Backdrop
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.launch
import zone.ien.hig.CupertinoModalBottomSheet
import zone.ien.hig.PresentationDetent
import zone.ien.hig.PresentationStyle
import zone.ien.hig.rememberCupertinoSheetState

val LocalBottomSheetPageState = compositionLocalOf { BottomSheetPageState() }

@Stable
class BottomSheetPageState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)
    var isFloating by mutableStateOf(false)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        isFloating = false
        this.content = content
    }

    fun showFloating(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        isFloating = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@Composable
fun BottomSheetPage(
    modifier: Modifier = Modifier,
    state: BottomSheetPageState,
    background: Color = Color.Unspecified,
    liveBackdrop: Backdrop? = null,
    liquidGlassPlayerMode: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    val iosColors = IosTheme.colors
    // The in-app theme and the screen underneath decide this, not the system setting.
    val isDark = glassUsesDarkAppearance()
    val scope = rememberCoroutineScope()
    val sheetState = rememberCupertinoSheetState(
        presentationStyle = PresentationStyle.Modal(
            detents = setOf(PresentationDetent.Medium, PresentationDetent.Large),
        ),
    )
    val (sheetCornerRadius) = rememberPreference(SheetCornerRadiusKey, MenuSheetCornerRadius.value)
    val sheetShape = remember(sheetCornerRadius) {
        ContinuousRoundedRectangle(
            topStart = sheetCornerRadius.dp,
            topEnd = sheetCornerRadius.dp,
            bottomEnd = 0.dp,
            bottomStart = 0.dp,
        )
    }

    val resolvedContainerColor = when {
        background.isSpecified -> background
        isDark -> Color(0xFF141416)
        else -> iosColors.systemGroupedBackground
    }

    fun dismiss() {
        focusManager.clearFocus()
        if (state.isFloating) state.isVisible = false
        else scope.launch { sheetState.hide() }
    }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(CupertinoSheetEnterMillis, easing = CupertinoSheetEasing)),
        exit = fadeOut(tween(CupertinoSheetExitMillis, easing = EaseIn)),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackHandler(onBack = ::dismiss)

            if (state.isFloating) {
                val capturedBackdrop = if (liquidGlassPlayerMode) liveBackdrop else null
                CompositionLocalProvider(
                    LocalAppBackdrop provides (capturedBackdrop ?: LocalAppBackdrop.current),
                    com.ozyern.exhale.ui.ios.foundation.LocalIosBackdrop provides (capturedBackdrop ?: com.ozyern.exhale.ui.ios.foundation.LocalIosBackdrop.current),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = if (liquidGlassPlayerMode) 0.20f else 0.12f))
                            .clickable(onClick = ::dismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .widthIn(max = 620.dp)
                                .fillMaxHeight(0.88f)
                                .then(
                                    if (isGlassAllowed()) {
                                        val config = LocalGlassEffectConfig.current
                                        Modifier.liquidGlass(
                                            config = config,
                                            shape = ContinuousRoundedRectangle(28.dp),
                                            blurRadiusDp = if (liquidGlassPlayerMode) 12f else config.blurRadius,
                                            highlightAlpha = 0.16f,
                                        )
                                    } else {
                                        Modifier.background(resolvedContainerColor, ContinuousRoundedRectangle(28.dp))
                                    },
                                )
                                .clickable(interactionSource = null, indication = null, onClick = {}),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
                                content = { state.content(this) },
                            )
                        }
                    }
                }
            } else CupertinoModalBottomSheet(
                onDismissRequest = { state.isVisible = false },
                sheetState = sheetState,
                shape = sheetShape,
                containerColor = resolvedContainerColor,
                contentColor = iosColors.label,
                scrimColor = Color.Black.copy(alpha = 0.12f),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        IosGrabber()
                    }
                },
                modifier = Modifier.fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                ) {
                    state.content(this)
                }
            }
        }
    }
}
