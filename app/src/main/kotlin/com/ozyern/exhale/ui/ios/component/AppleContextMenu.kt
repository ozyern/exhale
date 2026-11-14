/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosContentColor
import com.ozyern.exhale.ui.ios.foundation.LocalIosTextStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Press-and-hold menu timing from the Apple Music reference (30 fps): press feedback on
 * frames 0–8, haptic and page dim on 9, item expansion on 10–14, and menu expansion on 12–15.
 * Drag the preview down or back to dismiss; tap it to open, or tap outside to close.
 * Keep the source item hidden until the preview finishes closing to avoid showing both.
 * [preview] is laid out at card width and should start with the source item's cover.
 */
@Composable
fun AppleContextMenu(
    sections: List<IosMenuSection>,
    preview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    quickActions: List<IosMenuItem> = emptyList(),
    onClick: (() -> Unit)? = null,
    /** External controller for reorderable items; open on hold and cancel when dragging starts. */
    controller: AppleMenuController? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val menu = controller?.state ?: remember { AppleContextMenuState() }
    // A plain holder, not state: written on every layout (every scrolled frame), read only
    // when the overlay composes on open — as state it recomposed every tile per frame.
    val anchor = remember { arrayOf(Rect.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { anchor[0] = it.boundsInWindow() }
            .graphicsLayer {
                val s = 1f - (1f - PressScale) * menu.press.value
                scaleX = s
                scaleY = s
                alpha = menu.itemAlpha()
            }
            .drawWithContent {
                drawContent()
                val p = menu.press.value
                if (p > 0f) drawRect(Color.Black.copy(alpha = 0.14f * p))
            }
            .then(if (controller != null) Modifier else Modifier.pointerInput(onClick) {
                val dismissDown = with(density) { 64.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val sink = scope.launch { menu.press.animateTo(1f, tween(300)) }
                    // null = held past the long-press timeout; true = lifted (a tap);
                    // false = the list took the gesture (scrolled), it moved away, or a
                    // child claimed the tap. Read in the Main pass, i.e. after children:
                    // a tap on a button inside the item (a row's "…") is the button's.
                    val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: return@withTimeoutOrNull false
                            if (!change.pressed) return@withTimeoutOrNull !change.isConsumed
                            if (change.isConsumed ||
                                (change.position - down.position).getDistance() > viewConfiguration.touchSlop
                            ) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    sink.cancel()
                    scope.launch { menu.press.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f)) }
                    when (lifted) {
                        true -> onClick?.invoke()
                        false -> Unit
                        null -> {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { menu.drag.snapTo(Offset.Zero) }
                            menu.open()
                            // The finger is still down: it now drags the preview.
                            val start = down.position
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull() ?: break
                                // Swallowed before children see it — the lift included, or a
                                // clickable inside the item would fire on release.
                                change.consume()
                                if (!change.pressed) break
                                val total = change.position - start
                                scope.launch { menu.drag.snapTo(rubberBand(total)) }
                            }
                            val drag = menu.drag.value
                            val home = menu.towardHome()
                            val homeLen = home.getDistance()
                            val backHome = homeLen > 1f &&
                                (drag.x * home.x + drag.y * home.y) / homeLen > 0.4f * homeLen
                            if (drag.y > dismissDown || backHome) {
                                menu.close()
                            } else {
                                scope.launch { menu.drag.animateTo(Offset.Zero, spring(dampingRatio = 0.75f, stiffness = 500f)) }
                            }
                        }
                    }
                }
            }),
    ) { content() }

    AppleContextMenuOverlay(
        menu = menu,
        anchorOf = { anchor[0] },
        sections = sections,
        quickActions = quickActions,
        preview = preview,
        onPreviewClick = onClick,
    )
}

/** Drag follows the finger downward/sideways; upward it resists, as on iOS. */
private fun rubberBand(d: Offset) = Offset(d.x * 0.6f, if (d.y < 0f) d.y * 0.3f else d.y)

/** Opens and drops an [AppleContextMenu] whose gesture its caller owns. */
@Stable
class AppleMenuController internal constructor() {
    internal val state = AppleContextMenuState()

    /** Whether the menu is up (or still animating back). */
    val isOpen: Boolean get() = state.visible

    fun open() = state.open()

    /** Gone at once, no landing — the item is being dragged instead. */
    fun cancel() = state.cancel()
}

