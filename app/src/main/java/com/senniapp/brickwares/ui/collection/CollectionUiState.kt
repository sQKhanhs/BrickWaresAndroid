package com.senniapp.brickwares.ui.collection

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem

/** Collection tab has two modes: the collection view and its Sales sub-view. */
enum class CollectionMode { COLLECTION, SALES }

/** Item-type filter chips shown above the list. */
enum class CollectionFilter { ALL, SET, MINIFIG }

/**
 * Immutable UI state for the Collection tab. [visibleItems] applies the active [filter] and
 * [detailItem] resolves the open See Details set from the live list (so edits/deletes reflect).
 */
data class CollectionUiState(
    val mode: CollectionMode = CollectionMode.COLLECTION,
    val filter: CollectionFilter = CollectionFilter.ALL,
    val summary: CollectionSummary? = null,
    /** Whether the item Flow has emitted at least once (empty list is a valid loaded state). */
    val itemsLoaded: Boolean = false,
    val items: List<CollectionItem> = emptyList(),
    val soldItems: List<SoldItem> = emptyList(),
    val salesSummary: SalesSummary? = null,
    val showAddSheet: Boolean = false,
    val addSheetPreselect: CatalogSet? = null,
    /** When non-null the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    val editingSetNumber: String? = null,
    val detailSetNumber: String? = null,
    /** When non-null, the swipe-to-delete confirmation dialog is open for this set. */
    val pendingDeleteSetNumber: String? = null,
    /** Transient toast message (e.g. after a delete); cleared once shown. */
    val toastMessage: String? = null,
) {
    /** The first Collection frame needs the summary *and* the items, so gate on both. */
    val isLoading: Boolean get() = summary == null || !itemsLoaded

    /** The item awaiting delete confirmation, resolved from the live list. */
    val pendingDeleteItem: CollectionItem?
        get() = pendingDeleteSetNumber?.let { sn -> items.find { it.setNumber == sn } }

    val visibleItems: List<CollectionItem>
        get() = when (filter) {
            CollectionFilter.ALL -> items
            CollectionFilter.SET -> items.filter { it.itemType == ItemType.SET }
            CollectionFilter.MINIFIG -> items.filter { it.itemType == ItemType.MINIFIG }
        }

    /** The set whose See Details modal is open, resolved from the live list (null closes it). */
    val detailItem: CollectionItem?
        get() = detailSetNumber?.let { sn -> items.find { it.setNumber == sn } }
}
