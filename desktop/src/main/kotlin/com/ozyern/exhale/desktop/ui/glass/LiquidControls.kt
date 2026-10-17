/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * The toggle, slider and button follow the components in Kyant's AndroidLiquidGlass catalog
 * (github.com/Kyant0/AndroidLiquidGlass, Apache-2.0) — the same glass the Android app's controls are
 * made of. The desktop parts are Exhale's: the glass answers the pointer hovering as well as pressing,
 * the slider takes the mouse wheel and snaps to its steps, and at rest no control blurs or bends
 * anything, so a settings page of them costs no more to draw than flat ones.
 */

@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.ozyern.exhale.desktop.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The track's colour off, as dark iOS switches have it. */
private val TrackOff = Color(0xFF787880).copy(alpha = 0.36f)

/** What the controls refract: the page's own ground when it publishes one, nothing otherwise. */
@Composable
private fun pageBackdrop(): Backdrop = LocalPageBackdrop.current ?: emptyBackdrop()

// ============================================================================================
// The motion
// ============================================================================================

/**
 * One value pulled by the pointer, with the press, the stretch and the squash that make the thumb
 * read as a drop of liquid: it swells when held, smears in the direction it travels by how fast it is
 * going, and settles without a wobble when let go. Hovering raises it part of the way, so on a desktop
 * the glass answers the pointer before the click.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    val valueRange: ClosedRange<Float>,
    private val visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueSpec = spring(1f, 1000f, visibilityThreshold)
    private val velocitySpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressSpec = spring(1f, 1000f, 0.001f)
    private val scaleXSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val mutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    private var pressed = false
    private var hovered = false

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            },
        ) { _, dragAmount -> onDrag(size, dragAmount) }
    }

    /** The pointer over it, without a button down: the glass lifts a little to say it can be taken. */
    fun hover(over: Boolean) {
        hovered = over
        if (pressed) return
        val target = if (over) HoverLift else 0f
        val scale = if (over) lerp(initialScale, pressedScale, HoverLift * 0.5f) else initialScale
        animationScope.launch {
            launch { pressAnimation.animateTo(target, pressSpec) }
            launch { scaleXAnimation.animateTo(scale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(scale, scaleYSpec) }
        }
    }

    fun press() {
        pressed = true
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYSpec) }
        }
    }

    fun release() {
        pressed = false
        animationScope.launch {
            // Held until the value has nearly arrived, so the drop lands before it hardens.
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }.filter { abs(it - valueAnimation.targetValue) < threshold }.first()
            }
            val rest = if (hovered) HoverLift else 0f
            val scale = if (hovered) lerp(initialScale, pressedScale, HoverLift * 0.5f) else initialScale
            launch { pressAnimation.animateTo(rest, pressSpec) }
            launch { scaleXAnimation.animateTo(scale, scaleXSpec) }
            launch { scaleYAnimation.animateTo(scale, scaleYSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch { valueAnimation.animateTo(target, valueSpec) { updateVelocity() } }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutex.mutate {
                press()
                launch { valueAnimation.animateTo(value.coerceIn(valueRange), valueSpec) }
                if (velocity != 0f) launch { velocityAnimation.animateTo(0f, velocitySpec) }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val target = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(target, velocitySpec) }
    }

    private companion object {
        /** How far hovering lifts the glass toward a press. */
        const val HoverLift = 0.35f
    }
}

/** Every drag and tap, reported from the first contact — a drag detector would swallow the press. */
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down)
        onDrag(initialDown, Offset.Zero)
        val up = trackDrag(initialDown.id) { onDrag(it, it.positionChange()) }
        if (up == null) onDragCancel() else onDragEnd(up)
    }
}

private suspend inline fun AwaitPointerEventScope.trackDrag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) return null
        if (change.changedToUpIgnoreConsumed()) return change
        onDrag(change)
        pointer = change.id
    }
}

private suspend fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed } ?: return change
            pointer = otherDown.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}

// ============================================================================================
// Toggle
// ============================================================================================

