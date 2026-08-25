package com.kma.quiz_game.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The original web app uses Google Font "Nunito". No .ttf is bundled here, so we approximate
 * its bold, rounded look with the system "sans-serif-rounded" family (available on API 29+;
 * falls back to the default sans-serif on older devices).
 */
val DuoFontFamily = FontFamily(
    Font(familyName = DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Normal),
    Font(familyName = DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.Bold),
    Font(familyName = DeviceFontFamilyName("sans-serif-rounded"), weight = FontWeight.ExtraBold),
)

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 46.sp),
    headlineLarge = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp),
    labelMedium = TextStyle(fontFamily = DuoFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
