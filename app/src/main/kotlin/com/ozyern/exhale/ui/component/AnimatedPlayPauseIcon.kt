package com.ozyern.exhale.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import com.ozyern.exhale.ui.ios.compat.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFPauseFill
import com.composables.sfsymbols.monochrome.SFPlayFill
import com.ozyern.exhale.R
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.utils.rememberPreference

@Composable
fun AnimatedPlayPauseIcon(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = 32.dp,
) {
    val (reduceMotion) = rememberPreference(ReduceDynamicBlurKey, false)
    val description = stringResource(if (isPlaying) R.string.pause else R.string.play)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                // SSOT: Motion.swapEnter/swapExit share one clock app-wide.
                // 0.3, not a polite 0.85: Flamingo's pop. At 0.85 the scale is small enough
                // to be indistinguishable from a plain cut, and the swap reads as a different
                // button appearing rather than this one changing its mind.
                ((fadeIn(Motion.gate(reduceMotion, Motion.swapEnter())) +
                    scaleIn(
                        Motion.gate(reduceMotion, Motion.swapEnter()),
                        initialScale = Motion.SwapInitialScale,
                    )) togetherWith
                    (fadeOut(Motion.gate(reduceMotion, Motion.swapExit())) +
                        scaleOut(
                            Motion.gate(reduceMotion, Motion.swapExit()),
                            targetScale = Motion.SwapInitialScale,
                        )))
                    .using(SizeTransform(clip = false))
            },
            contentKey = { it },
            label = "play_pause",
        ) { playing ->
            // Layout stays constant at [size]: the 0.88 settle and the 2.dp play
            // optical shift ride in graphicsLayer (draw phase, no remeasure),
            // so the swap never moves siblings mid-morph.
            val settleScale = if (playing) 0.88f else 1f
            val opticalShiftPx = if (!playing) 2.dp else 0.dp
            SymbolIcon(symbol = if (playing) sf("SFPauseFill") else sf("SFPlayFill"), contentDescription = description, modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = settleScale
                        scaleY = settleScale
                        translationX = opticalShiftPx.toPx()
                    }, tint = tint)
        }
    }
}
