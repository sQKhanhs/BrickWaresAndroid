package com.senniapp.brickwares.ui.collection

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.PAGE_SIZE

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
    /** 1-based current page for the collection list's numbered pagination. */
    val page: Int = 1,
    /** 1-based current page for the Sales (sold items) list — independent of [page]. */
    val salesPage: Int = 1,
    /** When non-null, the swipe-to-delete confirmation dialog is open for this set. */
    val pendingDeleteSetNumber: String? = null,
    /** Transient toast message (e.g. after a delete); cleared once shown. */
    val toastMessage: UiText? = null,
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

    /** Total pages (>=1) and the clamped current page for [pageItems] (collection mode). */
    val pageCount: Int get() = ((visibleItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val currentPage: Int get() = page.coerceIn(1, pageCount)

    /** The current page's slice of [visibleItems] (what the collection list renders). */
    val pageItems: List<CollectionItem>
        get() = visibleItems.drop((currentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** Numbered pagination for the Sales (sold items) list. */
    val salesPageCount: Int get() = ((soldItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val salesCurrentPage: Int get() = salesPage.coerceIn(1, salesPageCount)
    val salesPageItems: List<SoldItem>
        get() = soldItems.drop((salesCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** The set whose See Details modal is open, resolved from the live list (null closes it). */
    val detailItem: CollectionItem?
        get() = detailSetNumber?.let { sn -> items.find { it.setNumber == sn } }
}
