/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The Apple-Music "inset grouped table" palette, in one place.
 *
 * Both the settings screens (`ui.screens.settings`) and the shared preference widgets
 * (`ui.component`) draw the same grouped lists, so the tokens live down here in `ui.component`
 * where either side can reach them without the components package having to depend on a screen
 * package. `SettingsDimensions` re-exports these under its own names for call sites that already
 * read from it.
 *
 * The defining property of the look is a *two-tone* pair: a solid page colour and a solid card
 * colour one step off it. Translucent cards were the previous approach and they fail on a pure
 * black page — 5% white ink over black is just a slightly lighter black, so the group stops
 * reading as a floating plate and starts reading as a smudge.
 */

/**
 * Corner radius of every inset group card.
 *
 * 22dp, not 16. At 16 the cards read as *rectangles with the corners taken off*; the softness has
 * to be a visible property of the shape before a stack of plates on a grey ground stops looking
 * like a table with borders. This is the single cheapest change that moves the whole of Settings.
 */
val SettingsGroupCornerRadius = 24.dp

/** How far a grouped row's text sits in from the card's edges, ColorOS's inset; hairlines match it. */
val SettingsRowInset = 20.dp

/** Hairline between rows inside a group, iOS's separator. Never drawn at a card's top or bottom edge. */
val SettingsDividerThickness = 0.5.dp

/**
 * Left indent so a divider starts under the row's text, not under its leading icon.
 *
 * This is not a taste value — it is the sum of the row geometry above it: 16dp of card padding,
 * a 36dp glyph tile, and the 14dp gap after it. At 60dp (what it used to be) every hairline in
 * Settings stopped 6dp short of the text it was supposed to align with, which is exactly the kind
 * of near-miss that makes a grouped list read as an imitation of iOS rather than as iOS.
 */
// 16dp of row padding + a 30dp glyph + the 14dp gap: the hairline starts exactly where the
// label does, now that the glyph has no tile to add width.
val SettingsDividerStartIndent = 60.dp

/**
 * The ground the group cards float on.
 *
 * These were hard-coded `0xFF000000` / `0xFFF2F2F7` / `0xFF1C1C1E` / `0xFFFFFFFF` — the literal
 * iOS system greys. They gave a clean two-tone, but they were the only surfaces in Exhale that
 * ignored the theme: everywhere else the palette is generated from the current album art, so
 * opening Settings dropped you out of the app into a neutral grey clone of another platform.
 *
 * The two-tone is what makes a grouped table work, so it is kept exactly — a solid page colour
 * and a solid card colour one step off it, never translucent, never elevated. The two steps are
 * now taken from the Material container ramp, so they carry the same tint as the rest of the app
 * and follow the user's palette instead of contradicting it.
 */
// iOS's `systemGroupedBackground` again: the cells are its solid `secondarySystemGroupedBackground`
// now, and on the theme's lowest container (near white in light mode) a white cell had no edge.
@Composable
fun settingsPageBackgroundColor(): Color =
    if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7)

/** The card colour — one step off [settingsPageBackgroundColor], solid, never elevated. */
@Composable
fun settingsGroupSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** iOS's `separator`: rgba(84,84,88,0.6) on dark, rgba(60,60,67,0.29) on light. */
@Composable
fun settingsDividerColor(): Color =
    if (isSystemInDarkTheme()) Color(0x99545458) else Color(0x4A3C3C43)

/** iOS's `systemGray4`, the flat fill a grouped row takes while held. */
@Composable
fun settingsRowPressedColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFD1D1D6)

/** The card, one solid step off the page: ColorOS's dark grey, white in light mode. */
@Composable
private fun settingsCellColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFF1E1E1E) else Color.White

/**
 * Hairline rim for a group card.
 *
 * On a two-tone grey the card edge is carried entirely by the step between the two colours, and
 * on a tinted palette that step can shrink to almost nothing. A rim guarantees the plate has an
 * edge whatever the palette does, and it catches light the same way every other surface in the
 * app does.
 */
@Composable
fun settingsGroupBorderBrush(): Brush {
    val dark = isSystemInDarkTheme()
    return Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.10f else 0.55f),
            Color.White.copy(alpha = if (dark) 0.02f else 0.16f),
        ),
    )
}

/**
 * The group card itself, as one modifier: iOS's inset grouped cell.
 *
 * It was glass: a tonal floor under a white gradient, a diagonal sheen, a bright rim and lens
 * bending at the edge. Each of those is a tell that the surface is imitating something, and
 * together they read as cheap. iOS Settings draws none of them: the cell is one solid colour a step
 * off the page, no border, no gradient, no elevation, and the rows' hairlines do the rest. The
 * name is kept because fourteen files call it.
 */
@Composable
fun Modifier.settingsGlassGroup(
    shape: Shape = RoundedCornerShape(SettingsGroupCornerRadius),
): Modifier = this
    .clip(shape)
    .background(settingsCellColor())

/**
 * The rounded-square plate every settings glyph sits on.
 *
 * One definition for what used to be seven copies of `background(accent.copy(alpha = 0.12f))`
 * scattered across the settings screens — which is how a "design system" quietly becomes seven
 * things that happen to look similar today.
 *
 * A flat 12% wash is what made the rows look generic: a real puck catches light. This is a
 * vertical gradient from a brighter top to a dimmer bottom with a hairline rim, so the glyph
 * plate reads as a small physical object rather than a tinted rectangle. The values are low
 * enough that a row of four different accents still reads as one family.
 */
// Flat, as iOS's are: the gradient and rim it had were the same imitation-glass tells as the cards.
fun Modifier.settingsIconPuck(
    accent: Color,
    shape: Shape = RoundedCornerShape(8.dp),
): Modifier = this
    .clip(shape)
    .background(accent.copy(alpha = 0.18f))

/**
 * The colours ColorOS paints its Settings icons in: saturated, warm-leaning, every one strong
 * enough to carry a white glyph. Ordered so neighbours in a list never sit side by side in the
 * same family.
 */
val ColorOsIconColors = listOf(
    Color(0xFFF7931E), // orange
    Color(0xFF1DA1F2), // sky blue
    Color(0xFF2DBE5B), // green
    Color(0xFF3B7BF6), // blue
    Color(0xFFF2642E), // deep orange
    Color(0xFF8C5CF6), // purple
    Color(0xFFF5B321), // yellow
    Color(0xFF14B8A6), // teal
    Color(0xFFF2545B), // red
    Color(0xFF5B6CF0), // indigo
)

/**
 * A settings icon the way ColorOS 17 draws one: the glyph itself in its colour, a little larger
 * than a list glyph, with no disc or tile behind it. The old solid disc with a white glyph read
 * as a column of swatches; ColorOS's own Settings hands the colour to the glyph and the row back
 * to its label.
 */
@androidx.compose.runtime.Composable
fun ColorOsIconDisc(
    color: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    content: @androidx.compose.runtime.Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(size),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides color,
        ) {
            // Glyphs arrive at 24dp; ColorOS sets them at about 26 in a 30dp slot.
            androidx.compose.foundation.layout.Box(
                Modifier.graphicsLayer {
                    scaleX = 1.08f
                    scaleY = 1.08f
                },
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) { content() }
        }
    }
}
