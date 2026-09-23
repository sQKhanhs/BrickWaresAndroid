package com.senniapp.brickwares.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.repository.AuthRepository
import com.senniapp.brickwares.data.repository.AuthState
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.collectionSummaryOf
import com.senniapp.brickwares.data.repository.themeSummariesOf
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.util.NewSets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.delay
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
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Whether the intro hero GIF has already had its turn this app session. Held here (not in the
     * Flow state) because this ViewModel is Activity-scoped, so the flag survives leaving and
     * re-entering the Home tab — the GIF plays once on app open and never again. Marked the moment
     * the animation STARTS (not on completion): otherwise leaving the tab mid-play left the flag
     * false, so returning restarted the GIF from frame 0. Once it has started, a return shows the
     * resting last-frame poster instead of replaying.
     */
    var hasHeroGifStarted: Boolean = false
        private set

    fun onHeroGifStarted() {
        hasHeroGifStarted = true
    }

    /** Set once the New Sets preview has loaded, so the reconnect observer only retries a FAILED load. */
    private var newSetsLoaded = false

    init {
        authRepository.authState
            .onEach { authState ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = authState is AuthState.SignedIn,
                        // Resolved once it's no longer the initial Loading state (SignedIn or SignedOut).
                        authReady = authState !is AuthState.Loading,
                        memberName = (authState as? AuthState.SignedIn)?.user?.displayName ?: "",
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
                // All owned items (value desc, in the display currency) — the pool the share card's
                // "Top Sets" slots pick from. Value = per-unit worth × qty.
                val sets = items
                    .map { item ->
                        FeaturedSet(
                            setNumber = item.setNumber,
                            name = item.name,
                            theme = item.theme,
                            value = item.worthPerUnitIn(currency) * item.totalQty,
                            imageUrl = item.imageUrl,
                            variantKey = item.variantKey,
                        )
                    }
                    .sortedByDescending { it.value }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currency = currency,
                        summary = collectionSummaryOf(items, currency),
                        themes = themeSummariesOf(items, currency),
                        collectionSets = sets,
                    )
                }
            }
            .launchIn(viewModelScope)

        // "New LEGO Sets" preview — a server-side query for the recent/upcoming candidates (Decision 16,
        // no full catalog in memory), narrowed by NewSets. catalogReady gates the whole page so the
        // section appears with the rest of Home instead of popping in later; a failure still releases
        // the page (the card is simply absent) — Home is never held on a network error.
        loadNewSets()
        // If the launch load failed (offline at open), reload when connectivity returns — otherwise the
        // card stays gone for the whole session even after the network is back (the Search tab's Retry
        // only refreshes the Search browse, not Home). drop(1) skips the current value; once a load
        // succeeds [newSetsLoaded] stops further reloads so a connectivity blip doesn't reshuffle the card.
        AppGraph.connectivity.isOnline
            .drop(1)
            .filter { it }
            .onEach { if (!newSetsLoaded) loadNewSets() }
            .launchIn(viewModelScope)
    }

    private fun loadNewSets() {
        if (newSetsLoaded) return // already have it — don't reshuffle on a connectivity blip
        viewModelScope.launch {
            // Retry a few times: on a reconnect the isOnline edge can fire a beat before the network is
            // actually routable, so a single attempt could fail with nothing left to re-trigger it. A
            // short bounded retry rides out the settle so the card reliably comes back. catalogReady is
            // released on the first failure so Home is never held on the network.
            repeat(NEW_SETS_LOAD_ATTEMPTS) { attempt ->
                try {
                    // A RANDOM 5 of the eligible new sets (reshuffled each load), not the first 5 — the
                    // full list is still available, sorted, behind "View more new sets".
                    val newSets = NewSets.select(catalogRepo.newSetCandidates()).shuffled().take(NEW_SETS_PREVIEW)
                    newSetsLoaded = true
                    _uiState.update { it.copy(newSets = newSets, catalogReady = true) }
                    return@launch
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _uiState.update { it.copy(catalogReady = true) }
                    if (attempt < NEW_SETS_LOAD_ATTEMPTS - 1) delay(NEW_SETS_RETRY_DELAY_MS)
                }
            }
        }
    }

    fun onShareClick() {
        _uiState.update { it.copy(shareOpen = true) }
    }

    fun onCloseShare() {
        _uiState.update { it.copy(shareOpen = false) }
    }

    private companion object {
        /** How many new sets the Home card previews before "View more new sets". */
        const val NEW_SETS_PREVIEW = 5
        /** Bounded retry for the New Sets query, so a reload right as the network returns rides out the
         *  brief window where connectivity is reported but the socket isn't routable yet. */
        const val NEW_SETS_LOAD_ATTEMPTS = 3
        const val NEW_SETS_RETRY_DELAY_MS = 2_000L
    }
}
