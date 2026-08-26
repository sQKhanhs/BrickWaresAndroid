package com.senniapp.brickwares.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.collectionSummaryOf
import com.senniapp.brickwares.data.repository.themeSummariesOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen. Observes the real auth session (for the logged-out "!" prompt) and
 * derives the collection summary/themes from the live item Flow, so the hero + stats reflect the
 * actual (initially empty) collection. The repository is a mock default until Supabase/Room lands.
 */
class HomeViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val authRepository: AuthRepository = AuthRepository,
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
        authRepository.authState
            .onEach { authState ->
                _uiState.update { it.copy(isLoggedIn = authState is AuthState.SignedIn) }
            }
            .launchIn(viewModelScope)

        repository.getCollectionItems()
            .onEach { items ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = collectionSummaryOf(items),
                        themes = themeSummariesOf(items),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onShareClick() {
        // TODO: open the share sheet once implemented.
    }
}
