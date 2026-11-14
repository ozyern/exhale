/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.ozyern.exhale

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.ozyern.exhale.constants.LiquidGlassAdaptiveContrastKey
import com.ozyern.exhale.constants.LiquidGlassBlurRadiusKey
import com.ozyern.exhale.constants.LiquidGlassChromaticAberrationKey
import com.ozyern.exhale.constants.LiquidGlassDepthEffectKey
import com.ozyern.exhale.constants.LiquidGlassGlobalEnabledKey
import com.ozyern.exhale.constants.LiquidGlassHighlightColorKey
import com.ozyern.exhale.constants.LiquidGlassHighlightOpacityKey
import com.ozyern.exhale.constants.LiquidGlassLensAmountKey
import com.ozyern.exhale.constants.LiquidGlassLensHeightKey
import com.ozyern.exhale.constants.LiquidGlassMiniPlayerEnabledKey
import com.ozyern.exhale.constants.LiquidGlassNavBarEnabledKey
import com.ozyern.exhale.constants.LiquidGlassPuckColorKey
import com.ozyern.exhale.constants.LiquidGlassPuckOpacityKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelBlurRadiusKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelColorKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelEnabledKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelLensAmountKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelLensHeightKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelSurfaceOpacityKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelTextColorKey
import com.ozyern.exhale.constants.LiquidGlassSidePanelVibrancyKey
import com.ozyern.exhale.constants.LiquidGlassStyleKey
import com.ozyern.exhale.constants.LiquidGlassSurfaceOpacityKey
import com.ozyern.exhale.constants.LiquidGlassSurfaceTintColorKey
import com.ozyern.exhale.constants.LiquidGlassTextColorKey
import com.ozyern.exhale.constants.LiquidGlassVibrancyKey
import com.ozyern.exhale.ui.component.GlassEffectConfig
import com.ozyern.exhale.ui.component.GlassStyle
import com.ozyern.exhale.ui.component.glassContentColorFor
import com.ozyern.exhale.ui.ios.compat.AppTheme
import com.ozyern.exhale.ui.theme.GlassTintTokens
import com.ozyern.exhale.utils.rememberEnumPreference
import com.ozyern.exhale.utils.rememberPreference

