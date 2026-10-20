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
private val OpenRowHeight = 68.dp
// The folded row with a song in it, and the search row.
private val InlineRowHeight = 48.dp
private val SearchRowHeight = 48.dp
private val ComponentSpacing = 8.dp
private val FoldingIconSize = 32.dp

/* ------------------------------------------------------------------ */
/* Glass                                                               */
/* ------------------------------------------------------------------ */

/**
 * The glass of the bar and of its player, at the strength the reference 1.5.2 ships: saturation
 * x1.6, a 2dp frost, a 19dp lens bending 29dp at the rim, an even grey film at half opacity and a
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
    colorControls(saturation = 1.6f)
    blur(2f.dp.toPx())
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        lens(refractionHeight = 19.2f.dp.toPx(), refractionAmount = 28.8f.dp.toPx())
    }
}

/* ------------------------------------------------------------------ */
/* Motion                                                              */
/* ------------------------------------------------------------------ */

// The reference 1.5.2 morph: every piece rides one critically damped spring at medium-low
// stiffness (its shared-bounds default), with the gooey pulse peaking halfway through.
private val FoldSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val SearchSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
private val GooeyPeakBlur = 12.dp
private const val GooeyDurationMs = 300

/* ------------------------------------------------------------------ */
/* The bar                                                             */
/* ------------------------------------------------------------------ */

