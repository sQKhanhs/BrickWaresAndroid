package com.senniapp.brickwares.ui.login

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.SignInResult
import com.senniapp.brickwares.ui.components.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the email form is signing into an existing account or creating a new one. */
enum class LoginMode { SIGN_IN, SIGN_UP }

data class LoginUiState(
    val mode: LoginMode = LoginMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val signingIn: Boolean = false,
    val error: UiText? = null,
    /** Non-error status, e.g. "a new code is on the way" after a resend. */
    val info: UiText? = null,
    /** After a confirm-required sign-up, the form is replaced by a 6-digit code (OTP) entry step. */
    val awaitingCode: Boolean = false,
    /** The address being confirmed — shown on the code step and used to verify/resend. */
    val pendingEmail: String = "",
    val code: String = "",
    /** Epoch-ms until which Resend is on cooldown (0 = available). Throttles confirmation-email spam. */
    val resendCooldownUntil: Long = 0L,
    /** Password typed in a sign-up awaiting confirmation; re-applied after OTP verify so the latest
     *  attempt's password wins (GoTrue keeps the first password when an unconfirmed email is re-signed-up). */
    val pendingPassword: String = "",
)

/**
 * Drives the app-open login page: Google sign-in and email/password sign-in + sign-up. Persists the
 * "stay signed in" choice (via [AuthGate]); on a successful session [AuthGate] routes to the main app.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        // When sign-in completes through ANY path (email, OTP verify, Google), clear the form so a
        // later reopen — e.g. after signing out in the same session — starts fresh instead of
        // resurrecting the pending OTP step. The modal only opens while logged out, so this never
        // wipes a code the user is mid-entry.
        viewModelScope.launch {
            authRepository.authState.collect { if (it is AuthState.SignedIn) reset() }
        }
    }

    /** Restores the pristine form (clears any pending OTP step and typed fields). */
    fun reset() { _uiState.value = LoginUiState() }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun onSwitchMode() = _uiState.update {
        // Switching between Sign In / Sign Up starts fresh — clear every field, error, and info.
        LoginUiState(mode = if (it.mode == LoginMode.SIGN_IN) LoginMode.SIGN_UP else LoginMode.SIGN_IN)
    }

    fun onGoogleSignIn(context: Context) {
        if (_uiState.value.signingIn) return
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val message: UiText? = when (authRepository.signInWithGoogle(context)) {
                SignInResult.Success, SignInResult.Cancelled -> null
                SignInResult.NoCredential -> UiText.Res(R.string.login_err_no_google)
                else -> UiText.Res(R.string.login_err_generic)
            }
            _uiState.update { it.copy(signingIn = false, error = message) }
        }
    }

    /** [languageTag] is the app's current display language ("en"/"vi"), stamped onto a new account so
     *  its confirmation email arrives in that language. Ignored for sign-in. */
    fun onEmailSubmit(languageTag: String) {
        val s = _uiState.value
        if (s.signingIn) return
        validate(s)?.let { err -> _uiState.update { it.copy(error = err) }; return }

        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val result = if (s.mode == LoginMode.SIGN_IN) {
                authRepository.signInWithEmail(s.email, s.password)
            } else {
                authRepository.signUpWithEmail(s.email, s.password, languageTag)
            }
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(signingIn = false) // AuthGate routes to the app
                    SignInResult.EmailConfirmationRequired -> it.copy(
                        signingIn = false, awaitingCode = true, pendingEmail = s.email.trim(),
                        password = "", confirmPassword = "", code = "", info = null,
                        // Remember the just-typed password so we can force it after the OTP verify.
                        pendingPassword = s.password,
                        // A code was just sent — start the cooldown so an immediate Resend can't spam.
                        resendCooldownUntil = System.currentTimeMillis() + RESEND_COOLDOWN_MS,
                    )
                    // Signing in to an account that was never confirmed → open the code step (with the
                    // Resend option right there) instead of a dead-end error. No email was sent on this
                    // path, so Resend is available immediately (the old code may have expired). Don't
                    // touch the password here (this is a sign-in, not a new password).
                    SignInResult.EmailNotConfirmed -> it.copy(
                        signingIn = false, awaitingCode = true, pendingEmail = s.email.trim(),
                        password = "", confirmPassword = "", code = "", resendCooldownUntil = 0L,
                        pendingPassword = "", info = UiText.Res(R.string.login_info_needs_confirm),
                    )
                    SignInResult.InvalidCredentials -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_invalid_credentials))
                    SignInResult.EmailAlreadyRegistered -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_email_exists))
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    is SignInResult.Error -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_generic))
                    else -> it.copy(signingIn = false)
                }
            }
        }
    }

    fun onCodeChange(value: String) = _uiState.update {
        it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), error = null)
    }

    /** Verifies the 6-digit sign-up code; on success the session is established and [AuthGate] routes in. */
    fun onVerifyCode() {
        val s = _uiState.value
        if (s.signingIn) return
        if (s.code.length < CODE_LENGTH) {
            _uiState.update { it.copy(error = UiText.Res(R.string.login_err_code_invalid)) }; return
        }
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val result = authRepository.verifySignUpOtp(s.pendingEmail, s.code)
            // The account is now confirmed + signed in. Force the password to the one typed in THIS
            // sign-up: GoTrue keeps the FIRST password when an unconfirmed email is signed up again, so
            // without this a second attempt's password would be silently ignored. Best-effort — the
            // user is signed in regardless, and it's a no-op when it already matches.
            if (result == SignInResult.Success && s.pendingPassword.isNotEmpty()) {
                authRepository.setPassword(s.pendingPassword)
            }
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(signingIn = false) // AuthGate routes to the app
                    is SignInResult.Error -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_code_invalid))
                    else -> it.copy(signingIn = false)
                }
            }
        }
    }

    /** Re-sends the confirmation code to the pending email, then starts a 60s cooldown. */
    fun onResendCode() {
        val s = _uiState.value
        if (s.signingIn || s.pendingEmail.isBlank()) return
        if (System.currentTimeMillis() < s.resendCooldownUntil) return // still cooling down
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val result = authRepository.resendSignUpCode(s.pendingEmail)
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(
                        signingIn = false, info = UiText.Res(R.string.login_code_resent),
                        resendCooldownUntil = System.currentTimeMillis() + RESEND_COOLDOWN_MS,
                    )
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    is SignInResult.Error -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_generic))
                    else -> it.copy(signingIn = false)
                }
            }
        }
    }

    /** Leaves the code step, returning to the email/password form. */
    fun onBackFromCode() = _uiState.update {
        it.copy(awaitingCode = false, code = "", error = null, info = null)
    }

    /** Returns an error message if the form is invalid, else null. */
    private fun validate(s: LoginUiState): UiText? = when {
        s.email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(s.email.trim()).matches() ->
            UiText.Res(R.string.login_err_invalid_email)
        s.password.length < MIN_PASSWORD -> UiText.Res(R.string.login_err_password_short, listOf(MIN_PASSWORD))
        s.mode == LoginMode.SIGN_UP && s.password != s.confirmPassword -> UiText.Res(R.string.login_err_password_mismatch)
        else -> null
    }

    private companion object {
        const val MIN_PASSWORD = 6
        const val CODE_LENGTH = 6
        const val RESEND_COOLDOWN_MS = 60_000L
    }
}
