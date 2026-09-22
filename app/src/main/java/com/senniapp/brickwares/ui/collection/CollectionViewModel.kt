package com.senniapp.brickwares.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.ItemDetailsTab
import com.senniapp.brickwares.ui.components.ItemSort
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.collectionSummaryOf
import com.senniapp.brickwares.data.repository.salesSummaryOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Collection tab. Loads the summary + sales data once and observes the item
 * list as a Flow (offline-first shape — Room will later back this same Flow).
 */
class CollectionViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        // Summary is derived from the live items so the stat row reads 0 when empty and updates as
        // copies are added/removed (no longer a static snapshot).
        viewModelScope.launch {
            repository.getCollectionItems().collect { items ->
                _uiState.update {
                    // Only the counts are read from this summary (the stat row); its money is in the
                    // current display currency for consistency, though the Collection tab doesn't show it.
                    it.copy(itemsLoaded = true, items = items, summary = collectionSummaryOf(items, CurrencyPrefs.current))
                }
            }
        }
        // Sales data is observed as a Flow so newly recorded sales appear live (and the summary
        // tiles recompute), mirroring the collection list.
        viewModelScope.launch {
            repository.getSoldItems().collect { sold ->
                // Money is summed in the current display currency; the UI recomputes it reactively too
                // (SalesStatsRow/ProfitBar) so a currency switch updates the tiles without a data change.
                _uiState.update { it.copy(soldItems = sold, salesSummary = salesSummaryOf(sold, CurrencyPrefs.current)) }
            }
        }
    }

    fun onFilterSelected(filter: CollectionFilter) {
        _uiState.update { it.copy(filter = filter, page = 1) }
    }

    fun onPageChange(page: Int) {
        _uiState.update { it.copy(page = page) }
    }

    fun onSalesPageChange(page: Int) {
        _uiState.update { it.copy(salesPage = page) }
    }

    fun onSortChange(sort: ItemSort) {
        _uiState.update { it.copy(sort = sort, page = 1) }
    }

    fun onSalesSortChange(sort: ItemSort) {
        _uiState.update { it.copy(salesSort = sort, salesPage = 1) }
    }

    fun onToggleMode() {
        _uiState.update {
            val next = if (it.mode == CollectionMode.COLLECTION) CollectionMode.SALES else CollectionMode.COLLECTION
            it.copy(mode = next)
        }
    }

    // ---- Add sheet ----

    fun onAddClick() {
        _uiState.update { it.copy(showAddSheet = true, addSheetPreselect = null, addSheetSalesMode = it.mode == CollectionMode.SALES) }
    }

    fun onDismissAddSheet() {
        _uiState.update {
            it.copy(
                showAddSheet = false, addSheetPreselect = null, addSheetSalesMode = false, editingCopy = null,
                editingSetNumber = null, editingSaleId = null, editingSalePrice = null,
            )
        }
    }

    /** The catalog record for the open detail set/fig — built from the user's own (already-overlaid) row. */
    private fun detailCatalog(): CatalogSet? {
        val key = _uiState.value.detailSetNumber ?: return null // holds the item's variantKey
        _uiState.value.items.find { it.variantKey == key }?.let { return catalogFrom(it) }
        _uiState.value.soldItems.find { it.variantKey == key }?.let { return catalogFrom(it) }
        return null
    }

    /** From the merged modal's Collection tab (add-another-copy or empty-state): open the Add sheet. */
    fun onDetailAddCollection() {
        val cat = detailCatalog() ?: return
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = cat, addSheetSalesMode = false,
                editingCopy = null, editingSetNumber = null, editingSaleId = null, editingSalePrice = null,
            )
        }
    }

    /** From the merged modal's Sales tab (add-another-sale or empty-state): open the Add sheet in Sales mode. */
    fun onDetailAddSale() {
        val cat = detailCatalog() ?: return
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = cat, addSheetSalesMode = true,
                editingCopy = null, editingSetNumber = null, editingSaleId = null, editingSalePrice = null,
            )
        }
    }

    // Add-sheet suggestions — a DB query now (Decision 16); the sheet debounces it off the composition.
    suspend fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.searchSets(query)

    /**
     * Submits the Add sheet. In edit mode (editingCopy set) it replaces that copy and reopens
     * See Details; otherwise it adds the item to the collection. Changes appear live via the Flow.
     */
    fun submitAddSheet(item: CollectionItem) {
        val state = _uiState.value
        val editingSet = state.editingSetNumber
        if (state.editingCopy != null && editingSet != null) {
            repository.updateCopy(editingSet, item.copies.first())
            _uiState.update {
                it.copy(
                    showAddSheet = false, addSheetPreselect = null,
                    editingCopy = null, editingSetNumber = null, detailSetNumber = editingSet,
                )
            }
        } else {
            repository.addItem(item)
            _uiState.update { it.copy(showAddSheet = false, addSheetPreselect = null) }
        }
    }

    /** Submits the Add sheet in Sales mode: records a standalone sale (does not touch the collection). */
    fun submitAddSheetSale(item: CollectionItem, salePrice: Long) {
        repository.addSale(item, salePrice)
        _uiState.update {
            it.copy(
                showAddSheet = false, addSheetPreselect = null,
                toastMessage = UiText.Res(R.string.toast_added_sales, listOf(item.name)),
            )
        }
    }

    // ---- Sold-item See Details (view / edit / delete a sale) ----

    fun onSaleDetail(sold: SoldItem) {
        _uiState.update { it.copy(detailSetNumber = sold.variantKey, detailInitialTab = ItemDetailsTab.SALES) }
    }

    fun onDeleteSale(saleId: String) {
        val name = _uiState.value.soldItems.find { it.id == saleId }?.name
        repository.removeSale(saleId)
        // Leave the merged modal open — it closes itself once the set has no copies and no sales left.
        _uiState.update {
            it.copy(toastMessage = name?.let { n -> UiText.Res(R.string.toast_removed_sale, listOf(n)) } ?: it.toastMessage)
        }
    }

    // ---- Swipe-to-delete a sale (with confirmation, mirroring the collection list) ----

    fun onRequestDeleteSale(saleId: String) {
        _uiState.update { it.copy(pendingDeleteSaleId = saleId) }
    }

    fun onCancelDeleteSale() {
        _uiState.update { it.copy(pendingDeleteSaleId = null) }
    }

    fun onConfirmDeleteSale() {
        val id = _uiState.value.pendingDeleteSaleId ?: return
        val name = _uiState.value.soldItems.find { it.id == id }?.name
        repository.removeSale(id)
        _uiState.update {
            it.copy(
                pendingDeleteSaleId = null,
                toastMessage = name?.let { n -> UiText.Res(R.string.toast_removed_sale, listOf(n)) } ?: it.toastMessage,
            )
        }
    }

    /** Open the Add sheet in Sale-edit mode, prefilled from this sale. */
    fun onEditSale(sold: SoldItem) {
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = catalogFrom(sold),
                editingCopy = Copy(
                    id = sold.id, condition = sold.condition, qty = sold.quantity,
                    pricePaid = sold.pricePaid, currency = sold.currency,
                    dateAdded = sold.soldOn ?: "", note = sold.note,
                ),
                editingSetNumber = null, editingSaleId = sold.id, editingSalePrice = sold.saleValue,
            )
        }
    }

    /** Saves the Sale-edit sheet, updating the sale row. */
    fun submitEditSale(item: CollectionItem, salePrice: Long) {
        val saleId = _uiState.value.editingSaleId
        val copy = item.copies.firstOrNull()
        if (saleId != null && copy != null) {
            repository.updateSale(saleId, copy.qty, copy.condition, copy.pricePaid, salePrice, copy.currency, copy.dateAdded, copy.note)
        }
        _uiState.update {
            it.copy(
                showAddSheet = false, addSheetPreselect = null, editingCopy = null,
                editingSetNumber = null, editingSaleId = null, editingSalePrice = null,
            )
        }
    }

    // ---- Sell an owned copy → Sales ----

    /** From See Details' per-copy Sell button: open the Sell dialog for this copy. */
    fun onSellCopyRequest(variantKey: String, copy: Copy) {
        _uiState.update { it.copy(detailSetNumber = null, sellSetNumber = variantKey, sellCopyId = copy.id) }
    }

    fun onDismissSell() {
        _uiState.update { it.copy(sellSetNumber = null, sellCopyId = null) }
    }

    fun onConfirmSell(quantity: Int, salePrice: Long, currency: AppCurrency, soldOn: String) {
        val state = _uiState.value
        val sn = state.sellSetNumber
        val cid = state.sellCopyId
        val name = state.sellTarget?.first?.name
        if (sn != null && cid != null) {
            repository.sellCopy(sn, cid, quantity, salePrice, currency, soldOn)
        }
        _uiState.update {
            it.copy(
                sellSetNumber = null, sellCopyId = null,
                toastMessage = name?.let { n -> UiText.Res(R.string.toast_sold, listOf(n)) } ?: it.toastMessage,
            )
        }
    }

    // ---- See Details ----

    fun onItemDetail(item: CollectionItem) {
        _uiState.update { it.copy(detailSetNumber = item.variantKey, detailInitialTab = ItemDetailsTab.COLLECTION) }
    }

    fun onDismissDetail() {
        _uiState.update { it.copy(detailSetNumber = null) }
    }

    fun onDeleteCopy(setNumber: String, copyId: String) {
        repository.removeCopy(setNumber, copyId)
    }

    // ---- Swipe-to-delete (whole item, with confirmation) ----

    fun onRequestDeleteItem(item: CollectionItem) {
        _uiState.update { it.copy(pendingDeleteSetNumber = item.variantKey) }
    }

    fun onCancelDeleteItem() {
        _uiState.update { it.copy(pendingDeleteSetNumber = null) }
    }

    fun onConfirmDeleteItem() {
        val item = _uiState.value.pendingDeleteItem ?: return
        repository.removeItem(item.setNumber, item.setId)
        _uiState.update {
            it.copy(pendingDeleteSetNumber = null, toastMessage = UiText.Res(R.string.toast_removed_collection, listOf(item.name)))
        }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /** From the See Details edit button: open the Add sheet in edit mode for this copy. */
    fun onEditCopy(item: CollectionItem, copy: Copy) {
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = catalogFrom(item),
                addSheetSalesMode = false, editingCopy = copy, editingSetNumber = item.variantKey,
            )
        }
    }

    private fun catalogFrom(item: CollectionItem) = CatalogSet(
        setNumber = item.setNumber, name = item.name, itemType = item.itemType,
        theme = item.theme, releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
        pieces = item.pieces, minifigs = item.minifigs,
        retailPrice = item.retailPrice, status = item.status,
        setId = item.setId, // preserve the exact variant when re-adding from the See-Details modal
    )

    /** The catalog record for a sale's edit sheet, built from the (already catalog-overlaid) sale row. */
    private fun catalogFrom(sold: SoldItem): CatalogSet =
        CatalogSet(
            setNumber = sold.setNumber, name = sold.name, itemType = sold.itemType,
            theme = sold.theme, releaseYear = sold.releaseYear, releaseMonth = sold.releaseMonth,
            pieces = sold.pieces, minifigs = sold.minifigs,
            retailPrice = sold.retailPrice.takeIf { it > 0L }, status = sold.status,
            setId = sold.setId,
        )
}
