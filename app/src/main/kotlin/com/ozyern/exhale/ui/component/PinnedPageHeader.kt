/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A tab's title and the account button, pinned over its page rather than scrolling with it — the
 * same header on Home, Library and Mood & Genres (Search keeps its own field).
 *
 * A scrim behind the status bar keeps the clock readable over artwork; once the page has moved it
 * deepens to cover the title too, so a heading scrolling underneath never shows through. Read in
 * the draw phase, so a scroll frame costs a layer invalidation only.
 */
@Composable
fun PinnedPageHeader(
    title: String,
    scrolled: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val chrome = com.ozyern.exhale.ui.screens.search.LocalSearchChrome.current
    val surface = MaterialTheme.colorScheme.surface
    // Derived, so the header recomposes when the page leaves or returns to the top, not per frame.
    val isScrolled by remember { derivedStateOf(scrolled) }
    val scrimDepth by animateFloatAsState(
        targetValue = if (isScrolled) 1f else 0f,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "pinnedHeaderScrim",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // At rest: just the status bar. Scrolled: down past the title.
                val reach = size.height * (0.45f + 0.8f * scrimDepth)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.92f + 0.08f * scrimDepth),
                        0.7f to surface.copy(alpha = 0.55f + 0.42f * scrimDepth),
                        1f to Color.Transparent,
                        endY = reach,
                    ),
                    size = size.copy(height = reach),
                )
            }
            .windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(start = 20.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
    ) {
        val (settingsShortcut) = com.ozyern.exhale.utils.rememberPreference(
            com.ozyern.exhale.constants.SettingsShortcutKey,
            false,
        )
        val showGear = chrome != null && settingsShortcut
        // Long titles ("Mood & Genres") step down to the iOS large-title size, or they would run
        // under the account button.
        val long = title.length > (if (showGear) 6 else 8)
        // Apple Music's large title is about a tenth of the window wide in height; sized to this
        // window the same way rather than to a fixed point size.
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val largeTitle = (screenWidth * 0.097f).coerceIn(34f, 44f).sp
        val avatarSize = (screenWidth * 0.111f).coerceIn(40f, 50f).dp
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = AppleTitleFont,
                fontSize = if (long) 34.sp else largeTitle,
                lineHeight = 46.sp,
                letterSpacing = (-0.4).sp,
                // Over artwork the word needs its own shadow; the scrim alone is too light at rest.
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.45f),
                    offset = Offset(0f, 2f),
                    blurRadius = 12f,
                ),
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 14.dp, end = if (showGear) avatarSize + 72.dp else avatarSize + 16.dp),
        )
        if (chrome != null && showGear) {
            // Apple's toolbar button: a circle of thin glass — a light translucent fill and a hairline
            // rim, no drop shadow — with a regular-weight gear in the label colour. Quieter than the
            // account picture beside it, so the two never compete. 44dp, the touch target Apple asks
            // for, and centred on the picture's line.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 14.dp, end = avatarSize + 10.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape)
                    .clickable(onClick = chrome.openSettings),
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(com.ozyern.exhale.R.drawable.gearshape),
                    contentDescription = androidx.compose.ui.res.stringResource(com.ozyern.exhale.R.string.settings),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        if (chrome != null) {
            // Apple's: flat, no drop shadow, centred on the title's line, on the page's right margin
            // (Apple's own ⋮ button beside it is deliberately not copied).
            AppleAvatar(
                onClick = chrome.openAccount,
                size = avatarSize,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 14.dp, end = 2.dp),
            )
        }
    }
}

/** Space a page leaves at its top so its content starts below the pinned header. */
val PinnedPageHeaderHeight = 78.dp

/** Apple's large-title and shelf-title face: SF Pro Display Bold, which the app bundles. */
val AppleTitleFont = androidx.compose.ui.text.font.FontFamily(
    androidx.compose.ui.text.font.Font(com.ozyern.exhale.R.font.sfprodisplaybold, FontWeight.Bold),
)

/** Whether a list has moved off its top, for [PinnedPageHeader]. */
fun LazyListState.isScrolledFromTop(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0

/** Whether a grid has moved off its top, for [PinnedPageHeader]. */
fun LazyGridState.isScrolledFromTop(): Boolean = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
