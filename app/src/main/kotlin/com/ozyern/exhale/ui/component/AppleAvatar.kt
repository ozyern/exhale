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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R

/**
 * The account button beside a tab's large title, as Apple Music draws it: a disc of soft grey,
 * lighter at the top, with the account's initial on it in white — or, signed out, a white person.
 */
@Composable
fun AppleAvatar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val (accountName) = com.ozyern.exhale.utils.rememberPreference(com.ozyern.exhale.constants.AccountNameKey, "")
    val initial = accountName.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
    val label = stringResource(R.string.account)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFA7A7AD), Color(0xFF7D7D84))))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        if (initial != null) {
            // Sized to the disc, not the font scale: it is a mark, like the person glyph it replaces.
            val fontSize = with(LocalDensity.current) { (size * 0.46f).toSp() }
            Text(
                text = initial.toString(),
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person_fill),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.6f),
            )
        }
    }
}