@Composable
fun rememberAppleMenuController(): AppleMenuController = remember { AppleMenuController() }

@Stable
internal class AppleContextMenuState {
    /** Replaced, not reset, on [cancel]: a fresh state is simply closed, with no animation. */
    var transition by mutableStateOf(MutableTransitionState(false))
    val press = Animatable(0f)
    val drag = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Card target top-left and the card's "home" top-left (on the item), from layout. */
    val placed = FloatArray(4)

    /** The item sat in the right half: card and menu align to its trailing edge. */
    var rightSide = false

    /** The overlay's lift progress, shared so the item can crossfade with it. */
    var progress: State<Float>? = null

    /**
     * The item is hidden while its preview is up. On the way back the last [LandFade]
     * of the travel crossfades — the item fades in under the landing preview as the
     * preview fades out — so the two never swap in one frame (their text differs).
     */
    fun itemAlpha(): Float {
        if (!visible) return 1f
        if (transition.targetState) return 0f
        val p = progress?.value ?: return 0f
        return (1f - p / LandFade).coerceIn(0f, 1f)
    }

    /** The preview's own alpha: full, except through the landing crossfade. */
    fun previewAlpha(p: Float): Float =
        if (transition.targetState) 1f else (p / LandFade).coerceIn(0f, 1f)

    /** Open, or still animating back: the item stays hidden the whole time. */
    val visible: Boolean get() = transition.currentState || transition.targetState

    fun open() {
        transition.targetState = true
    }

    fun close() {
        transition.targetState = false
    }

    /** Gone at once — the item is being dragged, not put back. */
    fun cancel() {
        transition = MutableTransitionState(false)
    }

    /** From the card's resting place back to where the item sits. */
    fun towardHome() = Offset(placed[2] - placed[0], placed[3] - placed[1])
}

