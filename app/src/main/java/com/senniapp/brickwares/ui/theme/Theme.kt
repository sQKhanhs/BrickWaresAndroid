package com.senniapp.brickwares.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * BrickWares' full colour token set. Material3's [androidx.compose.material3.ColorScheme]
 * only has a handful of semantic slots, so the exact brand tokens (muted variants,
 * borders, link accents, placeholders…) live here and are read via [BwTheme.colors].
 */
@Immutable
data class BwColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textMuted2: Color,
    val textFaint: Color,
    val border: Color,
    val borderSoft: Color,
    val borderStrong: Color,
    val track: Color,
    val linkAccent: Color,
    val linkAccent2: Color,
    val placeholderA: Color,
    val placeholderB: Color,
    // brand (shared)
    val brandYellow: Color,
    val onYellow: Color,
    val success: Color,
    val error: Color,
    val isDark: Boolean,
)

private val LightBwColors = BwColors(
    bg = LightBg,
    surface = LightSurface,
    card = LightCard,
    text = LightText,
    textSecondary = LightTextSecondary,
    textMuted = LightTextMuted,
    textMuted2 = LightTextMuted2,
    textFaint = LightTextFaint,
    border = LightBorder,
    borderSoft = LightBorderSoft,
    borderStrong = LightBorderStrong,
    track = LightTrack,
    linkAccent = LightLinkAccent,
    linkAccent2 = LightLinkAccent2,
    placeholderA = LightPlaceholderA,
    placeholderB = LightPlaceholderB,
    brandYellow = BrandYellow,
    onYellow = OnYellow,
    success = SuccessGreen,
    error = ErrorRed,
    isDark = false,
)

private val DarkBwColors = BwColors(
    bg = DarkBg,
    surface = DarkSurface,
    card = DarkCard,
    text = DarkText,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    textMuted2 = DarkTextMuted2,
    textFaint = DarkTextFaint,
    border = DarkBorder,
    borderSoft = DarkBorderSoft,
    borderStrong = DarkBorderStrong,
    track = DarkTrack,
    linkAccent = DarkLinkAccent,
    linkAccent2 = DarkLinkAccent2,
    placeholderA = DarkPlaceholderA,
    placeholderB = DarkPlaceholderB,
    brandYellow = BrandYellow,
    onYellow = OnYellow,
    success = SuccessGreen,
    error = ErrorRed,
    isDark = true,
)

private val LocalBwColors = staticCompositionLocalOf { LightBwColors }

/** Accessor for BrickWares design tokens inside composables: `BwTheme.colors.text`. */
object BwTheme {
    val colors: BwColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBwColors.current
}

// A minimal Material3 scheme so stock M3 components (ripples, dialogs) sit on-brand.
private val LightMaterial = lightColorScheme(
    primary = BrandYellow,
    onPrimary = OnYellow,
    background = LightBg,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    error = ErrorRed,
)

private val DarkMaterial = darkColorScheme(
    primary = BrandYellow,
    onPrimary = OnYellow,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    error = ErrorRed,
)

@Composable
fun BrickWaresTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val bwColors = if (darkTheme) DarkBwColors else LightBwColors
    CompositionLocalProvider(LocalBwColors provides bwColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkMaterial else LightMaterial,
            typography = Typography,
            content = content,
        )
    }
}
