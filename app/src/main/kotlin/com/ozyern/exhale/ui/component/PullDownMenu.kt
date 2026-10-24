/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.ozyern.exhale.R
import kotlinx.coroutines.launch
import kotlin.math.abs

/** One row of an [ExhaleMenu]. */
data class MenuEntry(
    val text: String,
    val icon: Int? = null,
    /** The current choice, ticked. */
    val checked: Boolean = false,
    /** Drawn in red: delete, remove, sign out. */
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A choice as [ExhaleMenu] offers it, for a list of values with one current.
 *
 * It replaces a centred Material dialog of radio buttons, which asked for the whole screen to change
 * one setting. This stays next to the thing it changes and takes only the room its options need.
 */
@Composable
fun <T> PullDownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val entries = options.map { option ->
        MenuEntry(text = label(option), checked = option == selected, onClick = { onSelect(option) })
    }
    ExhaleMenu(expanded = expanded, onDismiss = onDismiss, entries = entries)
}

/**
 * The app's pop-up menu.
 *
 * It grows out of the button that opened it — from that corner, small to full size on a spring
 * that just overshoots and settles — rather than fading up in place. The rows are words on a dark
 * card, and one highlight serves all of them: it comes up under a finger, and as the finger moves
 * it slides to the row beneath, its leading edge quicker than its trailing one so it stretches a
 * little on the way. Press, slide, lift on the one you want; or just tap. A long list scrolls, and
 * then the highlight follows taps only.
 */
@Composable
fun ExhaleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    entries: List<MenuEntry>,
) {
    var present by remember { mutableStateOf(false) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            present = true
            open.snapTo(0f)
            open.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = 520f))
        } else if (present) {
            open.animateTo(0f, tween(140))
            present = false
        }
    }
    if (!present && !expanded) return

    val density = LocalDensity.current
    val gap = with(density) { 4.dp.roundToPx() }
    val edge = with(density) { 12.dp.roundToPx() }
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    val position = remember {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                if (anchor != anchorBounds) anchor = anchorBounds
                return IntOffset.Zero
            }
        }
    }
    val dismiss by rememberUpdatedState(onDismiss)

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { dismiss() },
            )
            // Which corner it grows from, worked out where it is placed and read while drawing.
            val origin = remember { floatArrayOf(1f, 0f) }
            Layout(
                content = {
                    MenuCard(
                        entries = entries,
                        modifier = Modifier.graphicsLayer {
                            val o = open.value
                            val s = 0.32f + 0.68f * o
                            scaleX = s
                            scaleY = s
                            alpha = (o * 2.2f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(origin[0], origin[1])
                        },
                        onPick = { entry ->
                            entry.onClick()
                            dismiss()
                        },
                    )
                },
            ) { measurables, constraints ->
                val card = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
                val windowW = constraints.maxWidth
                val windowH = constraints.maxHeight
                // Hung from the side of the button nearer the screen's edge, just under it — or
                // above it when there isn't room below.
                val fromRight = anchor.center.x > windowW / 2
                val x = (if (fromRight) anchor.right - card.width else anchor.left)
                    .coerceIn(edge, (windowW - card.width - edge).coerceAtLeast(edge))
                val below = anchor.bottom + gap
                val up = below + card.height > windowH - edge
                val y = if (up) (anchor.top - gap - card.height).coerceAtLeast(edge) else below
                origin[0] = if (fromRight) 1f else 0f
                origin[1] = if (up) 1f else 0f
                layout(windowW, windowH) { card.place(x, y) }
            }
        }
    }
}

