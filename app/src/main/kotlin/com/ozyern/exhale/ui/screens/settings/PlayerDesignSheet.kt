/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.screens.settings

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ozyern.exhale.LocalDatabase
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.PlayerArtworkStyle
import com.ozyern.exhale.constants.PlayerArtworkStyleKey
import com.ozyern.exhale.constants.PlayerBackgroundStyle
import com.ozyern.exhale.constants.PlayerBackgroundStyleKey
import com.ozyern.exhale.constants.PlayerGradientAngleKey
import com.ozyern.exhale.constants.PlayerGradientStopsKey
import com.ozyern.exhale.constants.PlayerLook
import com.ozyern.exhale.constants.PlayerLookKey
import com.ozyern.exhale.constants.PlayerStaticColorKey
import com.ozyern.exhale.constants.ShowPlayerThumbnailShadowKey
import com.ozyern.exhale.constants.ShowUpNextKey
import com.ozyern.exhale.constants.HideVolumeBarKey
import com.ozyern.exhale.constants.SliderStyle
import com.ozyern.exhale.constants.SliderStyleKey
import com.ozyern.exhale.constants.UseAppleMusicPlayerKey
import com.ozyern.exhale.ui.component.LocalMenuState
import com.ozyern.exhale.ui.component.MenuState
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import com.ozyern.exhale.ui.ios.compat.Switch
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.ios.foundation.IosMetrics
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.player.PlayerBackdropPreview
import com.ozyern.exhale.ui.player.PlayerDesign
import com.ozyern.exhale.ui.player.PlayerLookSpec
import com.ozyern.exhale.ui.player.PlayerPhonePreview
import com.ozyern.exhale.ui.player.PlayerSeekBarSample
import com.ozyern.exhale.ui.player.PreviewTrack
import com.ozyern.exhale.ui.player.rememberPreviewPosition
import com.ozyern.exhale.ui.theme.decodeGradientStops
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs


/**
 * Player design, chosen by looking: a curved row of phones each running the real player
 * for the real song, and under it the options that belong to whichever design is in use.
 * Reads and writes the player's own preferences, so it stays live wherever it is shown.
 */
