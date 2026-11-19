/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component.classicdock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.ozyern.exhale.ui.component.LocalAppBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.layerBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberCombinedBackdrop
import com.ozyern.exhale.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.ui.component.backdrop.drawBackdrop
import com.ozyern.exhale.ui.component.backdrop.effects.blur
import com.ozyern.exhale.ui.component.backdrop.effects.lens
import com.ozyern.exhale.ui.component.backdrop.effects.vibrancy
import com.ozyern.exhale.ui.component.backdrop.highlight.Highlight
import com.ozyern.exhale.ui.component.backdrop.isRuntimeShaderSupported
import com.ozyern.exhale.ui.component.backdrop.shadow.InnerShadow
import com.ozyern.exhale.ui.component.backdrop.shadow.Shadow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.sign

/**
 * The classic dock's open tab bar, from master: one glass pill with a capsule riding on the selected
 * tab. Press anywhere and the capsule swells and glides to your finger, then rides it; let go and it
 * lands on the nearest tab, and only then does the page change. A route change from elsewhere gives
 * the same press, slide and settle. Falls back to a flat pill on devices with no runtime shaders.
 *
 * Adapted to the classic dock's own [FoldingDockTab]s rather than master's screen list, so it needs
 * nothing from master's navigation model.
 */
@Composable
internal fun ClassicLiquidTabBar(
    tabs: List<FoldingDockTab>,
    selectedKey: String?,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
) {
    if (tabs.isEmpty()) return
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val shape = remember { RoundedCornerShape(percent = 50) }
    val glassy = remember { isRuntimeShaderSupported() }
    val isDark = isSystemInDarkTheme()

    val lastIndex = (tabs.size - 1).coerceAtLeast(0)
    // -1 when no tab is selected (decides tap versus re-tap); the capsule always rests somewhere.
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
    val capsuleIndex = selectedIndex.coerceIn(0, lastIndex)

    val showLabels = rememberShowLabels(tabs.size)

    var totalWidthPx by remember { mutableFloatStateOf(0f) }
    var tabWidthPx by remember { mutableFloatStateOf(0f) }

    // 64dp of bar minus 4dp top and bottom is the capsule's 56dp.
    val inset = 4.dp
    val insetPx = with(density) { inset.toPx() }
    val capsuleHeight = height - inset * 2

    // The bar is tugged a few dp by the drag, and let go on one spring at release.
    val tug = remember { mutableFloatStateOf(0f) }
    val tugRelease = remember { arrayOfNulls<Job>(1) }
    val tugPx = with(density) { 4.dp.toPx() }
    val panelOffset: () -> Float = {
        val raw = tug.floatValue
        if (totalWidthPx == 0f || raw == 0f) {
            0f
        } else {
            val fraction = (raw / totalWidthPx).fastCoerceIn(-1f, 1f)
            tugPx * fraction.sign * EaseOut.transform(abs(fraction))
        }
    }

    val selectedIndexState by rememberUpdatedState(selectedIndex)
    val tabsState by rememberUpdatedState(tabs)
    val moved = remember { mutableFloatStateOf(0f) }
    val tapSlopPx = with(density) { 12.dp.toPx() }

    val drag = remember(scope, tabs.size, isLtr) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = capsuleIndex.toFloat(),
            valueRange = 0f..lastIndex.toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // 78dp of swell on a 56dp capsule: it bulges past the bar while held, a drop of liquid.
            pressedScale = 78f / 56f,
            onDragStarted = { position ->
                moved.floatValue = 0f
                tugRelease[0]?.cancel()
                val fromStart = if (isLtr) position.x else totalWidthPx - position.x
                val under = if (tabWidthPx > 0f) (fromStart - insetPx) / tabWidthPx - 0.5f else value
                if (abs(under - value) >= 0.5f) followTo(under.fastCoerceIn(0f, lastIndex.toFloat()))
            },
            onDragStopped = {
                val landed = targetValue.fastRoundToInt().fastCoerceIn(0, lastIndex)
                updateValue(landed.toFloat())
                val tugFrom = tug.floatValue
                if (tugFrom != 0f) {
                    tugRelease[0] = scope.launch {
                        Animatable(tugFrom).animateTo(0f, spring(1f, 300f, 0.5f)) { tug.floatValue = value }
                    }
                }
                val tab = tabsState.getOrNull(landed)
                if (tab != null) {
                    if (landed != selectedIndexState) {
                        // Let the capsule travel before the next page is built, which holds the main
                        // thread; capped, so a slow frame never makes the tap feel ignored.
                        scope.launch {
                            withTimeoutOrNull(TabNavigateMaxWaitMs) {
                                snapshotFlow { abs(value - landed) < TabNavigateArrival }.first { it }
                            }
                            tab.onClick()
                        }
                    } else if (moved.floatValue < tapSlopPx) {
                        // A tap on the capsule itself: a re-tap, which the host answers by scrolling up.
                        tab.onClick()
                    }
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    moved.floatValue += abs(dragAmount.x)
                    val before = targetValue
                    val after = (before + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, lastIndex.toFloat())
                    if (after.fastRoundToInt() != before.fastRoundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    followTo(after)
                    tug.floatValue += dragAmount.x
                }
            },
            velocityDampingRatio = 1f,
        )
    }

    // Route changes from elsewhere move the capsule with the same press, slide and settle.
    LaunchedEffect(drag, capsuleIndex) {
        val target = capsuleIndex.toFloat()
        if (abs(drag.targetValue - target) > 0.001f) drag.animateToValue(target)
    }

    val appBackdrop = LocalAppBackdrop.current
    val tabsBackdrop = rememberLayerBackdrop()
    val capsuleBackdrop = rememberCombinedBackdrop(appBackdrop, tabsBackdrop)
    val restWash = if (isDark) Color(0xFF1C1B1C) else Color(0xFFF2F2F2)
    val selectedInk = MaterialTheme.colorScheme.primary
    val restInk = clearGlassContentColor().copy(alpha = 0.6f)

    Box(
        modifier = modifier.height(height).then(drag.modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        // The pane, and the row on it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val width = coords.size.width.toFloat()
                    if (totalWidthPx != width) {
                        totalWidthPx = width
                        tabWidthPx = ((width - insetPx * 2f) / tabs.size).coerceAtLeast(0f)
                    }
                }
                .graphicsLayer { translationX = panelOffset() },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .dockGlass(shape)
                    // While held, the pane lights a little and a soft glow follows the capsule.
                    .drawWithContent {
                        drawContent()
                        val press = drag.pressProgress
                        if (press > 0.01f && tabWidthPx > 0f) {
                            val x = insetPx + (drag.value + 0.5f) * tabWidthPx
                            val cx = if (isLtr) x else size.width - x
                            val outline = shape.createOutline(size, layoutDirection, this)
                            clipPath(Path().apply { addOutline(outline) }) {
                                drawRect(Color.White.copy(alpha = 0.06f * press), blendMode = BlendMode.Plus)
                                drawRect(
                                    Brush.radialGradient(
                                        listOf(Color.White.copy(alpha = 0.16f * press), Color.Transparent),
                                        center = Offset(cx, size.height / 2f),
                                        radius = size.height * 1.5f,
                                    ),
                                    blendMode = BlendMode.Plus,
                                )
                            }
                        }
                    },
            )
            // The lit tab is the one the capsule is over, so the two never disagree mid-navigation.
            val inkIndex by remember(tabs.size) {
                derivedStateOf { drag.value.fastRoundToInt().coerceIn(0, lastIndex) }
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(inset),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    LiquidTab(
                        tab = tab,
                        tint = if (selectedIndex >= 0 && index == inkIndex) selectedInk else restInk,
                        filled = index == selectedIndex,
                        showLabel = showLabels,
                        scaleProvider = { if (index == capsuleIndex) lerp(1f, 1.12f, drag.pressProgress) else 1f },
                        onClick = { tab.onClick() },
                        gestureOwnedByBar = true,
                    )
                }
            }
        }

        // The twin the capsule reads: a sample of the page with the tabs on it, never seen directly.
        // Held, the capsule magnifies this, which is why it clears to bright glass.
        if (glassy) {
            Row(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset() }
                    .fillMaxWidth()
                    .height(capsuleHeight)
                    .padding(horizontal = inset)
                    .drawBackdrop(
                        backdrop = appBackdrop,
                        shape = { shape },
                        effects = {
                            val progress = drag.pressProgress
                            vibrancy()
                            blur(2f.dp.toPx())
                            lens(15f.dp.toPx() * progress, 18f.dp.toPx() * progress)
                        },
                        highlight = { Highlight.Default.copy(alpha = drag.pressProgress) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, tab ->
                    LiquidTab(
                        tab = tab,
                        tint = selectedInk,
                        filled = true,
                        showLabel = showLabels,
                        scaleProvider = { if (index == capsuleIndex) lerp(1f, 1.12f, drag.pressProgress) else 1f },
                        onClick = null,
                    )
                }
            }
        }

        // The capsule.
        if (tabWidthPx > 0f) {
            val tabWidth = with(density) { tabWidthPx.toDp() }.coerceAtLeast(capsuleHeight)
            val capsuleModifier = Modifier
                .padding(horizontal = inset)
                .graphicsLayer {
                    val travel = drag.value * tabWidthPx
                    translationX = (if (isLtr) travel else -travel) + panelOffset()
                }
            if (glassy) {
                Box(
                    capsuleModifier
                        .drawBackdrop(
                            backdrop = capsuleBackdrop,
                            shape = { shape },
                            effects = {
                                val progress = drag.pressProgress
                                // Softly frosted at rest; clear, bending glass while held.
                                blur(3f.dp.toPx() * (1f - progress))
                                lens(
                                    lerp(0f, 10f.dp.toPx(), progress),
                                    lerp(0f, 12f.dp.toPx(), progress),
                                    chromaticAberration = progress > 0.01f,
                                )
                            },
                            highlight = { Highlight.Default.copy(alpha = lerp(0.5f, 1f, drag.pressProgress)) },
                            shadow = { Shadow(alpha = lerp(0.35f, 1f, drag.pressProgress)) },
                            innerShadow = {
                                val progress = drag.pressProgress
                                InnerShadow(radius = 8f.dp * progress, alpha = progress)
                            },
                            layerBlock = {
                                scaleX = drag.scaleX
                                scaleY = drag.scaleY
                                // Longer and flatter the faster it travels, round again as it slows.
                                val velocity = drag.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                drawRect(restWash.copy(alpha = 0.8f * (1f - 0.75f * drag.pressProgress)))
                            },
                        )
                        .height(capsuleHeight)
                        .width(tabWidth),
                )
                // The tab under the capsule drawn again on top, crisp at rest, gone a third into a press.
                val capsuleTab by remember(tabs.size) {
                    derivedStateOf { drag.value.fastRoundToInt().coerceIn(0, lastIndex) }
                }
                Row(
                    capsuleModifier
                        .graphicsLayer {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                            alpha = (1f - drag.pressProgress * 3f).fastCoerceIn(0f, 1f)
                        }
                        .clearAndSetSemantics {}
                        .height(capsuleHeight)
                        .width(tabWidth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.getOrNull(capsuleTab)?.let { tab ->
                        LiquidTab(tab = tab, tint = selectedInk, filled = true, showLabel = showLabels, scaleProvider = { 1f }, onClick = null)
                    }
                }
            } else {
                Box(
                    capsuleModifier
                        .graphicsLayer {
                            scaleX = drag.scaleX
                            scaleY = drag.scaleY
                        }
                        .clip(shape)
                        .background(
                            if (pureBlack) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            shape,
                        )
                        .height(capsuleHeight)
                        .width(tabWidth),
                )
            }
        }
    }
}

