package com.senniapp.brickwares.ui.detail

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.UiText

/**
 * Immutable UI state for the Minifig Detail page. [fig] is the catalog record; ownership/wishlist are
 * derived live from the user's data (minifigs are keyed by `fig_num`, stored with `setNumber = figNum`).
 * [appearsIn] is the list of catalog sets that contain this minifig.
 */
data class MinifigDetailUiState(
    val loaded: Boolean = false,
    val fig: Minifig? = null,
    val isOwned: Boolean = false,
    val ownedCount: Int = 0,
    /** The owned collection item (with copies) for the See Details dialog, when [isOwned]. */
    val ownedItem: CollectionItem? = null,
    val isWishlisted: Boolean = false,
    /** This fig has one or more sale records — the hero shows "See Detail" when owned OR sold. */
    val isSold: Boolean = false,
    /** Sale records for this minifig (shown under the See Details modal's Sales tab). */
    val sales: List<SoldItem> = emptyList(),
    /** Community current value for this minifig (Decision 17); NONE until [valueLoading] finishes. */
    val currentValue: CurrentValue = CurrentValue.NONE,
    val valueLoading: Boolean = true,
    /** Catalog sets this minifig appears in (tap to open that set's detail). */
    val appearsIn: List<CatalogSet> = emptyList(),
    /**
     * Two-state availability shown on the detail page: false = Retail (still obtainable in some set),
     * true = Retired (all its sets are retired/promo/magazine). Null = unknown (no sets resolved yet).
     */
    val retired: Boolean? = null,
    /** Live owned / wishlisted set numbers so the "appears in" cards flip their action buttons. */
    val ownedNumbers: Set<String> = emptySet(),
    val wishlistedNumbers: Set<String> = emptySet(),
    /** When true, the owned-copies See Details dialog is open. */
    val showCopies: Boolean = false,
    /** When non-null, the shared Add sheet is open for this target (the minifig, or an "appears in" set). */
    val addTarget: CatalogSet? = null,
    /** Open the Add sheet in Sales mode (the See Details modal's Sales-tab add button). */
    val addSalesMode: Boolean = false,
    /** When non-null, the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    /** The catalog isn't available (offline / not loaded) so the detail can't be shown. */
    val offline: Boolean = false,
    val toastMessage: UiText? = null,
)
