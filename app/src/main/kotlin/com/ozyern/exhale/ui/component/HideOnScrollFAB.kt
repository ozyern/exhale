/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.ozyern.exhale.ui.ios.compat.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ozyern.exhale.LocalPlayerAwareWindowInsets
import com.ozyern.exhale.R
import com.ozyern.exhale.ui.utils.isScrollingUp
import dev.chrisbanes.haze.HazeState

/** Pass the screen's scrolling [HazeState] for a frosted blur; null uses a flat fallback to avoid backdrop self-capture. */
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (onRecognitionClick != null) {
                GlassCircleButton(
                    onClick = onRecognitionClick,
                    hazeState = hazeState,
                    // Keep this frequently shown action flat to avoid backdrop sampling
                    // from inside its own capture.
                    forceFlat = true,
                ) {
                    SymbolIcon(symbol = sf("mic.fill"), contentDescription = stringResource(R.string.recognize_music), size = 20.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            GlassCircleButton(
                onClick = onClick,
                size = 56.dp,
                hazeState = hazeState,
                forceFlat = true,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyGridState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    AnimatedVisibility(
        visible = visible && lazyListState.isScrollingUp(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (onRecognitionClick != null) {
                GlassCircleButton(
                    onClick = onRecognitionClick,
                    hazeState = hazeState,
                    // Sacrifice: always flat — see LazyList overload comment.
                    forceFlat = true,
                ) {
                    SymbolIcon(symbol = sf("mic.fill"), contentDescription = stringResource(R.string.recognize_music), size = 20.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            GlassCircleButton(
                onClick = onClick,
                size = 56.dp,
                hazeState = hazeState,
                forceFlat = true,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    scrollState: ScrollState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    onRecognitionClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
) {
    AnimatedVisibility(
        visible = visible && scrollState.isScrollingUp(),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (onRecognitionClick != null) {
                GlassCircleButton(
                    onClick = onRecognitionClick,
                    hazeState = hazeState,
                    // Sacrifice: always flat — see LazyList overload comment.
                    forceFlat = true,
                ) {
                    SymbolIcon(symbol = sf("mic.fill"), contentDescription = stringResource(R.string.recognize_music), size = 20.dp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            GlassCircleButton(
                onClick = onClick,
                size = 56.dp,
                hazeState = hazeState,
                forceFlat = true,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                )
            }
        }
    }
}
