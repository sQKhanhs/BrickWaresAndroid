package com.senniapp.brickwares.ui.settings

import androidx.lifecycle.ViewModel
import com.senniapp.brickwares.util.AppCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel for the Settings tab. State is in-memory (mock stage): sign-in/out and delete-account
 * are simulated, preference toggles are stored locally, and not-yet-built actions (export, privacy
 * policy, feedback…) surface a toast instead of doing work.
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // ---- Account (mock) ----

    fun onSignIn() {
        _uiState.update { it.copy(isLoggedIn = true) }
    }

    fun onSignOut() {
        _uiState.update { it.copy(isLoggedIn = false, showDeleteConfirm = false) }
    }

    fun onRequestDeleteAccount() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun onCancelDeleteAccount() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun onConfirmDeleteAccount() {
        _uiState.update {
            it.copy(isLoggedIn = false, showDeleteConfirm = false, toastMessage = "Account deleted")
        }
    }

    // ---- Preferences ----

    fun onLanguageChange(language: AppLanguage) {
        _uiState.update { it.copy(language = language) }
    }

    fun onCurrencyChange(currency: AppCurrency) {
        _uiState.update { it.copy(currency = currency) }
    }

    fun onToggleRetirementAlerts() {
        _uiState.update { it.copy(retirementAlerts = !it.retirementAlerts) }
    }

    fun onToggleSharePrices() {
        _uiState.update { it.copy(sharePrices = !it.sharePrices) }
    }

    fun onToggleAnalytics() {
        _uiState.update { it.copy(analyticsConsent = !it.analyticsConsent) }
    }

    fun onToggleChangelog() {
        _uiState.update { it.copy(showChangelog = !it.showChangelog) }
    }

    /** Placeholder for actions whose real behaviour (files, external links, OAuth) isn't built yet. */
    fun onComingSoon(action: String) {
        _uiState.update { it.copy(toastMessage = "$action isn't available in this preview yet") }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
