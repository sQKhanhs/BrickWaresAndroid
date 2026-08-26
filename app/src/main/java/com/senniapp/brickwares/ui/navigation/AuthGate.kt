package com.senniapp.brickwares.ui.navigation

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

/**
 * Gates only the initial splash: [State.Loading] while the persisted session restores, then
 * [State.Ready] once auth resolves — after which the app shows regardless of signed-in state
 * (there's no login wall; logged-out users browse, and write features prompt sign-in on demand).
 * Avoids a flash of the logged-out UI on cold start when a session is about to restore.
 */
object AuthGate {

    enum class State { Loading, Ready }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        AuthRepository.authState
            .onEach { if (it != AuthState.Loading) _state.value = State.Ready }
            .launchIn(scope)
    }
}
