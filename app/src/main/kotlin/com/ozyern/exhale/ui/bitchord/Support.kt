/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.bitchord

import android.os.Build
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow

/*
 * What BitChord's lyric panel expects of the app around it, given from Exhale's side: the
 * strings, the haptics, the handful of player settings it reads, its tint and two helpers that
 * live in other BitChord files.
 */

/** The text the player draws in. Exhale's player is always white type on the artwork. */
internal val LocalPlayerTint = staticCompositionLocalOf { Color.White }

/** BitChord's player settings, as the panel reads them. Exhale keeps BitChord's defaults. */
internal object PlayerSettings {
    val lyricsOffsetMs = MutableStateFlow(0)
    val reduceDynamicBlur = MutableStateFlow(false)
    val lyricsBlur = MutableStateFlow(true)
    val reduceAnimation = MutableStateFlow(false)
    val translationLanguage = MutableStateFlow("")
}

internal enum class Haptic { Select, Tap, ToggleOn }

internal class Haptics(private val feedback: HapticFeedback) {
    fun play(haptic: Haptic) {
        feedback.performHapticFeedback(
            when (haptic) {
                Haptic.Select, Haptic.Tap -> HapticFeedbackType.TextHandleMove
                Haptic.ToggleOn -> HapticFeedbackType.ContextClick
            },
        )
    }
}

/** Exhale's haptics already follow the user's haptics setting (LocalHapticFeedback). */
@Composable
internal fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

// RenderEffect, API 31+; `Modifier.blur` is a no-op below it.
internal val renderEffectBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** BitChord's PlayerQueue.keepScrollInList: the list keeps its own scroll and flings. */
internal fun keepScrollInList(listState: LazyListState) = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available

    override suspend fun onPreFling(available: Velocity): Velocity =
        if (available.y > 0f && !listState.canScrollBackward) available else Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** BitChord's string resources, in English. */
internal object Res {
    object string {
        const val instrumental = "Instrumental"
        const val lyrics_already_in_language = "These lyrics are already in %1\$s"
        const val lyrics_already_romanized = "These lyrics already use Latin characters"
        const val lyrics_by = "Lyrics by %1\$s"
        const val lyrics_not_available = "Lyrics not available"
        const val lyrics_romanization_unavailable = "Romanization is unavailable for these lyrics"
        const val lyrics_romanized = "Romanized pronunciation"
        const val lyrics_saved_with_download = "Lyrics saved with this download"
        const val lyrics_translated_to = "Translated to %1\$s"
        const val lyrics_translation_unavailable = "Translation is unavailable right now"
        const val no_lyrics_for_track = "No lyrics for this track"
        const val no_lyrics_found = "No lyrics found"
        const val open_lyrics = "Lyrics"
        const val romanize_lyrics = "Romanize lyrics"
        const val romanizing_lyrics = "Romanizing lyrics…"
        const val show_original_lyrics = "Show original lyrics"
        const val translate_lyrics = "Translate lyrics"
        const val translating_lyrics_to = "Translating to %1\$s…"
    }

    object array {
        val lyrics_intro_lines = listOf(
            "The beat is landing", "The song is starting", "Warming up", "Setting the mood",
            "Bass first, words later", "Wait for it", "Feel that build", "Just the groove for now",
            "The hook is on the way", "Let it breathe", "Cue the vocals", "First notes in",
        )
    }
}

@Composable
internal fun stringResource(text: String, vararg args: Any): String =
    if (args.isEmpty()) text else String.format(text, *args)

@Composable
internal fun stringArrayResource(lines: List<String>): List<String> = lines

/** BitChord's player side margin; scrollable panels reach back across it. */
internal val PLAYER_GUTTER = 30.dp

/** BitChord's PlayerPlatformUi.android clip-and-shift. */
internal fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    dy: Float,
    block: DrawScope.() -> Unit,
) {
    clipRect(left = left, top = top, right = right, bottom = bottom) {
        translate(top = dy) { block() }
    }
}

/** BitChord's PlayerControls.bleedHorizontally: the panel reaches across the gutter it sits in. */
internal fun androidx.compose.ui.Modifier.bleedHorizontally(gutter: Dp): androidx.compose.ui.Modifier =
    layout { measurable, constraints ->
        val extra = gutter.roundToPx() * 2
        val widened = if (constraints.hasBoundedWidth) {
            constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
        } else {
            constraints
        }
        val placeable = measurable.measure(widened)
        val width = (placeable.width - extra).coerceAtLeast(0)
        layout(width, placeable.height) { placeable.place(-(placeable.width - width) / 2, 0) }
    }

/** BitChord's PlayerQueue.fadingEdges: softens the list where it meets the header and the scrubber. */
internal fun androidx.compose.ui.Modifier.fadingEdges(): androidx.compose.ui.Modifier = this
    .then(
        androidx.compose.ui.Modifier.graphicsLayer(
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen,
        ),
    )
    .drawWithContent {
        drawContent()
        val fade = 28.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black),
                startY = 0f,
                endY = fade,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - fade,
                endY = size.height,
            ),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
        )
    }
