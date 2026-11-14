/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color

/**
 * The iOS row press state: the row fills with a flat grey for as long as a finger is on
 * it, and clears the instant it lifts.
 *
 * This is the whole feedback model on iOS — no ripple, no spread, no release animation.
 * Rows built with a bare `clickable` gave no feedback at all, which reads as a dead
 * control rather than a restrained one.
 *
 * The highlight is drawn *behind* the row content, so it has to sit before any padding
 * in the chain and after whatever clips the row's corners.
 */
fun Modifier.iosRowPress(
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val highlight = IosTheme.colors.systemFill

    this
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )
        .background(if (pressed && enabled) highlight else Color.Transparent)
}
