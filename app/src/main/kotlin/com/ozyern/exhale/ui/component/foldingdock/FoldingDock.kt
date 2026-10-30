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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
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
import com.ozyern.exhale.ui.component.miniPlayerGlass
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    /** An SF Symbol name; drawn bold in place of [icon] when set. */
    val symbol: String? = null,
)

private val DockShape = RoundedCornerShape(percent = 50)

// Open tab row, Convx 2.0.4's NavBarRowHeight: 32dp glyph, label and the selector's inset.
private val OpenRowHeight = 68.dp
// The folded row with a song in it, and the search row.
private val InlineRowHeight = 48.dp
private val SearchRowHeight = 48.dp
private val ComponentSpacing = 8.dp
private val FoldingIconSize = 32.dp

/** Where the dock is parked while the typing field has taken over: well below the screen. */
private const val OFF_SCREEN_PX = 100_000

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
    backdrop: Backdrop = LocalAppBackdrop.current,
): Modifier {
    // Convx 2.0.4's liquidGlass at its nav defaults (GlassEffect.kt + GlassTintTokens): nearly
    // clear. Dark, a 15% white lift and no tint; light, #F5F5F7 at 40% under a 5% grey. The colour
    // and the bend come from the page beneath, not from a film laid over it.
    val dark = isSystemInDarkTheme()
    val base = if (dark) Color.White.copy(alpha = 0.15f) else Color(0xFFF5F5F7).copy(alpha = 0.40f)
    val tint = if (dark) Color.Transparent else Color(0xFF3C3C43).copy(alpha = 0.05f)
    // The specular rim ("pluck"): white, 30% dark / 40% light, 0.8dp, lit from 45 degrees.
    val rim = Color.White.copy(alpha = if (dark) 0.30f else 0.40f)
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { dockGlassEffects() },
        highlight = { Highlight(width = 0.8.dp, style = HighlightStyle.Default(color = rim, angle = 45f)) },
        // The bar draws no outer shadow of its own (Convx passes outerShadow = false).
        shadow = null,
        onDrawSurface = {
            drawRect(base)
            if (tint.alpha > 0f) drawRect(tint)
        },
    ).outerDarkRim(shape)
}

/**
 * Convx's dark outer rim, paired with the light one: 1.6dp at 32%, strongest on the two corners
 * the light doesn't reach, so the glass keeps an edge over a bright or busy page.
 */
internal fun Modifier.outerDarkRim(shape: Shape, width: Dp = 1.6.dp, alpha: Float = 0.32f, angle: Float = 45f): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = width.toPx())
        val center = Offset(size.width / 2f, size.height / 2f)
        fun flank(axisDeg: Float): androidx.compose.ui.graphics.Brush {
            val a = Math.toRadians(axisDeg.toDouble()).toFloat()
            val dir = Offset(kotlin.math.cos(a), kotlin.math.sin(a))
            val reach = dir * ((abs(dir.x) * size.width + abs(dir.y) * size.height) / 2f)
            return androidx.compose.ui.graphics.Brush.linearGradient(
                0f to Color.Black.copy(alpha = alpha),
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = alpha),
                start = center - reach,
                end = center + reach,
            )
        }
        val first = flank(angle)
        val second = flank(angle + 90f)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, first, style = stroke)
            drawOutline(outline, second, style = stroke)
        }
    }

internal fun BackdropEffectScope.dockGlassEffects() {
    // Convx 2.0.4's nav glass: vibrancy 2.0 (saturation x2), a 3dp blur, and a lens 0.60 x 48dp
    // high bending 0.69 x 48dp at the rim.
    colorControls(saturation = 2f)
    blur(3f.dp.toPx())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        lens(refractionHeight = (0.60f * 48f).dp.toPx(), refractionAmount = (0.69f * 48f).dp.toPx())
    }
}

/* ------------------------------------------------------------------ */
/* Motion                                                              */
/* ------------------------------------------------------------------ */

// The three shapes of the bar, as the reference names them: the open tab bar with the player
// above it, the folded row with the player inline, and the search row.
private enum class DockVisual { Inline, Expanded, Search, Typing }