/**
 * The bottom bar as one layout folded by one number: the tab group shrinks onto the selected
 * tab, search detaches into its own circle, and the mini player slides into the row between
 * them. Nothing changes parent mid-fold, so there is nothing to flash.
 *
 * Search is the last of [tabs] when [hasSearchTab]; open it sits in the group, folded it becomes
 * the circle on the right, and in [searchMode] it widens into [searchBarContent].
 *
 * The row is what this measures to. With a song, the open bar's player hangs above it, outside
 * its bounds, exactly where the player sheet's own pill sits — the dock only draws it while it is
 * moving or folded, see [FoldingDockMotion].
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
    searchMode: Boolean = false,
    searchBarContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val inSearch = searchMode && searchBarContent != null && hasSearchTab
    val groupTabs = tabs
    val searchTab = if (hasSearchTab) tabs.lastOrNull() else null
    val contentTabs = if (hasSearchTab) tabs.dropLast(1) else tabs

    // The page you are on; off the tabs (and on Search), the last one you were on.
    val sticky = remember { arrayOfNulls<String>(1) }
    val live = contentTabs.firstOrNull { it.key == selectedKey }
    if (live != null) sticky[0] = live.key
    val selected = live ?: contentTabs.firstOrNull { it.key == sticky[0] } ?: contentTabs.firstOrNull()
    val selectedIndex = groupTabs.indexOf(selected).coerceAtLeast(0)
    val tabsCount = groupTabs.size.coerceAtLeast(1)

    val fold = remember { Animatable(if (folded) 1f else 0f) }
    LaunchedEffect(folded) { fold.animateTo(if (folded) 1f else 0f, FoldSpring) }
    val search = remember { Animatable(if (inSearch) 1f else 0f) }
    LaunchedEffect(inSearch) { search.animateTo(if (inSearch) 1f else 0f, SearchSpring) }

    // The gooey pulse, on every change of shape: up to its peak halfway, back to nothing at rest.
    val goo = remember { Animatable(0f) }
    val firstShape = remember { booleanArrayOf(true) }
    LaunchedEffect(folded, inSearch) {
        if (firstShape[0]) {
            firstShape[0] = false
            return@LaunchedEffect
        }
        goo.snapTo(0f)
        goo.animateTo(0f, keyframes {
            durationMillis = GooeyDurationMs
            0f at 0
            1f at GooeyDurationMs / 2 using FastOutSlowInEasing
            0f at GooeyDurationMs
        })
    }

    DisposableEffect(Unit) { onDispose { FoldingDockMotion.fold = 0f } }
    LaunchedEffect(fold) { snapshotFlow { fold.value }.collect { FoldingDockMotion.fold = it } }

    val surfaceBackdrop = rememberLayerBackdrop()
    val foldedOrSearching = folded || inSearch
    val noTap = remember { MutableInteractionSource() }
    val glowScope = rememberCoroutineScope()
    val selectedGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val searchGlow = remember(glowScope) { InteractiveHighlight(animationScope = glowScope) }
    val density = LocalDensity.current
    val gooeyPx = with(density) { GooeyPeakBlur.toPx() }
    val appBackdrop = LocalAppBackdrop.current

    val ink = if (isSystemInDarkTheme()) Color.White else Color.Black
    val inactiveInk = ink.copy(alpha = 0.82f)
    // The glyphs replay a small bounce as the bar folds or opens.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(folded) {
        if (bounce.isRunning || fold.value != (if (folded) 1f else 0f)) {
            bounce.snapTo(0.8f)
            bounce.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f))
        }
    }

    val icon: @Composable (FoldingDockTab, Boolean) -> Unit = { tab, isSelected ->
        Box(
            Modifier
                .size(tab.glyphSize)
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
            contentAlignment = Alignment.Center,
        ) {
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

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .gooey { gooeyPx * goo.value },
        content = {
            // 0: the navigation surface.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(selectedGlow))
                    .layerBackdrop(surfaceBackdrop)
                    .clip(DockShape)
                    .foldingDockGlass(DockShape)
                    .then(selectedGlow.modifier),
            )
            // 1: the full tab group at its resting width, clipped to the shrinking surface.
            Box(
                Modifier
                    .graphicsLayer {
                        alpha = (1f - fold.value.coerceIn(0f, 1f) * 3f).coerceIn(0f, 1f) *
                            (1f - search.value.coerceIn(0f, 1f))
                    }
                    .drawWithContent {
                        val p = fold.value.coerceIn(0f, 1f)
                        val s = search.value.coerceIn(0f, 1f)
                        if (p == 0f && s == 0f) {
                            drawContent()
                        } else {
                            val width = lerp(lerp(size.width, size.height, s), size.height, p)
                            val outline = Path().apply {
                                addRoundRect(RoundRect(0f, 0f, width, size.height, CornerRadius(size.height / 2f)))
                            }
                            clipPath(outline) { this@drawWithContent.drawContent() }
                        }
                    }
                    .then(if (foldedOrSearching) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                val selectedState = rememberUpdatedState(selectedIndex)
                FoldingTabGroup(
                    selectedTabIndex = { selectedState.value },
                    onTabSelected = { index -> groupTabs.getOrNull(index)?.onClick?.invoke() },
                    backdrop = appBackdrop,
                    surfaceBackdrop = surfaceBackdrop,
                    tabsCount = groupTabs.size,
                    accentColor = accentColor,
                    height = OpenRowHeight,
                    indicatorVisible = groupTabs.any { it.key == selectedKey },
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
            // 2: the selected tab alone, shown while folded or searching.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(selectedGlow))
                    .graphicsLayer { alpha = maxOf(fold.value, search.value).coerceIn(0f, 1f) }
                    .then(
                        if (foldedOrSearching) {
                            Modifier.then(selectedGlow.gestureModifier).clickable(noTap, null) {
                                if (folded) onUnfold() else selected?.onClick?.invoke()
                            }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
            ) {
                FoldingTabContent(
                    icon = { selected?.let { icon(it, true) } },
                    title = { selected?.let { title(it, true) } },
                    titleFraction = { (1f - fold.value.coerceIn(0f, 1f)) * (1f - search.value.coerceIn(0f, 1f)) },
                )
            }
            // 3: search's own glass, only while detached from the group.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(searchGlow))
                    .graphicsLayer {
                        alpha = if (searchTab == null) 0f else maxOf(fold.value, search.value).coerceIn(0f, 1f)
                    }
                    .clip(DockShape)
                    .then(if (searchTab != null) Modifier.foldingDockGlass(DockShape) else Modifier)
                    .then(searchGlow.modifier),
            )
            // 4: the detached search control.
            Box(
                Modifier
                    .graphicsLayer(elasticStretchLayerBlock(searchGlow))
                    .graphicsLayer {
                        val p = fold.value.coerceIn(0f, 1f)
                        val s = search.value.coerceIn(0f, 1f)
                        alpha = if (searchTab == null) 0f else maxOf(p, s) * (1f - s * (1f - p))
                    }
                    .then(
                        if (searchTab != null && foldedOrSearching && (!inSearch || folded)) {
                            Modifier.then(searchGlow.gestureModifier).clickable(noTap, null) { searchTab.onClick() }
                        } else {
                            Modifier.clearAndSetSemantics {}
                        },
                    ),
            ) {
                if (searchTab != null) {
                    FoldingTabContent(
                        icon = { icon(searchTab, selectedKey == searchTab.key) },
                        title = { title(searchTab, selectedKey == searchTab.key) },
                        titleFraction = { 1f - fold.value.coerceIn(0f, 1f) },
                    )
                }
            }
            // 5: the search field, grown out of the circle.
            Box(
                Modifier
                    .graphicsLayer { alpha = (search.value * (1f - fold.value)).coerceIn(0f, 1f) }
                    .then(if (!inSearch || folded) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                if (inSearch || search.value > 0f) {
                    searchBarContent?.invoke(Modifier.fillMaxSize())
                }
            }
            // 6: the mini player, one node for every fold state.
            Box(
                Modifier.graphicsLayer {
                    // Open and at rest it is the player sheet's pill that shows, not this one.
                    alpha = if (fold.value > 0.001f) accessoryReveal().coerceIn(0f, 1f) else 0f
                },
            ) {
                accessory?.invoke(
                    Modifier
                        .fillMaxSize()
                        .clip(DockShape)
                        .foldingDockGlass(DockShape),
                    folded,
                )
            }
        },
    ) { children, constraints ->
        val width = constraints.maxWidth
        val expandedHeight = OpenRowHeight.roundToPx().coerceAtMost(constraints.maxHeight)
        val compactHeight = if (accessory != null) InlineRowHeight.roundToPx() else expandedHeight
        val spacing = ComponentSpacing.roundToPx()
        val p = fold.value
        val openHeight = lerp(expandedHeight.toFloat(), SearchRowHeight.toPx(), search.value.coerceIn(0f, 1f))
        val height = lerp(openHeight, compactHeight.toFloat(), p).roundToInt().coerceAtLeast(1)
        val circle = height.coerceAtMost(width)
        val playerWidth = lerp(width.toFloat(), (width - 2 * (compactHeight + spacing)).coerceAtLeast(0).toFloat(), p)
            .roundToInt().coerceIn(0, width)
        val playerHeight = lerp(openPlayerHeight.toPx(), compactHeight.toFloat(), p.coerceIn(0f, 1f))
            .roundToInt().coerceAtLeast(1)
        val player = children[6].measure(Constraints.fixed(playerWidth, playerHeight))
        val geometry = foldGeometry(
            width = width,
            expandedRowHeight = openHeight.roundToInt(),
            collapsedRowHeight = compactHeight,
            spacing = spacing,
            // The sheet's pill sits on top of the band the dock is given, so the open player hangs
            // from there: whatever room the band has over the row, plus the pill itself.
            playerGap = if (constraints.hasBoundedHeight) {
                (constraints.maxHeight - expandedHeight).coerceAtLeast(0)
            } else {
                openPlayerGap.roundToPx()
            },
            playerHeight = player.height,
            hasPlayer = accessory != null,
            fold = p,
            search = search.value,
            tabsCount = tabsCount,
            selectedIndex = selectedIndex,
            inset = 4.dp.toPx(),
        )
        val surface = children[0].measure(Constraints.fixed(geometry.navWidth, height))
        val tabsPlaceable = children[1].measure(Constraints.fixed(width, height))
        val selectedButton = children[2].measure(Constraints.fixed(circle, height))
        val searchSurface = children[3].measure(Constraints.fixed(geometry.searchWidth, height))
        val searchControl = children[4].measure(Constraints.fixed(circle, height))
        val field = children[5].measure(Constraints.fixed(geometry.searchWidth, height))
        // The bar measures to its row; the open player hangs above it.
        layout(width, height) {
            surface.placeRelative(0, 0)
            tabsPlaceable.placeRelative(0, 0)
            selectedButton.placeRelative(geometry.selectedX, 0)
            searchSurface.placeRelative(geometry.searchX, 0)
            searchControl.placeRelative(geometry.searchX, 0)
            field.placeRelative(geometry.searchX, 0)
            player.placeRelative(geometry.playerX, geometry.playerY)
        }
    }
}

private class FoldGeometry(
    val navWidth: Int,
    val searchWidth: Int,
    val searchX: Int,
    val selectedX: Int,
    val playerX: Int,
    val playerY: Int,
)

/**
 * Every position of the folding bar from two progress values, so a reversed fold retraces the
 * same path. Positions are relative to the top of the row.
 */
