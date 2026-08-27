package com.senniapp.brickwares.ui.login

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.repository.AuthRepository
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
    /** Non-error status, e.g. "check your email to confirm" after sign-up on a confirm-required project. */
    val info: UiText? = null,
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

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, error = null) }
    fun onConfirmPasswordChange(value: String) = _uiState.update { it.copy(confirmPassword = value, error = null) }

    fun onSwitchMode() = _uiState.update {
        it.copy(
            mode = if (it.mode == LoginMode.SIGN_IN) LoginMode.SIGN_UP else LoginMode.SIGN_IN,
            error = null, info = null, confirmPassword = "",
        )
    }

    fun onGoogleSignIn(context: Context) {
        if (_uiState.value.signingIn) return
        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val message: UiText? = when (val result = authRepository.signInWithGoogle(context)) {
                SignInResult.Success, SignInResult.Cancelled -> null
                SignInResult.NoCredential -> UiText.Res(R.string.login_err_no_google)
                is SignInResult.Error -> UiText.Raw(result.message)
                SignInResult.EmailConfirmationRequired -> null
            }
            _uiState.update { it.copy(signingIn = false, error = message) }
        }
    }

    fun onEmailSubmit() {
        val s = _uiState.value
        if (s.signingIn) return
        validate(s)?.let { err -> _uiState.update { it.copy(error = err) }; return }

        _uiState.update { it.copy(signingIn = true, error = null, info = null) }
        viewModelScope.launch {
            val result = if (s.mode == LoginMode.SIGN_IN) {
                authRepository.signInWithEmail(s.email, s.password)
            } else {
                authRepository.signUpWithEmail(s.email, s.password)
            }
            _uiState.update {
                when (result) {
                    SignInResult.Success -> it.copy(signingIn = false) // AuthGate routes to the app
                    SignInResult.EmailConfirmationRequired -> it.copy(
                        signingIn = false, mode = LoginMode.SIGN_IN, password = "", confirmPassword = "",
                        info = UiText.Res(R.string.login_info_confirm_email),
                    )
                    is SignInResult.Error -> it.copy(signingIn = false, error = UiText.Raw(result.message))
                    else -> it.copy(signingIn = false)
                }
            }
        }
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
    }
}
