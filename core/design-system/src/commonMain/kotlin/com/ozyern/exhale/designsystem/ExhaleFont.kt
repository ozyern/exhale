package com.ozyern.exhale.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ozyern.exhale.designsystem.resources.Res
import com.ozyern.exhale.designsystem.resources.sf_pro_text_regular
import com.ozyern.exhale.designsystem.resources.sf_pro_text_medium
import com.ozyern.exhale.designsystem.resources.sf_pro_text_semibold
import com.ozyern.exhale.designsystem.resources.sf_pro_text_bold
import com.ozyern.exhale.designsystem.resources.chillax_semibold
import org.jetbrains.compose.resources.Font

/** The same font assets and weight mapping used by the production Android client. */
@Composable
fun exhaleFontFamily(): FontFamily = FontFamily(
    Font(Res.font.sf_pro_text_regular, FontWeight.Normal),
    Font(Res.font.sf_pro_text_medium, FontWeight.Medium),
    Font(Res.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(Res.font.sf_pro_text_bold, FontWeight.Bold),
)

@Composable
fun exhaleWordmarkFontFamily(): FontFamily = FontFamily(Font(Res.font.chillax_semibold, FontWeight.SemiBold))
