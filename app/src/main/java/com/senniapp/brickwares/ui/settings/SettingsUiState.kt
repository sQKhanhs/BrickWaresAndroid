package com.senniapp.brickwares.ui.settings

import com.senniapp.brickwares.util.AppCurrency

/** Display language, chosen in Settings. (UI selection only for now — i18n isn't wired yet.) */
enum class AppLanguage(val label: String) {
    ENGLISH("English"),
    VIETNAMESE("Tiếng Việt"),
}

/**
 * Immutable UI state for the Settings tab. All values are in-memory at the mock stage (no
 * persistence yet). [isLoggedIn] gates the account card; sign-in is mocked. The theme preference
 * lives at the app level (MainActivity), not here, so it can drive [com.senniapp.brickwares.ui.theme.BrickWaresTheme].
 */
data class SettingsUiState(
    val isLoggedIn: Boolean = false,
    val userName: String = "John Nguyen",
    val userEmail: String = "john.nguyen@gmail.com",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val currency: AppCurrency = AppCurrency.VND,
    val retirementAlerts: Boolean = true,
    val analyticsConsent: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showChangelog: Boolean = false,
    val toastMessage: String? = null,
)
