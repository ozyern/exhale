/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import kotlin.math.roundToInt

/**
 * The iOS "hold to open" context menu: press and hold anything, the pressed thing lifts out
 * of a dimmed screen and a glass menu materialises beside it.
 *
 * Timing read off the reference comparison clip (AQPov…, 30fps): the panel is up in about
 * four frames, arriving as a small, heavily blurred, half-transparent shape that resolves
 * into a crisp full-size card -- a blur that clears, not just an alpha ramp, which is what
 * makes it read as glass condensing rather than a popup appearing. Dismissal is faster and
 * plain (no blur on the way out), as it is on iOS.
 *
 * The panel itself is [IosMenu]; this adds the gesture, the lift, the scrim and the motion.
 */
@Composable
fun IosContextMenu(
    sections: List<IosMenuSection>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Gap between the anchor and the menu, matching the reference's tight offset. */
    anchorGap: Dp = 8.dp,
    /** Kept off the screen edges by this much, as iOS clamps its menus. */
    screenMargin: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    /** Apple Music's Add / Favourite / Share row across the top of the panel. */
    quickActions: List<IosMenuItem> = emptyList(),
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var anchor by remember { mutableStateOf(Rect.Zero) }
    var open by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onGloballyPositioned { anchor = it.boundsInWindow() }
            .pointerInput(enabled, onClick) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        open = true
                    },
                    onTap = { onClick?.invoke() },
                )
            }
    ) {
        content()
    }

    IosContextMenuOverlay(
        sections = sections,
        anchor = anchor.takeIf { open },
        onDismissRequest = { open = false },
        anchorGap = anchorGap,
        screenMargin = screenMargin,
        liftedContent = content,
        quickActions = quickActions,
    )
}

/**
 * The same presentation, for callers that already know where the anchor is and run their
 * own long-press (the floating nav bar's tabs, which cannot hand their gesture over --
 * their cells are the tab bar's own click targets).
 *
 * A non-null [anchor] opens it; null closes it, and the overlay stays composed until the
 * exit animation has actually finished.
 */
