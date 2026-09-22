package com.senniapp.brickwares.ui.detail

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.UiText

/**
 * Immutable UI state for the Set Detail page. [set] is the catalog record; the ownership fields
 * ([isOwned]/[ownedCount]/[ownedItem]) and [isWishlisted] are derived live from the collection
 * and wishlist so the page reflects adds made from here or elsewhere.
 */
data class SetDetailUiState(
    val loaded: Boolean = false,
    val set: CatalogSet? = null,
    val isOwned: Boolean = false,
    val ownedCount: Int = 0,
    /** The hero set's owned collection item (with copies), when [isOwned]. Its `totalPaidIn(display)`
     *  gives the "Total paid" shown, exact in a single currency. */
    val ownedItem: CollectionItem? = null,
    /**
     * The [com.senniapp.brickwares.data.model.CollectionItem.variantKey] of the set whose See-Details
     * (copies) dialog is open — the hero set OR a recommended owned set — with [copiesItem] the live
     * collection item resolved from it. Keyed on the exact variant (not a bare number) so a shared-number
     * CMF/SDCC dialog shows only that variant's copies/sales. Null = the dialog is closed.
     */
    val copiesVariantKey: String? = null,
    val copiesItem: CollectionItem? = null,
    /** When non-null, the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    val isWishlisted: Boolean = false,
    /** The hero set has one or more sale records — the hero shows "See Detail" when owned OR sold. */
    val isSold: Boolean = false,
    /** Sale records for the set whose copies modal is open (hero or a recommended set). */
    val copiesSales: List<SoldItem> = emptyList(),
    /** The minifigs this set contains (from the `set_minifigs` inventory), for the minifig grid. */
    val minifigs: List<Minifig> = emptyList(),
    /** Recommended sets (a stable snapshot per page open; see [SetDetailViewModel.rebuild]). */
    val related: List<CatalogSet> = emptyList(),
    /** Live owned / wishlisted set numbers, so recommendation cards flip their action buttons. */
    val ownedNumbers: Set<String> = emptySet(),
    val wishlistedNumbers: Set<String> = emptySet(),
    /** When non-null, the shared Add-to-Collection sheet is open for this set. */
    val addTarget: CatalogSet? = null,
    /** Open the Add sheet in Sales mode (the copies modal's Sales-tab add button). */
    val addSalesMode: Boolean = false,
    /** When non-null, the Sell dialog is open for this owned copy. */
    val sellCopy: Copy? = null,
    /** The catalog isn't available (offline / not yet loaded) so the detail can't be shown. */
    val offline: Boolean = false,
    /** Community "current value" for the hero set (Decision 17); NONE until [valueLoading] finishes. */
    val currentValue: CurrentValue = CurrentValue.NONE,
    val valueLoading: Boolean = true,
    val toastMessage: UiText? = null,
)
