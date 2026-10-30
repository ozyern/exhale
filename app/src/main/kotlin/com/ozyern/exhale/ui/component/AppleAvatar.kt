/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R

/**
 * The account button beside a tab's large title, as iOS draws a signed-out account: a disc of
 * soft periwinkle, lighter at the top, with a solid white person on it. Not a grey glass puck —
 * the colour is what makes it read as the account rather than as one more tool button.
 */
@Composable
fun AppleAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFA9B6D8), Color(0xFF7378AD))))
            .clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(R.drawable.person_fill),
            contentDescription = stringResource(R.string.account),
            tint = Color.White,
            modifier = Modifier.size(size * 0.6f),
        )
    }
}
