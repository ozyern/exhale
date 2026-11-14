/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.ios.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.ui.ios.foundation.IosCapsule
import com.ozyern.exhale.ui.ios.foundation.IosGlassSpec
import com.ozyern.exhale.ui.ios.foundation.IosGlassStyle
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.TopRoundedShape
import com.ozyern.exhale.ui.ios.foundation.iosGlass
import com.ozyern.exhale.ui.utils.nearestProjectedSnap
import com.ozyern.exhale.ui.utils.projectedEndpoint
import kotlinx.coroutines.launch

/** SwiftUI's PresentationDetent. */
@Immutable
sealed interface IosDetent {
    /** Roughly half the screen — the system medium detent. */
    data object Medium : IosDetent

    /** Full height, minus the top safe inset iOS always leaves. */
    data object Large : IosDetent

    data class Height(val height: Dp) : IosDetent
    data class Fraction(val fraction: Float) : IosDetent
}

/** SwiftUI's presentationContentInteraction(_:). */
enum class IosContentInteraction {
    /** Dragging the content resizes the sheet first (the system default). */
    Resizes,

    /** Dragging scrolls the content first, so the sheet only moves at the top. */
    Scrolls,
}

/** SwiftUI's presentationBackgroundInteraction(_:). */
enum class IosBackgroundInteraction { Automatic, Enabled, Disabled }

@Immutable
class IosSheetState internal constructor(
    internal val detents: List<IosDetent>,
    internal val offset: Animatable<Float, *>,
    internal val onDismissRequest: () -> Unit,
    internal val interactiveDismissDisabled: Boolean,
) {
    /** Sheet height in px per detent, filled in once the container is measured. */
    internal var anchors: List<Float> = emptyList()
}

@Composable
fun rememberIosSheetState(
    detents: List<IosDetent> = listOf(IosDetent.Large),
    interactiveDismissDisabled: Boolean = false,
    onDismissRequest: () -> Unit = {},
): IosSheetState {
    val offset = remember { Animatable(0f) }
    return remember(detents, interactiveDismissDisabled) {
        IosSheetState(detents, offset, onDismissRequest, interactiveDismissDisabled)
    }
}

/**
 * SwiftUI's .sheet(isPresented:).
 *
 * Place it inside a Box that fills the window — it draws its own scrim and sheet on
 * top of whatever is behind it. It deliberately does not use a Dialog window: a
 * separate window cannot sample the app's backdrop, and the glass background would
 * come out empty.
 */
