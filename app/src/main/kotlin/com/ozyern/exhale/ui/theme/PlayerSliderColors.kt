/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Shared color configuration for the player's slider variants. */
object PlayerSliderColors {

    /**
     * Standard slider colors for all slider types. The unloaded/inactive track is always a
     * faded version of [activeColor] itself — not a separate background-dependent color — so
     * the two halves of the seek bar read as one color at two strengths, whatever [activeColor]
     * is (including the user's global text/button color when they've set one).
     *
     * @param activeColor Color for active (loaded) track, ticks, and thumb
     * @param inactiveAlpha Strength of the unplayed half. Flamingo's player drops this to a
     *   hairline (0.05) because its chrome is composited additively — see
     *   [com.ozyern.exhale.ui.utils.additiveChrome] — where 0.3 white would read as a solid bar.
     * @return SliderColors configuration
     */
    @Composable
    fun getSliderColors(activeColor: Color, inactiveAlpha: Float = 0.3f): SliderColors {
        val inactiveTrackColor = activeColor.copy(alpha = inactiveAlpha)

        return SliderDefaults.colors(
            activeTrackColor = activeColor,
            activeTickColor = activeColor,
            thumbColor = activeColor,
            inactiveTrackColor = inactiveTrackColor,
            disabledActiveTrackColor = activeColor,
            disabledInactiveTrackColor = inactiveTrackColor,
            disabledThumbColor = activeColor
        )
    }
}

