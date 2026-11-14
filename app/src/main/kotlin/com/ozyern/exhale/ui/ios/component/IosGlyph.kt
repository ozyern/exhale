/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.composables.sfsymbols.SfSymbol
import com.ozyern.exhale.ui.component.SymbolIcon

/**
 * An SF Symbol (or any glyph) fitted inside a [box] square *without* stretching it.
 * SF Symbols are not square — ellipsis is wide and short, music.note.list wider than
 * tall — and forcing them into a square Icon squashes them (the "…" drew as "|||").
 */
@Composable
fun IosGlyph(vector: ImageVector, tint: Color, box: Dp, modifier: Modifier = Modifier) =
    IosGlyph(rememberVectorPainter(vector), tint, box, modifier)

@Composable
fun IosGlyph(painter: Painter, tint: Color, box: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(box),
    )
}

/** Bold, hierarchical, bounce-on-touch SF Symbol fitted in a [box] square. */
@Composable
fun IosGlyph(symbol: SfSymbol, tint: Color, box: Dp, modifier: Modifier = Modifier, enterOnly: Boolean = false) =
    SymbolIcon(
        symbol, contentDescription = null, modifier = modifier, size = box, tint = tint,
        bounceOnEnter = enterOnly, enterKey = if (enterOnly) symbol.name else null,
        // Same chrome as the tab bar, unless an enclosing menu already picked its own strength.
        chrome = com.ozyern.exhale.ui.component.LocalSymbolChrome.current.takeIf { it > 0f } ?: com.ozyern.exhale.ui.utils.MenuIconChrome,
    )