/**
 * A switch whose thumb is a drop of glass: tap it, or drag it across. Held, it swells and turns clear,
 * bending the track and the page behind it; let go, it hardens back to white on the side it landed.
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF30D158),
    enabled: Boolean = true,
) {
    val backdrop = pageBackdrop()
    val selected by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)
    val density = LocalDensity.current
    val dragWidth = with(density) { 20.dp.toPx() }
    val scope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val drag = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                fraction = if (didDrag) (if (targetValue >= 0.5f) 1f else 0f) else (if (selected) 0f else 1f)
                didDrag = false
                if ((fraction == 1f) != selected) onChange(fraction == 1f)
            },
            onDrag = { _, amount ->
                if (!didDrag) didDrag = amount.x != 0f
                fraction = (fraction + amount.x / dragWidth).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(drag) { snapshotFlow { fraction }.collectLatest { drag.updateValue(it) } }
    LaunchedEffect(drag) {
        snapshotFlow { selected }.collectLatest { on ->
            val target = if (on) 1f else 0f
            if (target != fraction) {
                fraction = target
                drag.animateToValue(target)
            }
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { drag.hover(hovered && enabled) }

    val trackBackdrop = rememberLayerBackdrop()
    Box(
        modifier
            .size(64.dp, 28.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .hoverable(interaction, enabled),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(CircleShape)
                .drawBehind { drawRect(lerp(TrackOff, accent, drag.value)) }
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    val padding = 2.dp.toPx()
                    translationX = lerp(padding, padding + dragWidth, drag.value)
                }
                .semantics { role = Role.Switch }
                .then(if (enabled) drag.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawTrack ->
                            val progress = drag.pressProgress
                            scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
                        },
                    ),
                    shape = { CircleShape },
                    effects = {
                        // At rest the thumb is solid white over all of this; nothing to blur or bend.
                        val progress = drag.pressProgress
                        if (progress > 0.001f) {
                            blur(8.dp.toPx() * (1f - progress))
                            lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
                        }
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = drag.pressProgress,
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.12f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        val velocity = drag.velocity / 50f
                        scaleX = drag.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                        scaleY = drag.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - drag.pressProgress)) },
                )
                .size(40.dp, 24.dp),
        )
    }
}

// ============================================================================================
// Slider
// ============================================================================================

/**
 * A slider whose thumb is a drop of glass that magnifies the track under it while held. Click
 * anywhere on the track to send it there, drag it, or turn the mouse wheel over it. [steps] snaps
 * as Material's slider does; [onValueChangeFinished] is called once a change is let go of.
 */
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    accent: Color = Color(0xFF0091FF),
    onValueChangeFinished: () -> Unit = {},
) {
    val backdrop = pageBackdrop()
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val span = valueRange.endInclusive - valueRange.start
    fun snap(v: Float): Float {
        val clamped = v.coerceIn(valueRange)
        if (steps <= 0) return clamped
        val step = span / (steps + 1)
        return (valueRange.start + ((clamped - valueRange.start) / step).fastRoundToInt() * step).coerceIn(valueRange)
    }
    val trackBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.CenterStart) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val scope = rememberCoroutineScope()
        // Where the drag has got to, unsnapped: snapping each move would stick the thumb to the steps.
        var raw by remember { mutableFloatStateOf(value) }
        var didDrag by remember { mutableStateOf(false) }
        val drag = remember(scope, trackWidth) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = value,
                valueRange = valueRange,
                visibilityThreshold = span / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { raw = current },
                onDragStopped = {
                    if (didDrag) finished()
                    didDrag = false
                },
                onDrag = { _, amount ->
                    if (!didDrag) didDrag = amount.x != 0f
                    raw = (raw + span * amount.x / trackWidth).coerceIn(valueRange)
                    change(snap(raw))
                },
            )
        }
        LaunchedEffect(drag) {
            snapshotFlow { current }.collectLatest { if (drag.targetValue != it) drag.updateValue(it) }
        }
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        LaunchedEffect(hovered) { drag.hover(hovered) }

        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .hoverable(interaction)
                // A notch of the wheel is a step, or a twentieth of the range when there are none.
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta ?: return@onPointerEvent
                    val amount = -(delta.y.takeIf { it != 0f } ?: -delta.x)
                    if (amount == 0f) return@onPointerEvent
                    val step = if (steps > 0) span / (steps + 1) else span / 20f
                    val next = snap(current + step * kotlin.math.sign(amount))
                    if (next != current) {
                        change(next)
                        finished()
                    }
                    event.changes.forEach { it.consume() }
                }
                .pointerInput(scope, trackWidth) {
                    detectTapGestures { position ->
                        val target = snap(valueRange.start + span * (position.x / trackWidth))
                        drag.animateToValue(target)
                        change(target)
                        finished()
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.layerBackdrop(trackBackdrop)) {
                Box(Modifier.clip(CircleShape).background(TrackOff).height(6.dp).fillMaxWidth())
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(accent)
                        .height(6.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * drag.progress.fastCoerceIn(0f, 1f)).fastRoundToInt()
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        },
                )
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (-size.width / 2f + trackWidth * drag.progress)
                        .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f)
                }
                .hoverable(interaction)
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawTrack ->
                            val progress = drag.pressProgress
                            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawTrack() }
                        },
                    ),
                    shape = { CircleShape },
                    effects = {
                        val progress = drag.pressProgress
                        if (progress > 0.001f) {
                            blur(8.dp.toPx() * (1f - progress))
                            lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                        }
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = drag.pressProgress,
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.12f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        val velocity = drag.velocity / 10f
                        scaleX = drag.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                        scaleY = drag.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - drag.pressProgress)) },
                )
                .size(40.dp, 24.dp),
        )
    }
}