/** One equal-width tab: [onClick] is null for the hidden twin, which is a rendering, not a target. */
@Composable
private fun RowScope.LiquidTab(
    tab: FoldingDockTab,
    tint: Color,
    filled: Boolean,
    showLabel: Boolean,
    scaleProvider: () -> Float,
    onClick: (() -> Unit)?,
    gestureOwnedByBar: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .then(
                when {
                    // Touch is the bar's; this is only what TalkBack and switch access see.
                    onClick != null && gestureOwnedByBar -> Modifier.semantics {
                        role = Role.Tab
                        onClick { onClick(); true }
                    }
                    onClick != null -> Modifier.clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = onClick)
                    else -> Modifier
                },
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scaleProvider()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = tab.title,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
        if (showLabel) {
            Text(
                text = tab.title,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 7.dp),
            )
        }
    }
}

/** Labels go when a tab no longer has room to carry a readable word under its icon. */
@Composable
private fun rememberShowLabels(tabCount: Int): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, tabCount) {
        val pillWidth = minOf(configuration.screenWidthDp.dp - DockSideChrome, LiquidTabBarMaxWidth)
        (pillWidth - 8.dp) / tabCount.coerceAtLeast(1) >= LabelledTabMinWidth
    }
}

private val LiquidTabBarMaxWidth = 460.dp
private val DockSideChrome = 16.dp * 2 + 10.dp + 64.dp
private val LabelledTabMinWidth = 68.dp

/** How long a tap waits for the capsule to reach its tab before the page changes, and how close counts. */
private const val TabNavigateMaxWaitMs = 180L
private const val TabNavigateArrival = 0.15f
