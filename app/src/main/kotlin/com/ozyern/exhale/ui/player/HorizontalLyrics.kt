/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.ozyern.exhale.LocalPlayerConnection
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.LyricsV2

/**
 * The lyrics held sideways, as the iOS music app lays them out in landscape: the cover large on the left,
 * and on the right the song, its artist, the star and the menu above the lyrics running down the
 * rest of the page — all over the cover's own blurred wash.
 *
 * Opened from the lyrics' own button; it turns the phone to landscape while it is up (the way the
 * full-screen video does) and back upright on the way out.
 */
@Composable
internal fun HorizontalLyrics(
    sliderPositionProvider: () -> Long?,
    onExit: () -> Unit,
    onMore: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val song by playerConnection.currentSong.collectAsState(initial = null)
    val liked = song?.song?.liked == true
    val haptic = LocalHapticFeedback.current
    val activity = LocalContext.current as? android.app.Activity

    HoldLandscape(activity)

    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        FullscreenWindow()
        BackHandler(onBack = onExit)
        val media = metadata ?: return@Dialog

        // The cover's wash, recorded so the round buttons are glass over it.
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                AppleLyricsBackground(mediaMetadata = media)
            }
            CompositionLocalProvider(LocalPlayerBackdrop provides backdrop) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 36.dp, end = 28.dp, top = 22.dp, bottom = 22.dp),
                ) {
                    // The cover, as large as the height allows, square, softly rounded.
                    AsyncImage(
                        model = media.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .shadow(24.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.width(36.dp))
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        // A grabber across the top, as on iOS's sheet: tap it to put the lyrics back.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onExit,
                                ),
                        ) {
                            Box(
                                Modifier
                                    .size(width = 40.dp, height = 5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.45f)),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                androidx.compose.material3.Text(
                                    text = media.title,
                                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                androidx.compose.material3.Text(
                                    text = media.artists.joinToString { it.name },
                                    style = TextStyle(fontSize = 15.sp),
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            CircleGlyph(
                                icon = if (liked) R.drawable.favorite_filled else R.drawable.favorite_outline,
                                active = liked,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    playerConnection.toggleLike()
                                },
                            )
                            CircleGlyph(icon = R.drawable.more_horiz, onClick = onMore)
                        }
                        LyricsV2(
                            sliderPositionProvider = sliderPositionProvider,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            cornerButtons = false,
                        )
                    }
                }
            }
        }
    }
}
