package com.senniapp.brickwares.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * BrickWares typography. The handoff spec uses Roboto (weights 400/500/700/900);
 * on Android [FontFamily.Default] *is* Roboto, so no font files are needed.
 * Named styles map to the spec's roles (wordmark, hero value, stat number, etc.).
 */
object BwType {
    val wordmark = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,      // 900
        fontSize = 24.sp,
        letterSpacing = (-0.24).sp,
    )
    val heroLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,       // 700
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
    )
    val heroValue = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,      // 900
        fontSize = 44.sp,
        letterSpacing = (-0.5).sp,
    )
    val statNumber = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,      // 900
        fontSize = 22.sp,
    )
    val statLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,     // 500
        fontSize = 12.sp,
    )
    val pill = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,       // 700
        fontSize = 12.sp,
    )
    val cardTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,  // 800
        fontSize = 15.sp,
    )
    val body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,     // 400
        fontSize = 13.sp,
    )
    val micro = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,       // 700
        fontSize = 10.sp,
        letterSpacing = 0.4.sp,
    )
    val navLabel = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,   // 600
        fontSize = 11.sp,
    )
}

// Material3 typography (used by stock M3 components); app UI mostly uses BwType directly.
val Typography = Typography(
    bodyLarge = BwType.body,
    titleLarge = BwType.cardTitle,
    labelSmall = BwType.micro,
)