@Composable
fun ColumnScope.PlayerDesignSheetContent(onMoreOptions: () -> Unit) {
    val (artworkStyle, setArtworkStyle) = rememberEnumPreference(PlayerArtworkStyleKey, PlayerArtworkStyle.CARD)
    val (background, setBackground) = rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.GRADIENT)
    val (sliderStyle, setSliderStyle) = rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)
    val (staticColor) = rememberPreference(PlayerStaticColorKey, 0xFF1A1A1A.toInt())
    val (gradientRaw) = rememberPreference(PlayerGradientStopsKey, "")
    val (gradientAngle) = rememberPreference(PlayerGradientAngleKey, 90f)
    val (useV2, setUseV2) = rememberPreference(UseAppleMusicPlayerKey, true)
    val (look, setLook) = rememberEnumPreference(PlayerLookKey, PlayerLook.SQUARED)
    val (shadow, setShadow) = rememberPreference(ShowPlayerThumbnailShadowKey, false)
    val (showUpNext, setShowUpNext) = rememberPreference(ShowUpNextKey, false)
    val (hideVolumeBar, setHideVolumeBar) = rememberPreference(HideVolumeBarKey, false)

    val design = when {
        useV2 -> PlayerDesign.APPLE_MUSIC_V2
        background == PlayerBackgroundStyle.APPLE_MUSIC -> PlayerDesign.APPLE_MUSIC
        artworkStyle == PlayerArtworkStyle.VINYL -> PlayerDesign.VINYL
        artworkStyle == PlayerArtworkStyle.CLOVER -> PlayerDesign.CLOVER
        else -> PlayerDesign.CARD
    }
    val gradientStops = remember(gradientRaw) { decodeGradientStops(gradientRaw) }
    fun specFor(d: PlayerDesign, lookOverride: PlayerLook = look, backgroundOverride: PlayerBackgroundStyle = background) = PlayerLookSpec(
        design = d,
        background = if (d != PlayerDesign.APPLE_MUSIC && backgroundOverride == PlayerBackgroundStyle.APPLE_MUSIC && d != PlayerDesign.APPLE_MUSIC_V2) {
            PlayerBackgroundStyle.GRADIENT
        } else backgroundOverride,
        sliderStyle = sliderStyle,
        look = lookOverride,
        staticColor = Color(staticColor),
        gradientStops = gradientStops,
        gradientAngle = gradientAngle,
        thumbnailShadow = shadow,
        showUpNext = showUpNext,
        showVolumeBar = !hideVolumeBar,
    )

    fun apply(d: PlayerDesign) {
        val leaveAppleBackground = background == PlayerBackgroundStyle.APPLE_MUSIC
        when (d) {
            PlayerDesign.CARD, PlayerDesign.VINYL, PlayerDesign.CLOVER -> {
                setUseV2(false)
                setArtworkStyle(
                    when (d) {
                        PlayerDesign.VINYL -> PlayerArtworkStyle.VINYL
                        PlayerDesign.CLOVER -> PlayerArtworkStyle.CLOVER
                        else -> PlayerArtworkStyle.CARD
                    },
                )
                if (leaveAppleBackground) setBackground(PlayerBackgroundStyle.GRADIENT)
            }
            PlayerDesign.APPLE_MUSIC -> {
                setUseV2(false)
                setBackground(PlayerBackgroundStyle.APPLE_MUSIC)
            }
            PlayerDesign.APPLE_MUSIC_V2 -> {
                setUseV2(true)
                if (leaveAppleBackground) setBackground(PlayerBackgroundStyle.LIVE_MESH)
            }
        }
    }

    val track = rememberPreviewTrack()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by remember(playerConnection) { playerConnection?.isPlaying ?: MutableStateFlow(false) }
        .collectAsStateWithLifecycle()
    val position = rememberPreviewPosition({ playerConnection?.player?.currentPosition?.coerceAtLeast(0L) ?: 0L }, isPlaying)
    val upNext = remember(playerConnection, track?.id) {
        playerConnection?.player?.let { p ->
            val next = p.nextMediaItemIndex
            if (next in 0 until p.mediaItemCount) p.getMediaItemAt(next).mediaMetadata.title?.toString() else null
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(
            if (track != null) "Previewing “${track.title}”" else "Play a song to preview it with its artwork",
            style = IosTheme.typography.footnote,
            color = IosTheme.colors.secondaryLabel,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )

        DesignCarousel(
            selected = design,
            specFor = { specFor(it) },
            track = track,
            position = position,
            isPlaying = isPlaying,
            upNext = upNext,
            onSelect = ::apply,
        )

        AnimatedContent(
            targetState = design,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + expandVertically(tween(260))) togetherWith
                    (fadeOut(tween(120)) + shrinkVertically(tween(200)))
            },
            label = "designOptions",
        ) { shown ->
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (shown == PlayerDesign.APPLE_MUSIC_V2) {
                    OptionSection("Layout") {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            PlayerLook.entries.forEach { option ->
                                LayoutChoice(
                                    label = if (option == PlayerLook.HERO) "Full bleed" else "Square artwork",
                                    selected = look == option,
                                    onClick = { setLook(option) },
                                ) {
                                    PlayerPhonePreview(
                                        spec = specFor(shown, lookOverride = option),
                                        track = track,
                                        positionMs = position,
                                        isPlaying = false,
                                        width = 112.dp,
                                    )
                                }
                            }
                        }
                    }
                }
                val classic = shown == PlayerDesign.CARD || shown == PlayerDesign.VINYL || shown == PlayerDesign.CLOVER
                // Classic designs and V2's square layout take a background; full bleed and Apple Music paint their own.
                if (classic || (shown == PlayerDesign.APPLE_MUSIC_V2 && look == PlayerLook.SQUARED)) {
                    OptionSection("Background") {
                        BackgroundPicker(
                            current = specFor(shown).background,
                            design = shown,
                            artworkUrl = track?.artworkUrl,
                            spec = specFor(shown),
                            onPick = setBackground,
                        )
                        if (background == PlayerBackgroundStyle.STATIC || background == PlayerBackgroundStyle.CUSTOM_GRADIENT) {
                            Divider()
                            LinkRow(
                                if (background == PlayerBackgroundStyle.STATIC) "Edit color" else "Edit gradient",
                                onMoreOptions,
                            )
                        }
                    }
                }
                // Both Apple Music designs draw Apple's own progress bar, so the styles only apply to the classic ones.
                if (classic) OptionSection("Seek bar") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        items(SliderStyle.entries) { style ->
                            PickTile(
                                label = sliderLabelFor(style),
                                selected = sliderStyle == style,
                                onClick = { setSliderStyle(style) },
                            ) {
                                PlayerSeekBarSample(
                                    style = style,
                                    isPlaying = isPlaying,
                                    modifier = Modifier
                                        .size(width = 132.dp, height = 64.dp)
                                        .background(Color(0xFF1C1C1E)),
                                )
                            }
                        }
                    }
                }
                OptionSection(null) {
                    val heroLayout = shown == PlayerDesign.APPLE_MUSIC || (shown == PlayerDesign.APPLE_MUSIC_V2 && look == PlayerLook.HERO)
                    if (!heroLayout) {
                        SwitchRow("Artwork shadow", shadow, setShadow)
                        Divider()
                    }
                    SwitchRow("Show next track", showUpNext, setShowUpNext)
                    Divider()
                    SwitchRow("Volume bar", !hideVolumeBar) { setHideVolumeBar(!it) }
                    Divider()
                    LinkRow("More player options", onMoreOptions)
                }
            }
        }
    }
}