@Composable
fun BoxScope.IosSheet(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    detents: List<IosDetent> = listOf(IosDetent.Large),
    dragIndicatorVisible: Boolean = true,
    cornerRadius: Dp = IosMetrics.SheetCornerRadius,
    /** presentationBackground(_:) — Unspecified uses the system grouped material. */
    background: Color = Color.Unspecified,
    glassBackground: Boolean = false,
    backgroundInteraction: IosBackgroundInteraction = IosBackgroundInteraction.Automatic,
    contentInteraction: IosContentInteraction = IosContentInteraction.Resizes,
    interactiveDismissDisabled: Boolean = false,
    /** False places the sheet straight at its detent — needed for static previews. */
    animateEntrance: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!isPresented) return

    val colors = IosTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val state = rememberIosSheetState(detents, interactiveDismissDisabled, onDismissRequest)

    // Sheet translation, measured downward from the top of the container: 0 = fully
    // open, containerHeight = fully dismissed.
    val translation = state.offset
    var containerHeight by remember { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .matchParentSize()
            .then(
                if (backgroundInteraction == IosBackgroundInteraction.Enabled) {
                    Modifier
                } else {
                    // alpha is read inside graphicsLayer so the scrim re-draws with the
                    // drag instead of recomposing the whole sheet every frame.
                    // A scrim that eats taps is what blocks the content behind; iOS's
                    // .disabled and .automatic both do this for a non-resizable sheet.
                    Modifier
                        .graphicsLayer {
                            alpha = if (containerHeight <= 0f) 0f else {
                                0.32f * (1f - (translation.value / containerHeight).coerceIn(0f, 1f))
                            }
                        }
                        .background(Color.Black)
                        .pointerInput(interactiveDismissDisabled) {
                            detectTapGestures {
                                if (!interactiveDismissDisabled) onDismissRequest()
                            }
                        }
                }
            )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                val height = size.height.toFloat()
                containerHeight = height
                state.anchors = detents.map { detent ->
                    when (detent) {
                        IosDetent.Large -> height - with(density) { 24.dp.toPx() }
                        IosDetent.Medium -> height * 0.5f
                        is IosDetent.Height -> with(density) { detent.height.toPx() }
                        is IosDetent.Fraction -> height * detent.fraction.coerceIn(0f, 1f)
                    }
                }.sorted()
                if (translation.value == 0f) {
                    val opened = height - (state.anchors.firstOrNull() ?: height)
                    scope.launch { translation.snapTo(if (animateEntrance) height else opened) }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val tallest = state.anchors.maxOrNull() ?: 0f

        LaunchedEffect(isPresented, tallest) {
            if (tallest > 0f && animateEntrance) {
                val target = containerHeight - (state.anchors.firstOrNull() ?: tallest)
                translation.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 380f))
            }
        }

        val dragState = rememberDraggableState { delta ->
            scope.launch { translation.snapTo((translation.value + delta).coerceAtLeast(0f)) }
        }

        val nestedScroll = remember(contentInteraction) {
            sheetNestedScroll(
                enabled = contentInteraction == IosContentInteraction.Scrolls,
                translation = translation,
                scope = scope,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = translation.value }
                .then(if (contentInteraction == IosContentInteraction.Scrolls) Modifier.nestedScroll(nestedScroll) else Modifier)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        settleSheet(state, containerHeight, velocity)
                    },
                )
                .then(
                    if (glassBackground) {
                        Modifier.iosGlass(
                            shape = TopRoundedShape(cornerRadius),
                            style = IosGlassStyle.Regular,
                            spec = IosGlassSpec.Sheet,
                        )
                    } else {
                        Modifier
                            .clip(TopRoundedShape(cornerRadius))
                            .background(
                                if (background.isSpecified) background
                                else colors.secondarySystemGroupedBackground
                            )
                    }
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (dragIndicatorVisible) {
                Box(
                    Modifier
                        .padding(top = 6.dp, bottom = 2.dp)
                        .size(width = 36.dp, height = 5.dp)
                        .clip(IosCapsule)
                        .background(colors.tertiaryLabel)
                )
            }
            content()
        }
    }
}

/** Springs the sheet to the nearest detent, or dismisses it past the bottom one. */
private suspend fun settleSheet(state: IosSheetState, containerHeight: Float, velocity: Float) {
    if (state.anchors.isEmpty() || containerHeight <= 0f) return
    val current = state.offset.value
    val targets = state.anchors.map { containerHeight - it }

    // Project where the flick is going, pick nearest detent to that — not to release point.
    val projected = projectedEndpoint(current, velocity)
    val nearest = nearestProjectedSnap(current, velocity, targets) ?: return

    val dismissThreshold = (targets.maxOrNull() ?: 0f) + containerHeight * 0.12f
    if (!state.interactiveDismissDisabled && projected > dismissThreshold) {
        state.offset.animateTo(containerHeight, spring(dampingRatio = 1f, stiffness = 400f), initialVelocity = velocity)
        state.onDismissRequest()
        return
    }
    state.offset.animateTo(nearest, spring(dampingRatio = 0.86f, stiffness = 380f), initialVelocity = velocity)
}

/**
 * With presentationContentInteraction(.scrolls), the sheet only moves once the inner
 * scroll is at its top and the drag is still heading down.
 */
private fun sheetNestedScroll(
    enabled: Boolean,
    translation: Animatable<Float, *>,
    scope: kotlinx.coroutines.CoroutineScope,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled || available.y <= 0f) return Offset.Zero
        scope.launch { translation.snapTo(translation.value + available.y) }
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero
}

