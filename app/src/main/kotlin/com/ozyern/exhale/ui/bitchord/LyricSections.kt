/*
 * Ported from BitChord (github.com/kushagrasinghx/BitChord), GPL-3.0, by its authors.
 * Exhale is GPL-3.0 as well. Package and platform glue changed for Exhale; the lyric
 * timing, layout and animation are BitChord's own.
 */

package com.ozyern.exhale.ui.bitchord

/**
 * Whether a plain lyric line is one of Genius's section markers — `[Chorus]`,
 * `[Verse 2: Artist]` — rather than words to sing.
 */
fun isGeniusSectionHeader(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length in 3..60
}
