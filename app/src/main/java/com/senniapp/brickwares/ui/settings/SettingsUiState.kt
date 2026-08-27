package com.senniapp.brickwares.ui.settings

import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.ui.components.UiText

/** Display language, chosen in Settings. [tag] is the BCP-47 language tag applied to the app locale. */
enum class AppLanguage(val label: String, val tag: String) {
    ENGLISH("English", "en"),
    VIETNAMESE("Tiếng Việt", "vi");

    companion object {
        /** The language matching a saved tag (or the effective/system language), defaulting to English. */
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) } ?: ENGLISH
    }
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
    val currency: AppCurrency = AppCurrency.VND,
    val retirementAlerts: Boolean = true,
    val analyticsConsent: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showChangelog: Boolean = false,
    /** Chosen profile avatar (session-only for now, like the other prefs). */
    val avatar: AvatarGender = AvatarGender.MALE,
    /** Whether the "Choose Avatar" picker sheet is open. */
    val showAvatarPicker: Boolean = false,
    val toastMessage: UiText? = null,
)
