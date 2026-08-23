package com.senniapp.brickwares.ui.settings

import com.senniapp.brickwares.util.AppCurrency

/** Display language, chosen in Settings. (UI selection only for now — i18n isn't wired yet.) */
enum class AppLanguage(val label: String) {
    ENGLISH("English"),
    VIETNAMESE("Tiếng Việt"),
}

/**
 * Immutable UI state for the Settings tab. [isLoggedIn]/[userName]/[userEmail] now reflect the real
 * Supabase session (Google sign-in via [com.senniapp.brickwares.data.repository.AuthRepository]);
 * preferences below are still in-memory (no persistence yet). The theme preference lives at the app
 * level (MainActivity), not here, so it can drive [com.senniapp.brickwares.ui.theme.BrickWaresTheme].
 */
data class SettingsUiState(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val language: AppLanguage = AppLanguage.ENGLISH,
    val currency: AppCurrency = AppCurrency.VND,
    val retirementAlerts: Boolean = true,
    val analyticsConsent: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showChangelog: Boolean = false,
    val toastMessage: String? = null,
)
