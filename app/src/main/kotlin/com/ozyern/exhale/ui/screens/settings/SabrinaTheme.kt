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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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

/** What applying a look sets, so the screen can write each one and the card stays a view. */
data class SabrinaThemeSettings(
    val accent: Color = SabrinaPalette.Blue,
    val background: Color = SabrinaPalette.Butter,
    val text: Color = SabrinaPalette.Navy,
    val darkMode: DarkMode = DarkMode.OFF,
)

/**
 * One of the Sabrina looks: the theme it applies, the swatches its card shows (front last), and
 * a small mark of what it is about.
 */
data class SabrinaLook(
    val id: String,
    val name: String,
    val description: String,
    val mark: String,
    val settings: SabrinaThemeSettings,
    val swatches: List<Color>,
)

/**
 * Her looks, each from something of hers: her favourite colours first, then the Short n' Sweet
 * sky, Espresso's coffee and cream, and fruitcake's Christmas.
 */
val SabrinaLooks: List<SabrinaLook> = listOf(
    SabrinaLook(
        id = "sabrina",
        name = "Sabrina Carpenter",
        description = "Her favourite colours: yellow and blue, with Short n' Sweet sky",
        mark = "♡",
        settings = SabrinaThemeSettings(),
        swatches = listOf(SabrinaPalette.SkyBlue, SabrinaPalette.Blue, SabrinaPalette.Yellow),
    ),
    SabrinaLook(
        id = "short_n_sweet",
        name = "Short n' Sweet",
        description = "The cover's pale sky, with a deep blue for the controls",
        mark = "☁",
        settings = SabrinaThemeSettings(
            accent = Color(0xFF2F6FB8),
            background = Color(0xFFE8F4FD),
            text = Color(0xFF16304F),
        ),
        swatches = listOf(Color(0xFFFFFFFF), Color(0xFF8EC9F2), Color(0xFF2F6FB8)),
    ),
    SabrinaLook(
        id = "espresso",
        name = "Espresso",
        description = "That's that me espresso: cream, crema and a dark roast",
        mark = "☕",
        settings = SabrinaThemeSettings(
            accent = Color(0xFF7A4B2A),
            background = Color(0xFFF6EBDD),
            text = Color(0xFF33200F),
        ),
        swatches = listOf(Color(0xFFF6EBDD), Color(0xFFC89B6D), Color(0xFF7A4B2A)),
    ),
    SabrinaLook(
        id = "fruitcake",
        name = "fruitcake",
        description = "Her Christmas: holly red and cream on a winter green night",
        mark = "❄",
        settings = SabrinaThemeSettings(
            accent = Color(0xFFE0474C),
            background = Color(0xFF10241A),
            text = Color(0xFFFFF4E4),
            darkMode = DarkMode.ON,
        ),
        swatches = listOf(Color(0xFF2E5A3F), Color(0xFFFFF4E4), Color(0xFFE0474C)),
    ),
)

/** The look whose settings match what is applied now, if any. */
fun SabrinaLook.isApplied(accent: Int, background: Int, text: Int, darkMode: DarkMode): Boolean =
    accent == settings.accent.toArgbInt() && background == settings.background.toArgbInt() &&
        text == settings.text.toArgbInt() && darkMode == settings.darkMode

private fun Color.toArgbInt(): Int = toArgb()

/** A one-tap theme card: its palette as overlapping swatches, its name, and what it is made of. */
@Composable
fun SabrinaThemeCard(
    look: SabrinaLook,
    selected: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = ContinuousRoundedRectangle(24.dp)
    val page = look.settings.background
    val ink = look.settings.text
    val accent = look.settings.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(page, look.swatches[1].copy(alpha = 0.40f), look.swatches[0].copy(alpha = 0.40f)),
                ),
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else ink.copy(alpha = 0.12f),
                shape = shape,
            )
            .bounceClick(onClick = onApply)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        // The colours overlapping, the look's own in front, and its mark on the last.
        Box(Modifier.width(72.dp).height(40.dp)) {
            look.swatches.forEachIndexed { index, color ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset(x = (index * 16).dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(2.dp, page, CircleShape),
                ) {
                    if (index == look.swatches.lastIndex) {
                        Text(look.mark, color = if (color.luminanceOf() > 0.5f) ink else page, fontSize = 15.sp)
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = look.name, color = ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(text = look.description, color = ink.copy(alpha = 0.7f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(24.dp).clip(CircleShape).background(accent),
            ) {
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun Color.luminanceOf(): Float = luminance()

/** The themes section of Theme settings: its title and a card for each of her looks. */
@Composable
fun ThemePresetsSection(
    selectedLookId: String?,
    onApply: (SabrinaLook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.theme_presets),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SabrinaLooks.forEach { look ->
            SabrinaThemeCard(
                look = look,
                selected = look.id == selectedLookId,
                onApply = { onApply(look) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
