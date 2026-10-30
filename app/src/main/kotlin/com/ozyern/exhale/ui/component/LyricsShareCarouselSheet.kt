/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.ozyern.exhale.ui.component.liquid.LiquidToggle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.models.MediaMetadata

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The sheet where the listener:
 *  1. Sees the lyric card as it will be shared.
 *  2. Picks a design from the row of them.
 *  3. Picks the glass and colour style.
 *  4. Adjusts text size, alignment, what is shown and padding.
 *  5. Shares or saves the result.
 *
 * @param onShare  Called with the final [LyricsCardConfig] when the listener taps Share.
 *                 The caller renders the card to a bitmap and sends the Intent.
 * @param onSave   Optional; when given, a Save button appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsShareCarouselSheet(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    initialConfig: LyricsCardConfig = LyricsCardConfig(),
    onDismiss: () -> Unit,
    onShare: (config: LyricsCardConfig) -> Unit,
    onSave: ((config: LyricsCardConfig) -> Unit)? = null,
) {
    var config by remember { mutableStateOf(initialConfig) }

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surface,
        dragHandle        = { BottomSheetDefaults.DragHandle() },
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        // The options scroll; Save and Share stay pinned under them, always in reach.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Sheet title ───────────────────────────────────────────────
            Text(
                text       = "Share lyrics",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            // ── Preview principal ─────────────────────────────────────────
            // Fades between configs
            AnimatedContent(
                targetState  = config,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) },
                label        = "lyrics_card_preview",
            ) { previewConfig ->
                CardPreviewBox(
                    lyricText     = lyricText,
                    mediaMetadata = mediaMetadata,
                    config        = previewConfig,
                    previewSizeDp = 264,
                )
            }

            Spacer(Modifier.height(22.dp))

            // ── Designs ───────────────────────────────────────────────────
            SheetSectionLabel("Design")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LyricsLayoutStyle.values().forEach { style ->
                    LayoutStyleThumbnail(
                        style         = style,
                        lyricText     = lyricText,
                        mediaMetadata = mediaMetadata,
                        currentConfig = config,
                        isSelected    = config.layoutStyle == style,
                        onSelect      = {
                            // Vivid sets its lines flush left as Apple does; the others centre them.
                            val align = when {
                                style == LyricsLayoutStyle.Vivid -> TextAlign.Start
                                config.layoutStyle == LyricsLayoutStyle.Vivid -> TextAlign.Center
                                else -> config.textAlign
                            }
                            config = config.copy(layoutStyle = style, textAlign = align)
                        },
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Glass style ───────────────────────────────────────────────
            SheetSectionLabel("Style")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LyricsGlassStyle.allPresets.forEach { preset ->
                    GlassStyleChip(
                        preset     = preset,
                        isSelected = config.glassStyle == preset,
                        onSelect   = { config = config.copy(glassStyle = preset) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Customisation (expandable) ────────────────────────────────
            CustomizationPanel(
                config        = config,
                onConfigChange = { config = it },
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(16.dp))
        }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onSave != null) {
                    OutlinedButton(
                        onClick  = { onSave(config) },
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp),
                    ) {
                        Text("Save")
                    }
                } else {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(14.dp),
                    ) {
                        Text("Cancel")
                    }
                }
                Button(
                    onClick  = { onShare(config) },
                    modifier = Modifier.weight(1f),
                    shape    = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The main preview: the card, scaled down
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardPreviewBox(
    lyricText: String,
    mediaMetadata: MediaMetadata,
    config: LyricsCardConfig,
    previewSizeDp: Int,
) {
    val sizeDp = previewSizeDp.dp

    Box(
        modifier = Modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width  = 1.dp,
                color  = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                shape  = RoundedCornerShape(20.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The card is always laid out at 340dp and scaled to fit
        Box(
            modifier = Modifier
                .requiredSize(340.dp)
                .graphicsLayer {
                    val scale = previewSizeDp / 340f
                    scaleX          = scale
                    scaleY          = scale
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                }
        ) {
            LyricsCardByLayout(
                lyricText     = lyricText,
                mediaMetadata = mediaMetadata,
                config        = config,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// A design's thumbnail in the row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutStyleThumbnail(
    style: LyricsLayoutStyle,
    lyricText: String,
    mediaMetadata: MediaMetadata,
    currentConfig: LyricsCardConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val thumbnailDp = 96
    val previewConfig = currentConfig.copy(layoutStyle = style)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(thumbnailDp.dp),
    ) {
        Box(
            modifier = Modifier
                .size(thumbnailDp.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width  = if (isSelected) 2.5.dp else 1.dp,
                    color  = if (isSelected) MaterialTheme.colorScheme.primary
                             else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                    shape  = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onSelect),
            contentAlignment = Alignment.Center,
        ) {
            // Scaled preview: Coil caches the artwork and cloudy runs on the GPU
            Box(
                modifier = Modifier
                    .requiredSize(340.dp)
                    .graphicsLayer {
                        val scale = thumbnailDp / 340f
                        scaleX          = scale
                        scaleY          = scale
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
            ) {
                LyricsCardByLayout(
                    lyricText     = lyricText,
                    mediaMetadata = mediaMetadata,
                    config        = previewConfig,
                )
            }

            // Selection mark
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text      = "✓",
                        color     = MaterialTheme.colorScheme.onPrimary,
                        fontSize  = 8.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            text       = style.displayName,
            style      = MaterialTheme.typography.labelSmall,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            textAlign  = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Glass style chip
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassStyleChip(
    preset: LyricsGlassStyle,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                width  = if (isSelected) 2.dp else 1.dp,
                color  = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape  = RoundedCornerShape(50),
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The style's colour swatch
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(preset.surfaceTint.copy(alpha = 1f))
                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
        )
        Text(
            text       = preset.name,
            style      = MaterialTheme.typography.labelMedium,
            color      = if (isSelected) MaterialTheme.colorScheme.primary
                         else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Customisation (expandable)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomizationPanel(
    config: LyricsCardConfig,
    onConfigChange: (LyricsCardConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label       = "chevron",
    )

    Column(modifier = modifier) {

        // Header: tap to open or close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text       = "Customization",
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = null,
                modifier           = Modifier.rotate(chevronAngle),
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically() + fadeIn(),
            exit    = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {

                // ── Text size ─────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Text size",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = "${(config.textSizeMultiplier * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value         = config.textSizeMultiplier,
                        onValueChange = { onConfigChange(config.copy(textSizeMultiplier = it)) },
                        valueRange    = 0.6f..1.5f,
                        steps         = 17,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }

                // ── Text alignment ────────────────────────────────────────
                Column {
                    Text(
                        text       = "Alignment",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier   = Modifier.padding(bottom = 8.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_left),
                            label = "Izquierda",
                            isSelected = config.textAlign == TextAlign.Start,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.Start)) },
                        )
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_center),
                            label = "Izquierda",
                            isSelected = config.textAlign == TextAlign.Start,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.Start)) },
                        )
                        AlignButton(
                            painter = painterResource(R.drawable.format_align_right),
                            label = "Izquierda",
                            isSelected = config.textAlign == TextAlign.Start,
                            onClick = { onConfigChange(config.copy(textAlign = TextAlign.Start)) },
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                // ── What the card shows ───────────────────────────────────
                Text(
                    text       = "Visibilidad",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToggleRow("Show title",    config.showTitle)   { onConfigChange(config.copy(showTitle   = it)) }
                    ToggleRow("Show artist",   config.showArtist)  { onConfigChange(config.copy(showArtist  = it)) }
                    ToggleRow("Show cover",   config.showCoverArt){ onConfigChange(config.copy(showCoverArt = it)) }
                    ToggleRow("Show Exhale",  config.showBranding){ onConfigChange(config.copy(showBranding = it)) }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

                // ── Padding ───────────────────────────────────────────────
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Espaciado",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text  = "${config.cardPadding.value.toInt()} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value         = config.cardPadding.value,
                        onValueChange = { onConfigChange(config.copy(cardPadding = it.dp)) },
                        valueRange    = 12f..36f,
                        steps         = 11,
                        modifier      = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Componentes internos auxiliares
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AlignButton(
    painter: Painter,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color =
                    if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = label,
            tint =
                if (isSelected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
        )
        LiquidToggle(
            checked         = checked,
            onCheckedChange = onToggle,
        )
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, bottom = 8.dp),
    )
}
