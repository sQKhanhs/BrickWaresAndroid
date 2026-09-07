package com.senniapp.brickwares.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.collectionSummaryOf
import com.senniapp.brickwares.data.repository.themeSummariesOf
import com.senniapp.brickwares.data.local.CurrencyPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

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
                _uiState.update {
                    it.copy(
                        isLoggedIn = authState is AuthState.SignedIn,
                        // Resolved once it's no longer the initial Loading state (SignedIn or SignedOut).
                        authReady = authState !is AuthState.Loading,
                    )
                }
            }
            .launchIn(viewModelScope)

        // Recompute on either a data change or a display-currency switch, so the hero's value / paid /
        // growth are summed directly in the shown currency (exact for a single-currency collection) and
        // re-derive when the user switches ₫⇄$. The currency the amounts are in is carried in the state
        // so the view formats with it (never a stale symbol on a fresh amount).
        combine(repository.getCollectionItems(), CurrencyPrefs.currency) { items, currency ->
            items to currency
        }
            .onEach { (items, currency) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currency = currency,
                        summary = collectionSummaryOf(items, currency),
                        themes = themeSummariesOf(items, currency),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onShareClick() {
        // TODO: open the share sheet once implemented.
    }
}