@Composable
private fun MenuCard(
    entries: List<MenuEntry>,
    modifier: Modifier,
    onPick: (MenuEntry) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val card = if (dark) Color(0xFF1B1B1E) else Color(0xFFF7F7F9)
    val rim = if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val glow = if (dark) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.07f)
    val ink = if (dark) Color.White else Color(0xFF111114)
    val shape = RoundedCornerShape(MenuCornerRadius)
    val haptic = LocalHapticFeedback.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val rowPx = with(LocalDensity.current) { MenuRowHeight.toPx() }
    val padPx = with(LocalDensity.current) { MenuPadding.toPx() }

    val scroll = rememberScrollState()
    val firstChecked = entries.indexOfFirst { it.checked }
    LaunchedEffect(Unit) {
        if (firstChecked > 3) scroll.scrollTo(((firstChecked - 3) * rowPx).toInt().coerceAtMost(scroll.maxValue))
    }

    // The one highlight: its top and bottom on springs of their own, so it stretches as it travels.
    var hover by remember { mutableIntStateOf(-1) }
    val top = remember { Animatable(0f) }
    val bottom = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(hover) {
        if (hover < 0) {
            shown.animateTo(0f, tween(180))
            return@LaunchedEffect
        }
        val t = padPx + hover * rowPx
        val b = t + rowPx
        if (shown.value < 0.05f) {
            // Comes up where the finger is, not from wherever it last was.
            top.snapTo(t)
            bottom.snapTo(b)
        }
        launch { shown.animateTo(1f, tween(90)) }
        val goingDown = t > top.value
        launch { top.animateTo(t, spring(dampingRatio = 0.82f, stiffness = if (goingDown) 380f else 900f)) }
        bottom.animateTo(b, spring(dampingRatio = 0.82f, stiffness = if (goingDown) 900f else 380f))
    }
    val current by rememberUpdatedState(entries)

    Box(
        modifier
            .widthIn(min = 190.dp, max = 280.dp)
            .width(IntrinsicSize.Max)
            .androidxShadow(shape)
            // ColorOS's frosted card: a touch lighter at the top, as glass catching light, rather
            // than one flat fill.
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(card, if (dark) Color.White else Color.Black, 0.05f),
                        card,
                    ),
                ),
                shape,
            )
            .border(0.6.dp, rim, shape),
    ) {
        Column(
            Modifier
                .heightIn(max = 440.dp)
                .verticalScroll(scroll)
                .drawBehind {
                    val a = shown.value
                    if (a <= 0f) return@drawBehind
                    val inset = 6.dp.toPx()
                    val h = bottom.value - top.value
                    drawRoundRect(
                        color = glow.copy(alpha = glow.alpha * a),
                        topLeft = Offset(inset, top.value + 2.dp.toPx()),
                        size = Size(size.width - inset * 2, (h - 4.dp.toPx()).coerceAtLeast(0f)),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
                .pointerInput(Unit) {
                    fun rowAt(y: Float): Int {
                        val i = ((y - padPx) / rowPx).toInt()
                        return if (y < padPx || i !in current.indices || !current[i].enabled) -1 else i
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hover = rowAt(down.position.y)
                        val scrollable = scroll.maxValue > 0
                        var travelled = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                val picked = hover
                                if (picked >= 0) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { kotlinx.coroutines.delay(60); onPick(current[picked]) }
                                } else {
                                    hover = -1
                                }
                                break
                            }
                            travelled += abs(change.position.y - change.previousPosition.y)
                            if (scrollable && travelled > touchSlop) {
                                // A scroll, not a choice: let the list have it.
                                hover = -1
                                break
                            }
                            val next = if (change.position.x < 0 || change.position.x > size.width) -1 else rowAt(change.position.y)
                            if (next != hover) {
                                if (next >= 0) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                hover = next
                            }
                        }
                    }
                }
                .padding(vertical = MenuPadding),
        ) {
            entries.forEach { entry -> MenuRow(entry, ink) }
        }
    }
}

private fun Modifier.androidxShadow(shape: RoundedCornerShape): Modifier =
    this.then(Modifier.graphicsLayer {
        this.shape = shape
        shadowElevation = 18.dp.toPx()
        ambientShadowColor = Color.Black
        spotShadowColor = Color.Black
        clip = true
    })

private val MenuCornerRadius = 22.dp
/** ColorOS's menu rows: tighter than a list row, so the card stays compact beside its button. */
private val MenuRowHeight = 42.dp
private val MenuPadding = 6.dp

@Composable
private fun MenuRow(entry: MenuEntry, ink: Color) {
    val color = when {
        !entry.enabled -> ink.copy(alpha = 0.32f)
        entry.destructive -> Color(0xFFFF453A)
        else -> ink
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuRowHeight)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.icon != null) {
            Icon(
                painter = painterResource(entry.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = entry.text,
            fontSize = 15.5.sp,
            fontWeight = if (entry.checked) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.checked) {
            Spacer(Modifier.width(14.dp))
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
