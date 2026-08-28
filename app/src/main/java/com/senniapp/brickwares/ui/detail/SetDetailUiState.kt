package com.senniapp.brickwares.ui.detail

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
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
    /** The owned collection item (with copies), when [isOwned] — drives the See-Details dialog. */
    val ownedItem: CollectionItem? = null,
    /** Whether the See-Details (copies) dialog is open. */
    val showCopies: Boolean = false,
    /** When non-null, the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    val isWishlisted: Boolean = false,
    val related: List<CatalogSet> = emptyList(),
    /** When non-null, the shared Add-to-Collection sheet is open for this set. */
    val addTarget: CatalogSet? = null,
    /** The catalog isn't available (offline / not yet loaded) so the detail can't be shown. */
    val offline: Boolean = false,
    val toastMessage: UiText? = null,
)
