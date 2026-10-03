/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/** Where one source stands on the current song. */
enum class LyricsSourceState { WAITING, FETCHING, FOUND, NOT_FOUND }

/**
 * The lookup for one song: every source in the order it was asked, what each said, and which one
 * the lyrics on screen came from.
 */
data class LyricsLookup(
    val mediaId: String,
    val order: List<String>,
    val states: Map<String, LyricsSourceState>,
    val current: String?,
)

/**
 * The most recent lookup, for the player's source picker to show. Shared rather than held by a
 * [LyricsHelper], because the service that fetches and the screen that shows are given different
 * instances of it. What each source found is kept alongside, so picking one that already answered
 * switches at once instead of asking again.
 */
object LyricsLookups {
    private val _state = MutableStateFlow<LyricsLookup?>(null)
    val state: StateFlow<LyricsLookup?> = _state

    private val answers = ConcurrentHashMap<String, String>()
    @Volatile
    private var answersFor: String? = null

    fun begin(mediaId: String, order: List<String>) {
        if (answersFor != mediaId) {
            answers.clear()
            answersFor = mediaId
        }
        _state.value = LyricsLookup(
            mediaId = mediaId,
            order = order,
            states = order.associateWith { name ->
                if (answers.containsKey(name)) LyricsSourceState.FOUND else LyricsSourceState.WAITING
            },
            current = _state.value?.takeIf { it.mediaId == mediaId }?.current,
        )
    }

    fun update(mediaId: String, source: String, state: LyricsSourceState, lyrics: String? = null) {
        if (lyrics != null && answersFor == mediaId) answers[source] = lyrics
        _state.update { lookup ->
            if (lookup == null || lookup.mediaId != mediaId) lookup
            else lookup.copy(states = lookup.states + (source to state))
        }
    }

    fun choose(mediaId: String, source: String) {
        _state.update { lookup ->
            if (lookup == null || lookup.mediaId != mediaId) lookup else lookup.copy(current = source)
        }
    }

    fun found(mediaId: String, source: String): String? =
        if (answersFor == mediaId) answers[source] else null
}