private fun foldGeometry(
    width: Int,
    expandedRowHeight: Int,
    collapsedRowHeight: Int,
    spacing: Int,
    playerGap: Int,
    playerHeight: Int,
    hasPlayer: Boolean,
    fold: Float,
    search: Float,
    tabsCount: Int,
    selectedIndex: Int,
    inset: Float,
): FoldGeometry {
    fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    val p = fold
    val s = search.coerceIn(0f, 1f)
    val rowHeight = mix(expandedRowHeight.toFloat(), collapsedRowHeight.toFloat(), p).roundToInt().coerceAtLeast(1)
    val circle = rowHeight.coerceAtMost(width)
    val tabWidth = (width - 2 * inset) / tabsCount.coerceAtLeast(1)
    val searchCenter = inset + tabWidth * (tabsCount - 0.5f)
    val searchWidth = mix(circle.toFloat(), (width - circle - spacing).coerceAtLeast(circle).toFloat(), s * (1f - p))
        .roundToInt().coerceAtLeast(circle)
    val detachedSearchX = mix(searchCenter - circle / 2f, (width - circle).toFloat(), p)
    return FoldGeometry(
        navWidth = mix(mix(width.toFloat(), circle.toFloat(), s), circle.toFloat(), p).roundToInt().coerceIn(circle, width),
        searchWidth = searchWidth,
        searchX = mix(detachedSearchX, (width - searchWidth).toFloat(), s).roundToInt(),
        selectedX = mix(inset + tabWidth * (selectedIndex + 0.5f) - circle / 2f, 0f, maxOf(p, s)).roundToInt(),
        playerX = mix(0f, (circle + spacing).toFloat(), p).roundToInt(),
        playerY = if (hasPlayer) mix(-(playerHeight + playerGap).toFloat(), (rowHeight - playerHeight) / 2f, p).roundToInt() else 0,
    )
}

@Composable
private fun FoldingTabContent(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    titleFraction: () -> Float,
) {
    Layout(content = {
        Box(Modifier.size(FoldingIconSize), contentAlignment = Alignment.Center) { icon() }
        Box(Modifier.graphicsLayer { alpha = titleFraction() }) { title() }
    }) { children, constraints ->
        val iconSize = FoldingIconSize.roundToPx()
        val iconPlaceable = children[0].measure(Constraints.fixed(iconSize, iconSize))
        val titlePlaceable = children[1].measure(Constraints(maxWidth = constraints.maxWidth))
        val gap = 2.dp.roundToPx()
        val fullTop = (constraints.maxHeight - iconPlaceable.height - gap - titlePlaceable.height) / 2f
        val top = lerp((constraints.maxHeight - iconPlaceable.height) / 2f, fullTop, titleFraction()).roundToInt()
        layout(constraints.maxWidth, constraints.maxHeight) {
            iconPlaceable.placeRelative((constraints.maxWidth - iconPlaceable.width) / 2, top)
            titlePlaceable.placeRelative((constraints.maxWidth - titlePlaceable.width) / 2, top + iconPlaceable.height + gap)
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
