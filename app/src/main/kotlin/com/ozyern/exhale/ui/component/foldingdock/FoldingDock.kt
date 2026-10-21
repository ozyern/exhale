/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 *
 * Ported from Convx (github.com/cosmictaserdev-creator/Convx), GPL-3.0: the folding tab bar
 * and its fold geometry from 2.0.4, the fold and search morph timing and the gooey merge from
 * 1.5.2, and 1.5.2's liquid glass numbers for the bar and its player. The draggable selector is
 * adapted from the Backdrop catalog's LiquidBottomTabs (Kyant0/AndroidLiquidGlass, Apache-2.0).
 */

package com.ozyern.exhale.ui.component.foldingdock

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.ozyern.exhale.ui.component.liquid.LocalAppBackdrop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** One tab of the folding dock. */
class FoldingDockTab(
    val key: String,
    val title: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
    /** Glyphs that fill their whole viewbox are drawn smaller so every tab reads the same size. */
    val glyphSize: Dp = 32.dp,
)

private val DockShape = RoundedCornerShape(percent = 50)

// Open tab row: 32dp glyph, label and the selector's inset.
private val OpenRowHeight = 64.dp
// The folded row with a song in it, and the search row.
private val InlineRowHeight = 48.dp
private val SearchRowHeight = 48.dp
private val ComponentSpacing = 8.dp
private val FoldingIconSize = 32.dp

/* ------------------------------------------------------------------ */
/* Glass                                                               */
/* ------------------------------------------------------------------ */

/**
 * The glass of the bar and of its player, at the strength the reference ships: saturation
 * x2, a 3dp frost, a 19dp lens bending 29dp at the rim, an even grey film at half opacity and a
 * thin 0.8dp rim of light at 30%. Clearer than the rest of our chrome on purpose: the page reads
 * straight through and only the rim says glass.
 */
@Composable
fun Modifier.foldingDockGlass(
    shape: Shape = DockShape,
    surfaceOpacity: Float = 0.5f,
    highlightAlpha: Float = 0.3f,
    backdrop: Backdrop = LocalAppBackdrop.current,
): Modifier {
    val dark = isSystemInDarkTheme()
    val tint = (if (dark) Color(0xFF4A4A4E) else Color(0xFFFAFAFA)).copy(alpha = surfaceOpacity)
    val rim = Color.White.copy(alpha = highlightAlpha)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { dockGlassEffects() },
        highlight = { Highlight(width = 0.8.dp, style = HighlightStyle.Default(color = rim, angle = 45f)) },
        shadow = { Shadow.Default },
        onDrawSurface = { drawRect(tint) },
    )
}

internal fun BackdropEffectScope.dockGlassEffects() {
    // The reference's current nav glass: vibrancy 2.0 and a 3dp frost, sampled live every frame
    // (it never freezes on scroll here), on the search circle as much as on the pill.
    colorControls(saturation = 2f)
    blur(3f.dp.toPx())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        lens(refractionHeight = 19.2f.dp.toPx(), refractionAmount = 28.8f.dp.toPx())
    }
}

/* ------------------------------------------------------------------ */
/* Motion                                                              */
/* ------------------------------------------------------------------ */

// The three shapes of the bar, as the reference names them: the open tab bar with the player
// above it, the folded row with the player inline, and the search row.
private enum class DockVisual { Inline, Expanded, Search, Typing }

// Peak blur of the gooey pulse while the shapes merge or split.
private val GooeyPeakBlur = 12.dp

// What a tab's label adds under its glyph: 2dp of gap and one line of 10sp.
private val TabLabelBlock = 16.dp

/** Where every piece of the bar sits in one shape, y measured up from the bar's bottom edge. */
private class DockRects(val pill: Rect, val search: Rect, val player: Rect, val glyph: Rect)

/* ------------------------------------------------------------------ */
/* The bar                                                             */
/* ------------------------------------------------------------------ */

