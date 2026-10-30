/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.desktop.ui.shell

import com.ozyern.exhale.desktop.ui.pressScale

import com.ozyern.exhale.desktop.ui.rememberPosition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.ozyern.exhale.desktop.ui.formatTime
import com.ozyern.exhale.desktop.ui.rememberArtworkColors
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.ozyern.exhale.desktop.player.DesktopPlayer
import com.ozyern.exhale.desktop.player.PlayerState
import com.ozyern.exhale.desktop.ui.AppShortcuts
import com.ozyern.exhale.desktop.ui.LoadingRing
import com.ozyern.exhale.desktop.ui.Motion
import com.ozyern.exhale.desktop.ui.DockIcons
import com.ozyern.exhale.desktop.ui.NavIcons
import com.ozyern.exhale.desktop.ui.PressableIcon
import com.ozyern.exhale.desktop.ui.TransportIcons
import com.ozyern.exhale.desktop.ui.glass.LocalAppBackdrop
import com.ozyern.exhale.desktop.ui.glass.liquidGlass
import com.ozyern.exhale.desktop.ui.player.GlassTrack
import com.ozyern.exhale.desktop.ui.player.PlayerPanel
import com.ozyern.exhale.desktop.ui.resizedThumbnail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A dock tab: its glyph at rest, and the heavier one it takes on under the lens. */
data class DockTab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

val DockHeight = 64.dp

/** Tab strip (420) + gap (10) + search circle (64): the dock row, and the mini player above it. */
val DockMaxWidth = 494.dp
private val TabStripMaxWidth = 420.dp
private val DockCircleSize = 64.dp

/** The desktop dock's islands at rest, and folded while a page scrolls. */
val DesktopDockHeight = 68.dp
private val DesktopDockCompactHeight = 56.dp

// The dock's glass: Android's frostedGlassModifier — chrome tint plus DockGlassExtraTint, 68dp blur.
private const val DockTint = 0.37f
private val DockBlur = 68.dp

/**
 * Opacity hands over on matched linear ramps, so two states of glass stacked mid-morph always add
 * up to one plate's worth; the shape change is carried by the underdamped spring alone.
 */
private val MorphFade: FiniteAnimationSpec<Float> = tween(durationMillis = 190, easing = LinearEasing)
private val MorphShape: FiniteAnimationSpec<Float> = spring(dampingRatio = Motion.AquamorphicDamping, stiffness = Motion.AquamorphicStiffness)

@Composable
private fun AnimatedVisibilityScope.morph(label: String, spec: FiniteAnimationSpec<Float>): State<Float> =
    transition.animateFloat(transitionSpec = { spec }, label = label) { if (it == EnterExitState.Visible) 1f else 0f }

/** Lays a child out at [fraction] of its width, fading with it — a label that folds away. */
private fun Modifier.collapseWidth(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0))
    val f = fraction().coerceIn(0f, 1f)
    layout((placeable.width * f).roundToInt(), placeable.height) {
        placeable.placeWithLayer(0, 0) { alpha = f }
    }
}

/**
 * The Android LiquidGlassBottomBar, two scroll-driven states that morph into each other:
 *  - **A** (at rest): a wide glass tab strip with a sliding lens capsule, and the Search circle.
 *  - **B** (scrolled): a Home circle, a centre pill — the mini player when something is playing —
 *    and the Search circle. The strip folds into the Home circle; the pill slides out from behind it.
 */
