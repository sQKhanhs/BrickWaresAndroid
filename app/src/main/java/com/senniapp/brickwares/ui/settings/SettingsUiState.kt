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
    /** Signed in with Google but no password yet → offer "Set a password". */
    val isGoogleOnly: Boolean = false,
    /** Whether the "Set a password" dialog is open. */
    val showSetPassword: Boolean = false,
    val currency: AppCurrency = AppCurrency.USD,
    /** Retirement alerts opt-in (persisted in RetirementAlertPrefs; off by default, needs an account). */
    val retirementAlerts: Boolean = false,
    /** Crash-report + usage-analytics opt-in (persisted in AnalyticsPrefs; off by default). */
    val analyticsConsent: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /** Retirement alerts can't be enabled (notifications blocked / prompt won't show) → explain + "Open settings". */
    val showNotificationsBlocked: Boolean = false,
    /** "Open settings" was tapped from that dialog — finish the enable when the app comes back with notifications allowed. */
    val awaitingNotificationSettings: Boolean = false,
    val showChangelog: Boolean = false,
    /** Whether the "Send feedback" dialog is open. */
    val showFeedback: Boolean = false,
    /** A feedback submission is in flight (the dialog's Send shows a spinner and ignores taps). */
    val sendingFeedback: Boolean = false,
    /** Chosen profile avatar (session-only for now, like the other prefs). */
    val avatar: AvatarGender = AvatarGender.MALE,
    /** Whether the "Choose Avatar" picker sheet is open. */
    val showAvatarPicker: Boolean = false,
    /** A CSV import (overwrite + immediate sync) is running — the UI locks behind a loading screen. */
    val importing: Boolean = false,
    val toastMessage: UiText? = null,
)
