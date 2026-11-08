/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * The app's glass plate, as one modifier.
 *
 * Three things together are what make a rectangle read as glass rather than as a grey box: a
 * *gradient* fill so the top catches more light than the bottom, a bright hairline rim that fades
 * out down the same axis, and enough translucency that whatever is painted behind the card bleeds
 * through. Miss any one of them and it stops working — a flat translucent fill with no rim is just
 * a smudge, and an opaque fill with a rim is a bevel.
 *
 * [LiquidGlassSheet] carries its own copy of this recipe tuned for a full-width sheet against a
 * scrim. This one is for cards sitting on a page, and is shared by Sound Chem's capsule deck and
 * the Updates page so the two cannot drift apart.
 *
 * Cards using this need something behind them worth seeing — a colour wash, artwork, a blur. On a
 * flat `surface` page there is nothing for the translucency to reveal, and the card lands as a
 * grey smudge with a bright edge. [base] is the way out of that: an opaque-ish tonal floor laid
 * under the glass so the plate is guaranteed to exist whatever is or is not behind it. Pass it
 * for controls that have to work on every screen in the app (see `LiquidBackButton`); leave it
 * unspecified for cards that are only ever placed over artwork, where a floor would just throw
 * away the effect.
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape,
    tint: Color = Color.Unspecified,
    base: Color = Color.Unspecified,
): Modifier {
    val dark = isSystemInDarkTheme()

    val fill = Brush.verticalGradient(
        // Dark: a body of its own under a faint white lift. 10%-to-4% white alone was clear glass,
        // and a card's text sat on whatever artwork scrolled under it.
        colors = if (dark) {
            listOf(Color(0xFF242426).copy(alpha = 0.44f), Color(0xFF1C1C1E).copy(alpha = 0.52f))
        } else {
            listOf(Color.White.copy(alpha = 0.92f), Color.White.copy(alpha = 0.66f))
        },
    )

    // The diagonal sheen. A pane of glass catches light at one corner and loses it by the middle;
    // a card whose only gradient runs top-to-bottom reads as a *painted panel* instead. This is
    // the cheapest possible version of that — one extra gradient, no blur, no shader.
    val sheen = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (dark) 0.11f else 0.55f),
        0.42f to Color.Transparent,
        1f to Color.Transparent,
    )

    val rim = Brush.verticalGradient(
        colors = if (dark) {
            listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))
        } else {
            listOf(Color.White.copy(alpha = 0.95f), Color.Black.copy(alpha = 0.05f))
        },
    )

    // Real glass where there is something to bend: the page's recorded ground (the moving artwork
    // behind every screen, or a page's own wash), lensed at the rim, softened and saturated, with
    // the same film, sheen and rim drawn on the pane. Not inside a dialog's window — the recording
    // belongs to the activity's window and would land in the wrong place there.
    val pageBackdrop = com.ozyern.exhale.ui.component.liquid.pageBackdropOrNull
    val inDialog = androidx.compose.ui.platform.LocalView.current.parent is androidx.compose.ui.window.DialogWindowProvider
    if (pageBackdrop != null && !inDialog) {
        return this
            .drawBackdrop(
                backdrop = pageBackdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(10f.dp.toPx())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        lens(12f.dp.toPx(), 22f.dp.toPx(), true)
                    }
                },
                highlight = { com.kyant.backdrop.highlight.Highlight.Default.copy(alpha = 0.55f) },
                onDrawSurface = {
                    if (base.isSpecified) drawRect(base.copy(alpha = base.alpha * 0.55f))
                    drawRect(fill)
                    if (tint.isSpecified) {
                        drawRect(
                            Brush.verticalGradient(
                                listOf(tint.copy(alpha = if (dark) 0.16f else 0.18f), tint.copy(alpha = 0.03f)),
                            ),
                        )
                    }
                    drawRect(sheen)
                },
            )
            .border(width = 1.dp, brush = rim, shape = shape)
    }

    return this
        .clip(shape)
        // The tonal floor, under everything, so a control on a flat page still has a body.
        .then(if (base.isSpecified) Modifier.background(base) else Modifier)
        .background(fill)
        // An optional wash of the card's own accent, laid under the sheen. Kept deliberately faint
        // — the point is that a row of cards is visibly *not* the same colour, not that any one of
        // them is coloured.
        .then(
            if (tint.isSpecified) {
                Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = if (dark) 0.14f else 0.16f),
                            tint.copy(alpha = if (dark) 0.02f else 0.03f),
                        ),
                    )
                )
            } else {
                Modifier
            }
        )
        .background(sheen)
        .border(width = 1.dp, brush = rim, shape = shape)
}
