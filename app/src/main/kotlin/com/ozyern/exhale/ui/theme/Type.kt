/*
 * Exhale Project Original (2026)
 * ozyern (github.com/ozyern)
 * Licensed Under GPL-3.0 | see git history for contributors
 */



package com.ozyern.exhale.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ozyern.exhale.R

// TODO: Define or import actual M3 Expressive font families if needed.
// For now, using default FontFamily as a placeholder.

// Define M3 Expressive Typography based on Material Design guidelines
// https://m3.material.io/styles/typography/type-scale-tokens
// Note: M3 Expressive might introduce subtle changes or new roles.
// Referencing standard M3 roles for now, adjust if Expressive spec differs significantly.
// ---------------------------------------------------------------------------------------------
// iOS large-title convention, applied to the display/headline/title band.
//
// Material's type scale sets its big roles at Normal weight with neutral tracking; Apple sets
// theirs Bold-to-Heavy and tightens tracking as the size grows (optical sizing — large text needs
// less letter space to read as one word). Leaving the app on the Material defaults is what made
// the headers read as "Android" no matter what the surfaces around them did, so the weights and
// tracking below follow the iOS convention instead. Body, label and the small title roles keep
// the Material values: those are set for legibility at small sizes and Apple's are effectively
// the same there.
// ---------------------------------------------------------------------------------------------
private fun buildTypography(fontFamily: FontFamily, displayFamily: FontFamily = fontFamily) =
    Typography(
        // Apple's scale, role for role: Large Title 34, Title 1 28, Title 2 22, Title 3 20,
        // Headline 17 semibold, Body 17, Callout 16, Subheadline 15, Footnote 13, Caption 12/11.
        // Tracking follows SF's optical tables — tight at display sizes, near zero at captions.
        displayLarge = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 54.sp, letterSpacing = (-0.9).sp),
        displayMedium = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.7).sp),
        displaySmall = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
        headlineLarge = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = (-0.4).sp),
        headlineMedium = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp),
        headlineSmall = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
        titleLarge = TextStyle(fontFamily = displayFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.26).sp),
        titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
        titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp),
        bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp),
        bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp),
        bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp),
        labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp),
        labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp),
        labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.06.sp),
    )

// Linotte, Exhale's own face on every platform, set to Apple's sizes and weights. Its round,
// wide letterforms don't want SF's tight tracking, so the body sizes are left at neutral spacing.
private val AppFontFamily = FontFamily(Font(R.font.linotte))
val AppTypography = buildTypography(AppFontFamily).let { t ->
    t.copy(
        titleMedium = t.titleMedium.copy(letterSpacing = 0.sp),
        titleSmall = t.titleSmall.copy(letterSpacing = 0.sp),
        bodyLarge = t.bodyLarge.copy(letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.copy(letterSpacing = 0.sp),
        bodySmall = t.bodySmall.copy(letterSpacing = 0.1.sp),
        labelLarge = t.labelLarge.copy(letterSpacing = 0.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.1.sp),
        labelSmall = t.labelSmall.copy(letterSpacing = 0.2.sp),
    )
}
val SystemTypography = buildTypography(FontFamily.Default)
