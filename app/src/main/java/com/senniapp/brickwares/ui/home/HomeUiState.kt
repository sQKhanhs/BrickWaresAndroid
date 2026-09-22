package com.senniapp.brickwares.ui.home

import com.senniapp.brickwares.data.model.CatalogSet
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
    /**
     * Preview of the newest catalog sets (pending release + released this/last month), shown in the
     * Home "New LEGO Sets" card. Capped to the preview count; the full grouped list lives on the
     * dedicated New Sets page. Empty until the catalog has loaded.
     */
    val newSets: List<CatalogSet> = emptyList(),
    /**
     * Whether the catalog has finished its first load attempt (success — even empty — or failure), so
     * the "New LEGO Sets" section is ready to draw. Gating [isReady] on this holds the whole page until
     * the (network) catalog resolves, so the new-sets card appears with everything else instead of
     * popping in 2-3s after the hero/stats. A failed load still releases it (the card is just absent).
     */
    val catalogReady: Boolean = false,
) {
    /**
     * The page is shown only once the collection summary has loaded, auth has resolved, AND the
     * catalog has finished loading — so the whole Home (hero, stats, themes, and the new-sets section)
     * appears at once on cold start, instead of the sign-in prompt flashing in first and the
     * hero/stats/themes then the new-sets card each popping in a beat later.
     */
    val isReady: Boolean
        get() = !isLoading && authReady && catalogReady

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
    /**
     * The owning item's [com.senniapp.brickwares.data.model.CollectionItem.variantKey] — unique per
     * owned variant. Used as the picker/slot identity so a collection holding several shared-number
     * CMF/SDCC variants (all one set_number) doesn't collide on duplicate LazyColumn keys (crash) or
     * grey out / mis-render a sibling variant.
     */
    val variantKey: String,
)
