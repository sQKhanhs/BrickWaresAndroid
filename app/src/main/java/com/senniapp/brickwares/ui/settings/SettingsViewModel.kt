package com.senniapp.brickwares.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.SignInResult
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

    /** Guards against launching a second Credential Manager request while one is in flight. */
    private var signingIn = false

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

    // ---- Account ----

    /** Requires an Activity context — Credential Manager anchors its UI to the current activity. */
    fun onSignIn(context: Context) {
        if (signingIn) return
        signingIn = true
        viewModelScope.launch {
            val message = when (val result = authRepository.signInWithGoogle(context)) {
                SignInResult.Success -> null // authState flow flips the UI to signed-in
                SignInResult.Cancelled -> null // user backed out; stay quiet
                SignInResult.NoCredential ->
                    "No Google account available on this device"
                is SignInResult.Error -> "Sign-in failed: ${result.message}"
            }
            if (message != null) _uiState.update { it.copy(toastMessage = message) }
            signingIn = false
        }
    }

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
                toastMessage = "Account deletion isn't available in this preview yet",
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
    fun onComingSoon(action: String) {
        _uiState.update { it.copy(toastMessage = "$action isn't available in this preview yet") }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
