/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.AppBarHeight
import com.ozyern.exhale.ui.component.ArtistHeroImage
import com.ozyern.exhale.ui.component.ContrastCircleButton
import com.ozyern.exhale.ui.component.ExpandableText
import com.ozyern.exhale.ui.component.LinkSegment
import com.ozyern.exhale.ui.component.ShimmerLine
import com.ozyern.exhale.ui.component.SymbolIcon
import com.ozyern.exhale.ui.component.sf
import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.screens.library.apple.SfStyle
import com.ozyern.exhale.ui.utils.sharedTitleTarget
import com.ozyern.exhale.ui.component.shapes.ContinuousRoundedRectangle

/**
 * Apple Music's album page head: the cover edge to edge from the top of the screen, running down
 * into the page's colour, with the title, the artist and a short line of facts sitting on its foot;
 * shuffle, a wide Play and add-to-library under them; then Apple's notes on the record.
 *
 * Shares its hero with the artist page ([ArtistHeroImage]), so the two pages read as one design.
 */
@Composable
internal fun AlbumHeroHeader(
    title: String,
    artist: String,
    artwork: String?,
    appleArtwork: String?,
    pageColor: Color,
    heroScale: () -> Float,
    /** Genre, year and the like, joined with middle dots. */
    facts: List<String>,
    /** Every song on the album has a lossless copy to play from. */
    lossless: Boolean,
    playing: Boolean,
    enabled: Boolean,
    inLibrary: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onArtistClick: (() -> Unit)? = null,
    onToggleLibrary: (() -> Unit)? = null,
    description: String? = null,
    descriptionRuns: List<LinkSegment>? = null,
    artworkOverlay: @Composable BoxScope.() -> Unit = {},
    /** Artist, facts and notes: the page's own hue, lifted (see AlbumPagePalette). */
    secondaryColor: Color = Color.White.copy(alpha = 0.6f),
) {
    val foreground = Color.White
    CompositionLocalProvider(LocalContentColor provides foreground) {
        BoxWithConstraints(modifier.fillMaxWidth()) {
            val side = maxWidth
            // The list starts below the status bar and the app bar; the cover starts at the glass.
            val topInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding() + AppBarHeight
            ArtistHeroImage(
                artworkUrl = artwork,
                appleArtworkUrl = appleArtwork,
                pageColor = pageColor,
                heroScale = heroScale,
                loading = loading && artwork.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth().offset { IntOffset(0, -topInset.roundToPx()) },
                overlay = artworkOverlay,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = (side * TitleTopOnCover - topInset).coerceAtLeast(0.dp)),
            ) {
                if (loading && title.isBlank()) {
                    ShimmerLine(.55f, 28.dp)
                } else {
                    Text(
                        title,
                        style = SfStyle(24.sp, 29.sp, FontWeight.Bold, tracking = (-0.3).sp),
                        color = foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.sharedTitleTarget().padding(horizontal = 28.dp),
                    )
                }
                if (artist.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        artist,
                        style = SfStyle(22.sp, 27.sp),
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .then(if (onArtistClick != null) Modifier.clickable(role = Role.Button, onClick = onArtistClick) else Modifier),
                    )
                }
                val line = facts.filter { it.isNotBlank() }
                if (line.isNotEmpty() || lossless) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    ) {
                        val factColor = secondaryColor
                        val factStyle = SfStyle(13.sp, 17.sp, FontWeight.SemiBold)
                        if (line.isNotEmpty()) {
                            Text(line.joinToString(" · "), style = factStyle, color = factColor, maxLines = 1)
                        }
                        if (lossless) {
                            if (line.isNotEmpty()) Text(" · ", style = factStyle, color = factColor)
                            SymbolIcon(sf("waveform"), null, size = 13.dp, tint = factColor)
                            Spacer(Modifier.width(3.dp))
                            Text(stringResource(R.string.audio_quality_lossless), style = factStyle, color = factColor, maxLines = 1)
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    // Play takes the width between the two circles.
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).padding(horizontal = 24.dp),
                ) {
                    ContrastCircleButton(foreground, onShuffle, size = 52.dp, enabled = enabled) {
                        SymbolIcon(sf("shuffle"), stringResource(R.string.shuffle), size = 24.dp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(ContinuousRoundedRectangle(26.dp))
                            .background(foreground.copy(alpha = if (enabled) 1f else .45f))
                            .clickable(enabled = enabled, role = Role.Button, onClick = onPlay),
                    ) {
                        SymbolIcon(sf(if (playing) "pause.fill" else "play.fill"), null, tint = Color.Black, size = 20.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(if (playing) R.string.pause else R.string.play),
                            style = SfStyle(19.sp, 23.sp, FontWeight.Bold),
                            color = Color.Black,
                        )
                    }
                    if (onToggleLibrary != null) {
                        ContrastCircleButton(foreground, onToggleLibrary, size = 52.dp) {
                            SymbolIcon(
                                sf(if (inLibrary) "checkmark" else "plus"),
                                stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
                                size = 24.dp,
                            )
                        }
                    }
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        stringResource(R.string.album_about_heading),
                        style = SfStyle(20.sp, 25.sp, FontWeight.Bold),
                        color = foreground,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    CompositionLocalProvider(LocalContentColor provides secondaryColor) {
                        ExpandableText(
                            description,
                            runs = descriptionRuns,
                            collapsedMaxLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/** Where the title sits on the cover, as a share of its width: on its foot, as Apple sets it. */
private const val TitleTopOnCover = 0.84f
