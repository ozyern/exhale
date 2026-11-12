/*
 * OpenTune Project Original (2026)
 * Arturo254 (github.com/Arturo254)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    /** Where a hold opened this menu, on screen; null when it was opened any other way. */
    var heldAt by mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(content: @Composable ColumnScope.() -> Unit) {
        // The touch that opened it, hold or tap: the menu grows out of that point.
        heldAt = HoldTracker.heldNow() ?: HoldTracker.touchedNow()
        isVisible = true
        this.content = content
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun dismiss() {
        isVisible = false
    }
}

/** True inside a menu drawn as a pop-out beside the touch rather than as a sheet at the foot. */
val LocalMenuIsPopOut = compositionLocalOf { false }

/**
 * The room a menu leaves under its last row. A sheet sits on the navigation bar and clears it; a
 * pop-out floats in the middle of the screen, where that same padding was a dead strip at the
 * foot of the panel.
 */
@Composable
fun menuBottomInset(): androidx.compose.ui.unit.Dp =
    if (LocalMenuIsPopOut.current) 0.dp
    else androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMenu(
    modifier: Modifier = Modifier,
    state: MenuState,
    background: Color = MaterialTheme.colorScheme.surface,
    /** The player is covering the page: a pop-out bends the player's glass, not the page's. */
    playerOpen: Boolean = false,
) {
    val focusManager = LocalFocusManager.current

    // Opened by a touch, a menu is the player's pop-out: glass grown out of the point touched,
    // bending the page behind it. Opened any other way it has no point to grow from, and is a sheet.
    val anchor = state.heldAt
    var popOutAlive by remember { mutableStateOf(false) }
    if (state.isVisible && anchor != null) popOutAlive = true
    if (popOutAlive && anchor != null) {
        val playerBackdrop = PopOutBackdrops.player
        PopOutPanel(
            anchor = anchor,
            visible = state.isVisible,
            // Over the open player it is the player's glass; over a page, the page's. Over a
            // player that publishes none, a plain pane rather than a picture of the page behind it.
            backdrop = playerBackdrop ?: if (playerOpen) null else com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop.current,
            onDismissRequest = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            onClosed = { popOutAlive = false },
        ) {
            androidx.compose.runtime.CompositionLocalProvider(LocalMenuIsPopOut provides true) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .weight(1f, fill = false),
                ) {
                    state.content(this)
                }
            }
        }
    } else if (state.isVisible) {
        LiquidGlassSheet(
            onDismiss = {
                focusManager.clearFocus()
                state.isVisible = false
            },
            maxHeightFraction = 0.9f,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .weight(1f, fill = false)
            ) {
                state.content(this)
            }
        }
    }
}
