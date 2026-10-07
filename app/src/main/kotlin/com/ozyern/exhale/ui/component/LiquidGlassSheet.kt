/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ozyern.exhale.constants.AquamorphicDampingRatio
import com.ozyern.exhale.constants.AquamorphicStiffness
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Top corner radius of every frosted sheet. Deep enough to read as iOS rather than Material. */
/**
 * The sheet is an island, not a drawer.
 *
 * It used to be the standard bottom sheet: full width, welded to the bottom edge, square at the
 * foot and rounded only at the head. That is Material's shape, and it is the one surface in the app
 * that was still wearing it - the dock, the now-playing pill and the search row are all floating
 * capsules with a margin of their own, and a sheet that runs into the screen edge underneath them
 * reads as a different app's component borrowed for the occasion.
 *
 * So it sits inset, with all four corners equal and the same rim as the rest of the chrome.
 */
private val SheetCornerRadius = 34.dp

/** How far the island holds off the screen edges. */
private val SheetSideMargin = 12.dp
private val SheetBottomMargin = 16.dp

/**
 * Radius of the **real** blur applied to everything behind the sheet's window, in pixels.
 *
 * Not a `Dp`: `WindowManager.LayoutParams.blurBehindRadius` is specified in raw pixels by the
 * platform, and this is a physical light effect rather than a piece of layout — the amount of
 * scattering that reads as "frosted" does not want to scale with the user's font-size setting.
 */
private const val BlurBehindRadiusPx = 56

/** How dark the app behind the sheet gets when the sheet is fully seated. */
private const val ScrimDimAmount = 0.28f

/**
 * The app's signature frosted-glass bottom sheet.
 *
 * This is the chrome only — the rising spring entrance and matching exit, the real blur behind the
 * window, the layered translucent gradient, the bright hairline along the top rim, the grab handle
 * you can throw the sheet away with, and the scrim tap-to-dismiss. Callers supply the contents.
 *
 * It exists because the account sheet and the update sheet were otherwise going to carry two
 * independent copies of the same forty lines of gradient and border, which is exactly how two
 * surfaces that are supposed to be the *same material* drift apart. Anything presented as a sheet
 * should come through here.
 *
 * Deliberately not a Material `ModalBottomSheet`: that paints an opaque `surfaceContainer` with a
 * tonal elevation overlay, which defeats the translucency the whole look depends on, and its
 * entrance is a fixed Material easing rather than the app's shared spring.
 *
 * ### Why the glass here is a window flag and not [rememberChromeGlassModifier]
 *
 * A sheet lives in its own `Dialog` window. `LocalAppBackdrop` is a `GraphicsLayer` recording of
 * the *main* window's NavHost, so sampling it from inside a second window would be reading another
 * window's pixels at coordinates that no longer mean anything — the dock can do it only because it
 * is a Scaffold slot in the same window. What a separate window has instead is
 * `FLAG_BLUR_BEHIND`, which asks the compositor to blur everything already on screen underneath
 * it. That is the same effect the dock fakes, done by the system, across the whole display.
 *
 * The platform can refuse: cross-window blur is off on low-end devices, in battery saver, and
 * behind a developer setting. So the surface gradient is chosen from whether the blur actually
 * took — translucent enough to show it off when it is live, milky enough to stay legible when it
 * is not.
 *
 * @param onDismiss invoked on scrim tap, back press, and handle dismissal — **after** the closing
 *   animation has finished, so callers can keep doing the simple `if (show) { Sheet() }` thing and
 *   still get an exit.
 * @param dismissible when false the sheet ignores every dismissal route. Used while an operation
 *   the user must not interrupt is in flight (an in-progress download); the grab handle is hidden
 *   too, so the sheet also *looks* modal rather than merely refusing to close.
 * @param maxHeightFraction cap on the sheet's height as a fraction of the available window.
 */
