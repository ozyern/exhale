/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Ported from Convx PR #53 (MeYashverma/Convxy) — agent-id parsing only.
 */

package com.ozyern.exhale.lyrics

/**
 * Returns the canonical id of the (possibly composite) [agent] string.
 *
 * Supported agent forms:
 *  - `"v1"`, `"v2"`, … (single voice, the common case)
 *  - `"v1000"` (Apple convention for a vocal group / shared line)
 *  - `"v1+v2"`, `"v1,v2"`, `"v1&v2"` (explicit shared line: both leads sing)
 *
 * Composite forms normalize to the group id so a shared line is treated as one
 * (group) voice rather than flipping between its members.
 */
fun primaryAgentId(agent: String): String {
    val trimmed = agent.trim()
    if (SHARED_AGENT_REGEX.matches(trimmed)) return GROUP_AGENT_ID
    return trimmed.substringBefore('+').substringBefore(',').substringBefore('&').trim()
}

/** Agents recognized as "shared vocals" (`v1+v2`, `v1,v2`, `v1&v2`). */
private val SHARED_AGENT_REGEX = Regex("v\\d+\\s*[+,&]\\s*v\\d+(\\s*[+,&]\\s*v\\d+)*")

/** Apple convention for the all-vocalists group agent. */
const val GROUP_AGENT_ID = "v1000"

/**
 * True when the line at [index] starts a new singer section, i.e. the previous
 * lead line (skipping background vocals and blank/head entries) belongs to a
 * different vocalist. Used to show the "who is singing" badge on section
 * starts only.
 */
fun isSingerSectionStart(lines: List<LyricsEntry>, index: Int): Boolean {
    val entry = lines.getOrNull(index) ?: return false
    val agent = entry.agent ?: return false
    if (entry.isBackground) return false
    val currentId = primaryAgentId(agent)
    for (i in index - 1 downTo 0) {
        val previous = lines[i]
        if (previous.isBackground || previous.text.isBlank()) continue
        val previousAgent = previous.agent ?: return true
        return primaryAgentId(previousAgent) != currentId
    }
    return true
}
