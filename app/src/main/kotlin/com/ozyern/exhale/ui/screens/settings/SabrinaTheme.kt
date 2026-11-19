/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.bounceClick

/**
 * The Sabrina Carpenter theme, in her two favourite colours: yellow and blue.
 *
 * Exhale's UI tints from one accent, so the blue carries the controls and the yellow is the page
 * itself; a lighter sky blue (the Short n' Sweet cover) joins them in the card's swatches, and the
 * text is a deep navy that reads on the yellow. Light mode, because the yellow page is the point.
 */
object SabrinaPalette {
    /** Blue: the accent. Deep enough to stay legible on the yellow page. */
    val Blue = Color(0xFF3D8BD9)

    /** Yellow. */
    val Yellow = Color(0xFFFFD45E)

    /** The Short n' Sweet sky. */
    val SkyBlue = Color(0xFF8EC9F2)

    /** Butter yellow: the page. */
    val Butter = Color(0xFFFFF5D1)

    /** Deep navy: the text. */
    val Navy = Color(0xFF1E3350)
}

/** What applying the theme sets, so the screen can write each one and the card stays a view. */
data class SabrinaThemeSettings(
    val accent: Color = SabrinaPalette.Blue,
    val background: Color = SabrinaPalette.Butter,
    val text: Color = SabrinaPalette.Navy,
    val darkMode: DarkMode = DarkMode.OFF,
)

/** A one-tap theme card: its palette as overlapping swatches, its name, and what it is made of. */
@Composable
fun SabrinaThemeCard(
    selected: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ContinuousRoundedRectangle(24.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        SabrinaPalette.Butter,
                        SabrinaPalette.Yellow.copy(alpha = 0.45f),
                        SabrinaPalette.SkyBlue.copy(alpha = 0.45f),
                    ),
                ),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) SabrinaPalette.Blue else SabrinaPalette.Navy.copy(alpha = 0.12f),
                shape = shape,
            )
            .bounceClick(onClick = onApply)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // The three colours overlapping, her two favourites in front.
        Box(Modifier.width(72.dp).height(40.dp)) {
            listOf(SabrinaPalette.SkyBlue, SabrinaPalette.Blue, SabrinaPalette.Yellow).forEachIndexed { index, color ->
                Box(
                    Modifier
                        .offset(x = (index * 16).dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, SabrinaPalette.Butter, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.theme_sabrina_name),
                color = SabrinaPalette.Navy,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.theme_sabrina_description),
                color = SabrinaPalette.Navy.copy(alpha = 0.7f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(SabrinaPalette.Blue),
            ) {
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The themes section of Theme settings: its title and the cards. */
@Composable
fun ThemePresetsSection(
    sabrinaSelected: Boolean,
    onApplySabrina: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.theme_presets),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SabrinaThemeCard(selected = sabrinaSelected, onApply = onApplySabrina, modifier = Modifier.fillMaxWidth())
    }
}
