package com.ozyern.exhale.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.constants.ReduceDynamicBlurKey
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.ios.compat.Text
import com.ozyern.exhale.ui.utils.Motion
import com.ozyern.exhale.utils.makeTimeString
import com.ozyern.exhale.utils.rememberPreference

private data class TimeDigit(val character: Char, val seconds: Long)

@Composable
internal fun RollingPlaybackTime(
    seconds: () -> Long,
    prefix: String,
    color: Color,
    modifier: Modifier,
    textStyle: TextStyle? = null,
    /** Digits roll only while true (scrubbing); otherwise they change in place. */
    animate: Boolean = true,
) {
    val rolling by rememberUpdatedState(animate)
    val currentSeconds by rememberUpdatedState(seconds)
    val value by remember { derivedStateOf { currentSeconds().coerceIn(0, Long.MAX_VALUE / 1000) } }
    val formatted = makeTimeString(value * 1000)
    val reduceMotion by rememberPreference(ReduceDynamicBlurKey, false)
    val style = (textStyle ?: AppTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = .35.sp,
    )).copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val digitWidth = remember(style, density) { with(density) { measurer.measure("0", style).size.width.toDp() } }
    val height = with(density) { style.lineHeight.toDp() }
    Row(modifier.semantics { text = AnnotatedString(prefix + formatted) }) {
        if (prefix.isNotEmpty()) Text(prefix, color = color, style = style, modifier = Modifier.clearAndSetSemantics {})
        formatted.forEachIndexed { index, character ->
            key(formatted.length - index) {
                if (!character.isDigit()) {
                    Text(character.toString(), color = color, style = style, modifier = Modifier.clearAndSetSemantics {})
                } else AnimatedContent(
                    targetState = TimeDigit(character, value),
                    contentKey = { it.character },
                    transitionSpec = {
                        if (reduceMotion || !rolling) EnterTransition.None togetherWith ExitTransition.None
                        else {
                            val direction = if (targetState.seconds >= initialState.seconds) 1 else -1
                            (slideInVertically(Motion.appear()) { it * direction } + fadeIn(tween(120))) togetherWith
                                (slideOutVertically(Motion.appear()) { -it * direction } + fadeOut(tween(100)))
                        }
                    },
                    modifier = Modifier.width(digitWidth).height(height).clipToBounds().clearAndSetSemantics {},
                    label = "playbackTimeDigit",
                ) { digit -> Text(digit.character.toString(), color = color, style = style) }
            }
        }
    }
}
