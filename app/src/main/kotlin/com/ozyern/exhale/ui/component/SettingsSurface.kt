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
val SettingsGroupCornerRadius = 26.dp

/** Hairline between rows inside a group. Never drawn at a card's top or bottom edge. */
val SettingsDividerThickness = 1.dp

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
@Composable
fun settingsPageBackgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainerLowest

/** The card colour — one step off [settingsPageBackgroundColor], solid, never elevated. */
@Composable
fun settingsGroupSurfaceColor(): Color = MaterialTheme.colorScheme.surfaceContainerHigh

/** Low-contrast row separator: enough to divide, not enough to draw a grid. */
@Composable
fun settingsDividerColor(): Color =
    if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)

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
 * The group card itself, as one modifier: a tonal floor, glass over it, and a rim.
 *
 * The two-tone grouped table above is the *structure* — a solid page colour and a card colour one
 * step off it. This is that structure rendered as glass instead of as flat paint, and the tonal
 * floor is what lets it be both. Settings now has the album-art wash behind it (see
 * `SettingsPage`), so a fully translucent card would work on the landing page; but the same card
 * is drawn on twenty deeper pages that have nothing behind them, and there it would collapse into
 * a smudge. Floating the glass on a floor at 72% keeps the two-tone step intact everywhere, and
 * lets the wash come through the remaining 28% where there is a wash to come through.
 *
 * The three things that make it read as glass rather than as a grey box are all here and all
 * required: a vertical gradient so the top of the card catches more light than the bottom, a
 * diagonal sheen falling off by the middle, and a hairline rim that is bright at the top edge and
 * gone by the bottom. Drop any one and it stops working — no rim and it is a smudge, no gradient
 * and it is a painted panel, no translucency and it is a bevel.
 */
@Composable
fun Modifier.settingsGlassGroup(
    shape: Shape = RoundedCornerShape(SettingsGroupCornerRadius),
): Modifier {
    val dark = isSystemInDarkTheme()

    val fill = Brush.verticalGradient(
        if (dark) {
            listOf(Color.White.copy(alpha = 0.055f), Color.White.copy(alpha = 0.012f))
        } else {
            listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.14f))
        },
    )
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.05f else 0.30f),
        0.45f to Color.Transparent,
        1f to Color.Transparent,
    )

    return this
        .clip(shape)
        .background(settingsGroupSurfaceColor().copy(alpha = 0.72f))
        .background(fill)
        .background(sheen)
        .border(width = 0.8.dp, brush = settingsGroupBorderBrush(), shape = shape)
}

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
fun Modifier.settingsIconPuck(
    accent: Color,
    shape: Shape = RoundedCornerShape(12.dp),
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0.11f))
        )
    )
    .border(
        width = 0.7.dp,
        brush = Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.38f), accent.copy(alpha = 0.06f))
        ),
        shape = shape,
    )
