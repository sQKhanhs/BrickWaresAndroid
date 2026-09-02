package com.senniapp.brickwares.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw design tokens from the BrickWares handoff spec (README "Design Tokens").
 * These feed the [BwColors] light/dark schemes in Theme.kt — prefer reading
 * colors through `BwTheme.colors` in composables rather than these raw vals.
 */

// ---- Brand (shared across light/dark) ----
val BrandYellow = Color(0xFFFFD500)
val OnYellow = Color(0xFF1A1A1A)
val SuccessGreen = Color(0xFF2F7D4F)
// Brighter green for dark theme so success text/badges don't read dim against dark cards.
val DarkSuccessGreen = Color(0xFF57C46E)
val ErrorRed = Color(0xFFC0392B)
// Status badges — Gift With Purchase (violet) and Promotional (teal). Distinct from the existing
// red/gold/green/blue; darker in light theme for text contrast, lighter in dark theme.
val LightGwp = Color(0xFF7A3E9D)
val DarkGwp = Color(0xFFC79BE6)
val LightPromo = Color(0xFF1E7F7B)
val DarkPromo = Color(0xFF57C4BF)
// Magazine gift — blue, distinct from the teal promo / violet GWP badges.
val LightMagazine = Color(0xFF2563A8)
val DarkMagazine = Color(0xFF6FA6E6)
// Pending release (launch date in the future) — orange, reads as "coming soon".
val LightPending = Color(0xFFC2671C)
val DarkPending = Color(0xFFE89A54)

// ---- Light theme ----
val LightBg = Color(0xFFFAF8F5)
val LightSurface = Color(0xFFF4F2EC)
val LightCard = Color(0xFFFFFFFF)
val LightText = Color(0xFF1A1A1A)
val LightTextSecondary = Color(0xFF3A3A36)
val LightTextMuted = Color(0xFF8A8A84)
val LightTextMuted2 = Color(0xFF6A6A64)
val LightTextFaint = Color(0xFF9A9A94)
val LightBorder = Color(0x14000000)       // rgba(0,0,0,0.08)
val LightBorderSoft = Color(0x0F000000)   // rgba(0,0,0,0.06)
val LightBorderStrong = Color(0x26000000) // rgba(0,0,0,0.15)
val LightTrack = Color(0xFFE6E4DC)
val LightLinkAccent = Color(0xFF2F5FBF)
val LightLinkAccent2 = Color(0xFF8A6D1E)
val LightPlaceholderA = Color(0xFFEEEEEE)
val LightPlaceholderB = Color(0xFFF7F7F5)

// ---- Dark theme ----
val DarkBg = Color(0xFF18181B)
val DarkSurface = Color(0xFF242420)
val DarkCard = Color(0xFF2B2B27)
val DarkText = Color(0xFFF3F1EA)
val DarkTextSecondary = Color(0xFFD9D7CD)
val DarkTextMuted = Color(0xFFA9A79D)
val DarkTextMuted2 = Color(0xFFBCBAB0)
val DarkTextFaint = Color(0xFF7D7B73)
val DarkBorder = Color(0x1AFFFFFF)        // rgba(255,255,255,0.1)
val DarkBorderSoft = Color(0x12FFFFFF)    // rgba(255,255,255,0.07)
val DarkBorderStrong = Color(0x2EFFFFFF)  // rgba(255,255,255,0.18)
val DarkTrack = Color(0x1FFFFFFF)
val DarkLinkAccent = Color(0xFF7AA8FF)
val DarkLinkAccent2 = Color(0xFFE0BB5A)
val DarkPlaceholderA = Color(0xFF302F2B)
val DarkPlaceholderB = Color(0xFF262521)