/** The song the previews play: what is playing now, else the last thing played. */
@Composable
private fun rememberPreviewTrack(): PreviewTrack? {
    val playerConnection = LocalPlayerConnection.current
    val playing by remember(playerConnection) { playerConnection?.mediaMetadata ?: MutableStateFlow(null) }
        .collectAsStateWithLifecycle()
    val database = LocalDatabase.current.get()
    val recent by remember(database) {
        database.recentEvents(1).map { it.firstOrNull()?.song }
    }.collectAsState(initial = null)
    playing?.let { m ->
        return PreviewTrack(m.id, m.title, m.artists.joinToString(", ") { it.name }, m.thumbnailUrl, m.duration * 1000L)
    }
    return recent?.let { s ->
        PreviewTrack(s.id, s.song.title, s.artists.joinToString(", ") { it.name }, s.song.thumbnailUrl, s.song.duration * 1000L)
    }
}

/**
 * Phones along a gentle arc: the centred one full size, its neighbours smaller and lower.
 * A tap on a side phone brings it to the centre; a tap on the centre one puts it in use.
 */
@Composable
private fun DesignCarousel(
    selected: PlayerDesign,
    specFor: (PlayerDesign) -> PlayerLookSpec,
    track: PreviewTrack?,
    position: () -> Long,
    isPlaying: Boolean,
    upNext: String?,
    onSelect: (PlayerDesign) -> Unit,
) {
    val designs = PlayerDesign.entries
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selected.ordinal)
    val scope = rememberCoroutineScope()
    val centred by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2f - mid) }?.index ?: selected.ordinal
        }
    }
    LaunchedEffect(selected) { listState.animateScrollToItem(selected.ordinal) }

    val phoneWidth = 176.dp
    // Bleeds past the sheet's 16dp content inset so side phones run off the edge instead of clipping at it.
    BoxWithConstraints(
        Modifier
            .layout { measurable, constraints ->
                val bleed = 32.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + bleed, maxWidth = constraints.maxWidth + bleed))
                layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed / 2, 0) }
            }
            .padding(top = 8.dp),
    ) {
        val side = ((maxWidth - phoneWidth) / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = side),
            flingBehavior = rememberSnapFlingBehavior(listState),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth().height(440.dp),
        ) {
            itemsIndexed(designs, key = { _, d -> d.name }) { index, d ->
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val mid = (listState.layoutInfo.viewportStartOffset + listState.layoutInfo.viewportEndOffset) / 2f
                val distance = info?.let { ((it.offset + it.size / 2f - mid) / it.size.coerceAtLeast(1)).coerceIn(-2f, 2f) } ?: 2f
                val depth = abs(distance)
                Box(
                    Modifier
                        .width(phoneWidth)
                        .padding(top = 6.dp)
                        .graphicsLayer {
                            val scale = 1f - (depth * 0.16f).coerceAtMost(0.3f)
                            scaleX = scale
                            scaleY = scale
                            // Arc: neighbours sit lower and lean outwards.
                            translationY = depth * depth * 22.dp.toPx()
                            rotationZ = distance * 5f
                            alpha = 1f - (depth * 0.25f).coerceAtMost(0.5f)
                        }
                        .clickable(interactionSource = null, indication = null) {
                            if (index == centred) onSelect(d) else scope.launch { listState.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    PlayerPhonePreview(
                        spec = specFor(d),
                        track = track,
                        positionMs = position,
                        // Only the centred phone runs its animations; the rest hold still.
                        isPlaying = isPlaying && index == centred,
                        upNext = upNext,
                        width = phoneWidth - 12.dp,
                    )
                }
            }
        }
    }

    val shown = designs[centred.coerceIn(0, designs.lastIndex)]
    val inUse = shown == selected
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(designName(shown), style = IosTheme.typography.headline, color = IosTheme.colors.label)
        Spacer(Modifier.width(10.dp))
        val pill by animateColorAsState(if (inUse) IosTheme.colors.systemGreen.copy(alpha = 0.18f) else IosTheme.tint, label = "usePill")
        Text(
            if (inUse) "In use" else "Use",
            style = IosTheme.typography.subheadline,
            fontWeight = FontWeight.SemiBold,
            color = if (inUse) IosTheme.colors.systemGreen else Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(pill)
                .clickable(enabled = !inUse) { onSelect(shown) }
                .padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun BackgroundPicker(
    current: PlayerBackgroundStyle,
    design: PlayerDesign,
    artworkUrl: String?,
    spec: PlayerLookSpec,
    onPick: (PlayerBackgroundStyle) -> Unit,
) {
    val styles = PlayerBackgroundStyle.entries
        .filter { (it != PlayerBackgroundStyle.BLUR && it != PlayerBackgroundStyle.GLASS) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        .filter { it != PlayerBackgroundStyle.APPLE_MUSIC || design == PlayerDesign.APPLE_MUSIC_V2 }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = styles.indexOf(current).coerceAtLeast(0))
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        items(styles) { style ->
            PickTile(label = backgroundName(style), selected = current == style, onClick = { onPick(style) }) {
                Box(Modifier.size(width = 76.dp, height = 136.dp)) {
                    // The real background, animating, with the cover it is built from.
                    PlayerBackdropPreview(style, artworkUrl, isPlaying = current == style, spec = spec, heroArtwork = false, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun LayoutChoice(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring by animateColorAsState(if (selected) IosTheme.tint else Color.Transparent, label = "layoutRing")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Box(Modifier.border(2.5.dp, ring, ContinuousRoundedRectangle(18.dp)).padding(5.dp)) { content() }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = IosTheme.typography.subheadline,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) IosTheme.tint else IosTheme.colors.label,
        )
    }
}

@Composable
private fun PickTile(label: String, selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val ring by animateColorAsState(if (selected) IosTheme.tint else Color.Transparent, tween(180), label = "pickRing")
    val lift by animateFloatAsState(if (selected) 1f else 0.96f, label = "pickLift")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = lift; scaleY = lift }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .border(2.5.dp, ring, ContinuousRoundedRectangle(16.dp))
                .padding(4.dp)
                .clip(ContinuousRoundedRectangle(12.dp)),
        ) { content() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = IosTheme.typography.caption,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) IosTheme.tint else IosTheme.colors.secondaryLabel,
            maxLines = 1,
        )
    }
}