@Composable
fun IosContextMenuOverlay(
    sections: List<IosMenuSection>,
    anchor: Rect?,
    onDismissRequest: () -> Unit,
    anchorGap: Dp = 8.dp,
    screenMargin: Dp = 12.dp,
    liftedContent: (@Composable () -> Unit)? = null,
    quickActions: List<IosMenuItem> = emptyList(),
) {
    val density = LocalDensity.current
    // The popup has to stay composed through the exit animation, so the transition -- not
    // the caller's anchor -- decides when it is really over.
    val transition = updateTransition(targetState = anchor != null, label = "iosContextMenu")
    // The last anchor is kept for that exit: the menu shrinks back toward the thing it came
    // out of, which the caller has already forgotten about by then.
    val lastAnchor = remember { arrayOf(Rect.Zero) }
    if (anchor != null) lastAnchor[0] = anchor
    val anchorRect = anchor ?: lastAnchor[0]

    if (transition.currentState || transition.targetState) {
        // Which side the menu landed on: written during layout, read during the draw of the
        // same frame by the panel's transform origin. A plain array, not snapshot state --
        // an observable write from layout would invalidate the layout that just made it.
        val goesAbove = remember { booleanArrayOf(true) }

        // Read animation state in draw and layer blocks to avoid recomposing the panel per frame.
        // One spring drives the menu size, blur, and fade.
        val progress = transition.animateFloat(
            transitionSpec = {
                if (targetState) spring(dampingRatio = 0.78f, stiffness = 700f) else tween(150, easing = MenuExitEasing)
            },
            label = "morph",
        ) { open -> if (open) 1f else 0f }
        val enterBlurPx = with(density) { MenuEnterBlur.toPx() }
        // Menu's placed top-left, written by layout, read by the layer's pivot.
        val menuOrigin = remember { intArrayOf(0, 0) }

        val scrimAlpha = transition.animateFloat(
            transitionSpec = {
                if (targetState) tween(180, easing = MenuEnterEasing) else tween(140, easing = MenuExitEasing)
            },
            label = "scrim",
        ) { open -> if (open) ScrimAlpha else 0f }

        // Anchor lift: iOS raises the pressed view a few percent as it leaves the page.
        val liftScale = transition.animateFloat(
            transitionSpec = {
                if (targetState) tween(150, easing = MenuEnterEasing) else tween(120, easing = MenuExitEasing)
            },
            label = "lift",
        ) { open -> if (open) AnchorLift else 1f }

        Popup(
            popupPositionProvider = FullWindowPositionProvider,
            properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false),
            onDismissRequest = onDismissRequest,
        ) {
            val gapPx = with(density) { anchorGap.roundToPx() }
            val marginPx = with(density) { screenMargin.roundToPx() }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(Color.Black, alpha = scrimAlpha.value) }
                    .pointerInput(Unit) {
                        detectTapGestures { onDismissRequest() }
                    }
            ) {
                CompositionLocalProvider(
                    LocalIosTextStyle provides IosTheme.typography.body,
                    LocalIosContentColor provides IosTheme.colors.label,
                ) {
                    Layout(
                        modifier = Modifier.fillMaxSize(),
                        content = {
                            // The lifted copy of whatever was pressed, drawn above the scrim
                            // at the place it already occupies, so nothing appears to move.
                            Box(
                                Modifier.graphicsLayer {
                                    scaleX = liftScale.value
                                    scaleY = liftScale.value
                                }
                            ) { liftedContent?.invoke() }

                            Box(
                                Modifier.graphicsLayer {
                                    val p = progress.value
                                    // Visible from the first frame as a faint blob (clip f9).
                                    this.alpha = (p / 0.35f).coerceIn(0f, 1f)
                                    // Starts the size of the pressed thing, per axis.
                                    val startX = (anchorRect.width / size.width).coerceIn(0.08f, 1f)
                                    val startY = (anchorRect.height / size.height).coerceIn(0.08f, 1f)
                                    scaleX = startX + (1f - startX) * p
                                    scaleY = startY + (1f - startY) * p
                                    // Grows out of the anchor itself, wherever it sits
                                    // relative to the panel.
                                    transformOrigin = TransformOrigin(
                                        pivotFractionX = ((anchorRect.center.x - menuOrigin[0]) / size.width).coerceIn(0f, 1f),
                                        pivotFractionY = ((anchorRect.center.y - menuOrigin[1]) / size.height).coerceIn(0f, 1f),
                                    )
                                    // Heavy blur that clears as it arrives; none on exit
                                    // (the reference's dismissal is a straight fade).
                                    // Below API 31 the panel arrives without it.
                                    val radius = if (transition.targetState) enterBlurPx * (1f - p).coerceIn(0f, 1f) else 0f
                                    if (radius > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        renderEffect = BlurEffect(radius, radius)
                                    }
                                }
                            ) {
                                IosMenu(
                                    sections = sections,
                                    quickActions = quickActions,
                                    onDismissRequest = onDismissRequest,
                                )
                            }
                        },
                    ) { measurables, constraints ->
                        val loose = constraints.copy(minWidth = 0, minHeight = 0)
                        val lifted = measurables[0].measure(loose)
                        val menu = measurables[1].measure(loose)

                        val width = constraints.maxWidth
                        val height = constraints.maxHeight

                        // Above when there isn't room below -- which is always the case for a
                        // bottom bar, and matches the reference's menu sitting over the list.
                        val spaceBelow = height - anchorRect.bottom.roundToInt()
                        val above = spaceBelow < menu.height + gapPx + marginPx
                        goesAbove[0] = above

                        val menuY = if (above) {
                            (anchorRect.top.roundToInt() - gapPx - menu.height)
                        } else {
                            anchorRect.bottom.roundToInt() + gapPx
                        }.coerceIn(marginPx, (height - menu.height - marginPx).coerceAtLeast(marginPx))

                        val menuX = (anchorRect.center.x.roundToInt() - menu.width / 2)
                            .coerceIn(marginPx, (width - menu.width - marginPx).coerceAtLeast(marginPx))

                        menuOrigin[0] = menuX
                        menuOrigin[1] = menuY
                        layout(width, height) {
                            lifted.place(anchorRect.left.roundToInt(), anchorRect.top.roundToInt())
                            menu.place(menuX, menuY)
                        }
                    }
                }
            }
        }
    }
}

/** Cubic fits of the reference's in/out curves; the enter one carries a hint of overshoot. */
private val MenuEnterEasing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1.03f)
private val MenuExitEasing = CubicBezierEasing(0.4f, 0f, 0.9f, 0.5f)

/** How blurred the panel is on its first frame (clip f10: content unreadable). */
private val MenuEnterBlur = 20.dp
private const val ScrimAlpha = 0.28f
private const val AnchorLift = 1.05f

/** Places the popup window at the window origin so its content can cover the whole screen. */
private object FullWindowPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