/**
 * The bottom bar in the reference's three shapes, and the morph between them.
 *
 * Open, the tab pill and the search circle sit side by side with the player above, full width.
 * Folded, the pill retracts into a circle holding the page you are on, the player slides down
 * into the row between it and search, and search stays a circle. On the search pages the search
 * circle widens into the field and the pill retracts the same way, with the player above.
 *
 * Each piece is one rect, and a change of shape animates every rect from wherever it is to its
 * place in the new shape on the reference's [BarMotion] clocks: the bodies swell past their size
 * and settle, the player slides sideways first and drops a beat later (rises first and widens a
 * beat later the other way), and the page glyph travels between its tab and the circle on a curve
 * that never overshoots. A gooey pulse joins the pieces where they touch. Nothing crossfades.
 *
 * Search is the last of [tabs] when [hasSearchTab]. The bar measures to its row; the open player
 * hangs above it, exactly where the player sheet's own pill sits, and the dock only draws it while
 * it is folded or moving — see [FoldingDockMotion].
 */
@Composable
fun FoldingDock(
    tabs: List<FoldingDockTab>,
    selectedKey: String?,
    folded: Boolean,
    onUnfold: () -> Unit,
    modifier: Modifier = Modifier,
    hasSearchTab: Boolean = true,
    showLabels: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    accessory: (@Composable (Modifier, isInline: Boolean) -> Unit)? = null,
    accessoryReveal: () -> Float = { 1f },
    openPlayerHeight: Dp = 64.dp,
    openPlayerGap: Dp = ComponentSpacing,
    /** Extra air between the band the dock is given and the open player's bottom edge. */
    openPlayerLift: Dp = 0.dp,
    searchMode: Boolean = false,
    searchBarContent: (@Composable (Modifier) -> Unit)? = null,
    typing: Boolean = false,
    /**
     * The open tab bar to show in place of the dock's own tab group, with its own glass, puck and
     * swipe — the liquid style's. At rest it is exactly that bar; folding, it is clipped into the
     * retracting pill and fades as the dock's plate takes over under it.
     */
    openBar: (@Composable (Modifier) -> Unit)? = null,
    /** The gap between the open tab bar and the search circle. */
    openGap: Dp = ComponentSpacing,
) {
    val inSearch = searchMode && searchBarContent != null && hasSearchTab
    val searchTab = if (hasSearchTab) tabs.lastOrNull() else null
    val contentTabs = if (hasSearchTab) tabs.dropLast(1) else tabs
    val hasPlayer = accessory != null

    // The page you are on; off the tabs (and on Search), the last one you were on.
    val sticky = remember { arrayOfNulls<String>(1) }
    val live = contentTabs.firstOrNull { it.key == selectedKey }
    if (live != null) sticky[0] = live.key
    val selected = live ?: contentTabs.firstOrNull { it.key == sticky[0] } ?: contentTabs.firstOrNull()
    val selectedIndex = contentTabs.indexOf(selected).coerceAtLeast(0)
    val tabsCount = contentTabs.size.coerceAtLeast(1)

    val visual = when {
        inSearch && typing -> DockVisual.Typing
        folded -> DockVisual.Inline
        inSearch -> DockVisual.Search
        else -> DockVisual.Expanded
    }

    val density = LocalDensity.current
    // Known only once measured: the bar's width and the band it is given to stand in.
    var widthPx by remember { mutableIntStateOf(0) }
    var bandPx by remember { mutableIntStateOf(-1) }

    val labels = showLabels
    val targets: (DockVisual) -> DockRects = { v ->
        with(density) {
            val w = widthPx.toFloat()
            val s = ComponentSpacing.toPx()
            val open = OpenRowHeight.toPx()
            val row = InlineRowHeight.toPx()
            val gap = (if (bandPx >= 0) (bandPx - open).coerceAtLeast(0f) else openPlayerGap.toPx()) + openPlayerLift.toPx()
            val playerH = openPlayerHeight.toPx()
            val icon = FoldingIconSize.toPx()
            val abovePlayer = Rect(0f, -open - gap - playerH, w, -open - gap)
            fun glyphIn(r: Rect) = Rect(r.center.x - icon / 2f, r.center.y - icon / 2f, r.center.x + icon / 2f, r.center.y + icon / 2f)
            when (v) {
                DockVisual.Expanded -> {
                    val pillRight = if (searchTab != null) w - open - openGap.toPx() else w
                    val pill = Rect(0f, -open, pillRight, 0f)
                    val search = if (searchTab != null) Rect(w - open, -open, w, 0f) else Rect(w, -open, w, 0f)
                    val inset = 4.dp.toPx()
                    val cell = (pill.width - 2 * inset) / tabsCount
                    val cx = inset + cell * (selectedIndex + 0.5f)
                    val block = icon + if (labels) TabLabelBlock.toPx() else 0f
                    val cy = -open + (open - block) / 2f + icon / 2f
                    DockRects(pill, search, abovePlayer, Rect(cx - icon / 2f, cy - icon / 2f, cx + icon / 2f, cy + icon / 2f))
                }
                DockVisual.Inline -> {
                    if (hasPlayer || searchTab == null) {
                        val pill = Rect(0f, -row, row, 0f)
                        val search = if (searchTab != null) Rect(w - row, -row, w, 0f) else Rect(w, -row, w, 0f)
                        val playerRight = if (searchTab != null) w - row - s else w
                        DockRects(pill, search, Rect(row + s, -row, playerRight, 0f), glyphIn(pill))
                    } else {
                        // Nothing playing: the two circles, side by side in the middle.
                        val left = (w - (2 * row + s)) / 2f
                        val pill = Rect(left, -row, left + row, 0f)
                        DockRects(pill, Rect(left + row + s, -row, left + 2 * row + s, 0f), abovePlayer, glyphIn(pill))
                    }
                }
                DockVisual.Search -> {
                    val pill = Rect(0f, -row, row, 0f)
                    DockRects(pill, Rect(row + s, -row, w, 0f), abovePlayer, glyphIn(pill))
                }
                // Typing: the field takes the whole row, over the page circle, as the reference's does.
                DockVisual.Typing -> {
                    val pill = Rect(0f, -row, row, 0f)
                    DockRects(pill, Rect(0f, -row, w, 0f), abovePlayer, glyphIn(pill))
                }
            }
        }
    }

    val pillRect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val searchRect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val playerRect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val glyphRect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    // The shape the player last came to rest in; while it differs from [visual] the player is moving.
    var restVisual by remember { mutableStateOf(visual) }
    val shownVisual = remember { arrayOf(visual) }

    LaunchedEffect(visual, widthPx, bandPx, hasPlayer, selectedIndex, tabsCount, labels, searchTab != null) {
        if (widthPx <= 0) return@LaunchedEffect
        val t = targets(visual)
        val switched = visual != shownVisual[0]
        shownVisual[0] = visual
        val running = pillRect.isRunning || searchRect.isRunning || playerRect.isRunning || glyphRect.isRunning
        if (!placed || (!switched && !running)) {
            // First frame, or at rest with the room changed (rotation, insets): follow, don't animate.
            pillRect.snapTo(t.pill)
            searchRect.snapTo(t.search)
            playerRect.snapTo(t.player)
            glyphRect.snapTo(t.glyph)
            placed = true
            restVisual = visual
            return@LaunchedEffect
        }
        // A change of shape usually lands with a page change whose first frame is a long one. The
        // clocks start after it, so the opening of the morph is drawn instead of skipped.
        withFrameNanos { }
        val collapsing = visual == DockVisual.Inline
        launch { pillRect.animateTo(t.pill, if (collapsing) BarMotion.CollapseSurface else BarMotion.ExpandSurface) }
        launch { searchRect.animateTo(t.search, if (collapsing) BarMotion.CollapseSurface else BarMotion.ExpandSurface) }
        launch { glyphRect.animateTo(t.glyph, if (collapsing) BarMotion.CollapseItem else BarMotion.ExpandItem) }
        launch {
            playerRect.animateTo(t.player, if (collapsing) BarMotion.CollapseAccessory else BarMotion.ExpandAccessory)
            restVisual = visual
        }
    }

    // The contents of each shape: the outgoing ones clear quickly once the bodies start moving,
    // the incoming ones arrive after a beat and overlap them, so there is never a blank midpoint.
    val tabsAlpha = remember { Animatable(if (visual == DockVisual.Expanded) 1f else 0f) }
    val fieldAlpha = remember { Animatable(if (visual == DockVisual.Search || visual == DockVisual.Typing) 1f else 0f) }
    val handOver = remember { Animatable(if (visual == DockVisual.Typing) 0f else 1f) }
    val typingGlyph = remember { Animatable(if (visual == DockVisual.Typing) 1f else 0f) }
    val goo = remember { Animatable(0f) }
    val firstShape = remember { booleanArrayOf(true) }
    LaunchedEffect(visual) {
        if (firstShape[0]) {
            firstShape[0] = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        val collapsing = visual == DockVisual.Inline
        val ms = BarMotion.barMs(collapsing)
        val enter = tween<Float>((ms * 0.62f).toInt(), delayMillis = (ms * 0.16f).toInt(), easing = LinearOutSlowInEasing)
        val exit = tween<Float>((BarMotion.morphMs(collapsing) * 0.6f).toInt(), easing = FastOutLinearInEasing)
        launch {
            val on = visual == DockVisual.Expanded
            tabsAlpha.animateTo(if (on) 1f else 0f, if (on) enter else exit)
        }
        launch {
            val on = visual == DockVisual.Search || visual == DockVisual.Typing
            fieldAlpha.animateTo(if (on) 1f else 0f, if (on) enter else exit)
        }
        launch {
            // Typing, the field that rides the keyboard takes over once this one has widened.
            if (visual == DockVisual.Typing) {
                launch { typingGlyph.animateTo(1f, tween(120, easing = LinearEasing)) }
                handOver.animateTo(0f, tween(180, delayMillis = 260, easing = LinearEasing))
            } else {
                launch { typingGlyph.animateTo(0f, tween(120, easing = LinearEasing)) }
                handOver.animateTo(1f, tween(120, easing = LinearEasing))
            }
        }
        launch {
            goo.snapTo(0f)
            goo.animateTo(0f, keyframes {
                durationMillis = ms
                0f at 0
                1f at ms / 2 using FastOutSlowInEasing
                0f at ms
            })
        }
    }

    // The dock draws the player whenever it is not resting open: folded, or on its way.
    // Typing, the player is away altogether.
    val ownsPlayer = hasPlayer && visual != DockVisual.Typing && (visual == DockVisual.Inline || restVisual != visual || restVisual == DockVisual.Inline)
    SideEffect { FoldingDockMotion.fold = if (ownsPlayer) 1f else 0f }
    DisposableEffect(Unit) { onDispose { FoldingDockMotion.fold = 0f } }
    val ownsPlayerState = rememberUpdatedState(ownsPlayer)

    val surfaceBackdrop = rememberLayerBackdrop()
    val noTap = remember { MutableInteractionSource() }
    val glowScope = rememberCoroutineScope()
    val selectedGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val searchGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val gooeyPx = with(density) { GooeyPeakBlur.toPx() }
    val appBackdrop = LocalAppBackdrop.current

    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    val inactiveInk = ink.copy(alpha = 0.82f)

    val icon: @Composable (FoldingDockTab, Boolean) -> Unit = { tab, isSelected ->
        Box(Modifier.size(tab.glyphSize), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(tab.icon),
                contentDescription = tab.title,
                tint = if (isSelected) accentColor else inactiveInk,
                modifier = Modifier.size(tab.glyphSize * 0.8f),
            )
        }
    }
    val title: @Composable (FoldingDockTab, Boolean) -> Unit = { tab, isSelected ->
        if (showLabels) {
            Text(
                text = tab.title,
                color = if (isSelected) accentColor else inactiveInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }

    val expanded = visual == DockVisual.Expanded
    Layout(
        // The gooey pulse melts only the contents that fade (the open tabs, the field), as the
        // reference's does: its surfaces and its player draw above that layer and stay crisp.
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = handOver.value },
        content = {
            // 0: the tab pill's glass, which is also the folded page circle. Under a bar with glass
            // of its own, it only shows as that bar fades.
            Box(
                Modifier
                    .graphicsLayer { if (openBar != null) alpha = 1f - tabsAlpha.value }
                    .graphicsLayer(elasticStretchLayerBlock(selectedGlow))
                    .layerBackdrop(surfaceBackdrop)
                    .clip(DockShape)
                    .foldingDockGlass(DockShape)
                    .then(selectedGlow.modifier),
            )
            // 1: the open tabs, at the open pill's size, clipped to the pill as it retracts.
            Box(
                Modifier
                    .gooey { gooeyPx * goo.value }
                    .graphicsLayer { alpha = tabsAlpha.value }
                    .drawWithContent {
                        // At rest and open the bar draws whole: its pressed capsule swells past its
                        // own edges, and clipping it to the pill cut that swell off mid-swipe.
                        if (expanded && !pillRect.isRunning) {
                            drawContent()
                            return@drawWithContent
                        }
                        val r = pillRect.value
                        // The group's own coordinates: its bottom is the bar's bottom edge.
                        val top = size.height + r.top
                        val bottom = size.height + r.bottom
                        val outline = Path().apply {
                            addRoundRect(RoundRect(r.left, top, r.right, bottom, CornerRadius(minOf(r.width, r.height) / 2f)))
                        }
                        clipPath(outline) { this@drawWithContent.drawContent() }
                    }
                    .then(if (!expanded) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                val selectedState = rememberUpdatedState(selectedIndex)
                if (openBar != null) openBar(Modifier.fillMaxSize()) else FoldingTabGroup(
                    selectedTabIndex = { selectedState.value },
                    onTabSelected = { index -> contentTabs.getOrNull(index)?.onClick?.invoke() },
                    backdrop = appBackdrop,
                    surfaceBackdrop = surfaceBackdrop,
                    tabsCount = contentTabs.size,
                    accentColor = accentColor,
                    height = OpenRowHeight,
                    indicatorVisible = contentTabs.any { it.key == selectedKey },
                ) {
                    contentTabs.forEach { tab ->
                        val isSelected = tab.key == selectedKey
                        FoldingTabCell(onClick = tab.onClick) {
                            icon(tab, isSelected)
                            title(tab, isSelected)
                        }
                    }
                }
            }
            // 2: search's own glass, with its glyph; a circle open and folded, the field in search.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(searchGlow))
                    .graphicsLayer { alpha = if (searchTab == null) 0f else 1f }
                    .clip(DockShape)
                    .then(if (searchTab != null) Modifier.foldingDockGlass(DockShape) else Modifier)
                    .then(searchGlow.modifier)
                    .then(
                        if (searchTab != null && visual != DockVisual.Search && visual != DockVisual.Typing) {
                            Modifier.then(searchGlow.gestureModifier).clickable(noTap, null) { searchTab.onClick() }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (searchTab != null) {
                    Box(Modifier.graphicsLayer { alpha = 1f - fieldAlpha.value }) {
                        icon(searchTab, selectedKey == searchTab.key)
                    }
                }
            }
            // 3: the search field, grown out of the circle.
            Box(
                Modifier
                    .gooey { gooeyPx * goo.value }
                    .graphicsLayer { alpha = fieldAlpha.value }
                    .then(if (visual != DockVisual.Search) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                if (visual == DockVisual.Search || visual == DockVisual.Typing || fieldAlpha.value > 0f) {
                    searchBarContent?.invoke(Modifier.fillMaxSize())
                }
            }
            // 4: the page glyph, travelling between its tab and the circle. It passes under the player.
            Box(
                // Typing, the field has grown over the circle and the glyph goes with it.
                Modifier.graphicsLayer { alpha = (1f - tabsAlpha.value) * (1f - typingGlyph.value) },
                contentAlignment = Alignment.Center,
            ) {
                selected?.let { icon(it, true) }
            }
            // 5: the mini player, one node for every shape.
            Box(
                Modifier.graphicsLayer {
                    // Open and at rest it is the player sheet's pill that shows, not this one.
                    alpha = if (ownsPlayerState.value) accessoryReveal().coerceIn(0f, 1f) else 0f
                },
            ) {
                accessory?.invoke(
                    Modifier
                        .fillMaxSize()
                        .clip(DockShape)
                        .foldingDockGlass(DockShape),
                    visual == DockVisual.Inline,
                )
            }
            // 6: the folded or search-row page circle's touch target, over everything in it.
            Box(
                Modifier
                    .then(selectedGlow.gestureModifier)
                    .clickable(noTap, null) {
                        if (folded) onUnfold() else selected?.onClick?.invoke()
                    },
            )
        },
    ) { children, constraints ->
        val width = constraints.maxWidth
        if (widthPx != width) widthPx = width
        val band = if (constraints.hasBoundedHeight) constraints.maxHeight else -1
        if (bandPx != band) bandPx = band
        val settled = if (placed) null else targets(visual)
        val pill = settled?.pill ?: pillRect.value
        val search = settled?.search ?: searchRect.value
        val player = settled?.player ?: playerRect.value
        val glyph = settled?.glyph ?: glyphRect.value
        val openPx = OpenRowHeight.roundToPx()
        val height = maxOf(-pill.top, -search.top, 1f).roundToInt()

        fun fixed(r: Rect) = Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0))
        val surface = children[0].measure(fixed(pill))
        val openPillWidth = targets(DockVisual.Expanded).pill.width.roundToInt().coerceAtLeast(0)
        val group = children[1].measure(Constraints.fixed(openPillWidth, openPx))
        val searchSurface = children[2].measure(fixed(search))
        val field = children[3].measure(fixed(search))
        val glyphBox = children[4].measure(fixed(glyph))
        val playerBox = children[5].measure(fixed(player))
        val pillTap = children[6].measure(if (expanded) Constraints.fixed(0, 0) else fixed(pill))
        layout(width, height) {
            fun at(r: Rect) = IntOffset(r.left.roundToInt(), (height + r.top).roundToInt())
            surface.place(at(pill))
            // Gone once faded, so the open tabs never catch a tap meant for the row.
            if (expanded || tabsAlpha.value > 0f) group.place(0, height - openPx)
            searchSurface.place(at(search))
            if (visual == DockVisual.Search || visual == DockVisual.Typing || fieldAlpha.value > 0f) field.place(at(search))
            glyphBox.place(at(glyph))
            if (hasPlayer) playerBox.place(at(player))
            if (!expanded) pillTap.place(at(pill))
        }
    }
}

/* ------------------------------------------------------------------ */
/* The open bar's tabs and their selector                              */
/* ------------------------------------------------------------------ */

private val LocalTabPressScale = compositionLocalOf<() -> Float> { { 1f } }

/**
 * The open bar's tabs with a draggable glass slot under the selected one. Press-and-drag moves
 * the slot; releasing selects the nearest tab, including the one already selected. The group
 * draws no shell: [FoldingDock] owns the surface.
 */
@Composable
private fun FoldingTabGroup(
    selectedTabIndex: () -> Int,
    onTabSelected: (Int) -> Unit,
    backdrop: Backdrop,
    surfaceBackdrop: Backdrop,
    tabsCount: Int,
    accentColor: Color,
    height: Dp,
    modifier: Modifier = Modifier,
    indicatorVisible: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount <= 0) return
    val isLightTheme = !isSystemInDarkTheme()
    val tabsBackdrop = rememberLayerBackdrop()
    val widthPx = remember { mutableFloatStateOf(0f) }
    val onSelected by rememberUpdatedState(onTabSelected)

    Box(
        modifier.onSizeChanged { widthPx.floatValue = it.width.toFloat() },
        contentAlignment = Alignment.CenterStart,
    ) {
        val density = LocalDensity.current
        val tabWidth: () -> Float = remember(density, tabsCount) {
            { with(density) { (widthPx.floatValue - 8.dp.toPx()) / tabsCount } }
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val width = widthPx.floatValue
                val fraction = if (width > 0f) (offsetAnimation.value / width).fastCoerceIn(-1f, 1f) else 0f
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dragAnimation = remember(animationScope, tabsCount) {
            DockDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).coerceAtLeast(0).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    // Releasing on the current tab still navigates: it is how a page above a tab gets back to it.
                    if (targetIndex == currentIndex) onSelected(targetIndex)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth() * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { selectedTabIndex() }.collectLatest { currentIndex = it }
        }
        LaunchedEffect(dragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dragAnimation.animateToValue(index.toFloat())
                    if (index != selectedTabIndex()) onSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dragAnimation.value + 0.5f) * tabWidth() + panelOffset
                        else size.width - (dragAnimation.value + 0.5f) * tabWidth() + panelOffset,
                        size.height / 2f,
                    )
                },
            )
        }

        Row(
            Modifier
                .graphicsLayer { translationX = panelOffset }
                .clip(DockShape)
                .then(interactiveHighlight.modifier)
                .height(height)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        // The tabs again, tinted and invisible: what the slot shows the selected tab through.
        CompositionLocalProvider(
            LocalTabPressScale provides { lerp(1f, 1.2f, dragAnimation.pressProgress) },
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .height(height - 8.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }

        val indicatorAlpha by animateFloatAsState(
            targetValue = if (indicatorVisible) 1f else 0f,
            animationSpec = tween(220),
            label = "dockIndicatorAlpha",
        )
        val slotPlacement = Modifier
            .padding(horizontal = 4.dp)
            .graphicsLayer {
                alpha = indicatorAlpha
                translationX =
                    if (isLtr) dragAnimation.value * tabWidth() + panelOffset
                    else size.width - (dragAnimation.value + 1f) * tabWidth() + panelOffset
            }
        val slotGrowth = Modifier.layout { measurable, constraints ->
            val velocity = dragAnimation.velocity / 10f
            val scaleX = dragAnimation.scaleX / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
            val scaleY = dragAnimation.scaleY * (1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f))
            val width = constraints.maxWidth
            val slotHeight = constraints.maxHeight
            val grownWidth = (width * scaleX).roundToInt().coerceAtLeast(1)
            val grownHeight = (slotHeight * scaleY).roundToInt().coerceAtLeast(1)
            val child = measurable.measure(Constraints.fixed(grownWidth, grownHeight))
            layout(width, slotHeight) {
                child.placeRelative((width - grownWidth) / 2, (slotHeight - grownHeight) / 2)
            }
        }
        val slotLens: BackdropEffectScope.() -> Unit = {
            val progress = dragAnimation.pressProgress
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
            }
        }
        // While pressed the slot also refracts the bar's own surface, faded in so release doesn't pop.
        val pressedBackdrop = remember(backdrop, surfaceBackdrop, dragAnimation) {
            object : Backdrop {
                private val surfacePaint = Paint()
                override val isCoordinatesDependent = true
                override fun DrawScope.drawBackdrop(
                    density: Density,
                    coordinates: LayoutCoordinates?,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?,
                ) {
                    with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
                    val progress = dragAnimation.pressProgress.coerceIn(0f, 1f)
                    if (progress > 0f) {
                        surfacePaint.alpha = progress
                        val canvas = drawContext.canvas
                        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), surfacePaint)
                        try {
                            with(surfaceBackdrop) { drawBackdrop(density, coordinates, layerBlock) }
                        } finally {
                            canvas.restore()
                        }
                    }
                }
            }
        }

        Box(
            slotPlacement
                .then(interactiveHighlight.gestureModifier)
                .then(dragAnimation.modifier)
                .height(height - 8.dp)
                .fillMaxWidth(1f / tabsCount)
                .then(slotGrowth)
                .drawBackdrop(
                    backdrop = pressedBackdrop,
                    shape = { DockShape },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx() * (1f - dragAnimation.pressProgress.coerceIn(0f, 1f)))
                        slotLens()
                    },
                    highlight = { Highlight.Default.copy(alpha = dragAnimation.pressProgress) },
                    shadow = { Shadow(alpha = dragAnimation.pressProgress) },
                    innerShadow = {
                        val progress = dragAnimation.pressProgress
                        InnerShadow(radius = 8.dp * progress, alpha = progress)
                    },
                    onDrawSurface = {
                        val progress = dragAnimation.pressProgress
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                ),
        )
        Box(
            slotPlacement
                .height(height - 8.dp)
                .fillMaxWidth(1f / tabsCount)
                .then(slotGrowth)
                .drawBackdrop(
                    backdrop = tabsBackdrop,
                    shape = { DockShape },
                    effects = slotLens,
                    highlight = null,
                    shadow = null,
                ),
        )
    }
}

/** One cell of [FoldingTabGroup]: icon over title, scaled with the slot while pressed. */
@Composable
private fun RowScope.FoldingTabCell(
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val scale = LocalTabPressScale.current
    Column(
        Modifier
            .clip(DockShape)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
