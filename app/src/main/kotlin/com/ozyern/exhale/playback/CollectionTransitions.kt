/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.playback

/**
 * How one album or playlist moves from song to song, overriding the global crossfade and AutoMix
 * settings while it plays — Apple Music's per-collection transitions. A live album or a DJ mix is
 * meant to run gapless; a party playlist wants AutoMix; most things want whatever the app does.
 */
enum class TransitionMode(val label: String) {
    DEFAULT("App setting"),
    GAPLESS("Gapless"),
    CROSSFADE("Crossfade"),
    AUTOMIX("AutoMix"),
    ;

    fun next(): TransitionMode = entries[(ordinal + 1) % entries.size]
}

/** The per-collection modes, stored as one preference: `id=MODE` lines. Default is never stored. */
object CollectionTransitions {
    /** What Crossfade means for a collection when the app's own crossfade is off. */
    const val FallbackCrossfadeMs = 6_000

    fun parse(raw: String?): Map<String, TransitionMode> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val id = line.substringBefore('=', "").trim()
            val mode = TransitionMode.entries.firstOrNull { it.name == line.substringAfter('=', "").trim() }
            if (id.isEmpty() || mode == null || mode == TransitionMode.DEFAULT) null else id to mode
        }.toMap()

    fun encode(modes: Map<String, TransitionMode>): String =
        modes.filterValues { it != TransitionMode.DEFAULT }
            .entries.joinToString("\n") { (id, mode) -> "$id=${mode.name}" }

    /** The crossfade length and AutoMix switch in effect for a collection's [mode]. */
    fun effective(mode: TransitionMode, globalCrossfadeMs: Int, globalAutomix: Boolean): Pair<Int, Boolean> =
        when (mode) {
            TransitionMode.DEFAULT -> globalCrossfadeMs to globalAutomix
            TransitionMode.GAPLESS -> 0 to false
            TransitionMode.CROSSFADE -> (globalCrossfadeMs.takeIf { it > 0 } ?: FallbackCrossfadeMs) to false
            TransitionMode.AUTOMIX -> globalCrossfadeMs to true
        }
}
