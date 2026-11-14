/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

/**
 * The app's haptics, played on the vibrator directly.
 *
 * Compose's default goes through View.performHapticFeedback, which Android silences
 * whenever the system "touch interactions" vibration is off — and on Samsung that switch
 * is off by default, so every haptic in the app did nothing. Driving the vibrator with the
 * platform's predefined click/tick effects keeps the same feel and actually fires.
 */
class AppHaptics(private val vibrator: Vibrator?) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val strong = hapticFeedbackType == HapticFeedbackType.LongPress ||
            hapticFeedbackType == HapticFeedbackType.Confirm ||
            hapticFeedbackType == HapticFeedbackType.Reject
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_TICK)
        } else {
            VibrationEffect.createOneShot(if (strong) 20L else 8L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        v.vibrate(effect)
    }
}

@Composable
fun rememberAppHaptics(): HapticFeedback {
    val context = LocalContext.current
    return remember(context) { AppHaptics(context.vibrator()) }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