@Composable
fun LiquidGlassSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissible: Boolean = true,
    maxHeightFraction: Float = 0.86f,
    /**
     * Where on screen a hold opened this, for a popup in place of a sheet: the panel opens out of
     * that point — scaled up from it, the page blurring behind — and sits beside the finger
     * rather than at the bottom edge, the way Apple Music answers a long press. Null for a sheet.
     */
    anchor: androidx.compose.ui.geometry.Offset? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 0 = fully off the bottom edge, 1 = seated. Everything the sheet does to the window — the
    // dim, the blur — is a function of this one number, so the whole presentation moves together.
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(1f) }

    val dismissAnimated: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                // Linear-out rather than the entrance spring: a sheet being sent away should not
                // look like it is having second thoughts on the way down.
                progress.animateTo(0f, tween(durationMillis = 200, easing = FastOutLinearInEasing))
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = { if (dismissible) dismissAnimated() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = dismissible,
            dismissOnBackPress = dismissible,
        ),
    ) {
        val scheme = MaterialTheme.colorScheme
        val sheetShape = RoundedCornerShape(SheetCornerRadius)
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window

        // Did the compositor agree to blur? Decides the surface's own opacity, below.
        val blurLive = remember(window) {
            window != null &&
                view.context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        }

        LaunchedEffect(window) { progress.animateTo(1f, SheetEntranceSpring) }

        // Dim and blur ride the same value the sheet does, so dragging it halfway down lightens
        // the room by half rather than holding a hard scrim until the window is torn down.
        //
        // Quantised to twentieths on purpose: each of these is a `WindowManager` relayout, and the
        // eye cannot tell 20 steps of a 200ms ramp from 120 of them.
        LaunchedEffect(window, blurLive) {
            if (window == null) return@LaunchedEffect
            // Both flags are set explicitly rather than inherited: `dimAmount` is ignored
            // unless the window owns the dim, and taking the dim over from the theme is the
            // whole point — a scrim that snaps on and off around an animated sheet is worse
            // than no scrim at all.
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
            )
            snapshotFlow {
                val dragged = (dragPx / sheetHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
                (progress.value * (1f - dragged) * 20f).roundToInt() / 20f
            }
                .distinctUntilChanged()
                .collect { shown ->
                    window.attributes = window.attributes.also { params ->
                        params.dimAmount = ScrimDimAmount * shown
                        params.blurBehindRadius = (BlurBehindRadiusPx * shown).roundToInt()
                    }
                }
        }

        val draggableState = rememberDraggableState { delta ->
            // Downward is free; upward is mostly refused, so the sheet can be nudged but never
            // dragged off the top of its own resting position.
            val resisted = if (dragPx <= 0f && delta < 0f) delta * 0.25f else delta
            dragPx = (dragPx + resisted).coerceAtLeast(-24f)
        }

        var originOnScreen by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { originOnScreen = it.localToScreen(androidx.compose.ui.geometry.Offset.Zero) }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { if (dismissible) dismissAnimated() },
            contentAlignment = if (anchor != null) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            BoxWithConstraints {
                val boxHeightPx = constraints.maxHeight.toFloat()
                // The held point, in this window's own coordinates.
                val held = anchor?.let { it - originOnScreen }
                val density = androidx.compose.ui.platform.LocalDensity.current
                val marginPx = with(density) { 16.dp.toPx() }
                val sideMarginPx = with(density) { SheetSideMargin.toPx() }
                Column(
                    modifier = modifier
                        .fillMaxWidth()
                        .then(if (held == null) Modifier.navigationBarsPadding() else Modifier)
                        .padding(
                            start = SheetSideMargin,
                            end = SheetSideMargin,
                            bottom = if (held == null) SheetBottomMargin else 0.dp,
                        )
                        .heightIn(max = maxHeight * maxHeightFraction)
                        .onSizeChanged { sheetHeightPx = it.height.toFloat() }
                        .then(
                            if (held == null) {
                                Modifier
                            } else {
                                // Just under the finger, kept wholly on screen.
                                Modifier.offset {
                                    androidx.compose.ui.unit.IntOffset(
                                        0,
                                        (held.y - marginPx * 2f)
                                            .coerceAtMost(boxHeightPx - sheetHeightPx - marginPx * 3f)
                                            .coerceAtLeast(marginPx * 3f)
                                            .roundToInt(),
                                    )
                                }
                            },
                        )
                        // Translate in the draw phase — animating an offset or padding here would
                        // relayout the whole sheet on every frame of the entrance.
                        .graphicsLayer {
                            if (held == null) {
                                translationY = (1f - progress.value) * size.height + dragPx
                            } else {
                                // Out of the held point: scaled up from it and faded in, rather
                                // than slid up from the bottom edge.
                                val p = progress.value
                                val top = (held.y - marginPx * 2f)
                                    .coerceAtMost(boxHeightPx - sheetHeightPx - marginPx * 3f)
                                    .coerceAtLeast(marginPx * 3f)
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    ((held.x - sideMarginPx) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                    ((held.y - top) / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                                )
                                val scale = 0.6f + 0.4f * p
                                scaleX = scale
                                scaleY = scale
                                alpha = p.coerceIn(0f, 1f)
                            }
                        }
                        .clip(sheetShape)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (blurLive) {
                                    // There is a real blur behind this now. At the old opacity it
                                    // was doing all the work for nothing — the sheet was very
                                    // nearly solid and the blur never showed through it.
                                    listOf(
                                        scheme.surfaceContainerHigh.copy(alpha = 0.80f),
                                        scheme.surfaceContainer.copy(alpha = 0.72f),
                                    )
                                } else {
                                    listOf(
                                        scheme.surfaceContainerHigh.copy(alpha = 0.96f),
                                        scheme.surfaceContainer.copy(alpha = 0.92f),
                                    )
                                },
                            ),
                        )
                        // The hairline is what sells it as glass: a bright rim catching light at
                        // the top edge, fading to nothing by the bottom.
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.24f),
                                    Color.White.copy(alpha = 0.03f),
                                ),
                            ),
                            shape = sheetShape,
                        )
                        // Swallow taps so anything inside the sheet never reaches the scrim.
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {},
                ) {
                    if (dismissible && anchor == null) {
                        // The handle is a control, not a decoration.
                        //
                        // It looked exactly like this before and did nothing: every sheet in every
                        // other app on the phone can be thrown away with a flick, and this one
                        // drew the affordance for it and then made you reach for the scrim. The
                        // drag lives on the handle strip alone because the sheet's body is a
                        // scrolling column — a drag handler over both would have to arbitrate
                        // between "close me" and "scroll me" on every gesture, and gets it wrong
                        // the moment the content is already scrolled.
                        var dragging by remember { mutableStateOf(false) }
                        val handleWidth by animateDpAsState(
                            targetValue = if (dragging) 56.dp else 40.dp,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
                            label = "sheetHandleWidth",
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .draggable(
                                    state = draggableState,
                                    orientation = Orientation.Vertical,
                                    onDragStarted = { dragging = true },
                                    onDragStopped = { velocity ->
                                        dragging = false
                                        val height = sheetHeightPx.coerceAtLeast(1f)
                                        // Either far enough or fast enough — a short, quick flick
                                        // is a dismissal even though it never travelled, which is
                                        // the whole reason people flick instead of dragging.
                                        if (dragPx > height * 0.22f || velocity > 1400f) {
                                            dismissAnimated()
                                        } else {
                                            animate(
                                                initialValue = dragPx,
                                                targetValue = 0f,
                                                initialVelocity = velocity,
                                                animationSpec = spring(
                                                    dampingRatio = 0.72f,
                                                    stiffness = 520f,
                                                ),
                                            ) { value, _ -> dragPx = value }
                                        }
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = handleWidth, height = 4.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        scheme.onSurfaceVariant.copy(
                                            alpha = if (dragging) 0.55f else 0.35f,
                                        ),
                                    ),
                            )
                        }
                    }

                    content()
                }
            }
        }
    }
}

private val SheetEntranceSpring = spring<Float>(
    dampingRatio = AquamorphicDampingRatio,
    stiffness = AquamorphicStiffness,
)