@Composable
private fun AppleContextMenuOverlay(
    menu: AppleContextMenuState,
    anchorOf: () -> Rect,
    sections: List<IosMenuSection>,
    quickActions: List<IosMenuItem>,
    preview: @Composable () -> Unit,
    onPreviewClick: (() -> Unit)?,
) {
    if (!menu.visible) return
    // Read now, as the overlay composes on open: where the item sits at that moment.
    val anchor = remember(menu.transition) { anchorOf() }

    val transition = rememberTransition(menu.transition, label = "appleContextMenu")
    // Lift: one spring with a touch of overshoot. Landing: critically damped, so it
    // settles onto the item instead of bouncing and snapping.
    val p = transition.animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = 0.8f, stiffness = 420f) else spring(dampingRatio = 1f, stiffness = 520f)
        },
        label = "lift",
    ) { if (it) 1f else 0f }
    menu.progress = p
    // Let the menu grow wider before it grows taller, with a soft spring on both axes.
    val mx = transition.menuAxis(dampingRatio = 0.62f, stiffness = 560f, "menuX")
    val my = transition.menuAxis(dampingRatio = 0.78f, stiffness = 330f, "menuY")

    val colors = IosTheme.colors
    val dark = colors.isDark
    val cardColor = if (dark) Color(0xFF2C2C2E) else Color.White
    val dim = Color.Black.copy(alpha = if (dark) 0.28f else 0.14f)
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current
    val padPx = with(density) { CardPadding.toPx() }
    val menuFadePx = with(density) { 120.dp.toPx() }

    Popup(
        popupPositionProvider = WindowOrigin,
        properties = PopupProperties(focusable = true, usePlatformDefaultWidth = false),
        onDismissRequest = menu::close,
    ) {
        Box(Modifier.fillMaxSize()) {
            // The page behind: a slight dim only (no blur).
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind { drawRect(dim, alpha = p.value.coerceIn(0f, 1f)) }
                    .pointerInput(Unit) { detectTapGestures { menu.close() } },
            )

            CompositionLocalProvider(
                LocalIosTextStyle provides IosTheme.typography.body,
                LocalIosContentColor provides colors.label,
            ) {
                Layout(
                    modifier = Modifier.fillMaxSize(),
                    content = {
                        // Preview card: at p = 0 its *content* sits exactly on the item.
                        Box(
                            Modifier
                                .graphicsLayer {
                                    val v = p.value
                                    val inner = size.width - 2 * padPx
                                    val s0 = (anchor.width / inner).coerceIn(0.05f, 1f)
                                    val s = s0 + (1f - s0) * v
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    scaleX = s
                                    scaleY = s
                                    val d = menu.drag.value
                                    translationX = (anchor.left - padPx * s0 - menu.placed[0]) * (1f - v) + d.x * v
                                    translationY = (anchor.top - padPx * s0 - menu.placed[1]) * (1f - v) + d.y * v
                                    alpha = menu.previewAlpha(v)
                                }
                                .clip(ContinuousRoundedRectangle(22.dp))
                                .drawBehind { drawRect(cardColor, alpha = p.value.coerceIn(0f, 1f)) }
                                .pointerInput(onPreviewClick) {
                                    detectTapGestures {
                                        onPreviewClick?.invoke()
                                        menu.close()
                                    }
                                }
                                .padding(CardPadding),
                        ) { Column { preview() } }

                        // Menu: stretches open from its corner under the card.
                        Box(
                            Modifier.graphicsLayer {
                                val x = mx.value
                                val y = my.value
                                val dragFade = 1f - (menu.drag.value.getDistance() / menuFadePx).coerceIn(0f, 1f)
                                alpha = (p.value * 3f).coerceIn(0f, 1f) * dragFade
                                scaleX = 0.25f + 0.75f * x
                                scaleY = 0.08f + 0.92f * y
                                transformOrigin = TransformOrigin(if (menu.rightSide) 1f else 0f, 0f)
                                val r = if (transition.targetState) 20.dp.toPx() * (1f - minOf(x, y)).coerceIn(0f, 1f) else 0f
                                if (r > 0.01f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    renderEffect = BlurEffect(r, r)
                                }
                            },
                        ) {
                            IosMenu(
                                sections = sections,
                                quickActions = quickActions,
                                onDismissRequest = menu::close,
                            )
                        }
                    },
                ) { measurables, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val margin = 16.dp.roundToPx()
                    val gap = 8.dp.roundToPx()
                    val top = statusTop.roundToPx() + 8.dp.roundToPx()
                    val bottom = h - navBottom.roundToPx() - 16.dp.roundToPx()

                    val menuPlaceable = measurables[1].measure(Constraints())
                    // Card ~78% of the width (reference), shrunk if card + menu can't fit.
                    val textBlock = 76.dp.roundToPx()
                    val ideal = minOf((w * 0.78f).roundToInt(), 340.dp.roundToPx())
                    val fit = bottom - top - gap - menuPlaceable.height - textBlock
                    val cardW = ideal.coerceAtMost(fit).coerceAtLeast(140.dp.roundToPx())
                    val card = measurables[0].measure(Constraints.fixedWidth(cardW))

                    // Keeps the item's side: leading edge unless it sat in the right half.
                    val rightSide = anchor.center.x > w / 2f
                    val cardX = (if (rightSide) anchor.right.roundToInt() - card.width else anchor.left.roundToInt())
                        .coerceIn(margin, (w - margin - card.width).coerceAtLeast(margin))
                    val cardY = anchor.top.roundToInt()
                        .coerceAtMost(bottom - card.height - gap - menuPlaceable.height)
                        .coerceAtLeast(top)
                    val menuX = (if (rightSide) cardX + card.width - menuPlaceable.width else cardX)
                        .coerceIn(margin, (w - margin - menuPlaceable.width).coerceAtLeast(margin))
                    val menuY = cardY + card.height + gap

                    menu.placed[0] = cardX.toFloat()
                    menu.placed[1] = cardY.toFloat()
                    menu.placed[2] = anchor.left - padPx
                    menu.placed[3] = anchor.top - padPx
                    menu.rightSide = rightSide
                    layout(w, h) {
                        card.place(cardX, cardY)
                        menuPlaceable.place(menuX, menuY)
                    }
                }
            }
        }
    }
}

@Composable
private fun Transition<Boolean>.menuAxis(dampingRatio: Float, stiffness: Float, label: String): State<Float> =
    animateFloat(
        transitionSpec = {
            if (targetState) spring(dampingRatio = dampingRatio, stiffness = stiffness) else tween(140)
        },
        label = label,
    ) { if (it) 1f else 0f }

/** The share of the landing travel over which the preview and the item crossfade. */
private const val LandFade = 0.3f

/** Scale used while the menu item is pressed. */
private const val PressScale = 0.96f
private val CardPadding = 10.dp

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset.Zero
}
