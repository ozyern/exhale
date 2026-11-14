/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The iOS volume control: a capsule track that fills, with the speaker glyph riding
 * inside it. No thumb, no stop indicator, no wavy active track — iOS has none of them;
 * the bar simply grows taller while it is being dragged.
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.shapes.ContinuousCapsule
import com.ozyern.exhale.ui.ios.foundation.IosTheme

private object IosVolumeDefaults {
    /** Resting bar height, and the taller one it grows to under a finger. */
    val TrackHeight: Dp = 14.dp
    val TrackHeightActive: Dp = 22.dp
    val GlyphSize: Dp = 15.dp
    val GlyphInset: Dp = 8.dp
}

@Composable
fun VolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color = Color.Unspecified,
) {
    val colors = IosTheme.colors
    val fill = if (accentColor.isSpecified) accentColor else colors.label
    val track = colors.tertiarySystemFill

    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    val height by animateDpAsState(
        targetValue = if (dragging) IosVolumeDefaults.TrackHeightActive
        else IosVolumeDefaults.TrackHeight,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
        label = "IosVolumeHeight",
    )

    val volumeOff = painterResource(R.drawable.volume_off)
    val volumeMute = painterResource(R.drawable.volume_mute)
    val volumeDown = painterResource(R.drawable.volume_down)
    val volumeUp = painterResource(R.drawable.volume_up)
    val glyph = when {
        value <= 0f -> volumeOff
        value < 0.33f -> volumeMute
        value < 0.66f -> volumeDown
        else -> volumeUp
    }

    fun report(x: Float) {
        if (widthPx > 0f) onValueChange((x / widthPx).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IosVolumeDefaults.TrackHeightActive)
            .padding(vertical = 0.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(ContinuousCapsule())
                .background(track)
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            dragging = true
                            report(it.x)
                            tryAwaitRelease()
                            dragging = false
                            onValueChangeFinished?.invoke()
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            dragging = false
                            onValueChangeFinished?.invoke()
                        },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        report(change.position.x)
                    }
                }
                .drawWithContent {
                    drawContent()
                    val filled = size.width * value.coerceIn(0f, 1f)

                    // The fill is drawn, not laid out, so the capsule clip rounds its
                    // leading end and leaves the trailing end square against the track.
                    if (filled > 0f) {
                        drawRect(fill, size = Size(filled, size.height))
                    }

                    val glyphSize = IosVolumeDefaults.GlyphSize.toPx()
                    val inset = IosVolumeDefaults.GlyphInset.toPx()
                    // The glyph sits on whichever half of the track has room for it,
                    // flipping colour so it stays legible on the fill or on the groove.
                    val onFill = filled >= glyphSize + inset * 2
                    val x = if (onFill) inset else filled + inset
                    if (x + glyphSize <= size.width) {
                        translate(x, (size.height - glyphSize) / 2f) {
                            with(glyph) {
                                draw(
                                    Size(glyphSize, glyphSize),
                                    colorFilter = ColorFilter.tint(
                                        if (onFill) colors.systemBackground else colors.secondaryLabel
                                    ),
                                )
                            }
                        }
                    }
                }
        )
    }
}