@Composable
fun LiquidDock(
    tabs: List<DockTab>,
    selected: Int,
    searchSelected: Boolean,
    collapsed: Boolean,
    state: PlayerState,
    player: DesktopPlayer,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onExpandPlayer: () -> Unit,
    onPillBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasNowPlaying = state.current != null
    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurface

    AnimatedContent(
        targetState = collapsed,
        // Nothing on the container: every piece animates itself, so the search circle — the same
        // in both states — stays nailed down instead of cross-fading against itself.
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(SizeTransform(clip = false) { _, _ -> snap() }) },
        modifier = modifier.fillMaxWidth().height(DockHeight),
        label = "dock",
    ) { isCollapsed ->
        if (!isCollapsed) {
            val ink by morph("stripInk", MorphFade)
            val fold by morph("stripFold", MorphShape)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LiquidTabStrip(
                    tabs = tabs,
                    selected = selected,
                    onSelect = onTab,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = TabStripMaxWidth)
                        // Folds along its length into the 64dp the home circle will occupy.
                        .graphicsLayer {
                            alpha = ink
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            val folded = (DockCircleSize.toPx() / size.width.coerceAtLeast(1f)).coerceIn(0.05f, 1f)
                            scaleX = folded + (1f - folded) * fold
                        },
                )
                FrostedCircle(onClick = onSearch) {
                    Icon(if (searchSelected) DockIcons.SearchSelected else DockIcons.Search, contentDescription = "Search", tint = if (searchSelected) accent else rest, modifier = Modifier.size(24.dp))
                }
            }
        } else {
            val circleInk by morph("homeInk", MorphFade)
            val circleShape by morph("homeShape", MorphShape)
            val pillInk by morph("pillInk", MorphFade)
            val pillShape by morph("pillShape", MorphShape)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FrostedCircle(
                    onClick = { onTab(0) },
                    // Grows in place — it's where the strip folded to.
                    modifier = Modifier.graphicsLayer {
                        alpha = circleInk
                        val grow = 0.74f + 0.26f * circleShape
                        scaleX = grow
                        scaleY = grow
                    },
                ) {
                    Icon(if (selected == 0) tabs.first().selectedIcon else tabs.first().icon, contentDescription = tabs.first().label, tint = if (selected == 0) accent else rest, modifier = Modifier.size(24.dp))
                }
                FrostedPill(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { if (hasNowPlaying) onPillBounds(it.boundsInRoot()) }
                        // Out from behind the home circle, and back in behind it.
                        .graphicsLayer {
                            alpha = pillInk
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = 0.14f + 0.86f * pillShape
                            scaleY = 0.88f + 0.12f * pillShape
                        },
                    onClick = if (hasNowPlaying) onExpandPlayer else null,
                ) {
                    if (hasNowPlaying) {
                        MiniPlayerContent(state, player, compact = true)
                    } else if (selected >= 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(tabs[selected].selectedIcon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(tabs[selected].label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = accent)
                        }
                    }
                }
                FrostedCircle(onClick = onSearch) {
                    Icon(if (searchSelected) DockIcons.SearchSelected else DockIcons.Search, contentDescription = "Search", tint = if (searchSelected) accent else rest, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/**
 * The dock for a PC's width. The phone dock is one thumb-sized bar; a window has room, so the
 * dock splits into three glass islands across it:
 *  - **left** — the tabs with their lens capsule, and a real search field you can type into from any page;
 *  - **centre** — what's playing: cover, title, a progress line you can click, and transport;
 *  - **right** — lyrics, up next, volume and settings.
 * Scrolling a page folds it: islands shrink, tab labels and the volume slider fold away, the search
 * field closes to a circle — and all of it opens again the moment you scroll back.
 */
@Composable
fun DesktopDock(
    tabs: List<DockTab>,
    selected: Int,
    searchSelected: Boolean,
    compact: Boolean,
    state: PlayerState,
    player: DesktopPlayer,
    searchQuery: String,
    searchFocus: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchFocused: () -> Unit,
    onTab: (Int) -> Unit,
    onExpandPlayer: (PlayerPanel) -> Unit,
    onIslandBounds: (Rect) -> Unit,
    onArtBounds: (Rect) -> Unit,
    artAlpha: () -> Float,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalAppBackdrop.current ?: return
    val openness by animateFloatAsState(
        if (compact) 0f else 1f,
        spring(dampingRatio = Motion.AquamorphicDamping, stiffness = Motion.AquamorphicStiffness),
        label = "dock_open",
    )
    val islandHeight = lerp(DesktopDockCompactHeight, DesktopDockHeight, openness)
    val shape = RoundedCornerShape(percent = 50)
    val hasSong = state.current != null
    // The dock arrives with the window: it settles up into place once, on a spring.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 240f)) }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val t = intro.value
                alpha = t.coerceIn(0f, 1f)
                translationY = (1f - t) * 34.dp.toPx()
            },
    ) {
    // Below this the search field waits as a circle until it's wanted, so what's playing keeps its room.
    val roomy = maxWidth >= 1500.dp
    Row(
        Modifier.fillMaxWidth().height(DesktopDockHeight),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .height(islandHeight)
                .liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidTabStrip(
                tabs = tabs,
                selected = selected,
                onSelect = onTab,
                modifier = Modifier.width(lerp(58.dp, 146.dp, openness) * tabs.size),
                height = islandHeight - 8.dp,
                horizontal = true,
                labelFraction = { openness },
                plate = false,
            )
            Spacer(Modifier.width(6.dp))
            DockSearchField(
                query = searchQuery,
                selected = searchSelected,
                roomy = roomy,
                openness = openness,
                focus = searchFocus,
                height = islandHeight - 16.dp,
                onChange = onSearchQueryChange,
                onSubmit = onSearchSubmit,
                onFocused = onSearchFocused,
            )
            Spacer(Modifier.width(4.dp))
        }

        Box(Modifier.weight(1f).height(DesktopDockHeight), contentAlignment = Alignment.BottomCenter) {
            IslandVisibility(visible = hasSong) {
                NowPlayingIsland(
                    state = state,
                    player = player,
                    height = islandHeight,
                    openness = openness,
                    onExpandPlayer = onExpandPlayer,
                    onIslandBounds = onIslandBounds,
                    onArtBounds = onArtBounds,
                    artAlpha = artAlpha,
                    modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                )
            }
        }

        Row(
            Modifier
                .height(islandHeight)
                .volumeWheel(player)
                .liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DockIconButton(TransportIcons.Lyrics, "Lyrics", enabled = hasSong) { onExpandPlayer(PlayerPanel.Lyrics) }
            DockIconButton(TransportIcons.Queue, "Up next", enabled = hasSong) { onExpandPlayer(PlayerPanel.Queue) }
            DockIconButton(if (state.volume == 0) TransportIcons.VolumeOff else TransportIcons.VolumeUp, "Mute", onClick = player::toggleMute)
            Box(Modifier.collapseWidth { openness }) {
                GlassTrack(
                    value = state.volume / 100f,
                    onChange = { player.setVolume((it * 100).roundToInt()) },
                    onCommit = { player.setVolume((it * 100).roundToInt()) },
                    restHeight = 5.dp,
                    modifier = Modifier.width(if (roomy) 110.dp else 88.dp).padding(end = 10.dp),
                )
            }
            DockIconButton(NavIcons.Settings, "Settings", onClick = onOpenSettings)
        }
    }
    }
}

