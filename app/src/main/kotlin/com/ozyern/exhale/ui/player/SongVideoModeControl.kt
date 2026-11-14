package com.ozyern.exhale.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.VibrantChromeInk
import com.ozyern.exhale.ui.utils.vibrantChrome

/**
 * Song / Video switch that sits at the top of the player when a video exists for the track.
 * The selected side carries the same vibrant chrome as the scrub rail.
 */
@Composable
internal fun SongVideoModeControl(watchingVideo: Boolean, color: Color, onSelect: (Boolean) -> Unit) {
    Row(
        Modifier
            .width(148.dp)
            .height(36.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(3.dp),
    ) {
        listOf(false to R.string.video_mode_song, true to R.string.video_mode_video).forEach { (video, label) ->
            val selected = watchingVideo == video
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(video) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.matchParentSize().vibrantChrome().background(VibrantChromeInk, CircleShape))
                }
                Text(stringResource(label), color = color, fontSize = 13.sp)
            }
        }
    }
}
