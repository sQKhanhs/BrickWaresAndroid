package com.senniapp.brickwares.ui.login

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.BuildConfig
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.MIN_PASSWORD_LENGTH
import com.senniapp.brickwares.data.repository.SignInResult
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.ui.navigation.SignInController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Whether the email form is signing into an existing account or creating a new one. */
enum class LoginMode { SIGN_IN, SIGN_UP }

/**
 * Forgot-password steps (from the sign-in form's "Forgot password?" link): [EMAIL] ask for the address
 * → [CODE] the emailed 6-digit recovery code → [NEW_PASSWORD] choose a new one. Verifying the code
 * signs the user in, so the last step runs on a fresh session with the overlay held open.
 */
enum class ResetStep { NONE, EMAIL, CODE, NEW_PASSWORD }

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
    /** Forgot-password flow; [ResetStep.NONE] = the normal sign-in / sign-up form. Reuses [email],
     *  [pendingEmail], [code], [password], [confirmPassword] and the resend cooldown. */
    val reset: ResetStep = ResetStep.NONE,
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

    /**
     * Turnstile gate for the captcha-protected email calls (sign-up, password sign-in, resend). The
     * screen renders [TurnstileCaptchaHost] for it; [acquireCaptcha] awaits the token. Disabled when the
     * flavor ships no site key (dev by default).
     */
    val captcha = CaptchaGate(BuildConfig.TURNSTILE_SITE_KEY)

    init {
        // When sign-in completes through ANY path (email, OTP verify, Google), clear the form so a
        // later reopen — e.g. after signing out in the same session — starts fresh instead of
        // resurrecting the pending OTP step. The modal only opens while logged out, so this never
        // wipes a code the user is mid-entry.
        // Exception: the forgot-password flow — verifying its code signs the user in, and the
        // "choose a new password" step still has to run (see [holdingForReset]).
        viewModelScope.launch {
            authRepository.authState.collect { if (it is AuthState.SignedIn && !holdingForReset) reset() }
        }
    }

    /**
     * True from just before the recovery code is verified until the reset finishes/fails. It keeps the
     * sign-in observer above from wiping the form, and (via [SignInController.holdOpen]) keeps the app
     * shell from closing the overlay the moment the verification signs the user in.
     */
    @Volatile
    private var holdingForReset = false

    private fun setResetHold(hold: Boolean) {
        holdingForReset = hold
        SignInController.holdOpen(hold)
    }

    /** Restores the pristine form (clears any pending OTP / reset step and typed fields). Also releases
     *  the reset hold, so call it BEFORE dismissing the overlay. */
    fun reset() {
        setResetHold(false)
        _uiState.value = LoginUiState()
    }

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
                SignInResult.NetworkError -> UiText.Res(R.string.login_err_network)
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
            // Bot check first (invisible for normal users); a failed/dismissed check never reaches Auth.
            val token = acquireCaptcha() ?: return@launch
            val result = if (s.mode == LoginMode.SIGN_IN) {
                authRepository.signInWithEmail(s.email, s.password, captchaToken = token.value)
            } else {
                authRepository.signUpWithEmail(s.email, s.password, languageTag, captchaToken = token.value)
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
                    SignInResult.CaptchaFailed -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_captcha))
                    // The server rule is stricter than MIN_PASSWORD_LENGTH (dashboard changed) — say so
                    // rather than "something went wrong".
                    SignInResult.WeakPassword -> it.copy(
                        signingIn = false,
                        error = UiText.Res(R.string.login_err_password_short, listOf(MIN_PASSWORD_LENGTH)),
                    )
                    SignInResult.EmailAlreadyRegistered -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_email_exists))
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    SignInResult.NetworkError -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_network))
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
            val token = acquireCaptcha() ?: return@launch
            val result = authRepository.resendSignUpCode(s.pendingEmail, captchaToken = token.value)
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(
                        signingIn = false, info = UiText.Res(R.string.login_code_resent),
                        resendCooldownUntil = System.currentTimeMillis() + RESEND_COOLDOWN_MS,
                        // The new email supersedes the old code — empty the boxes so the stale digits
                        // aren't submitted by mistake. Only on success: after a failed resend (rate
                        // limit, captcha) the previous code is still the valid one, so keep it.
                        code = "",
                    )
                    SignInResult.CaptchaFailed -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_captcha))
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    is SignInResult.Error -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_generic))
                    else -> it.copy(signingIn = false)
                }
            }
        }
    }

    // ---- Forgot password: email → 6-digit recovery code → new password ----

    /** "Forgot password?" on the sign-in form. Carries the typed email over; clears everything else. */
    fun onForgotPassword() = _uiState.update {
        LoginUiState(mode = LoginMode.SIGN_IN, reset = ResetStep.EMAIL, email = it.email)
    }

    /** Step 1 (and Resend on step 2): request the recovery email. Captcha first — `/recover` is protected. */
    fun onSendResetCode() {
        val s = _uiState.value
        if (s.signingIn) return
        val resend = s.reset == ResetStep.CODE
        if (resend && System.currentTimeMillis() < s.resendCooldownUntil) return
        val email = (if (resend) s.pendingEmail else s.email).trim()
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = UiText.Res(R.string.login_err_invalid_email)) }; return
        }
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val token = acquireCaptcha() ?: return@launch
            val result = authRepository.sendPasswordResetCode(email, captchaToken = token.value)
            _uiState.update {
                when (result) {
                    // "Accepted", not "account exists" — the server never reveals which (no enumeration),
                    // so the code step opens either way and the wording stays conditional.
                    SignInResult.Success -> it.copy(
                        signingIn = false, reset = ResetStep.CODE, pendingEmail = email, code = "",
                        info = if (resend) UiText.Res(R.string.login_code_resent) else null,
                        resendCooldownUntil = System.currentTimeMillis() + RESEND_COOLDOWN_MS,
                    )
                    SignInResult.CaptchaFailed -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_captcha))
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    else -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_generic))
                }
            }
        }
    }

    /** Step 2: verify the recovery code. Success signs the user in — hold the overlay open for step 3. */
    fun onVerifyResetCode() {
        val s = _uiState.value
        if (s.signingIn) return
        if (s.code.length < CODE_LENGTH) {
            _uiState.update { it.copy(error = UiText.Res(R.string.login_err_code_invalid)) }; return
        }
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        setResetHold(true) // BEFORE the call: the session flips to signed-in as soon as it returns
        viewModelScope.launch {
            val result = authRepository.verifyPasswordResetCode(s.pendingEmail, s.code)
            if (result != SignInResult.Success) setResetHold(false)
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(
                        signingIn = false, reset = ResetStep.NEW_PASSWORD,
                        code = "", password = "", confirmPassword = "",
                    )
                    SignInResult.TooManyRequests -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                    else -> it.copy(signingIn = false, error = UiText.Res(R.string.login_err_code_invalid))
                }
            }
        }
    }

    /**
     * Step 3: set the new password on the (fresh, just-verified) session, then close the overlay. If the
     * user closes the modal here instead, they stay signed in with the old password — harmless; they
     * proved the email is theirs and can run the flow again.
     */
    fun onSubmitNewPassword() {
        val s = _uiState.value
        if (s.signingIn) return
        val err = when {
            s.password.length < MIN_PASSWORD_LENGTH ->
                UiText.Res(R.string.login_err_password_short, listOf(MIN_PASSWORD_LENGTH))
            s.password != s.confirmPassword -> UiText.Res(R.string.login_err_password_mismatch)
            else -> null
        }
        if (err != null) { _uiState.update { it.copy(error = err) }; return }
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            when (authRepository.setPassword(s.password)) {
                // PasswordAlreadySet = they chose the password they already had — it IS set, so: done.
                SignInResult.Success, SignInResult.PasswordAlreadySet -> {
                    _uiState.update { it.copy(signingIn = false, info = UiText.Res(R.string.login_reset_done)) }
                    delay(RESET_DONE_LINGER_MS) // let "Password updated" be read before the modal closes
                    reset()                     // releases the hold…
                    SignInController.dismiss()  // …so this actually closes the overlay
                }
                SignInResult.WeakPassword -> _uiState.update {
                    it.copy(signingIn = false, error = UiText.Res(R.string.login_err_password_short, listOf(MIN_PASSWORD_LENGTH)))
                }
                SignInResult.TooManyRequests -> _uiState.update {
                    it.copy(signingIn = false, error = UiText.Res(R.string.login_err_too_many))
                }
                // Still on the fresh session, so they can simply tap Save again.
                else -> _uiState.update { it.copy(signingIn = false, error = UiText.Res(R.string.login_err_generic)) }
            }
        }
    }

    /** Back within the reset flow: code → email → the sign-in form. (No Back from the new-password step.) */
    fun onBackFromReset() = _uiState.update {
        when (it.reset) {
            ResetStep.CODE -> it.copy(reset = ResetStep.EMAIL, code = "", error = null, info = null)
            else -> LoginUiState(mode = LoginMode.SIGN_IN, email = it.email)
        }
    }

    /**
     * Runs the Turnstile gate and returns the token holder to send — `value` is null when the gate is
     * disabled (no site key → no token, the server isn't enforcing). Returns null after showing the
     * captcha error (and clearing the spinner) when the check failed or the user dismissed it, so
     * callers just `?: return`.
     */
    private suspend fun acquireCaptcha(): CaptchaToken? = when (val outcome = captcha.acquire()) {
        CaptchaOutcome.Disabled -> CaptchaToken(null)
        is CaptchaOutcome.Token -> CaptchaToken(outcome.value)
        CaptchaOutcome.Failed -> {
            _uiState.update { it.copy(signingIn = false, error = UiText.Res(R.string.login_err_captcha)) }
            null
        }
    }

    /** Wrapper so "gate disabled" (send nothing) and "gate failed" (abort) stay distinct at call sites. */
    @JvmInline
    private value class CaptchaToken(val value: String?)

    /** Leaves the code step, returning to the email/password form. */
    fun onBackFromCode() = _uiState.update {
        it.copy(awaitingCode = false, code = "", error = null, info = null)
    }

    /** Returns an error message if the form is invalid, else null. */
    private fun validate(s: LoginUiState): UiText? = when {
        s.email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(s.email.trim()).matches() ->
            UiText.Res(R.string.login_err_invalid_email)
        // Length is a rule for NEW passwords only. On sign-in just require something — an account made
        // under an older, shorter rule must still get in, and the server is the judge of correctness.
        s.mode == LoginMode.SIGN_IN && s.password.isEmpty() -> UiText.Res(R.string.login_err_password_required)
        s.mode == LoginMode.SIGN_UP && s.password.length < MIN_PASSWORD_LENGTH ->
            UiText.Res(R.string.login_err_password_short, listOf(MIN_PASSWORD_LENGTH))
        s.mode == LoginMode.SIGN_UP && s.password != s.confirmPassword -> UiText.Res(R.string.login_err_password_mismatch)
        else -> null
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val RESEND_COOLDOWN_MS = 60_000L
        const val RESET_DONE_LINGER_MS = 1_200L
    }
}
