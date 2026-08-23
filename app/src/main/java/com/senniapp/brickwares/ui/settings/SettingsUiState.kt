package com.senniapp.brickwares.ui.settings

import com.senniapp.brickwares.util.AppCurrency

/** Display language, chosen in Settings. (UI selection only for now — i18n isn't wired yet.) */
enum class AppLanguage(val label: String) {
    ENGLISH("English"),
    VIETNAMESE("Tiếng Việt"),
}

/** The two bundled profile avatars a signed-in user can pick between. */
enum class AvatarGender(val asset: String) {
    MALE("file:///android_asset/avatar_m.png"),
    FEMALE("file:///android_asset/avatar_f.png"),
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
    /** Chosen profile avatar (session-only for now, like the other prefs). */
    val avatar: AvatarGender = AvatarGender.MALE,
    /** Whether the "Choose Avatar" picker sheet is open. */
    val showAvatarPicker: Boolean = false,
    val toastMessage: String? = null,
)
