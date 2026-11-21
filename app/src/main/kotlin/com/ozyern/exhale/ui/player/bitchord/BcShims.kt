/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The app-wide pieces BitChord's player reaches for (its string and drawable resources, haptics,
 * settings and repeat modes), stood in for by Exhale's own so the ported player
 * (kushagrasinghx/BitChord, GPL-3.0) compiles unchanged.
 */

package com.ozyern.exhale.ui.player.bitchord

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** BitChord's compose-resources drawable handle: here, an Android drawable id. */
typealias DrawableResource = Int

/** BitChord's `Res.string` and `Res.drawable`, as the words and drawables themselves. */
internal object Res {
    object string {
        const val mixing = "Mixing"
        const val widget_previous = "Previous"
        const val widget_next = "Next"
        const val play = "Play"
        const val pause = "Pause"
        const val open_lyrics = "Open lyrics"
        const val close_lyrics = "Close lyrics"
        const val shuffle_on = "Shuffle on"
        const val shuffle_off = "Shuffle off"
        const val repeat_one = "Repeat one"
        const val repeat_all = "Repeat all"
        const val repeat_off = "Repeat off"
        const val autoplay_on = "Autoplay on"
        const val autoplay_off = "Autoplay off"
        const val up_next = "Up next"
        const val eight_d_on = "8D audio on"
        const val eight_d_off = "8D audio off"
        const val output = "Audio output"
    }

    object drawable {
        val ic_player_previous: DrawableResource = R.drawable.bc_ic_player_previous
        val ic_player_next: DrawableResource = R.drawable.bc_ic_player_next
        val ic_player_play: DrawableResource = R.drawable.bc_ic_player_play
        val ic_player_pause: DrawableResource = R.drawable.bc_ic_player_pause
    }
}

/** compose-resources' `stringResource`: the shimmed strings are already the words. */
@Composable
internal fun stringResource(text: String): String = text

/** compose-resources' `painterResource`, on an Android drawable. */
@Composable
internal fun painterResource(id: DrawableResource): Painter = androidx.compose.ui.res.painterResource(id)

/** BitChord's haptic vocabulary, played on Android's own feedback. */
internal enum class Haptic { Tap, Select, ToggleOn, ToggleOff, Expand, Pause, Resume, SkipNext, SkipPrevious }

internal class Haptics(private val feedback: androidx.compose.ui.hapticfeedback.HapticFeedback) {
    fun play(haptic: Haptic) {
        feedback.performHapticFeedback(
            when (haptic) {
                Haptic.ToggleOn -> HapticFeedbackType.ToggleOn
                Haptic.ToggleOff -> HapticFeedbackType.ToggleOff
                Haptic.Select -> HapticFeedbackType.SegmentTick
                Haptic.Pause, Haptic.Resume, Haptic.SkipNext, Haptic.SkipPrevious -> HapticFeedbackType.Confirm
                Haptic.Tap, Haptic.Expand -> HapticFeedbackType.ContextClick
            },
        )
    }
}

@Composable
internal fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

/** BitChord's repeat modes, which are Media3's. */
internal object RepeatModes {
    const val OFF = androidx.media3.common.Player.REPEAT_MODE_OFF
    const val ONE = androidx.media3.common.Player.REPEAT_MODE_ONE
    const val ALL = androidx.media3.common.Player.REPEAT_MODE_ALL
}

/**
 * An Automix blend as BitChord's bar draws it. Exhale's transitions publish no beat clock to the
 * player, so there is never one, and the bar never shows "Mixing".
 */
class MixBlend(val playing: Boolean, val beatMs: Float, val beatAnchorNanos: Long)

/** BitChord's player settings, as far as its controls read them. */
internal object PlayerSettings {
    val smartMixBlend: StateFlow<MixBlend?> = MutableStateFlow(null)
    val reduceAnimation = MutableStateFlow(false)
    /** No Listen Together capsule in Exhale's row: the output glyph stands alone. */
    val showListenTogetherButton: StateFlow<Boolean> = MutableStateFlow(false)
}

/** Opens the mix view; Exhale has none, so the request goes nowhere. */
internal object MixViewRequest {
    var open: Boolean = false
}

internal fun uptimeMillis(): Long = android.os.SystemClock.uptimeMillis()

/** BitChord's marquee fade width (SongText.kt). */
internal val LINE_FADE = 36.dp