/** App-wide glass settings resolved from preferences, plus the raw flag the side bar needs. */
@Stable
class GlassConfigState(val config: GlassEffectConfig, val puckColorIsDefault: Boolean)

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun rememberGlassConfigState(artworkChromeDark: Boolean): GlassConfigState {
    val (liquidGlassGlobalEnabled) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = true)
    val (liquidGlassVibrancy) = rememberPreference(LiquidGlassVibrancyKey, defaultValue = 2.0f)
    val (liquidGlassBlurRadius) = rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 3f)
    val (liquidGlassLensHeight) = rememberPreference(LiquidGlassLensHeightKey, defaultValue = 0.60f)
    val (liquidGlassLensAmount) = rememberPreference(LiquidGlassLensAmountKey, defaultValue = 0.69f)
    val (liquidGlassChromaticAberration) = rememberPreference(LiquidGlassChromaticAberrationKey, defaultValue = false)
    val (liquidGlassDepthEffect) = rememberPreference(LiquidGlassDepthEffectKey, defaultValue = false)
    // Zero selects the theme-adaptive tint; the color picker cannot produce it.
    val (liquidGlassSurfaceTintColorInt) = rememberPreference(LiquidGlassSurfaceTintColorKey, defaultValue = 0)
    val (liquidGlassSurfaceOpacity) = rememberPreference(LiquidGlassSurfaceOpacityKey, defaultValue = 0.5f)
    val liquidGlassStyle by rememberEnumPreference(LiquidGlassStyleKey, defaultValue = GlassStyle.LIQUID)
    val (liquidGlassPuckColorInt) = rememberPreference(LiquidGlassPuckColorKey, defaultValue = 0)
    val (liquidGlassPuckOpacity) = rememberPreference(LiquidGlassPuckOpacityKey, defaultValue = 0.8f)
    val (liquidGlassHighlightColorInt) = rememberPreference(LiquidGlassHighlightColorKey, defaultValue = 0)
    val (liquidGlassHighlightOpacity) = rememberPreference(LiquidGlassHighlightOpacityKey, defaultValue = 0.33f)
    // Zero selects theme-adaptive text color.
    val (liquidGlassTextColorInt) = rememberPreference(LiquidGlassTextColorKey, defaultValue = 0)
    val (liquidGlassAdaptiveContrast) = rememberPreference(
        LiquidGlassAdaptiveContrastKey,
        defaultValue = true,
    )
    // Fallback when adaptive contrast is disabled.
    val themeGlassTextColor = if (artworkChromeDark) Color.White else Color(0xFF1A1A1A)
    val (liquidGlassMiniPlayerEnabled) = rememberPreference(LiquidGlassMiniPlayerEnabledKey, defaultValue = true)
    val (liquidGlassNavBarEnabled) = rememberPreference(LiquidGlassNavBarEnabledKey, defaultValue = true)
    val (liquidGlassSidePanelEnabled) = rememberPreference(LiquidGlassSidePanelEnabledKey, defaultValue = true)
    val (liquidGlassSidePanelVibrancy) = rememberPreference(LiquidGlassSidePanelVibrancyKey, defaultValue = 1.2f)
    val (liquidGlassSidePanelBlurRadius) = rememberPreference(LiquidGlassSidePanelBlurRadiusKey, defaultValue = 2f)
    val (liquidGlassSidePanelLensHeight) = rememberPreference(LiquidGlassSidePanelLensHeightKey, defaultValue = 0.4f)
    val (liquidGlassSidePanelLensAmount) = rememberPreference(LiquidGlassSidePanelLensAmountKey, defaultValue = 0.6f)
    val (liquidGlassSidePanelColorInt) = rememberPreference(LiquidGlassSidePanelColorKey, defaultValue = 0)
    val (liquidGlassSidePanelSurfaceOpacity) = rememberPreference(LiquidGlassSidePanelSurfaceOpacityKey, defaultValue = 0.75f)
    val (liquidGlassSidePanelTextColorInt) = rememberPreference(LiquidGlassSidePanelTextColorKey, defaultValue = 0)

    // The tint follows the theme, while opacity always follows the user's slider.
    val resolvedGlassTint = resolveGlassTint(
        liquidGlassSurfaceTintColorInt,
        artworkChromeDark,
        GlassTintTokens.DefaultSurfaceDark,
        GlassTintTokens.DefaultSurfaceLight,
    )
    val resolvedGlassOpacity = liquidGlassSurfaceOpacity.coerceIn(0f, 1f)
    val resolvedPuckTint = resolveGlassTint(
        liquidGlassPuckColorInt,
        artworkChromeDark,
        GlassTintTokens.DefaultPuckDark,
        GlassTintTokens.DefaultPuckLight,
    )
    val resolvedPuckOpacity = liquidGlassPuckOpacity.coerceIn(0f, 1f)
    val resolvedHighlightTint = resolveGlassTint(
        liquidGlassHighlightColorInt,
        artworkChromeDark,
        GlassTintTokens.DefaultHighlightDark,
        GlassTintTokens.DefaultHighlightLight,
    )
    val resolvedHighlightOpacity = liquidGlassHighlightOpacity.coerceIn(0f, 1f)
    val resolvedSidePanelTint = if (liquidGlassSidePanelColorInt == 0) {
        if (artworkChromeDark) Color.Black else Color.White
    } else {
        Color(liquidGlassSidePanelColorInt)
    }
    val glassSurfaceBehind = if (LocalAmbientDarkChrome.current.value) {
        LocalAmbientBackgroundColor.current.value
    } else AppTheme.colorScheme.surface
    val adaptiveGlassTextColor = if (liquidGlassAdaptiveContrast) {
        glassContentColorFor(glassSurfaceBehind, resolvedGlassTint, resolvedGlassOpacity)
    } else {
        themeGlassTextColor
    }
    val adaptiveSidePanelTextColor = if (liquidGlassAdaptiveContrast) {
        glassContentColorFor(
            glassSurfaceBehind,
            resolvedSidePanelTint,
            liquidGlassSidePanelSurfaceOpacity,
        )
    } else {
        themeGlassTextColor
    }

    val glassEffectConfig = remember(
        liquidGlassGlobalEnabled, liquidGlassVibrancy, liquidGlassBlurRadius,
        liquidGlassLensHeight, liquidGlassLensAmount, liquidGlassChromaticAberration,
        liquidGlassDepthEffect, liquidGlassSurfaceTintColorInt,
        liquidGlassSurfaceOpacity, liquidGlassTextColorInt,
        liquidGlassHighlightColorInt, liquidGlassHighlightOpacity, liquidGlassStyle,
        liquidGlassPuckColorInt, liquidGlassPuckOpacity,
        liquidGlassMiniPlayerEnabled, liquidGlassNavBarEnabled, liquidGlassSidePanelEnabled,
        liquidGlassSidePanelVibrancy, liquidGlassSidePanelBlurRadius,
        liquidGlassSidePanelLensHeight, liquidGlassSidePanelLensAmount,
        liquidGlassSidePanelColorInt, liquidGlassSidePanelSurfaceOpacity, liquidGlassSidePanelTextColorInt,
        adaptiveGlassTextColor, adaptiveSidePanelTextColor, artworkChromeDark,
    ) {
        // The UI toggle seeds these settings once; sliders remain authoritative.
        GlassEffectConfig(
            globalEnabled = liquidGlassGlobalEnabled,
            vibrancy = liquidGlassVibrancy,
            blurRadius = liquidGlassBlurRadius,
            lensHeight = liquidGlassLensHeight,
            lensAmount = liquidGlassLensAmount,
            chromaticAberration = liquidGlassChromaticAberration,
            depthEffect = liquidGlassDepthEffect,
            surfaceTintColor = resolvedGlassTint,
            surfaceOpacity = resolvedGlassOpacity,
            highlightColor = resolvedHighlightTint,
            highlightOpacity = resolvedHighlightOpacity,
            style = liquidGlassStyle,
            puckColor = resolvedPuckTint,
            puckOpacity = resolvedPuckOpacity,
            // Adaptive text is left unset so each surface resolves it from what is behind it
            // (or from the dark palette inside a forced-dark scope) instead of a light-theme value.
            textColor = if (liquidGlassTextColorInt == 0) {
                if (liquidGlassAdaptiveContrast) Color.Unspecified else adaptiveGlassTextColor
            } else Color(liquidGlassTextColorInt),
            miniPlayerEnabled = liquidGlassMiniPlayerEnabled,
            navBarEnabled = liquidGlassNavBarEnabled,
            sidePanelEnabled = liquidGlassSidePanelEnabled,
            sidePanelVibrancy = liquidGlassSidePanelVibrancy,
            sidePanelBlurRadius = liquidGlassSidePanelBlurRadius,
            sidePanelLensHeight = liquidGlassSidePanelLensHeight,
            sidePanelLensAmount = liquidGlassSidePanelLensAmount,
            sidePanelColor = if (liquidGlassSidePanelColorInt == 0) Color.Unspecified else Color(liquidGlassSidePanelColorInt),
            sidePanelSurfaceOpacity = liquidGlassSidePanelSurfaceOpacity,
            sidePanelTextColor = if (liquidGlassSidePanelTextColorInt == 0) adaptiveSidePanelTextColor else Color(liquidGlassSidePanelTextColorInt),
        )
    }
    return remember(glassEffectConfig, liquidGlassPuckColorInt) {
        GlassConfigState(glassEffectConfig, liquidGlassPuckColorInt == 0)
    }
}

private fun resolveGlassTint(
    customColor: Int,
    artworkChromeDark: Boolean,
    darkDefault: Color,
    lightDefault: Color,
): Color = when {
    customColor != 0 -> Color(customColor)
    artworkChromeDark -> darkDefault
    else -> lightDefault
}
