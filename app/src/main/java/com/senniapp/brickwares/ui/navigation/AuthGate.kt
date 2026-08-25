package com.senniapp.brickwares.ui.navigation

import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Decides the app's top-level screen: splash while the session restores, the login page when signed
 * out, or the main tabs when signed in. Enforces the "stay signed in" choice once per cold start —
 * if a session was restored but the user hadn't opted to stay signed in, it's cleared (LOCAL, no
 * network) so the login page shows again.
 */
object AuthGate {

    enum class State { Loading, LoggedOut, LoggedIn }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Whether the one-time stay-signed-in check has run for this process. */
    private var enforced = false

    init {
        AuthRepository.authState.onEach { handle(it) }.launchIn(scope)
    }

    private suspend fun handle(auth: AuthState) {
        when (auth) {
            AuthState.Loading -> _state.value = State.Loading
            AuthState.SignedOut -> { enforced = true; _state.value = State.LoggedOut }
            is AuthState.SignedIn -> {
                if (!enforced) {
                    enforced = true
                    if (!AppGraph.syncState.staySignedIn()) {
                        // Restored a session the user didn't want kept → drop it → login page.
                        AuthRepository.signOut()
                        return
                    }
                }
                _state.value = State.LoggedIn
            }
        }
    }

    /** Persist the login screen's "stay signed in" choice (read on the next cold start). */
    fun setStaySignedIn(value: Boolean) {
        scope.launch { AppGraph.syncState.setStaySignedIn(value) }
    }
}
