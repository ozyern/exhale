/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.screens.artist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.component.ArtworkPalette
import com.ozyern.exhale.ui.component.LiquidBackButton
import com.ozyern.exhale.utils.AppleArtistAbout

/** The page's side margin, from the iOS music app's About page at phone width. */
private val AboutGutter = 28.dp

/**
 * The About section closing the artist page: Apple's biography cut to a few lines, where the artist
 * is from and when they were born under it. The whole card opens [ArtistAboutPage].
 */
@Composable
internal fun ArtistAboutCard(
    about: AppleArtistAbout?,
    fallbackBio: String?,
    palette: ArtworkPalette,
    onOpen: () -> Unit,
) {
    val bio = about?.bio ?: fallbackBio
    if (bio.isNullOrBlank() && about?.origin == null && about?.born == null) return
    Column(Modifier.padding(top = 28.dp)) {
        Text(
            text = "About",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = palette.onBackground,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.elevated.copy(alpha = 0.72f))
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (!bio.isNullOrBlank()) {
                Text(
                    text = remember(bio) { bioMarkup(bio) },
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = palette.onBackgroundVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val facts = listOfNotNull(
                about?.origin,
                about?.born?.let { (if (about.isGroup) "Formed " else "Born ") + it },
            )
            if (facts.isNotEmpty()) {
                Text(
                    text = facts.joinToString("  ·  "),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.onBackgroundVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (bio.isNullOrBlank()) 0.dp else 10.dp),
                )
            }
        }
    }
}

/**
 * The iOS music app's artist About page: the photograph running off the top with the name set on its
 * lower edge, From and Born side by side, the genres as capsules, then the whole biography.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ArtistAboutPage(
    name: String,
    heroImage: String?,
    about: AppleArtistAbout?,
    fallbackBio: String?,
    palette: ArtworkPalette,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val bio = about?.bio ?: fallbackBio
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            // Taps stop here rather than reaching the artist page underneath.
            .clickable(enabled = true, indication = null, interactionSource = null) {},
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom)),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val photoHeight = maxWidth * 0.95f
                Box(Modifier.fillMaxWidth().height(photoHeight)) {
                    if (heroImage != null) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    // The photograph dissolves into the page from about its middle down.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.45f to Color.Transparent,
                                    0.82f to palette.background.copy(alpha = 0.72f),
                                    1f to palette.background,
                                ),
                            ),
                    )
                    Text(
                        text = name,
                        fontSize = 34.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.onBackground,
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomStart)
                            .padding(horizontal = AboutGutter)
                            .padding(bottom = 6.dp),
                    )
                }
            }

            val bornLabel = if (about?.isGroup == true) "Formed" else "Born"
            if (about?.origin != null || about?.born != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AboutGutter)
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    AboutFact("From", about.origin, palette, Modifier.weight(1f))
                    AboutFact(bornLabel, about.born, palette, Modifier.weight(1f))
                }
            }

            if (!about?.genres.isNullOrEmpty()) {
                Text(
                    text = "Genre",
                    fontSize = 16.sp,
                    color = palette.onBackgroundVariant,
                    modifier = Modifier.padding(start = AboutGutter, end = AboutGutter, top = 18.dp, bottom = 8.dp),
                )
                FlowRow(
                    Modifier.padding(horizontal = AboutGutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    about.genres.forEach { genre ->
                        Text(
                            text = genre,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.onBackground,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(palette.onBackground.copy(alpha = 0.10f))
                                .border(0.5.dp, palette.onBackground.copy(alpha = 0.14f), CircleShape)
                                .padding(horizontal = 15.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (!bio.isNullOrBlank()) {
                Text(
                    text = "About",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground,
                    modifier = Modifier.padding(start = AboutGutter, end = AboutGutter, top = 34.dp, bottom = 8.dp),
                )
                Text(
                    text = remember(bio) { bioMarkup(bio) },
                    fontSize = 18.sp,
                    lineHeight = 26.sp,
                    color = palette.onBackgroundVariant,
                    modifier = Modifier.padding(horizontal = AboutGutter),
                )
            }
            Spacer(Modifier.height(40.dp))
        }

        // A scrim under the status bar, so the clock never sits on top of the biography.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(
                    Brush.verticalGradient(
                        listOf(palette.background.copy(alpha = 0.85f), palette.background.copy(alpha = 0f)),
                    ),
                ),
        )
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 12.dp, top = 6.dp),
        ) {
            LiquidBackButton(onClick = onBack, icon = R.drawable.chevron_back)
        }
    }
}

/**
 * Apple's biography as styled text: it marks album titles with `<i>` (shown in italics, as on
 * Apple's own page), and its paragraph breaks are plain newlines, which HTML would fold away.
 */
private fun bioMarkup(bio: String): AnnotatedString =
    AnnotatedString.fromHtml(bio.trim().replace("\r\n", "\n").replace("\n", "<br>"))

@Composable
private fun AboutFact(label: String, value: String?, palette: ArtworkPalette, modifier: Modifier) {
    Column(modifier) {
        if (value != null) {
            Text(text = label, fontSize = 16.sp, color = palette.onBackgroundVariant)
            Text(
                text = value,
                fontSize = 18.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.onBackground,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