// ============================================================================================
// Segmented control
// ============================================================================================

/**
 * A segmented control whose selection is a pill you can click to or drag along: held, it becomes a
 * drop of glass that magnifies the labels under it as it slides, and settles on the nearest option
 * when let go. Segments share one width — the widest label's — as iOS's do. At rest it is a plain
 * coloured pill under the labels; the glass is only drawn while it is held or hovered.
 */
@Composable
fun LiquidSegmented(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Color(0xFF0091FF),
    height: androidx.compose.ui.unit.Dp = 36.dp,
    /** Stretch across the space given, segments sharing it, instead of sizing to the widest label. */
    fill: Boolean = false,
) {
    if (labels.isEmpty()) return
    val backdrop = pageBackdrop()
    val count = labels.size
    val current by rememberUpdatedState(selected.coerceIn(0, count - 1))
    val choose by rememberUpdatedState(onSelect)
    val scope = rememberCoroutineScope()
    val labelsBackdrop = rememberLayerBackdrop()
    var segmentWidth by remember { mutableFloatStateOf(0f) }
    val drag = remember(scope, count) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = selected.coerceIn(0, count - 1).toFloat(),
            valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.12f,
            onDragStarted = {},
            onDragStopped = {
                val index = targetValue.fastRoundToInt().coerceIn(0, count - 1)
                animateToValue(index.toFloat())
                if (index != current) choose(index)
            },
            onDrag = { _, amount ->
                if (segmentWidth > 0f) updateValue((targetValue + amount.x / segmentWidth).fastCoerceIn(0f, (count - 1).toFloat()))
            },
        )
    }
    LaunchedEffect(drag) {
        snapshotFlow { current }.collectLatest { index -> if (drag.targetValue.fastRoundToInt() != index) drag.animateToValue(index.toFloat()) }
    }
    val onAccent = if (accent.red * 0.299f + accent.green * 0.587f + accent.blue * 0.114f > 0.6f) Color.Black else Color.White
    val pad = 3.dp
    Box(
        modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .padding(pad),
    ) {
        // The coloured pill at rest, under the labels. It fades as the glass takes over.
        Box(
            Modifier
                .graphicsLayer {
                    translationX = drag.value * segmentWidth
                    alpha = (1f - drag.pressProgress) * (if (selected in 0 until count) 1f else 0f)
                }
                .layout { measurable, constraints ->
                    val w = segmentWidth.fastRoundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, constraints.maxHeight))
                    layout(w, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .clip(CircleShape)
                .background(accent),
        )
        androidx.compose.foundation.layout.Row(
            Modifier
                .layerBackdrop(labelsBackdrop)
                .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Max))
                .fillMaxHeight(),
        ) {
            labels.forEachIndexed { index, label ->
                // Each label's colour follows how much of the pill is under it, not a switch at the end.
                val cover = { (1f - abs(drag.value - index)).fastCoerceIn(0f, 1f) * (1f - drag.pressProgress) }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onSizeChanged { if (index == 0) segmentWidth = it.width.toFloat() }
                        .clip(CircleShape)
                        .pointerInput(index) { detectTapGestures { if (index != current) choose(index) } }
                        .padding(horizontal = if (fill) 4.dp else 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Shrinks to fit rather than cutting a word off when the segments are narrow.
                    val base = if (fill) androidx.compose.material3.MaterialTheme.typography.labelMedium else androidx.compose.material3.MaterialTheme.typography.labelLarge
                    androidx.compose.foundation.text.BasicText(
                        label,
                        style = base.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = lerp(Color.White, onAccent, cover())),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = base.fontSize),
                    )
                }
            }
        }
        // The glass: only drawn while held or hovered, and dragged by the pointer.
        Box(
            Modifier
                .graphicsLayer { translationX = drag.value * segmentWidth }
                .layout { measurable, constraints ->
                    val w = segmentWidth.fastRoundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, constraints.maxHeight))
                    layout(w, constraints.maxHeight) { placeable.place(0, 0) }
                }
                .then(drag.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, labelsBackdrop),
                    shape = { CircleShape },
                    effects = {
                        val progress = drag.pressProgress
                        if (progress > 0.001f) lens(8.dp.toPx() * progress, 12.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    shadow = { Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.18f * drag.pressProgress)) },
                    innerShadow = { InnerShadow(radius = 6.dp * drag.pressProgress, alpha = drag.pressProgress) },
                    layerBlock = {
                        alpha = if (drag.pressProgress > 0.001f) 1f else 0f
                        val velocity = drag.velocity / 10f
                        scaleX = drag.scaleX / (1f - (velocity * 0.6f).fastCoerceIn(-0.2f, 0.2f))
                        scaleY = drag.scaleY * (1f - (velocity * 0.2f).fastCoerceIn(-0.2f, 0.2f))
                    },
                    onDrawSurface = { drawRect(accent.copy(alpha = 0.18f)) },
                ),
        )
    }
}

