/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AutoLoadMoreKey
import com.ozyern.exhale.constants.CrossfadeEnabledKey
import com.ozyern.exhale.playback.PlaybackRepeatMode
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.utils.rememberPreference

/**
 * The queue's playback toggles (shuffle, repeat, keep playing, crossfade) as one pill in the player's bottom
 * bar, where the speaker button sits while the queue is closed. The queue page itself no longer
 * carries a row of them under its header.
 */
@Composable
internal fun QueueTogglesPill(
    playerConnection: PlayerConnection,
    tint: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val snapshot by playerConnection.playbackSnapshot.collectAsStateWithLifecycle()
    var keepPlaying by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    var crossfadeOn by rememberPreference(CrossfadeEnabledKey, defaultValue = false)
    val lightInk = tint.luminance() > 0.5f
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(41.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (lightInk) 0.14f else 0.10f))
            .padding(horizontal = 3.dp),
    ) {
        QueueToggle(
            icon = R.drawable.shuffle,
            description = stringResource(R.string.shuffle),
            active = snapshot.shuffleEnabled,
            enabled = enabled,
            tint = tint,
        ) { playerConnection.setShuffleEnabled(!snapshot.shuffleEnabled) }
        QueueToggle(
            icon = if (snapshot.repeatMode == PlaybackRepeatMode.ONE) R.drawable.repeat_one else R.drawable.repeat,
            description = stringResource(R.string.repeat),
            active = snapshot.repeatMode != PlaybackRepeatMode.OFF,
            enabled = enabled,
            tint = tint,
        ) { playerConnection.cycleRepeatMode() }
        QueueToggle(
            icon = R.drawable.ic_queue_infinity,
            description = stringResource(R.string.queue_keep_playing),
            active = keepPlaying,
            enabled = enabled,
            tint = tint,
        ) { keepPlaying = !keepPlaying }
        // MusicService reads this preference to control crossfades.
        QueueToggle(
            icon = R.drawable.ic_queue_crossfade,
            description = stringResource(R.string.queue_crossfade),
            active = crossfadeOn,
            enabled = enabled,
            tint = tint,
        ) { crossfadeOn = !crossfadeOn }
    }
}

@Composable
private fun QueueToggle(
    icon: Int,
    description: String,
    active: Boolean,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    val lightInk = tint.luminance() > 0.5f
    // Same fill as the bottom bar's selected tab (V2TabGlyph), so the pill reads as part of it.
    val fill = when {
        !active -> Color.Transparent
        lightInk -> Color.White.copy(alpha = 0.34f)
        else -> tint.copy(alpha = 0.86f)
    }
    val ink = when {
        !active -> tint
        lightInk -> Color.Black.copy(alpha = 0.82f)
        else -> Color.White
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(35.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(if (enabled) ink else ink.copy(alpha = ink.alpha * 0.4f)),
            modifier = Modifier.size(20.dp),
        )
    }
}
