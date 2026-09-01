package com.senniapp.brickwares.ui.detail

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.ui.components.UiText

/**
 * Immutable UI state for the Set Detail page. [set] is the catalog record; the ownership fields
 * ([isOwned]/[ownedCount]/[totalPaid]) and [isWishlisted] are derived live from the collection
 * and wishlist so the page reflects adds made from here or elsewhere.
 */
data class SetDetailUiState(
    val loaded: Boolean = false,
    val set: CatalogSet? = null,
    val isOwned: Boolean = false,
    val ownedCount: Int = 0,
    val totalPaid: Long = 0L,
    /** The hero set's owned collection item (with copies), when [isOwned]. */
    val ownedItem: CollectionItem? = null,
    /**
     * The set whose See-Details (copies) dialog is open — the hero set OR a recommended owned set —
     * with [copiesItem] the live collection item resolved from it. Null = the dialog is closed.
     */
    val copiesSetNumber: String? = null,
    val copiesItem: CollectionItem? = null,
    /** When non-null, the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    val isWishlisted: Boolean = false,
    /** Recommended sets (a stable snapshot per page open; see [SetDetailViewModel.rebuild]). */
    val related: List<CatalogSet> = emptyList(),
    /** Live owned / wishlisted set numbers, so recommendation cards flip their action buttons. */
    val ownedNumbers: Set<String> = emptySet(),
    val wishlistedNumbers: Set<String> = emptySet(),
    /** When non-null, the shared Add-to-Collection sheet is open for this set. */
    val addTarget: CatalogSet? = null,
    /** When non-null, the Sell dialog is open for this owned copy. */
    val sellCopy: Copy? = null,
    /** The catalog isn't available (offline / not yet loaded) so the detail can't be shown. */
    val offline: Boolean = false,
    /** Community "current value" for the hero set (Decision 17); NONE until [valueLoading] finishes. */
    val currentValue: CurrentValue = CurrentValue.NONE,
    val valueLoading: Boolean = true,
    val toastMessage: UiText? = null,
)
