/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.ozyern.exhale.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.pow

/**
 * Light/dark appearance of the bottom chrome, decided from the pixels actually behind it
 * (Apple's tab-bar rule: the bar flips between its light and dark material with the
 * content underneath). Null = not sampled yet / unavailable; fall back to the theme.
 *
 * Read by [glassUsesDarkAppearance], so every glass colour in the subtree follows it.
 */
val LocalSampledChromeDark = compositionLocalOf<Boolean?> { null }

/**
 * Ported from the reference app's BottomBarToneSampler. Samples a thin band just ABOVE the bar's top
 * edge -- never the bar itself, whose own glass would feed back into the reading and make
 * it flip forever -- converts to perceptual L*, and flips with hysteresis + confirmation.
 */
class ChromeToneState internal constructor() {
    internal var bounds: Rect? = null
    var dark by mutableStateOf<Boolean?>(null)
        internal set
    private var bitmap: Bitmap? = null

    fun updateBounds(coords: LayoutCoordinates) {
        val pos = coords.localToWindow(Offset.Zero)
        bounds = Rect(pos.x.toInt(), pos.y.toInt(), (pos.x + coords.size.width).toInt(), (pos.y + coords.size.height).toInt())
    }

    internal fun sampleRect(bandPx: Int, insetPx: Int): Rect? {
        val b = bounds ?: return null
        val left = b.left + insetPx
        val right = b.right - insetPx
        val top = b.top - bandPx
        if (right <= left || top < 0) return null
        return Rect(left, top, right, b.top)
    }

    internal fun ensureBitmap(): Bitmap =
        bitmap ?: Bitmap.createBitmap(SAMPLE_W, SAMPLE_H, Bitmap.Config.ARGB_8888).also { bitmap = it }
}

private const val SAMPLE_W = 32
private const val SAMPLE_H = 8
private const val IDLE_MS = 2_000L
private const val NEAR_MS = 500L
private const val CONFIRM_MS = 200L
private const val NEAR_BAND_LSTAR = 8f
private const val TIMEOUT_MS = 900L
private const val BAND_DP = 8
private const val INSET_DP = 8
/** L* 50 is perceptual mid-grey; Y 0.5 would be L* ~72 and flip far too eagerly. */
private const val MID_LSTAR = 50f
private const val DEADBAND_LSTAR = 5f
private const val CONFIRM_COUNT = 2
private const val COOLDOWN_MS = 400L

@Composable
fun rememberChromeToneState(): ChromeToneState {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state = remember { ChromeToneState() }
    val bandPx = with(density) { BAND_DP.dp.roundToPx() }
    val insetPx = with(density) { INSET_DP.dp.roundToPx() }

    LaunchedEffect(state, lifecycle) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        var pending: Boolean? = null
        var pendingCount = 0
        var lastSwitchAt = 0L
        var interval = CONFIRM_MS
        while (true) {
            delay(interval)
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                interval = IDLE_MS
                continue
            }
            val rect = state.sampleRect(bandPx, insetPx) ?: continue
            val lstar = withTimeoutOrNull(TIMEOUT_MS) { state.capture(window, rect) }?.let(::yToLstar) ?: continue

            val current = state.dark
            if (current == null) {
                // First reading: decide outright, no hysteresis to honour yet.
                state.dark = lstar < MID_LSTAR
                lastSwitchAt = SystemClock.uptimeMillis()
                interval = IDLE_MS
                continue
            }
            val threshold = if (current) MID_LSTAR + DEADBAND_LSTAR else MID_LSTAR - DEADBAND_LSTAR
            val want = if (current) lstar <= threshold else lstar < threshold
            if (want == current) {
                pending = null
                pendingCount = 0
                interval = if (abs(lstar - threshold) < NEAR_BAND_LSTAR) NEAR_MS else IDLE_MS
                continue
            }
            interval = CONFIRM_MS
            if (pending != want) {
                pending = want
                pendingCount = 1
            } else {
                pendingCount++
            }
            if (pendingCount < CONFIRM_COUNT) continue
            val now = SystemClock.uptimeMillis()
            if (now - lastSwitchAt < COOLDOWN_MS) continue
            state.dark = want
            pendingCount = 0
            lastSwitchAt = now
        }
    }
    return state
}

/** One PixelCopy of [rect]; linear-light mean luminance, or null on any failure. */
private suspend fun ChromeToneState.capture(window: Window, rect: Rect): Float? =
    suspendCancellableCoroutine { cont ->
        val bmp = ensureBitmap()
        val launched = try {
            PixelCopy.request(window, rect, bmp, { result ->
                if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) meanLinearLuminance(bmp) else null)
            }, Handler(Looper.getMainLooper()))
            true
        } catch (t: Throwable) {
            // No backing surface yet / OEM quirks: sampling is cosmetic, never crash for it.
            if (t is CancellationException) throw t
            false
        }
        if (!launched) cont.resume(null)
    }

private fun meanLinearLuminance(bmp: Bitmap): Float {
    val px = IntArray(SAMPLE_W * SAMPLE_H)
    bmp.getPixels(px, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H)
    var sum = 0f
    for (c in px) {
        sum += 0.2126f * srgbToLinear(((c shr 16) and 0xFF) / 255f) +
            0.7152f * srgbToLinear(((c shr 8) and 0xFF) / 255f) +
            0.0722f * srgbToLinear((c and 0xFF) / 255f)
    }
    return sum / px.size
}

private fun srgbToLinear(v: Float): Float =
    if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)

internal fun yToLstar(y: Float): Float {
    val yn = y.coerceIn(0f, 1f)
    return if (yn > 216f / 24389f) 116f * yn.pow(1f / 3f) - 16f else 116f * 841f / 108f * yn
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
