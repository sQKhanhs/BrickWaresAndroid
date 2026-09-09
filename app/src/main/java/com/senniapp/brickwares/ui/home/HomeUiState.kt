package com.senniapp.brickwares.ui.home

import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.util.AppCurrency

/**
 * Immutable UI state for the Home screen. The View renders purely from this; all
 * mutations happen in [HomeViewModel].
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    /** Whether the auth session has resolved (SignedIn/SignedOut) — false while still Initializing. */
    val authReady: Boolean = false,
    val isLoggedIn: Boolean = false,
    val currency: AppCurrency = AppCurrency.USD,
    val summary: CollectionSummary? = null,
    val themes: List<ThemeSummary> = emptyList(),
    /** Signed-in user's display name, shown on the share card ("<name> · My Collection"). */
    val memberName: String = "",
    /** The owned items (value desc) — the pool the share card's "Top Sets" slots pick from. */
    val collectionSets: List<FeaturedSet> = emptyList(),
    /** Whether the Share Collection sheet is open. */
    val shareOpen: Boolean = false,
) {
    /**
     * The page is shown only once BOTH the collection summary has loaded AND auth has resolved — so
     * the whole Home appears at once on cold start, instead of the logged-out sign-in prompt flashing
     * in first (summary null + auth not yet resolved) and the hero/stats/themes popping in 1-2s later.
     */
    val isReady: Boolean
        get() = !isLoading && authReady

    /** The header share action only appears when signed in with a non-empty collection. */
    val canShare: Boolean
        get() = isLoggedIn && (summary?.setCount ?: 0) > 0
}

/** A collection item as a "Top Sets" slot / picker entry. [value] is the line's total worth, in [HomeUiState.currency]'s unit. */
data class FeaturedSet(
    val setNumber: String,
    val name: String,
    val theme: String,
    val value: Long,
    val imageUrl: String? = null,
)
