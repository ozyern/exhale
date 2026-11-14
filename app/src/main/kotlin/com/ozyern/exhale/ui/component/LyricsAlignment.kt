/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Convx PR #53 (MeYashverma/Convxy) — Apple Music-style duet
 * left/right alignment only, generalized beyond the hardcoded v1/v2/v1000
 * checks so any "vN" pair alternates sides and composite/group agents
 * (see [primaryAgentId]) center.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.ozyern.exhale.lyrics.GROUP_AGENT_ID
import com.ozyern.exhale.lyrics.primaryAgentId
import com.ozyern.exhale.ui.screens.settings.LyricsPosition

/** Per-line horizontal alignment: background/group agents center, vN leads alternate sides. */
fun singerLineAlignment(
    agent: String?,
    isBackground: Boolean,
    lyricsTextPosition: LyricsPosition,
): Alignment.Horizontal =
    when {
        isBackground -> Alignment.CenterHorizontally
        agent != null -> when (val id = primaryAgentId(agent)) {
            GROUP_AGENT_ID -> Alignment.CenterHorizontally
            else -> when (id.removePrefix("v").toIntOrNull()?.rem(2)) {
                1 -> Alignment.Start
                0 -> Alignment.End
                else -> lyricsTextPosition.toAlignment()
            }
        }
        else -> lyricsTextPosition.toAlignment()
    }

/** [singerLineAlignment] for [TextAlign] (used by the text layers). */
fun singerTextAlign(
    agent: String?,
    isBackground: Boolean,
    lyricsTextPosition: LyricsPosition,
): TextAlign =
    when {
        isBackground -> TextAlign.Center
        agent != null -> when (val id = primaryAgentId(agent)) {
            GROUP_AGENT_ID -> TextAlign.Center
            else -> when (id.removePrefix("v").toIntOrNull()?.rem(2)) {
                1 -> TextAlign.Left
                0 -> TextAlign.Right
                else -> lyricsTextPosition.toTextAlign()
            }
        }
        else -> lyricsTextPosition.toTextAlign()
    }

private fun LyricsPosition.toAlignment(): Alignment.Horizontal = when (this) {
    LyricsPosition.LEFT -> Alignment.Start
    LyricsPosition.CENTER -> Alignment.CenterHorizontally
    LyricsPosition.RIGHT -> Alignment.End
}

private fun LyricsPosition.toTextAlign(): TextAlign = when (this) {
    LyricsPosition.LEFT -> TextAlign.Left
    LyricsPosition.CENTER -> TextAlign.Center
    LyricsPosition.RIGHT -> TextAlign.Right
}
