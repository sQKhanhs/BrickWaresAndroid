package com.senniapp.brickwares.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.SignInResult
import com.senniapp.brickwares.ui.navigation.AuthGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val staySignedIn: Boolean = true,
    val signingIn: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the app-open login page. Persists the "stay signed in" choice (via [AuthGate]) and runs the
 * native Google sign-in; on success [AuthGate] routes to the main app, so there's nothing to do here.
 */
class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onToggleStaySignedIn() {
        _uiState.update { it.copy(staySignedIn = !it.staySignedIn) }
    }

    fun onSignIn(context: Context) {
        if (_uiState.value.signingIn) return
        _uiState.update { it.copy(signingIn = true, error = null) }
        AuthGate.setStaySignedIn(_uiState.value.staySignedIn)
        viewModelScope.launch {
            val message = when (val result = authRepository.signInWithGoogle(context)) {
                SignInResult.Success, SignInResult.Cancelled -> null
                SignInResult.NoCredential -> "No Google account available on this device"
                is SignInResult.Error -> result.message
            }
            _uiState.update { it.copy(signingIn = false, error = message) }
        }
    }
}
