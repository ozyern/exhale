/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.ozyern.exhale.desktop.player.DesktopPlayer

/**
 * Where the song is, as state of its own. Read it inside a draw block (a progress line) or a small
 * composable (a time label), so only that redraws as the song plays — not every page holding the
 * player's state. While [active] is false (off screen) it holds still, and catches up when it's back.
 */
@Composable
fun DesktopPlayer.rememberPosition(active: Boolean = true): State<Double> =
    produceState(position.value, this, active) {
        if (active) position.collect { value = it }
    }
