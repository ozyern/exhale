/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.utils

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** How a touch feels: the phone's own haptics, or one of Exhale's three. */
enum class HapticFeel(val label: String, val detail: String) {
    SYSTEM("Phone default", "Whatever the phone itself does"),
    CRISP("Crisp", "Short and sharp, like a key clicking home"),
    SOFT("Soft", "Muted and low, like pressing into cloth"),
    RICH("Rich", "Round and full, with a little weight behind it"),
}

/**
 * Exhale's haptics: every Compose haptic in the app comes through here once the feel is one of
 * Exhale's own. Two strengths — a tick for moving things, a click for deciding things — played as
 * the motor's own primitives scaled by the intensity, falling back to the phone's predefined
 * effects where the motor can't do primitives.
 */
object ExhaleHaptics {

    private fun vibrator(context: Context): Vibrator? =
        runCatching { context.getSystemService(VibratorManager::class.java)?.defaultVibrator }.getOrNull()

    /** Whether [type] is a decision (a click) rather than motion (a tick). */
    private fun isStrong(type: HapticFeedbackType): Boolean = when (type) {
        HapticFeedbackType.LongPress,
        HapticFeedbackType.Confirm,
        HapticFeedbackType.Reject,
        HapticFeedbackType.ToggleOn,
        HapticFeedbackType.GestureThresholdActivate,
        -> true
        else -> false
    }

    fun perform(context: Context, type: HapticFeedbackType, feel: HapticFeel, intensity: Float) {
        play(context, strong = isStrong(type), feel = feel, intensity = intensity)
    }

    /** One touch, at [feel] and [intensity] — also what the settings page previews with. */
    fun play(context: Context, strong: Boolean, feel: HapticFeel, intensity: Float) {
        if (feel == HapticFeel.SYSTEM) return
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val scale = intensity.coerceIn(0.05f, 1f)
        val (primitive, weight) = when (feel) {
            HapticFeel.CRISP -> (if (strong) VibrationEffect.Composition.PRIMITIVE_CLICK else VibrationEffect.Composition.PRIMITIVE_TICK) to 1f
            HapticFeel.SOFT -> (if (strong) VibrationEffect.Composition.PRIMITIVE_TICK else VibrationEffect.Composition.PRIMITIVE_LOW_TICK) to 0.7f
            HapticFeel.RICH -> (if (strong) VibrationEffect.Composition.PRIMITIVE_THUD else VibrationEffect.Composition.PRIMITIVE_CLICK) to (if (strong) 1f else 0.6f)
            HapticFeel.SYSTEM -> return
        }
        val effect = if (vibrator.areAllPrimitivesSupported(primitive)) {
            VibrationEffect.startComposition()
                .addPrimitive(primitive, (scale * weight).coerceIn(0f, 1f))
                .compose()
        } else if (vibrator.hasAmplitudeControl()) {
            val ms = when (feel) {
                HapticFeel.CRISP -> if (strong) 18L else 8L
                HapticFeel.SOFT -> if (strong) 14L else 10L
                else -> if (strong) 30L else 12L
            }
            VibrationEffect.createOneShot(ms, (255 * scale * weight).toInt().coerceIn(1, 255))
        } else {
            VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK)
        }
        runCatching { vibrator.vibrate(effect) }
    }

    /** A short phrase for the settings preview: tick, tick, click, and a closing tick. */
    suspend fun demo(context: Context, feel: HapticFeel, intensity: Float, onBeat: (Int) -> Unit = {}) {
        val beats = listOf(false, false, true, false)
        beats.forEachIndexed { index, strong ->
            onBeat(index)
            play(context, strong, if (feel == HapticFeel.SYSTEM) HapticFeel.CRISP else feel, intensity)
            kotlinx.coroutines.delay(if (strong) 320 else 180)
        }
    }
}
