/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import com.ozyern.exhale.ui.ios.compat.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Icon
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.utils.rememberPreference

/**
 * The bar's own height, above whatever inset it is sitting under.
 *
 * The single source of truth for it: the bar lays itself out to this, and
 * everything that has to clear the bar — page content padding, [TopFadeBlur]'s
 * ramp, fixed headers that sit directly beneath it — measures from here rather
 * than from a copy of the number.
 */
val TopBarContentHeight = 52.dp

/**
 * The breathing room between the bar's bottom edge and the first thing under
 * it, so content rests below the glass instead of against it.
 */
val TopBarContentGap = 12.dp

/**
 * How far down the window the bar actually ends: the status bar inset it is
 * pinned under, plus its own height.
 *
 * Read at composition rather than baked in as a constant — the inset is a
 * property of the device and the window, not of the app.
 */
@Composable
fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarContentHeight

/** Where page content should start: clear of the bar, plus [TopBarContentGap]. */
@Composable
fun topBarContentPadding(): Dp = topBarHeight() + TopBarContentGap

/**
 * Shared frosted header for tab and detail screens. [TopFadeBlur] draws the
 * progressive backdrop blur underneath. Callers provide [titleAlpha] from
 * their scroll progress so the title stays synchronized with the page.
 */
@Composable
fun FrostedTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    // Long-press-to-home on the back button — many settings screens jump
    // straight back to the root instead of walking the stack one push at a time.
    onBackLongClick: (() -> Unit)? = null,
    title: String = "",
    titleAlpha: Float = 0f,
    // Draw-phase progress source. When supplied, the bar reads it only inside
    // graphicsLayer/derivedStateOf so scroll deltas never recompose callers.
    // Prefer this over [titleAlpha] for any scroll-driven title.
    titleAlphaProvider: (() -> Float)? = null,
    // Lets an artwork-tinted screen (History, Stats) keep its whole chrome in
    // that tint instead of snapping to the flat theme color the bar defaults
    // to everywhere else.
    titleColor: Color = Color.Unspecified,
    // Optional content shown at the leading edge when no back button is present.
    leading: @Composable () -> Unit = {},
    leadingInset: Dp = 16.dp,
    largeTitle: Boolean = false,
    refreshing: Boolean = false,
    // A lambda, not a value: the drag changes every frame, and reading it in
    // the caller would recompose the whole bar on each one.
    pullFraction: () -> Float = { 0f },
    actions: @Composable () -> Unit = {},
) {
    val reduceDynamicBlur by rememberPreference(ReduceDynamicBlurKey, false)
    // Boolean derived from progress: derivedStateOf only invalidates when the
    // threshold flips, not on every scroll pixel.
    val showDivider by remember(titleAlpha, titleAlphaProvider) {
        derivedStateOf { (titleAlphaProvider?.invoke() ?: titleAlpha) > 0.99f }
    }
    // Only the solid bar wants a hairline under it. A faded one has no edge for
    // the line to mark, and drawing it there would be inventing the very seam
    // the fade exists to remove.
    val dividerColor by animateColorAsState(
        targetValue = AppTheme.colorScheme.outline.copy(
            alpha = if (showDivider && reduceDynamicBlur) 0.6f else 0f,
        ),
        animationSpec = tween(220),
        label = "topBarDivider",
    )

    // In tab view the side bar floats over the left edge; the back button starts to its right.
    val sideInset = com.ozyern.exhale.LocalPlayerAwareWindowInsets.current.asPaddingValues()
        .calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
    val ambientColor = com.ozyern.exhale.LocalAmbientBackgroundColor.current.value
    val isDark = AppTheme.colorScheme.surface.luminance() <= 0.5f
    val activeTint = if (titleColor.isSpecified) titleColor else ambientColor
    val resolvedTitleColor = if (titleColor.isSpecified) {
        titleColor
    } else {
        com.ozyern.exhale.ui.theme.GlassTintTokens.glassContentColor(isDark = isDark, activeTint = activeTint, isTitle = true)
    }
    val resolvedIconColor = com.ozyern.exhale.ui.theme.GlassTintTokens.glassContentColor(isDark = isDark, activeTint = activeTint, isTitle = false)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (reduceDynamicBlur) Modifier.background(com.ozyern.exhale.ui.theme.GlassTintTokens.baseSurfaceColor(isDark))
                else Modifier,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(if (largeTitle) 44.dp else TopBarContentHeight),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = resolvedTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    // Reserve room for the back button/logo and the actions so a
                    // long title truncates instead of running under them.
                    .padding(horizontal = 96.dp)
                    // A few dp below true center: the fade's readability scrim is
                    // strongest right under the status bar and thins going down,
                    // so a title sitting slightly lower reads clearer against it.
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = titleAlphaProvider?.invoke() ?: titleAlpha
                    },
            )
            // On a pushed page the back affordance is always visible, since
            // there is no large in-list header to fall back on.
            if (onBack != null) {
                GlassCircleButton(
                    onClick = onBack,
                    onLongClick = onBackLongClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                        .padding(start = if (sideInset > 0.dp) sideInset + 16.dp else 8.dp),
                    size = 36.dp,
                ) {
                    SymbolIcon(symbol = sf("chevron.left"), contentDescription = "Back")
                }
            } else {
                androidx.compose.runtime.CompositionLocalProvider(com.ozyern.exhale.ui.ios.compat.LocalContentColor provides resolvedIconColor) {
                    Box(
                        modifier = Modifier
                            .align(if (largeTitle) Alignment.TopStart else Alignment.CenterStart)
                            .padding(start = leadingInset),
                    ) {
                        leading()
                    }
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(com.ozyern.exhale.ui.ios.compat.LocalContentColor provides resolvedIconColor) {
                Row(
                    modifier = Modifier
                        .align(if (largeTitle) Alignment.TopEnd else Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
        // The divider and the loader line share the bar's bottom edge; the box
        // only grows to the line's height while a refresh is actually showing.
        Box(Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
            RefreshLine(
                refreshing = refreshing,
                pullFraction = pullFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The account affordance at the right end of the bar — the signed-in
 * account's own photo, falling back to a person glyph on a filled circle so
 * the tap target never disappears while the account menu hasn't loaded yet.
 *
 * The hairline ring is what keeps a photo with light edges from dissolving
 * into the bar's glass.
 */
@Composable
fun TopBarAccountButton(
    photoUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCircleButton(
        onClick = onClick,
        modifier = modifier,
        size = 36.dp,
        // Account photo, not an icon — additive would wash it out.
        applyAdditiveChrome = false,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Settings",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .border(0.5.dp, AppTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(AVATAR_SIZE)
                    .clip(CircleShape)
                    .background(AppTheme.colorScheme.surfaceVariant)
                    .border(0.5.dp, AppTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(symbol = sf("person.fill"), contentDescription = "Settings", size = 18.dp, tint = AppTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The refresh indicator: a line along the bottom of the bar, directly under
 * the status bar. It tracks the drag on the way down, then sweeps
 * indefinitely once the refresh is away, so the two phases read as one
 * continuous gesture.
 */
@Composable
private fun RefreshLine(refreshing: Boolean, pullFraction: () -> Float, modifier: Modifier = Modifier) {
    val fraction = pullFraction()
    val pulling = fraction > 0.01f
    AnimatedVisibility(
        visible = refreshing || pulling,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        val lineModifier = Modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
        if (refreshing) {
            LinearProgressIndicator(
                modifier = lineModifier,
                color = AppTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = lineModifier,
                color = AppTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private val LINE_HEIGHT = 2.5.dp

/** Account avatar size, slightly larger than a 24dp icon. */
private val AVATAR_SIZE = 28.dp