// What a tab's label adds under its glyph: 2dp of gap and one line of 10sp text.
private val TabLabelGap = 2.dp
private val TabLabelLineHeight = 12.sp

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
 * place in the new shape on [BarMotion]'s springs: the pill retracts first and the player drops a
 * beat later into the room it left (rises first, and the pill widens a beat later, the other way),
 * and the page glyph travels between its tab and the circle without overshooting. Springs keep
 * their speed, so a fold caught by an unfold turns round instead of restarting.
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
    // Search rides in the open bar as its last tab unless a bar of the caller's own replaces the group.
    val searchInBar = searchTab != null && openBar == null
    val cellCount = if (searchInBar) tabs.size.coerceAtLeast(1) else tabsCount

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

    // Convx 2.0.4's FoldingTabBar: the whole bar folded by one number, [fold], and opened into the
    // search row by another, [search]. Every piece's place is worked out from the two each frame
    // (its foldGeometry), so a reversed fold retraces the same path and nothing ever disagrees.
    // [fold] overshoots under its spring easing; sizes are clamped, positions are not — the overshoot
    // is the glass swinging past its place and back, which is the bounce.
    val fold = remember { Animatable(if (visual == DockVisual.Inline) 1f else 0f) }
    val search = remember { Animatable(if (visual == DockVisual.Search || visual == DockVisual.Typing) 1f else 0f) }
    val handOver = remember { Animatable(if (visual == DockVisual.Typing) 0f else 1f) }
    val typingGlyph = remember { Animatable(if (visual == DockVisual.Typing) 1f else 0f) }

    val rectsAt: (Float, Float) -> DockRects = { p, sRaw ->
        with(density) {
            fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
            val w = widthPx.toFloat()
            val s = sRaw.coerceIn(0f, 1f)
            val spacing = ComponentSpacing.toPx()
            val open = mix(OpenRowHeight.toPx(), SearchRowHeight.toPx(), s)
            val compact = InlineRowHeight.toPx()
            val rowH = mix(open, compact, p).coerceAtLeast(1f)
            val circle = rowH.coerceAtMost(w)
            val inset = 4.dp.toPx()
            val icon = FoldingIconSize.toPx()

            // The tab group: full width open, the page circle folded or searching.
            val openRight = if (searchTab != null && !searchInBar) w - OpenRowHeight.toPx() - openGap.toPx() else w
            val navWidth = mix(mix(openRight, circle, s), circle, p).coerceIn(circle, maxOf(circle, w))
            val pill = Rect(0f, -rowH, navWidth, 0f)

            // Search: unseen over its own cell while open, out to the right edge folded, and wide as
            // the field in the search row.
            val tabW = (openRight - 2 * inset) / cellCount
            val searchCenter = if (searchInBar) inset + tabW * (cellCount - 0.5f) else w - OpenRowHeight.toPx() / 2f
            val searchWidth = mix(circle, (w - circle - spacing).coerceAtLeast(circle), s * (1f - p)).coerceAtLeast(circle)
            val detachedSearchX = mix(searchCenter - circle / 2f, w - circle, p)
            val searchX = mix(detachedSearchX, w - searchWidth, s)
            // Typing, the field takes the whole row, over the page circle.
            val t = typingGlyph.value
            val searchRect = when {
                searchTab == null -> Rect(w, -rowH, w, 0f)
                else -> Rect(mix(searchX, 0f, t), -rowH, searchX + searchWidth, 0f)
            }

            // The page glyph: from its cell, label under it, into the circle.
            val ps = maxOf(p, s)
            val selectedX = mix(inset + tabW * (selectedIndex + 0.5f) - circle / 2f, 0f, ps)
            val titleFraction = (1f - p.coerceIn(0f, 1f)) * (1f - s)
            val block = icon + if (labels) TabLabelGap.toPx() + TabLabelLineHeight.toPx() else 0f
            val cy = mix(-rowH / 2f, -rowH + (rowH - block) / 2f + icon / 2f, titleFraction)
            val gx = selectedX + circle / 2f
            val glyph = Rect(gx - icon / 2f, cy - icon / 2f, gx + icon / 2f, cy + icon / 2f)

            // The player: full width above the bar, inline between the two circles folded.
            val gap = (if (bandPx >= 0) (bandPx - OpenRowHeight.toPx()).coerceAtLeast(0f) else openPlayerGap.toPx()) + openPlayerLift.toPx()
            val openPlayerH = openPlayerHeight.toPx()
            val playerH = mix(openPlayerH, compact, p.coerceIn(0f, 1f))
            val inlineWidth = if (searchTab != null) w - 2 * (compact + spacing) else w - compact - spacing
            val playerW = mix(w, inlineWidth.coerceAtLeast(0f), p).coerceIn(0f, w)
            val playerX = mix(0f, circle + spacing, p)
            val playerTop = -rowH - mix(openPlayerH + gap, (playerH - rowH) / 2f, p)
            val player = Rect(playerX, playerTop, playerX + playerW, playerTop + playerH)

            DockRects(pill, searchRect, player, glyph)
        }
    }
    val current: () -> DockRects = { rectsAt(fold.value, search.value) }

    val firstShape = remember { booleanArrayOf(true) }
    val inSearchShape = visual == DockVisual.Search || visual == DockVisual.Typing
    LaunchedEffect(visual == DockVisual.Inline) {
        val target = if (visual == DockVisual.Inline) 1f else 0f
        if (fold.targetValue == target && !fold.isRunning) return@LaunchedEffect
        // The fold, on Convx 2.0.4's clock: collapse 560ms landing with a deep overshoot, expand
        // 470ms with a gentler one.
        fold.animateTo(
            target,
            if (target == 1f) tween(BarMotion.CollapseMorphMs, easing = BarMotion.FoldCollapseSpring)
            else tween(BarMotion.ExpandMorphMs, easing = BarMotion.FoldExpandSpring),
        )
    }
    LaunchedEffect(inSearchShape) {
        val target = if (inSearchShape) 1f else 0f
        if (search.targetValue == target && !search.isRunning) return@LaunchedEffect
        search.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
    }
    LaunchedEffect(visual) {
        if (firstShape[0]) {
            firstShape[0] = false
            return@LaunchedEffect
        }
        // Typing, the field that rides the keyboard takes over once this one has widened.
        if (visual == DockVisual.Typing) {
            launch { typingGlyph.animateTo(1f, tween(120, easing = LinearEasing)) }
            handOver.animateTo(0f, tween(180, delayMillis = 260, easing = LinearEasing))
        } else {
            launch { typingGlyph.animateTo(0f, tween(120, easing = LinearEasing)) }
            handOver.animateTo(1f, tween(120, easing = LinearEasing))
        }
    }

    // Convx's opacities, all read off the two numbers.
    val foldC = { fold.value.coerceIn(0f, 1f) }
    val searchC = { search.value.coerceIn(0f, 1f) }
    val tabsAlpha = object {
        val value: Float get() = (1f - foldC() * 3f).coerceIn(0f, 1f) * (1f - searchC())
    }
    val circlesAlpha = { maxOf(foldC(), searchC()) }
    val fieldAlpha = object {
        val value: Float get() = (searchC() * (1f - foldC())).coerceIn(0f, 1f)
    }

    // The dock draws the player whenever it is not resting open: folded, or on its way. Typing, the
    // player is away altogether.
    val ownsPlayer = hasPlayer && visual != DockVisual.Typing && (fold.targetValue == 1f || fold.isRunning || fold.value > 0.001f)
    SideEffect {
        FoldingDockMotion.fold = if (ownsPlayer) 1f else 0f
        FoldingDockMotion.inline = visual == DockVisual.Inline
    }
    DisposableEffect(Unit) { onDispose { FoldingDockMotion.fold = 0f } }
    val ownsPlayerState = rememberUpdatedState(ownsPlayer)

    val surfaceBackdrop = rememberLayerBackdrop()
    val noTap = remember { MutableInteractionSource() }
    val glowScope = rememberCoroutineScope()
    val selectedGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val searchGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val appBackdrop = LocalAppBackdrop.current

    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    val inactiveInk = ink.copy(alpha = 0.82f)

    // The whole row bounces as the bar folds or opens, and a tab bounces as it is selected.
    val foldKey = visual == DockVisual.Inline
    val icon: @Composable (FoldingDockTab, Boolean) -> Unit = { tab, isSelected ->
        // Headroom: the bounce scales the glyph up, so it is drawn at 80% inside its slot.
        Box(Modifier.size(tab.glyphSize), contentAlignment = Alignment.Center) {
            val name = tab.symbol
            val sf = remember(name) { name?.let { com.composables.sfsymbols.SfSymbol.named(it) } }
            if (sf != null) {
                var taps by remember { mutableIntStateOf(0) }
                var seenSelected by remember { mutableStateOf(isSelected) }
                LaunchedEffect(isSelected) {
                    if (isSelected && !seenSelected) taps++
                    seenSelected = isSelected
                }
                var seenFold by remember { mutableStateOf(foldKey) }
                LaunchedEffect(foldKey) {
                    if (foldKey != seenFold) taps++
                    seenFold = foldKey
                }
                com.composables.sfsymbols.SfSymbolIcon(
                    symbol = sf,
                    size = tab.glyphSize * 0.8f,
                    // Apple Music's tab bar draws its symbols at medium weight, not bold.
                    weight = com.composables.sfsymbols.SfWeight.Medium,
                    mode = com.composables.sfsymbols.SfRenderingMode.Monochrome,
                    tint = if (isSelected) accentColor else inactiveInk,
                    effect = DockSymbolBounce,
                    trigger = taps,
                    contentDescription = tab.title,
                )
            } else {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = tab.title,
                    tint = if (isSelected) accentColor else inactiveInk,
                    modifier = Modifier.size(tab.glyphSize * 0.8f),
                )
            }
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
                // One line of its own height, not the body style's 22sp, so it sits 2dp under the glyph.
                lineHeight = TabLabelLineHeight,
                letterSpacing = 0.sp,
                // As Apple Music's tab bar: one weight, the tint alone marks the selected tab.
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    val expanded = visual == DockVisual.Expanded
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = handOver.value }
            // Faded out while typing (the field riding the keyboard has taken over) but still laid
            // over it and still taking its touches, so it is parked off screen once it has handed
            // over rather than removed: its size, and the room it holds, stay the same.
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    val handedOver = visual == DockVisual.Typing && handOver.value < 0.05f
                    placeable.place(0, if (handedOver) OFF_SCREEN_PX else 0)
                }
            },
        content = {
            // 0: the tab bar's glass, which is also the folded page circle.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(selectedGlow))
                    .layerBackdrop(surfaceBackdrop)
                    .clip(DockShape)
                    .foldingDockGlass(DockShape)
                    .then(selectedGlow.modifier),
            )
            // 1: the open tabs at the open bar's size, clipped to the bar as it folds.
            Box(
                Modifier
                    .graphicsLayer { alpha = tabsAlpha.value }
                    .drawWithContent {
                        // At rest and open the bar draws whole: its pressed capsule swells past its
                        // own edges, and clipping it would cut that swell off mid-swipe.
                        if (expanded && !fold.isRunning && !search.isRunning) {
                            drawContent()
                            return@drawWithContent
                        }
                        val r = current().pill
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
                val groupTabs = if (searchInBar) tabs else contentTabs
                if (openBar != null) openBar(Modifier.fillMaxSize()) else FoldingTabGroup(
                    selectedTabIndex = { selectedState.value },
                    onTabSelected = { index -> groupTabs.getOrNull(index)?.onClick?.invoke() },
                    backdrop = appBackdrop,
                    surfaceBackdrop = surfaceBackdrop,
                    tabsCount = groupTabs.size,
                    accentColor = accentColor,
                    height = OpenRowHeight,
                    indicatorVisible = contentTabs.any { it.key == selectedKey },
                ) {
                    groupTabs.forEach { tab ->
                        val isSelected = tab.key == selectedKey
                        FoldingTabCell(onClick = tab.onClick) {
                            icon(tab, isSelected)
                            title(tab, isSelected)
                        }
                    }
                }
            }
            // 2: search's own glass, with its glyph: detached only while folded or searching.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(searchGlow))
                    .graphicsLayer {
                        alpha = when {
                            searchTab == null -> 0f
                            searchInBar -> circlesAlpha()
                            else -> 1f
                        }
                    }
                    .clip(DockShape)
                    .then(if (searchTab != null) Modifier.foldingDockGlass(DockShape) else Modifier)
                    .then(searchGlow.modifier)
                    .then(
                        if (searchTab != null && visual != DockVisual.Search && visual != DockVisual.Typing &&
                            !(searchInBar && expanded)
                        ) {
                            Modifier.then(searchGlow.gestureModifier).clickable(noTap, null) { searchTab.onClick() }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (searchTab != null) {
                    // Convx: the detached glyph gives way to the field as the search row opens.
                    Box(Modifier.graphicsLayer { alpha = 1f - searchC() * (1f - foldC()) }) {
                        icon(searchTab, selectedKey == searchTab.key)
                    }
                }
            }
            // 3: the search field, grown out of the circle.
            Box(
                Modifier
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
                Modifier
                    .graphicsLayer { alpha = circlesAlpha() * (1f - typingGlyph.value) }
                    // Pressed, the circle swells and leans; its glyph is a separate node, so it is
                    // stretched by the circle's own measure and goes with it, as search's does.
                    .graphicsLayer(elasticStretchLayerBlock(selectedGlow, frame = { current().pill.size })),
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
                        .then(Modifier.miniPlayerGlass(DockShape)),
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
        // Read here, in measure, so a running fold only relayouts the bar, never recomposes it.
        val now = current()
        val pill = now.pill
        val search = now.search
        val player = now.player
        val glyph = now.glyph
        val openPx = OpenRowHeight.roundToPx()
        val height = maxOf(-pill.top, -search.top, 1f).roundToInt()

        fun fixed(r: Rect) = Constraints.fixed(r.width.roundToInt().coerceAtLeast(0), r.height.roundToInt().coerceAtLeast(0))
        val surface = children[0].measure(fixed(pill))
        val openPillWidth = rectsAt(0f, 0f).pill.width.roundToInt().coerceAtLeast(0)
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
            // No tab at all (-1) leaves the slot where it was: sent to -1 it was clamped onto the
            // first tab, so a page off the tabs lit Home whichever tab it was opened from.
            snapshotFlow { selectedTabIndex() }.collectLatest { if (it >= 0) currentIndex = it }
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

/** One shared bounce, by layer, as SF Symbols bounce on iOS: a fresh object each recomposition would restart it. */
private val DockSymbolBounce = com.composables.sfsymbols.SfSymbolEffect.Bounce(scope = com.composables.sfsymbols.SfEffectScope.ByLayer)
