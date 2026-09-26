/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Play / Shuffle pair that sits under an album or playlist header.
 *
 * Apple Music gives these two a tinted capsule each, with the glyph and the word in the accent
 * colour - light on the page, and legible about what each one does. Exhale had Material's filled
 * `Button`: a solid primary slab carrying a bare icon and no label, which is both the loudest
 * shape on the page and the least clear, because "the triangle one" and "the crossed-arrows one"
 * are only obvious to somebody who already knows.
 *
 * Tinted rather than glass: these sit on a page that scrolls artwork behind them, and a frosted
 * pane over a moving cover is a blur pass per frame for a control that is pressed once.
 */
@Composable
fun PlayShuffleButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Solid, the way Apple Music draws these under an album cover: a filled capsule in the page's
     * ink with the label knocked out of it, rather than a tinted wash. Reserved for a page whose
     * header *is* the artwork - on a plain list the tinted pair is quieter and still correct.
     */
    solid: Boolean = false,
) {
    val accent = MaterialTheme.colorScheme.primary
    // On glass the label is the accent, the way every other glass control in the app labels
    // itself; a knocked-out surface colour only works on a solid fill.
    val ink = accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "playShufflePress",
    )

    // Glass, not paint.
    //
    // `solid` used to mean a filled slab of the page's ink, which is how Apple Music draws these -
    // but this app's own material is the frosted pane it uses for every other floating control,
    // and two systems on one page is one too many. The glass keeps the contrast (it carries its
    // own rim and gradient) and stops the pair being the brightest rectangle on the screen.
    val capsule = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(50.dp)
            .then(
                if (solid) {
                    Modifier.liquidGlassSurface(capsule)
                } else {
                    Modifier
                        .clip(capsule)
                        .background(accent.copy(alpha = 0.16f))
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(19.dp),
        )
        // One line, always. In a row of four controls each capsule gets about ninety dp, and
        // "Shuffle" at 16sp wrapped to "Shuffl / e" - the label breaking in half is worse than the
        // label being a size smaller.
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * The round controls that flank Play and Shuffle: like, download, queue, menu.
 *
 * Same height, same tint family and same press physics as [PlayShuffleButton], because they are one
 * row of controls and were reading as four unrelated widgets - a Material `surfaceVariant` disc, a
 * transparent one, and two accent capsules of a different height. Neutral rather than accent: the
 * two actions that start music get the colour, everything beside them is secondary.
 */
@Composable
fun PlayerActionCircle(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 700f),
        label = "actionCirclePress",
    )
    val ink = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(50.dp)
            // The same pane as the capsules beside it - see PlayShuffleButton.
            .liquidGlassSurface(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
    }
}