/** Outside any Row, so it's the plain AnimatedVisibility rather than the row-scoped one. */
@Composable
private fun IslandVisibility(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        // It rises out of the dock's own line rather than appearing in place.
        enter = fadeIn(tween(200)) +
            scaleIn(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness), initialScale = 0.88f, transformOrigin = TransformOrigin(0.5f, 1f)) +
            slideInVertically(spring(Motion.AquamorphicDamping, Motion.AquamorphicStiffness)) { it / 4 },
        exit = fadeOut(tween(140)) +
            scaleOut(tween(150), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)) +
            slideOutVertically(tween(150)) { it / 5 },
    ) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NowPlayingIsland(
    state: PlayerState,
    player: DesktopPlayer,
    height: Dp,
    openness: Float,
    onExpandPlayer: (PlayerPanel) -> Unit,
    onIslandBounds: (Rect) -> Unit,
    onArtBounds: (Rect) -> Unit,
    artAlpha: () -> Float,
    modifier: Modifier,
) {
    val song = state.current ?: return
    val backdrop = LocalAppBackdrop.current ?: return
    val shape = RoundedCornerShape(percent = 50)
    val duration = state.durationSeconds
    val position = player.rememberPosition()
    val fraction: () -> Float = { if (duration > 0) (position.value / duration).toFloat().coerceIn(0f, 1f) else 0f }
    val artShape = RoundedCornerShape(12.dp)
    // The cover's own colour washes in from the left of the glass, and changes with the song.
    val colors by rememberArtworkColors(song.thumbnail.resizedThumbnail(120))
    val glow by animateColorAsState((colors.firstOrNull() ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.3f), tween(700), label = "island_glow")
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    // Under the pointer it lifts a touch and brightens; pressed, it gives.
    val lift by pressScale(pressed, hovered, pressedScale = 0.985f, hoverScale = 1.01f)
    val sheen by animateFloatAsState(if (hovered) 1f else 0f, tween(180), label = "island_sheen")

    Box(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .onGloballyPositioned { onIslandBounds(it.boundsInRoot()) }
            .volumeWheel(player)
            .liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.horizontalGradient(listOf(glow, glow.copy(alpha = 0f)), startX = 0f, endX = size.width * 0.6f))
                if (sheen > 0f) drawRect(Color.White.copy(alpha = 0.035f * sheen))
            }
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onExpandPlayer(PlayerPanel.None) },
    ) {
        Row(Modifier.fillMaxSize().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val artLift by animateFloatAsState(if (hovered) 1.06f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "island_art")
            Box(
                Modifier
                    .size(height - 16.dp)
                    .graphicsLayer {
                        scaleX = artLift
                        scaleY = artLift
                    }
                    .onGloballyPositioned { onArtBounds(it.boundsInRoot()) }
                    .graphicsLayer { alpha = artAlpha() }
                    // The cover sits above the glass with its own shadow, tinted by its own colour.
                    .shadow(10.dp, artShape, ambientColor = glow, spotColor = glow)
                    .clip(artShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = song.thumbnail, animationSpec = tween(380), label = "island_art") { url ->
                    AsyncImage(url.resizedThumbnail(1200), contentDescription = "Open player", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                        LoadingRing(Modifier.size(20.dp), color = Color.White, stroke = 2.dp)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                AnimatedContent(
                    targetState = song,
                    contentKey = { it.id },
                    transitionSpec = {
                        // The old title leaves upward on a spring of its own, so the two never cross flatly.
                        (slideInVertically(spring(0.78f, 300f)) { it / 2 } + fadeIn(tween(230))) togetherWith
                            (slideOutVertically(spring(1f, 520f)) { -it / 2 } + fadeOut(tween(120)))
                    },
                    label = "island_song",
                ) { current ->
                    Column {
                        Text(current.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.basicMarquee())
                        Text(
                            state.error ?: current.artists.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                DockProgress(
                    fraction = fraction,
                    duration = duration,
                    onSeek = { if (duration > 0) player.seekTo(it * duration) },
                    modifier = Modifier.fillMaxWidth().collapseHeight { openness },
                )
            }
            Spacer(Modifier.width(10.dp))
            PressableIcon(onClick = player::previous, size = 40.dp) {
                Icon(TransportIcons.Previous, "Previous", modifier = Modifier.size(24.dp))
            }
            PlayPauseSquircle(state.isPlaying, state.isLoading, lerp(38.dp, 44.dp, openness), player::togglePlayPause)
            PressableIcon(onClick = player::next, size = 40.dp, enabled = state.hasNext) {
                Icon(TransportIcons.Next, "Next", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (state.hasNext) 1f else 0.4f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

private fun Modifier.collapseHeight(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val f = fraction().coerceIn(0f, 1f)
    layout(placeable.width, (placeable.height * f).roundToInt()) {
        placeable.placeWithLayer(0, 0) { alpha = f }
    }
}

/**
 * The island's progress line. Under the pointer it thickens, grows a knob and shows the time you're
 * pointing at; click to jump there, or drag along it to scrub — the line follows the pointer, and the
 * song moves when you let go.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DockProgress(
    fraction: () -> Float,
    duration: Double,
    onSeek: (Float) -> Unit,
    modifier: Modifier,
    fillColor: Color? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var pointerX by remember { mutableFloatStateOf(-1f) }
    var scrub by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val engaged = hovered || scrub != null
    val thickness by animateDpAsState(if (engaged) 6.dp else 3.dp, spring(0.6f, 500f), label = "dock_progress")
    val knob by animateFloatAsState(if (engaged) 1f else 0f, spring(0.6f, 600f), label = "dock_progress_knob")
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val fill = fillColor ?: MaterialTheme.colorScheme.primary
    val latestSeek by rememberUpdatedState(onSeek)
    Box(
        modifier
            .padding(top = 4.dp)
            .height(12.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Move) { pointerX = it.changes.first().position.x }
            .onPointerEvent(PointerEventType.Exit) { if (scrub == null) pointerX = -1f }
            .pointerInput(Unit) { detectTapGestures { latestSeek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { scrub = (it.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        scrub?.let { latestSeek(it) }
                        scrub = null
                    },
                    onDragCancel = { scrub = null },
                ) { change, _ ->
                    change.consume()
                    pointerX = change.position.x
                    scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(thickness).clip(CircleShape).background(track))
        // Drawn, not laid out: the position moves the line without remeasuring anything.
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val f = scrub ?: fraction()
            val h = thickness.toPx()
            val y = size.height / 2f
            val end = (size.width * f).coerceIn(h / 2f, size.width)
            if (f > 0f) {
                drawLine(
                    brush = Brush.horizontalGradient(listOf(fill.copy(alpha = 0.6f), fill), startX = 0f, endX = end),
                    start = Offset(h / 2f, y),
                    end = Offset((end - h / 2f).coerceAtLeast(h / 2f), y),
                    strokeWidth = h,
                    cap = StrokeCap.Round,
                )
            }
            if (knob > 0.01f) drawCircle(Color.White, radius = 5.dp.toPx() * knob, center = Offset(end, y))
        }
        val scrubbing = scrub
        val x = if (scrubbing != null) scrubbing * widthPx else pointerX
        if (engaged && x >= 0f && duration > 0) {
            val at = (x / widthPx).coerceIn(0f, 1f) * duration
            Popup(popupPositionProvider = remember(x) { AboveAt(x) }) {
                Text(
                    formatTime(at),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Centres a small popup above its anchor, [x] pixels in from the anchor's left edge. */
private class AboveAt(private val x: Float) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        IntOffset(
            (anchorBounds.left + x.roundToInt() - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            anchorBounds.top - popupContentSize.height - 10,
        )
}

/** Scroll over the dock to change the volume, about 4% a notch; a touchpad's small steps add up. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Modifier.volumeWheel(player: DesktopPlayer): Modifier {
    val carry = remember { floatArrayOf(0f) }
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
        if (dy == 0f) return@onPointerEvent
        carry[0] -= dy * 4f
        val step = carry[0].toInt()
        if (step != 0) {
            carry[0] -= step
            player.setVolume((player.state.value.volume + step).coerceIn(0, 100))
        }
        event.changes.forEach { it.consume() }
    }
}

/** A search field that lives in the dock: type from any page and Search opens to show you. */
@Composable
private fun DockSearchField(
    query: String,
    selected: Boolean,
    roomy: Boolean,
    openness: Float,
    focus: FocusRequester,
    height: Dp,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onFocused: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val fill by animateColorAsState(
        if (focused || selected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.07f),
        label = "dock_search_fill",
    )
    // A circle everywhere but Search: a field sitting open on Home is a lot of dock for something
    // nobody is typing into. It opens when you click it, when you're searching, or when it holds a query.
    val wanted = focused || selected || query.isNotEmpty()
    val want by animateFloatAsState(
        if (wanted) 1f else 0f,
        spring(dampingRatio = Motion.AquamorphicDamping, stiffness = Motion.AquamorphicStiffness),
        label = "dock_search_open",
    )
    val open = (want * openness).coerceIn(0f, 1f)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .width(lerp(height, 250.dp, open))
            .height(height)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { runCatching { focus.requestFocus() } }
            .padding(start = (height - 20.dp) / 2, end = lerp(0.dp, 12.dp, open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (focused || selected) DockIcons.SearchSelected else DockIcons.Search, contentDescription = "Search", tint = if (focused || selected) accent else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp).graphicsLayer { alpha = open }, contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        focused = it.isFocused
                        // While this holds focus, Space is a space — not play/pause.
                        AppShortcuts.setTyping(it.isFocused)
                        if (it.isFocused) onFocused()
                    }
                    .onPreviewKeyEvent {
                        if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (it.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                onSubmit()
                                true
                            }
                            Key.Escape -> {
                                focusManager.clearFocus()
                                true
                            }
                            else -> false
                        }
                    },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DockIconButton(icon: ImageVector, description: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val scale by pressScale(pressed, hovered, pressedScale = 0.86f, hoverScale = 1.08f)
    val wash by animateFloatAsState(if (hovered && enabled) 0.09f else 0f, tween(160), label = "dock_button_wash")
    // What the button does, above it, after a moment's hover.
    TooltipArea(
        tooltip = { DockTooltip(description) },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.TopCenter, alignment = Alignment.TopCenter, offset = DpOffset(0.dp, (-12).dp)),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = wash))
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DockTooltip(text: String) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun FrostedCircle(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = LocalAppBackdrop.current ?: return
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Underdamped and stiff: a droplet that overshoots, like the capsule beside it.
    val scale by pressScale(pressed, pressedScale = 0.88f)
    Box(
        modifier = modifier
            .size(DockCircleSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(CircleShape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun FrostedPill(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val backdrop = LocalAppBackdrop.current ?: return
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(DockHeight)
            .liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The dock's tab strip, built the way the Android one is: a contentless glass plate, the visible
 * row on it, an invisible accent twin of the row, and a capsule that draws plate + twin through a
 * lens — so the selected tab's accent colour is the hidden layer *seen through the glass*, bent at
 * the rim. Click springs the capsule over with a swell; drag and it follows, stretching with speed,
 * and lands on the tab the gesture was heading for.
 *
 * [horizontal] lays icon and label side by side for the desktop dock, where [labelFraction] folds
 * the labels away; [plate] is off there because the island under it is already glass.
 */
@Composable
private fun LiquidTabStrip(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = DockHeight,
    horizontal: Boolean = false,
    labelFraction: () -> Float = { 1f },
    plate: Boolean = true,
) {
    val backdrop = LocalAppBackdrop.current ?: return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(percent = 50)
    val inset = 4.dp
    val insetPx = with(density) { inset.toPx() }
    val capsuleHeight = height - inset * 2
    val lastIndex = (tabs.size - 1).coerceAtLeast(0)

    var widthPx by remember { mutableFloatStateOf(0f) }
    val tabWidthPx = if (widthPx > 0f) ((widthPx - insetPx * 2) / tabs.size).coerceAtLeast(0f) else 0f

    val capsule = remember { Animatable(selected.coerceIn(0, lastIndex).toFloat()) }
    val press = remember { Animatable(0f) }
    val swellX = remember { Animatable(1f) }
    val swellY = remember { Animatable(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var velocity by remember { mutableFloatStateOf(0f) }
    val valueSpring = spring<Float>(dampingRatio = 1f, stiffness = 1000f)

    // 78dp of swell on a 56dp capsule: it bulges past the bar while held, a blob sitting on it.
    fun swell(on: Boolean) {
        scope.launch {
            launch { press.animateTo(if (on) 1f else 0f, spring(1f, 1000f)) }
            launch { swellX.animateTo(if (on) (if (horizontal) 1.12f else 78f / 56f) else 1f, spring(0.6f, 250f)) }
            launch { swellY.animateTo(if (on) (if (horizontal) 1.22f else 78f / 56f) else 1f, spring(0.7f, 250f)) }
        }
    }

    fun tapTo(index: Int) {
        if (index == selected) {
            onSelect(index)
            return
        }
        swell(true)
        scope.launch {
            capsule.animateTo(index.toFloat(), valueSpring)
            delay(40)
            swell(false)
        }
        onSelect(index)
    }

    // Route changes from elsewhere (the Home circle, a search) move the capsule without the swell.
    LaunchedEffect(selected) {
        if (!dragging && selected >= 0 && capsule.targetValue != selected.toFloat()) capsule.animateTo(selected.toFloat(), valueSpring)
    }

    val glassBackdrop = rememberLayerBackdrop()
    val tabsBackdrop = rememberLayerBackdrop()
    val combined = rememberCombinedBackdrop(glassBackdrop, tabsBackdrop)

    Box(
        modifier = modifier
            .height(height)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(tabWidthPx, lastIndex, selected) {
                if (tabWidthPx <= 0f) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        dragValue = capsule.value
                        velocity = 0f
                        swell(true)
                    },
                    onDragEnd = {
                        // Land where the gesture was heading, never more than one tab past it.
                        val landed = (dragValue + (velocity * 0.25f).coerceIn(-0.5f, 0.5f)).roundToInt().coerceIn(0, lastIndex)
                        val from = dragValue
                        scope.launch {
                            capsule.snapTo(from)
                            dragging = false
                            // A thrown capsule settles with a little give; a tap still snaps flat.
                            capsule.animateTo(landed.toFloat(), spring(dampingRatio = 0.72f, stiffness = 420f))
                        }
                        val v = velocity
                        scope.launch { Animatable(v).animateTo(0f, spring(0.5f, 300f)) { velocity = value } }
                        swell(false)
                        if (landed != selected) onSelect(landed)
                    },
                    onDragCancel = {
                        dragging = false
                        swell(false)
                    },
                ) { _, dx ->
                    val delta = dx / tabWidthPx
                    val raw = dragValue + delta
                    // Past either end the capsule keeps moving, but against resistance — it pulls back
                    // into the bar when let go rather than stopping dead against an invisible wall.
                    dragValue = when {
                        raw < 0f -> raw * 0.34f
                        raw > lastIndex -> lastIndex + (raw - lastIndex) * 0.34f
                        else -> raw
                    }
                    velocity = velocity * 0.72f + delta * 2.4f
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        // 1 + 2: the plate, and the row you can see on it. The whole bar breathes a little under the press.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val s = lerp(1f, 1.012f, press.value)
                scaleX = s
                scaleY = s
            },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(glassBackdrop)
                    .then(if (plate) Modifier.liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = DockBlur) else Modifier),
            )
            Row(Modifier.fillMaxSize().padding(inset), verticalAlignment = Alignment.CenterVertically) {
                tabs.forEachIndexed { index, tab ->
                    // The unselected tabs sit back a shade, so the one under the capsule is the one you see,
                    // and each swells as the capsule arrives over it — the glyph greets the lens.
                    TabItem(
                        tab,
                        tint = rest.copy(alpha = 0.76f),
                        filled = false,
                        scale = {
                            val distance = kotlin.math.abs((if (dragging) dragValue else capsule.value) - index)
                            1f + 0.09f * (1f - distance.coerceIn(0f, 1f))
                        },
                        onClick = { tapTo(index) },
                        horizontal = horizontal,
                        labelFraction = labelFraction,
                    )
                }
            }
        }

        // 3: the accent twin the lens reads — invisible on its own.
        Row(
            Modifier
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .fillMaxWidth()
                .height(capsuleHeight)
                .padding(horizontal = inset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                TabItem(tab, tint = accent, filled = true, scale = { lerp(1f, 1.16f, press.value) }, onClick = null, horizontal = horizontal, labelFraction = labelFraction)
            }
        }

        // 4: the capsule.
        if (tabWidthPx > 0f && selected >= 0) {
            Box(
                Modifier
                    .padding(horizontal = inset)
                    .graphicsLayer { translationX = (if (dragging) dragValue else capsule.value) * tabWidthPx }
                    .drawBackdrop(
                        backdrop = combined,
                        shape = { shape },
                        effects = {
                            // Never fully off: at rest it already bends the label under it.
                            val depth = 0.4f + 0.6f * press.value
                            lens(9f.dp.toPx() * depth, 14f.dp.toPx() * depth, true)
                        },
                        highlight = { Highlight.Ambient.copy(alpha = 0.4f + 0.6f * press.value) },
                        innerShadow = {
                            InnerShadow(
                                radius = 3f.dp + 5f.dp * press.value,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = 0.35f + 0.65f * press.value,
                            )
                        },
                        layerBlock = {
                            // Conservation of volume, roughly: faster is longer and flatter.
                            scaleX = swellX.value / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                            scaleY = swellY.value * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                        },
                        onDrawSurface = {
                            // A lit plate the selected tab sits on, washed with a little of the accent — the
                            // tab's own colour then arrives through the lens rather than being painted on.
                            drawRect(Color.White.copy(alpha = 0.2f), alpha = 1f - press.value * 0.6f)
                            drawRect(accent.copy(alpha = 0.12f), alpha = 1f - press.value * 0.4f)
                        },
                    )
                    .height(capsuleHeight)
                    .width(with(density) { tabWidthPx.toDp() }),
            )
        }
    }
}

@Composable
private fun RowScope.TabItem(
    tab: DockTab,
    tint: Color,
    filled: Boolean,
    scale: () -> Float,
    onClick: (() -> Unit)?,
    horizontal: Boolean = false,
    labelFraction: () -> Float = { 1f },
) {
    val base = Modifier
        .then(if (onClick != null) Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick) else Modifier)
        .fillMaxHeight()
        .weight(1f)
        .graphicsLayer {
            val s = scale()
            scaleX = s
            scaleY = s
        }
    // The accent twin (the layer the capsule's lens reads) is drawn heavier: on the selected tab the
    // glyph and its label thicken and grow a touch, the way a chosen tab does on a glass bar.
    if (horizontal) {
        Row(base, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(if (filled) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(if (filled) 24.dp else 22.dp))
            Box(Modifier.collapseWidth(labelFraction)) {
                Text(
                    tab.label,
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = if (filled) 13.5.sp else 13.sp,
                    fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    } else {
        Column(base, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (filled) tab.selectedIcon else tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(if (filled) 26.dp else 24.dp))
            Text(
                tab.label,
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (filled) 10.5.sp else 10.sp,
                fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** State A's standalone pill floating above the dock. Click anywhere to open the player. */
@Composable
fun MiniPlayerPill(state: PlayerState, player: DesktopPlayer, onExpand: () -> Unit, onBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    val backdrop = LocalAppBackdrop.current ?: return
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .height(DockHeight)
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .liquidGlass(shape, backdrop, tintAlpha = DockTint, blurRadius = 52.dp)
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExpand),
    ) {
        MiniPlayerContent(state, player, compact = false)
    }
}

/** The Android NewMiniPlayerContent: disc artwork, two lines, and the squircle play button. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerContent(state: PlayerState, player: DesktopPlayer, compact: Boolean) {
    val song = state.current ?: return
    val duration = state.durationSeconds
    val position = player.rememberPosition()
    val fraction: () -> Float = { if (duration > 0) (position.value / duration).toFloat().coerceIn(0f, 1f) else 0f }

    Box(Modifier.fillMaxSize()) {
        MiniProgress(fraction, compact)
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniArtwork(song.thumbnail, state.isLoading, if (compact) 38.dp else 48.dp, playing = state.isPlaying)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.Center) {
                AnimatedContent(targetState = song.title, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mini_title") { title ->
                    Text(
                        title,
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    song.artists.joinToString { it.name },
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(4.dp))
            PlayPauseSquircle(state.isPlaying, state.isLoading, if (compact) 34.dp else 48.dp, player::togglePlayPause)
        }
    }
}

@Composable
private fun BoxScope.MiniProgress(progressFraction: () -> Float, compact: Boolean) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val progress = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = if (compact) 22.dp else 26.dp, end = if (compact) 22.dp else 26.dp, bottom = if (compact) 4.dp else 5.dp)
            .height(3.dp),
    ) {
        val y = size.height / 2f
        val fraction = progressFraction()
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        if (fraction > 0f) {
            drawLine(
                brush = Brush.horizontalGradient(listOf(progress.copy(alpha = 0.45f), progress), startX = 0f, endX = (size.width * fraction).coerceAtLeast(1f)),
                start = Offset(0f, y),
                end = Offset(size.width * fraction, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun MiniArtwork(url: String, loading: Boolean, size: Dp, playing: Boolean = false) {
    // Playing, the disc wears a ring of the accent; paused, it's the plain rim.
    val ring by animateColorAsState(
        if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f),
        tween(320),
        label = "mini_art_ring",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .shadow(6.dp, CircleShape, clip = false)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.5.dp, ring, CircleShape),
    ) {
        AsyncImage(url.resizedThumbnail(226), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (loading) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                LoadingRing(Modifier.size(22.dp), color = Color.White, stroke = 2.dp)
            }
        }
    }
}

/**
 * The pill's play button. The state is carried by the shape: a circle when paused, a squircle when
 * playing, springing between the two with an overshoot — readable from the corner of the eye.
 */
@Composable
fun PlayPauseSquircle(isPlaying: Boolean, isLoading: Boolean, size: Dp, onClick: () -> Unit) {
    val corner by animateDpAsState(
        if (isPlaying) size * 0.32f else size / 2f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "play_corner",
    )
    val shape = RoundedCornerShape(corner)
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by pressScale(pressed, pressedScale = Motion.PressedScale)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(6.dp, shape, clip = false, ambientColor = accent, spotColor = accent)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.80f))))
            .clickable(interactionSource = interaction, indication = null, enabled = !isLoading, onClick = onClick),
    ) {
        if (isLoading) {
            LoadingRing(Modifier.size(size * 0.46f), color = MaterialTheme.colorScheme.onPrimary, stroke = 2.dp)
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(120)) + scaleIn(initialScale = 0.55f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))) togetherWith
                        (fadeOut(tween(90)) + scaleOut(targetScale = 1.35f, animationSpec = tween(90)))
                },
                label = "play_icon",
            ) { playing ->
                Icon(
                    if (playing) TransportIcons.Pause else TransportIcons.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.46f),
                )
            }
        }
    }
}
