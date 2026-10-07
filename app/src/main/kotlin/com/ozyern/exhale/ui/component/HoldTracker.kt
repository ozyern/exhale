/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where the finger is being held, if it is — so a menu opened by a long press can open *there*,
 * as Apple Music's does, while the same menu opened from a ⋮ tap keeps arriving as a sheet.
 *
 * Watched once, at the root of the window, on the initial pass and without consuming anything:
 * no list, row or tile needs to know about it, and every long press in the app gets the popup.
 */
object HoldTracker {
    /** How long a press has to have lasted to be a hold. */
    private const val HOLD_MS = 350L

    /** A menu shown this soon after a hold was let go still counts as opened by it. */
    private const val AFTER_RELEASE_MS = 250L

    private var root: LayoutCoordinates? = null
    private var downAt = 0L
    private var upAt = 0L
    private var down = false
    private var position: Offset? = null

    /** The held point on screen, if a hold is opening the menu now; null for anything else. */
    fun heldNow(): Offset? {
        val now = SystemClock.uptimeMillis()
        val held = if (down) {
            now - downAt >= HOLD_MS
        } else {
            upAt - downAt >= HOLD_MS && now - upAt <= AFTER_RELEASE_MS
        }
        return if (held) position else null
    }

    fun Modifier.trackHolds(): Modifier = this
        .onGloballyPositioned { root = it }
        .pointerInput(Unit) {
            awaitEachGesture {
                val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                down = true
                downAt = SystemClock.uptimeMillis()
                position = root?.takeIf { it.isAttached }?.localToScreen(first.position) ?: first.position
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                down = false
                upAt = SystemClock.uptimeMillis()
            }
        }
}
