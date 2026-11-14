package com.ozyern.exhale.ui.theme

import com.ozyern.exhale.ui.ios.compat.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.ozyern.exhale.ui.ios.foundation.IosTheme
import com.ozyern.exhale.ui.ios.foundation.LocalIosColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.ozyern.exhale.constants.AppTextColorKey
import com.ozyern.exhale.ui.component.LocalAppBackdrop
import com.ozyern.exhale.ui.component.backdrop.Backdrop
import com.ozyern.exhale.utils.rememberPreference

/** saturation floor shared with [AppleTokens.shiftedForContrast] (0.08f). */
fun Color.isHueless(): Boolean {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return hsl[1] < 0.08f
}

/**
 * Wraps [content] in a nested [MaterialTheme] whose colour scheme is derived from
 * the screen's own hero [tint], so every `MaterialTheme.colorScheme` read on that
 * screen agrees with the background plane.
 *
 * When [tint] is hueless (grey / black / near-white), the app-wide scheme is kept
 * intact — re-theming from a grey produces a strictly worse result.
 *
 * Also provides [LocalAppBackdrop], [LocalContentColor] and [LocalAccentTextColor]
 * so glass chrome and explicit colour references stay in sync.
 */
@Composable
fun HeroTintedContent(
    tint: Color,
    backdrop: Backdrop,
    darkChrome: Boolean = false,
    content: @Composable () -> Unit,
) {
    val onDark = AppleTokens.prefersLightContent(tint)
    // Glass over a tint that needs light content takes the dark appearance; a light tint keeps the light one.
    com.ozyern.exhale.PublishAmbientBackgroundColor(tint, darkChrome || onDark)
    val base = MaterialTheme.colorScheme

    // Hero screens replace the scheme, so restore the explicit text-color override.
    val (appTextColorInt) = rememberPreference(AppTextColorKey, defaultValue = 0)

    // Apply the override to MaterialTheme roles too; screens read these colors directly.
    val scheme = remember(base, tint, onDark, appTextColorInt) {
        val tinted = base.accentText(tint, onDark, background = tint)
        if (appTextColorInt != 0) tinted.flatText(Color(appTextColorInt)) else tinted
    }
    val contentColor = if (appTextColorInt != 0) Color(appTextColorInt) else AppleTokens.onColor(tint)
    val headingColor = if (appTextColorInt != 0) Color(appTextColorInt) else AppleTokens.onColorHeading(tint)

    // Keep iOS labels in sync because these screens read colors through AppTheme/IosTheme.
    val baseIos = IosTheme.colors
    val heroIosColors = remember(baseIos, contentColor, headingColor, tint) {
        baseIos.copy(
            label = headingColor,
            secondaryLabel = contentColor.copy(alpha = 0.72f),
            tertiaryLabel = contentColor.copy(alpha = 0.44f),
            separator = contentColor.copy(alpha = 0.18f),
            opaqueSeparator = contentColor.copy(alpha = 0.24f),
        )
    }

    if (tint.isHueless()) {
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppBackdrop provides backdrop,
            LocalContentColor provides contentColor,
            LocalAccentTextColor provides headingColor,
        ) {
            content()
        }
    } else {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalAppBackdrop provides backdrop,
                LocalContentColor provides contentColor,
                LocalAccentTextColor provides headingColor,
                LocalIosColors provides heroIosColors,
            ) {
                content()
            }
        }
    }
}
