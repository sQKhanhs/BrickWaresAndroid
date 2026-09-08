package com.senniapp.brickwares.ui.collection

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.ui.components.ItemDetailsTab
import com.senniapp.brickwares.ui.components.ItemSort
import com.senniapp.brickwares.ui.components.PAGE_SIZE
import com.senniapp.brickwares.util.CurrencyConverter

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
    /** Open the Add sheet in Sales mode (Collection FAB in Sales mode, or the modal's Sales-tab add). */
    val addSheetSalesMode: Boolean = false,
    /** When non-null the Add sheet is in edit mode for this copy. */
    val editingCopy: Copy? = null,
    val editingSetNumber: String? = null,
    /** When non-null the Add sheet is editing this sale (Sales fields, prefilled [editingSalePrice]). */
    val editingSaleId: String? = null,
    val editingSalePrice: Long? = null,
    /** When non-null, the merged See Details modal is open for this set/fig (collection + sales). */
    val detailSetNumber: String? = null,
    /** Which tab the merged modal opens on (a collection card → Collection, a sold card → Sales). */
    val detailInitialTab: ItemDetailsTab = ItemDetailsTab.COLLECTION,
    /** When both are non-null, the Sell dialog is open for this owned copy. */
    val sellSetNumber: String? = null,
    val sellCopyId: String? = null,
    /** 1-based current page for the collection list's numbered pagination. */
    val page: Int = 1,
    /** 1-based current page for the Sales (sold items) list — independent of [page]. */
    val salesPage: Int = 1,
    /** Sort order for the collection list. */
    val sort: ItemSort = ItemSort.DATE_ADDED,
    /** Sort order for the Sales (sold items) list — independent of [sort]. */
    val salesSort: ItemSort = ItemSort.DATE_ADDED,
    /** When non-null, the swipe-to-delete confirmation dialog is open for this set. */
    val pendingDeleteSetNumber: String? = null,
    /** When non-null, the swipe-to-delete confirmation dialog is open for this sale record. */
    val pendingDeleteSaleId: String? = null,
    /** Transient toast message (e.g. after a delete); cleared once shown. */
    val toastMessage: UiText? = null,
) {
    /** The first Collection frame needs the summary *and* the items, so gate on both. */
    val isLoading: Boolean get() = summary == null || !itemsLoaded

    /** The item awaiting delete confirmation, resolved from the live list. */
    val pendingDeleteItem: CollectionItem?
        get() = pendingDeleteSetNumber?.let { sn -> items.find { it.setNumber == sn } }

    /** The sale record awaiting delete confirmation, resolved from the live sales list. */
    val pendingDeleteSale: SoldItem?
        get() = pendingDeleteSaleId?.let { id -> soldItems.find { it.id == id } }

    val visibleItems: List<CollectionItem>
        get() = when (filter) {
            CollectionFilter.ALL -> items
            CollectionFilter.SET -> items.filter { it.itemType == ItemType.SET }
            CollectionFilter.MINIFIG -> items.filter { it.itemType == ItemType.MINIFIG }
        }

    /** Total pages (>=1) and the clamped current page for [pageItems] (collection mode). */
    val pageCount: Int get() = ((visibleItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val currentPage: Int get() = page.coerceIn(1, pageCount)

    /** The current page's slice of the sorted [visibleItems] (what the collection list renders). */
    val pageItems: List<CollectionItem>
        get() = visibleItems.applyItemSort(sort).drop((currentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** Numbered pagination for the Sales (sold items) list. */
    val salesPageCount: Int get() = ((soldItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val salesCurrentPage: Int get() = salesPage.coerceIn(1, salesPageCount)
    val salesPageItems: List<SoldItem>
        get() = soldItems.applySalesSort(salesSort).drop((salesCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** The set whose See Details modal is open, resolved from the live list (null closes it). */
    val detailItem: CollectionItem?
        get() = detailSetNumber?.let { sn -> items.find { it.setNumber == sn } }

    /** The open set/fig's sale records (all of them), resolved from the live sales list. */
    val detailSales: List<SoldItem>
        get() = detailSetNumber?.let { sn -> soldItems.filter { it.setNumber == sn } } ?: emptyList()

    /**
     * The (item, copy) the Sell dialog targets, resolved from the live list — so the dialog closes
     * on its own once the copy is sold out and removed.
     */
    val sellTarget: Pair<CollectionItem, Copy>?
        get() {
            val item = sellSetNumber?.let { sn -> items.find { it.setNumber == sn } } ?: return null
            val copy = sellCopyId?.let { cid -> item.copies.find { it.id == cid } } ?: return null
            return item to copy
        }
}

/** Release ordering key: year*100 + month (month 0 = unknown, sorts before real months in a year). */
private fun releaseKey(year: Int, month: Int): Int = year * 100 + month

/** Apply an [ItemSort] to owned items: price = total paid (USD cents); date = the latest copy's acquired date. */
private fun List<CollectionItem>.applyItemSort(sort: ItemSort): List<CollectionItem> = when (sort) {
    ItemSort.NAME -> sortedBy { it.name.lowercase() }
    ItemSort.PRICE_HIGH -> sortedByDescending { it.totalPaid }
    ItemSort.PRICE_LOW -> sortedBy { it.totalPaid }
    ItemSort.DATE_ADDED -> sortedByDescending { it.copies.mapNotNull { c -> c.dateAdded.ifBlank { null } }.maxOrNull() ?: "" }
    ItemSort.RELEASE_NEWEST -> sortedByDescending { releaseKey(it.releaseYear, it.releaseMonth) }
    ItemSort.RELEASE_OLDEST -> sortedBy { releaseKey(it.releaseYear, it.releaseMonth) }
}

/** Apply an [ItemSort] to sold items: price = sale value normalized to USD cents; date = the sold date. */
private fun List<SoldItem>.applySalesSort(sort: ItemSort): List<SoldItem> = when (sort) {
    ItemSort.NAME -> sortedBy { it.name.lowercase() }
    ItemSort.PRICE_HIGH -> sortedByDescending { CurrencyConverter.usdCentsOf(it.saleValue, it.currency) }
    ItemSort.PRICE_LOW -> sortedBy { CurrencyConverter.usdCentsOf(it.saleValue, it.currency) }
    ItemSort.DATE_ADDED -> sortedByDescending { it.soldOn ?: "" }
    ItemSort.RELEASE_NEWEST -> sortedByDescending { releaseKey(it.releaseYear, it.releaseMonth) }
    ItemSort.RELEASE_OLDEST -> sortedBy { releaseKey(it.releaseYear, it.releaseMonth) }
}
