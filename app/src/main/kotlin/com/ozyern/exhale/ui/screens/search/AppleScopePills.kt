/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.screens.library.apple.SfStyle

/**
 * Apple Music's search scope pills (Top Results · Songs · Albums · …): one row, the same
 * on the catalogue and the library results. 32dp capsules, 14dp in; unselected a grey
 * fill with the label colour, selected the accent with white. No checkmark — the fill
 * alone says which one is on.
 */
@Composable
fun <T> AppleScopePills(
    pills: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = IosTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        pills.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(32.dp)
                    .clip(CircleShape)
                    .background(if (on) IosTheme.tint else Color(0xFF767680).copy(alpha = 0.12f))
                    .clickable(interactionSource = null, indication = null) { onSelect(value) }
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = label,
                    style = SfStyle(15.sp, 20.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, (-0.23).sp),
                    color = if (on) Color.White else colors.label,
                    maxLines = 1,
                )
            }
        }
    }
}
