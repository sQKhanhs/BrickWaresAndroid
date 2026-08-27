package com.senniapp.brickwares.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.util.AppCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings tab. Account state (sign-in/out) is real, backed by Supabase Auth via
 * [AuthRepository]; preference toggles are still in-memory, and not-yet-built actions (export, privacy
 * policy, feedback…) surface a toast instead of doing work.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        authRepository.authState
            .onEach { authState ->
                _uiState.update {
                    when (authState) {
                        is AuthState.SignedIn -> it.copy(
                            isLoggedIn = true,
                            userName = authState.user.displayName,
                            userEmail = authState.user.email,
                        )
                        AuthState.SignedOut, AuthState.Loading -> it.copy(
                            isLoggedIn = false,
                            userName = "",
                            userEmail = "",
                            showDeleteConfirm = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    // ---- Account (sign-in is handled by the shared sign-in modal via SignInController) ----

    fun onSignOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    // ---- Avatar ----

    fun onOpenAvatarPicker() {
        _uiState.update { it.copy(showAvatarPicker = true) }
    }

    fun onCloseAvatarPicker() {
        _uiState.update { it.copy(showAvatarPicker = false) }
    }

    fun onSelectAvatar(avatar: AvatarGender) {
        _uiState.update { it.copy(avatar = avatar, showAvatarPicker = false) }
    }

    fun onRequestDeleteAccount() {
        _uiState.update { it.copy(showDeleteConfirm = true) }
    }

    fun onCancelDeleteAccount() {
        _uiState.update { it.copy(showDeleteConfirm = false) }
    }

    fun onConfirmDeleteAccount() {
        // Real account deletion needs a server-side (admin) call that isn't built yet — the client
        // anon key can't delete an auth user. Surface that honestly instead of faking it.
        _uiState.update {
            it.copy(
                showDeleteConfirm = false,
                toastMessage = UiText.Res(R.string.toast_delete_account_soon),
            )
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

    fun onToggleAnalytics() {
        _uiState.update { it.copy(analyticsConsent = !it.analyticsConsent) }
    }

    fun onToggleChangelog() {
        _uiState.update { it.copy(showChangelog = !it.showChangelog) }
    }

    /** Placeholder for actions whose real behaviour (files, external links) isn't built yet. */
    fun onComingSoon(@Suppress("UNUSED_PARAMETER") action: String) {
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_coming_soon)) }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
