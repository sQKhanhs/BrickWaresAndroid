package com.senniapp.brickwares.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.local.AnalyticsPrefs
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.data.local.RetirementAlertPrefs
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.CsvTooNewException
import com.senniapp.brickwares.data.repository.FeedbackRepository
import com.senniapp.brickwares.data.repository.SignInResult
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.util.AppCurrency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the Settings tab. Account state (sign-in/out) is real, backed by Supabase Auth via
 * [AuthRepository]; preference toggles are still in-memory, and not-yet-built actions (export, privacy
 * policy, feedback…) surface a toast instead of doing work.
 */
class SettingsViewModel(
    private val authRepository: AuthRepository = AuthRepository,
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val feedbackRepository: FeedbackRepository = FeedbackRepository(),
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
                            isGoogleOnly = authState.user.isGoogleOnly,
                        )
                        AuthState.SignedOut, AuthState.Loading -> it.copy(
                            isLoggedIn = false,
                            userName = "",
                            userEmail = "",
                            isGoogleOnly = false,
                            showDeleteConfirm = false,
                            showSetPassword = false,
                        )
                    }
                }
            }
            .launchIn(viewModelScope)

        // Reflect the persisted display currency (seeded at app start) and any later change.
        CurrencyPrefs.currency
            .onEach { currency -> _uiState.update { it.copy(currency = currency) } }
            .launchIn(viewModelScope)

        // Persisted retirement-alerts toggle (Settings → Notifications).
        RetirementAlertPrefs.enabledFlow
            .onEach { enabled -> _uiState.update { it.copy(retirementAlerts = enabled) } }
            .launchIn(viewModelScope)

        // Persisted usage-analytics consent (Settings → Privacy). Crashlytics is always on, not gated here.
        AnalyticsPrefs.consentFlow
            .onEach { consent -> _uiState.update { it.copy(analyticsConsent = consent) } }
            .launchIn(viewModelScope)
    }

    // ---- Account (sign-in is handled by the shared sign-in modal via SignInController) ----

    fun onSignOut() {
        viewModelScope.launch {
            authRepository.signOut()
            // Confirm it happened — the tab stays put (no login wall), so a silent flip to the
            // signed-out layout can read as nothing having happened.
            _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_signed_out)) }
        }
    }

    // ---- Set a password (Google-only accounts, to add email+password sign-in) ----

    fun onOpenSetPassword() = _uiState.update { it.copy(showSetPassword = true) }
    fun onCloseSetPassword() = _uiState.update { it.copy(showSetPassword = false) }

    /** Sets the password on the current (Google) account; closes the dialog and toasts the outcome. */
    fun onSetPassword(newPassword: String) {
        viewModelScope.launch {
            val result = authRepository.setPassword(newPassword)
            val toast = when (result) {
                SignInResult.Success -> UiText.Res(R.string.toast_password_set)
                SignInResult.PasswordAlreadySet -> UiText.Res(R.string.toast_password_already_set)
                else -> UiText.Res(R.string.toast_password_failed)
            }
            // Either way the account now provably has a password → no longer "Google-only"; the
            // repository also re-emits the auth state, so this just avoids a one-frame lag.
            val hasPassword = result == SignInResult.Success || result == SignInResult.PasswordAlreadySet
            _uiState.update {
                it.copy(
                    showSetPassword = false,
                    toastMessage = toast,
                    isGoogleOnly = if (hasPassword) false else it.isGoogleOnly,
                )
            }
        }
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
        // Deletes the account + all its data via the SECURITY DEFINER RPC, wipes the local mirror and
        // drops the session (see AuthRepository.deleteAccount). authState → SignedOut then routes out.
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirm = false) }
            val toast = when (authRepository.deleteAccount()) {
                SignInResult.Success -> UiText.Res(R.string.toast_account_deleted)
                else -> UiText.Res(R.string.toast_delete_account_failed)
            }
            _uiState.update { it.copy(toastMessage = toast) }
        }
    }

    // ---- Preferences ----
    // Language is applied by persisting the tag + recreating the activity (SettingsScreen), and the
    // initial selection is derived from the effective locale above — so there's no VM setter for it.

    fun onCurrencyChange(currency: AppCurrency) {
        // Persist + broadcast; the CurrencyPrefs collector above updates our own UiState, and the
        // theme's LocalAppCurrency recomposes every price across the app.
        CurrencyPrefs.set(currency)
    }

    /**
     * Sets (not toggles) retirement alerts — the screen only asks for ON once the notification
     * permission is granted, so the switch never flips on after a denial. Persist + broadcast; the
     * RetirementAlertPrefs collector above updates our own UiState.
     */
    fun onSetRetirementAlerts(enabled: Boolean) {
        RetirementAlertPrefs.enabled = enabled
    }

    /**
     * Alerts can't be enabled because the system won't show a prompt — notifications are off for the
     * app in device settings, or the permission was permanently denied. Opens the explain + "Open
     * settings" dialog rather than leaving the tap a silent no-op.
     */
    fun onNotificationsBlocked() = _uiState.update { it.copy(showNotificationsBlocked = true) }

    fun onDismissNotificationsBlocked() = _uiState.update { it.copy(showNotificationsBlocked = false) }

    /** "Open settings" tapped: dismiss the dialog and remember to complete the enable on return. */
    fun onOpenNotificationSettings() =
        _uiState.update { it.copy(showNotificationsBlocked = false, awaitingNotificationSettings = true) }

    /**
     * The app came back to the foreground after "Open settings". If notifications are now allowed,
     * finish the enable the user already started (no second tap); either way drop the pending intent.
     */
    fun onResumedFromNotificationSettings(notificationsAllowed: Boolean) {
        if (notificationsAllowed) RetirementAlertPrefs.enabled = true
        _uiState.update { it.copy(awaitingNotificationSettings = false) }
    }

    fun onToggleAnalytics() {
        // Persist + broadcast; Observability applies it to Crashlytics/Analytics live, and the
        // AnalyticsPrefs collector above updates our own UiState.
        AnalyticsPrefs.consent = !AnalyticsPrefs.consent
    }

    fun onToggleChangelog() {
        _uiState.update { it.copy(showChangelog = !it.showChangelog) }
    }

    // ---- Export / import collection (CSV) ----

    /** Writes the collection as CSV to the user-picked [uri] (from the Create-Document picker). */
    fun onExportCollection(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            val toast = runCatching {
                val csv = repository.exportCollectionCsv()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error("Couldn't open the export file")
                }
            }.fold(
                onSuccess = { UiText.Res(R.string.toast_export_success) },
                onFailure = { UiText.Res(R.string.toast_export_failed) },
            )
            _uiState.update { it.copy(toastMessage = toast) }
        }
    }

    /**
     * Reads CSV from the user-picked [uri] and overwrites the collection with it, then syncs and waits.
     * The UI locks behind a loading screen ([SettingsUiState.importing]) until the whole thing finishes.
     */
    fun onImportCollection(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            val toast = runCatching {
                val csv = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                        ?: error("Couldn't open the import file")
                }
                repository.importCollectionCsv(csv) // overwrites locally, then syncs and waits
            }.fold(
                onSuccess = { count -> UiText.Res(R.string.toast_import_success, listOf(count)) },
                onFailure = { e ->
                    // A file from a newer app version → tell the user to update, not a generic error.
                    if (e is CsvTooNewException) UiText.Res(R.string.toast_import_too_new)
                    else UiText.Res(R.string.toast_import_failed)
                },
            )
            _uiState.update { it.copy(importing = false, toastMessage = toast) }
        }
    }

    /** Placeholder for actions whose real behaviour (files, external links) isn't built yet. */
    // ---- Send feedback ----

    fun onOpenFeedback() = _uiState.update { it.copy(showFeedback = true) }

    fun onCloseFeedback() {
        // Ignore a dismiss while a send is in flight — the toast will report the outcome.
        if (!_uiState.value.sendingFeedback) _uiState.update { it.copy(showFeedback = false) }
    }

    /**
     * Submits the feedback (the dialog validated length / email format). Closes the dialog on success
     * or a server-side rejection (rate-limited / invalid — nothing to retry as typed); keeps it open
     * with the text intact on a network failure so the user can try again.
     */
    fun onSendFeedback(message: String, contactEmail: String?) {
        if (_uiState.value.sendingFeedback) return
        _uiState.update { it.copy(sendingFeedback = true) }
        viewModelScope.launch {
            val result = feedbackRepository.send(message, contactEmail)
            _uiState.update {
                when (result) {
                    FeedbackRepository.Result.SENT -> it.copy(
                        sendingFeedback = false, showFeedback = false,
                        toastMessage = UiText.Res(R.string.toast_feedback_sent),
                    )
                    FeedbackRepository.Result.RATE_LIMITED -> it.copy(
                        sendingFeedback = false, showFeedback = false,
                        toastMessage = UiText.Res(R.string.toast_feedback_rate_limited),
                    )
                    FeedbackRepository.Result.INVALID -> it.copy(
                        sendingFeedback = false, showFeedback = false,
                        toastMessage = UiText.Res(R.string.toast_feedback_failed),
                    )
                    FeedbackRepository.Result.FAILED -> it.copy(
                        sendingFeedback = false,
                        toastMessage = UiText.Res(R.string.toast_feedback_failed),
                    )
                }
            }
        }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
