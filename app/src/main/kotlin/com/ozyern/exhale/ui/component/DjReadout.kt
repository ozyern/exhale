/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.audio.DjMixTier
import com.ozyern.exhale.playback.dj.DjState
import com.ozyern.exhale.playback.dj.TransitionStyle
import kotlin.math.roundToInt

/**
 * One line of DJ telemetry while Auto-DJ is on, reporting what actually happened
 * (e.g. a fallback to plain crossfade) rather than what was intended.
 */
@Composable
fun DjReadout(
    modifier: Modifier = Modifier,
    // Unspecified = player's own theme colour; Player V17 passes an adaptive tint for artwork backgrounds.
    color: Color = Color.Unspecified,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val state by playerConnection.djState.collectAsStateWithLifecycle()

    if (!state.enabled) return
    // Analysis needs a few seconds first; placeholder so an empty row isn't read as broken.
    val text = state.describe() ?: stringResource(R.string.dj_readout_analyzing)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(spring(dampingRatio = 0.8f, stiffness = 550f)) +
                    slideInVertically(spring(dampingRatio = 0.76f, stiffness = 550f)) { it / 3 })
                    .togetherWith(
                        fadeOut(spring(dampingRatio = 1.0f, stiffness = 650f)) +
                        slideOutVertically(spring(dampingRatio = 1.0f, stiffness = 650f)) { -it / 3 }
                    )
            },
            label = "DjReadoutTransition"
        ) { currentText ->
            Text(
                text = currentText,
                style = AppTheme.typography.labelSmall,
                color = color.takeOrElse { AppTheme.colorScheme.onSurfaceVariant },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `128 BPM · 8A · beatmatched → 124 BPM · 9A`, skipping anything unknown. */
private fun DjState.describe(): String? {
    val current = buildList {
        if (currentBpm > 0f) add("${currentBpm.roundToInt()} BPM")
        currentKey?.let(::add)
        tierLabel()?.let(::add)
    }
    if (current.isEmpty()) return null

    val next = buildList {
        if (nextBpm > 0f) add("${nextBpm.roundToInt()} BPM")
        nextKey?.let(::add)
    }

    val head = current.joinToString(" · ")
    return if (next.isEmpty()) head else "$head → ${next.joinToString(" · ")}"
}

private fun DjState.tierLabel(): String? {
    val base = when (tier ?: return null) {
        DjMixTier.FULL_DJ -> "beatmatched"
        DjMixTier.SMART_CROSSFADE -> "tempo matched"
        DjMixTier.PLAIN_CROSSFADE -> "crossfade"
    }
    val effect = when (style) {
        TransitionStyle.REVERB_BLOOM -> "reverb"
        TransitionStyle.DELAY_BLEND -> "delay"
        TransitionStyle.ECHO_FREEZE -> "echo"
        TransitionStyle.TAPE_STOP -> "brake"
        else -> null
    }
    return if (effect == null) base else "$base + $effect"
}