@Composable
private fun OptionSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column {
        if (title != null) Text(
            title,
            style = IosTheme.typography.footnote,
            fontWeight = FontWeight.SemiBold,
            color = IosTheme.colors.secondaryLabel,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(ContinuousRoundedRectangle(IosMetrics.GroupedCornerRadius))
                .background(IosTheme.colors.secondarySystemGroupedBackground),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosTheme.typography.body, color = IosTheme.colors.label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LinkRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = IosTheme.typography.body, color = IosTheme.tint, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Divider() = HorizontalDivider(
    Modifier.padding(start = 16.dp),
    color = IosTheme.colors.separator,
    thickness = IosMetrics.SeparatorThickness,
)

private fun designName(d: PlayerDesign) = when (d) {
    PlayerDesign.CARD -> "Card"
    PlayerDesign.VINYL -> "Vinyl"
    PlayerDesign.CLOVER -> "Clover"
    PlayerDesign.APPLE_MUSIC -> "Apple Music"
    PlayerDesign.APPLE_MUSIC_V2 -> "Apple Music v2"
}

private fun backgroundName(style: PlayerBackgroundStyle) = when (style) {
    PlayerBackgroundStyle.DEFAULT -> "Theme"
    PlayerBackgroundStyle.GRADIENT -> "Gradient"
    PlayerBackgroundStyle.BLUR -> "Blur"
    PlayerBackgroundStyle.GLOW_ANIMATED -> "Glow"
    PlayerBackgroundStyle.APPLE_MUSIC -> "Apple"
    PlayerBackgroundStyle.LIVE_MESH -> "Live mesh"
    PlayerBackgroundStyle.FLUID_MESH -> "Colour mesh"
    PlayerBackgroundStyle.STATIC -> "Solid"
    PlayerBackgroundStyle.CUSTOM_GRADIENT -> "Custom"
    PlayerBackgroundStyle.GLASS -> "Glass"
}

private fun sliderLabelFor(style: SliderStyle) = when (style) {
    SliderStyle.DEFAULT -> "Default"
    SliderStyle.WAVY -> "Wavy"
    SliderStyle.SLIM -> "Slim"
    SliderStyle.WAVEFORM -> "Waveform"
}

@Suppress("unused")
private val keepR = R.string.player_theme
