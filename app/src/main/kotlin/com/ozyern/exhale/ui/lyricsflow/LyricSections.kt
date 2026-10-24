/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.lyricsflow

/**
 * Whether a plain lyric line is one of Genius's section markers — `[Chorus]`,
 * `[Verse 2: Artist]` — rather than words to sing.
 */
fun isGeniusSectionHeader(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length in 3..60
}
