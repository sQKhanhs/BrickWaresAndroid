package com.senniapp.brickwares.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.MockCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen. Exposes an immutable [HomeUiState] as a [StateFlow]
 * and owns all state transitions. The repository is injected with a mock default so
 * `viewModel()` can construct it with no factory; a real DI-provided repository will
 * replace the default once Supabase is wired up.
 */
class HomeViewModel(
    private val repository: CollectionRepository = MockCollectionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Whether the intro hero GIF has already played this app session. Held here (not in the
     * Flow state) because this ViewModel is Activity-scoped, so the flag survives leaving and
     * re-entering the Home tab — the GIF plays once on app open and never again.
     */
    var hasHeroGifPlayed: Boolean = false
        private set

    fun onHeroGifPlayed() {
        hasHeroGifPlayed = true
    }

    init {
        loadSummary()
    }

    private fun loadSummary() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val summary = repository.getCollectionSummary()
            _uiState.update { it.copy(isLoading = false, summary = summary) }
        }
    }

    fun onShareClick() {
        // TODO: open the share sheet once implemented.
    }

    /** Tapped the red "!" sign-in FAB (logged-out state). */
    fun onSignInPrompt() {
        _uiState.update { it.copy(showSignInDialog = true) }
    }

    fun onDismissSignInDialog() {
        _uiState.update { it.copy(showSignInDialog = false) }
    }

    /** Mock sign-in: flips auth on and closes the dialog. Real Google OAuth comes later. */
    fun onSignIn() {
        _uiState.update { it.copy(isLoggedIn = true, showSignInDialog = false) }
    }
}
