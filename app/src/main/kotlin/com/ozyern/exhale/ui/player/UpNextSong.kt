package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.R
import com.ozyern.exhale.playback.PlayerConnection
import com.ozyern.exhale.extensions.metadata
import com.ozyern.exhale.ui.utils.additiveChrome
import com.ozyern.exhale.ui.utils.resize
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun UpNextSong(
    playerConnection: PlayerConnection,
    titleColor: Color,
    subtitleColor: Color,
    modifier: Modifier = Modifier,
    // Applied to the text column only -- the thumbnail below is a real photo, not a white
    // glyph, so additively blending it would wash it out rather than tint it.
    additiveChrome: Boolean = false,
) {
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    // Play-order index (see PlayerExt.getQueueWindows): timeline-order +1 goes wrong on shuffle.
    val currentIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

    val nextItem = queueWindows.getOrNull(currentIndex + 1)?.mediaItem?.metadata

    AnimatedVisibility(
        visible = nextItem != null,
        enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 550f)) +
                expandVertically(spring(dampingRatio = 0.78f, stiffness = 550f)),
        exit = fadeOut(spring(dampingRatio = 1.0f, stiffness = 650f)) +
               shrinkVertically(spring(dampingRatio = 1.0f, stiffness = 650f)),
    ) {
        AnimatedContent(
            targetState = nextItem,
            contentKey = { it?.id },
            transitionSpec = {
                (fadeIn(spring(dampingRatio = 0.8f, stiffness = 550f)) +
                    slideInVertically(spring(dampingRatio = 0.76f, stiffness = 550f)) { it / 3 })
                    .togetherWith(
                        fadeOut(spring(dampingRatio = 1.0f, stiffness = 650f)) +
                        slideOutVertically(spring(dampingRatio = 1.0f, stiffness = 650f)) { -it / 3 }
                    )
            },
            label = "UpNextSongTransition",
        ) { item ->
            if (item != null) {
                Row(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AsyncImage(
                        model = item.thumbnailUrl?.resize(96, 96),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Column(
                        modifier = Modifier.weight(1f)
                            .additiveChrome(additiveChrome)
                            .graphicsLayer { if (additiveChrome) alpha = 0.45f }
                    ) {
                        Text(
                            text = stringResource(R.string.up_next),
                            style = AppTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = subtitleColor.copy(alpha = 0.7f),
                            maxLines = 1,
                        )
                        Text(
                            text = item.title,
                            style = AppTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = titleColor,
                            maxLines = 1,
                        )
                        val artist = item.artists.firstOrNull()?.name
                        if (!artist.isNullOrBlank()) {
                            Text(
                                text = artist,
                                style = AppTheme.typography.labelSmall,
                                color = subtitleColor.copy(alpha = 0.7f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