// ============================================================================================
// Button
// ============================================================================================

/**
 * A capsule of glass, after the catalog's LiquidButton: held, it swells and leans after the pointer
 * like something soft being pushed; on a desktop a soft light also follows the pointer across it while
 * it hovers, so a button answers before it is clicked. [tint] colours the glass (an accent button).
 */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val backdrop = pageBackdrop()
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f, 0.001f) }
    val glow = remember { Animatable(0f, 0.001f) }
    var pointer by remember { mutableStateOf(Offset.Unspecified) }
    var pressAt by remember { mutableStateOf(Offset.Zero) }
    val lean = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val click by rememberUpdatedState(onClick)
    androidx.compose.foundation.layout.Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { Highlight.Default.copy(alpha = 0.6f + 0.4f * press.value) },
                shadow = { Shadow(radius = 10.dp, color = Color.Black.copy(alpha = 0.22f)) },
                layerBlock = {
                    // Swells a few pixels when held and leans after the pointer, never far.
                    val scale = lerp(1f, 1f + 4.dp.toPx() / size.height, press.value)
                    val reach = size.minDimension
                    val offset = lean.value
                    translationX = reach * kotlin.math.tanh(0.05f * offset.x / reach)
                    translationY = reach * kotlin.math.tanh(0.05f * offset.y / reach)
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = {
                    if (tint != Color.Unspecified) {
                        drawRect(tint, blendMode = androidx.compose.ui.graphics.BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    } else {
                        drawRect(Color.White.copy(alpha = 0.06f))
                    }
                    // The light under the pointer.
                    val g = glow.value
                    if (g > 0.001f && pointer.isSpecified) {
                        drawRect(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.22f * g), Color.Transparent),
                                center = pointer,
                                radius = size.minDimension * 1.6f,
                            ),
                            blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
                        )
                    }
                },
            )
            .then(
                if (!enabled) Modifier else Modifier
                    .onPointerEvent(PointerEventType.Enter) { e ->
                        pointer = e.changes.first().position
                        scope.launch { glow.animateTo(1f, spring(1f, 400f)) }
                    }
                    .onPointerEvent(PointerEventType.Move) { e -> pointer = e.changes.first().position }
                    .onPointerEvent(PointerEventType.Exit) { scope.launch { glow.animateTo(0f, spring(1f, 300f)) } }
                    .pointerInput(Unit) {
                        inspectDragGestures(
                            onDragStart = { down ->
                                pressAt = down.position
                                scope.launch { press.animateTo(1f, spring(0.5f, 300f)) }
                            },
                            onDragEnd = { up ->
                                scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
                                scope.launch { lean.animateTo(Offset.Zero, spring(0.5f, 300f)) }
                                // A click is a release still on the button.
                                if (up.position.x in 0f..size.width.toFloat() && up.position.y in 0f..size.height.toFloat()) click()
                            },
                            onDragCancel = {
                                scope.launch { press.animateTo(0f, spring(0.5f, 300f)) }
                                scope.launch { lean.animateTo(Offset.Zero, spring(0.5f, 300f)) }
                            },
                        ) { change, _ -> scope.launch { lean.snapTo(change.position - pressAt) } }
                    },
            )
            .semantics { role = Role.Button }
            .height(40.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
