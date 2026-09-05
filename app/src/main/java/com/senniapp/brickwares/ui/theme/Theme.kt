package com.senniapp.brickwares.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.util.AppCurrency

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
    val gwp: Color,
    val promo: Color,
    val magazine: Color,
    val pending: Color,
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
    gwp = LightGwp,
    promo = LightPromo,
    magazine = LightMagazine,
    pending = LightPending,
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
    success = DarkSuccessGreen,
    error = ErrorRed,
    gwp = DarkGwp,
    promo = DarkPromo,
    magazine = DarkMagazine,
    pending = DarkPending,
    isDark = true,
)

private val LocalBwColors = staticCompositionLocalOf { LightBwColors }

/** The app-wide display currency, provided by [BrickWaresTheme] from [CurrencyPrefs] so a change in
 *  Settings recomposes every price with no restart. Read via `BwTheme.currency`. */
private val LocalAppCurrency = staticCompositionLocalOf { AppCurrency.USD }

/** Accessor for BrickWares design tokens inside composables: `BwTheme.colors.text`. */
object BwTheme {
    val colors: BwColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBwColors.current

    /** The chosen display currency for money formatting (`formatMoney(amountUsdCents, BwTheme.currency)`). */
    val currency: AppCurrency
        @Composable
        @ReadOnlyComposable
        get() = LocalAppCurrency.current
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
    val currency by CurrencyPrefs.currency.collectAsState()
    CompositionLocalProvider(LocalBwColors provides bwColors, LocalAppCurrency provides currency) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkMaterial else LightMaterial,
            typography = Typography,
            content = content,
        )
    }
}
