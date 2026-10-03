/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ozyern.exhale.R

/** The wide face Home's title is set in, for every page heading. */
val ExhaleHeadingFont = FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))

/** Sets [content]'s text in [ExhaleHeadingFont], for a bar title. */
@androidx.compose.runtime.Composable
fun HeadingStyle(content: @androidx.compose.runtime.Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(
        androidx.compose.material3.LocalTextStyle.current.copy(
            fontFamily = ExhaleHeadingFont,
            fontWeight = FontWeight.SemiBold,
        ),
        content,
    )
}
