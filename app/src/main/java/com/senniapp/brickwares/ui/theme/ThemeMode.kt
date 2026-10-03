package com.senniapp.brickwares.ui.theme

/**
 * App theme preference, chosen in Settings and saved by [com.senniapp.brickwares.data.local.ThemePrefs].
 * SYSTEM follows the device's dark-mode setting.
 */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}
