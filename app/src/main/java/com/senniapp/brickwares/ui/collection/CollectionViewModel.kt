package com.senniapp.brickwares.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.MockCollectionRepository
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
    private val repository: CollectionRepository = MockCollectionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState: StateFlow<CollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val summary = repository.getCollectionSummary()
            _uiState.update { it.copy(summary = summary) }
        }
        viewModelScope.launch {
            repository.getCollectionItems().collect { items ->
                _uiState.update { it.copy(itemsLoaded = true, items = items) }
            }
        }
        // Sales data isn't needed for the first frame — load it in the background so it doesn't
        // hold up the initial Collection view.
        viewModelScope.launch {
            val sold = repository.getSoldItems()
            val salesSummary = repository.getSalesSummary()
            _uiState.update { it.copy(soldItems = sold, salesSummary = salesSummary) }
        }
    }

    fun onFilterSelected(filter: CollectionFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun onToggleMode() {
        _uiState.update {
            val next = if (it.mode == CollectionMode.COLLECTION) CollectionMode.SALES else CollectionMode.COLLECTION
            it.copy(mode = next)
        }
    }

    // ---- Add sheet ----

    fun onAddClick() {
        _uiState.update { it.copy(showAddSheet = true, addSheetPreselect = null) }
    }

    fun onDismissAddSheet() {
        _uiState.update {
            it.copy(showAddSheet = false, addSheetPreselect = null, editingCopy = null, editingSetNumber = null)
        }
    }

    fun searchCatalog(query: String): List<CatalogSet> = repository.searchCatalog(query)

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

    // ---- See Details ----

    fun onItemDetail(item: CollectionItem) {
        _uiState.update { it.copy(detailSetNumber = item.setNumber) }
    }

    fun onDismissDetail() {
        _uiState.update { it.copy(detailSetNumber = null) }
    }

    fun onDeleteCopy(setNumber: String, copyId: String) {
        repository.removeCopy(setNumber, copyId)
    }

    // ---- Swipe-to-delete (whole item, with confirmation) ----

    fun onRequestDeleteItem(item: CollectionItem) {
        _uiState.update { it.copy(pendingDeleteSetNumber = item.setNumber) }
    }

    fun onCancelDeleteItem() {
        _uiState.update { it.copy(pendingDeleteSetNumber = null) }
    }

    fun onConfirmDeleteItem() {
        val item = _uiState.value.pendingDeleteItem ?: return
        repository.removeItem(item.setNumber)
        _uiState.update {
            it.copy(pendingDeleteSetNumber = null, toastMessage = "${item.name} removed from Collection")
        }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /** From the See Details "Add Item" button: open the Add sheet pre-filled with this set. */
    fun onAddCopyForSet(item: CollectionItem) {
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = catalogFrom(item),
                editingCopy = null, editingSetNumber = null,
            )
        }
    }

    /** From the See Details edit button: open the Add sheet in edit mode for this copy. */
    fun onEditCopy(item: CollectionItem, copy: Copy) {
        _uiState.update {
            it.copy(
                detailSetNumber = null, showAddSheet = true, addSheetPreselect = catalogFrom(item),
                editingCopy = copy, editingSetNumber = item.setNumber,
            )
        }
    }

    private fun catalogFrom(item: CollectionItem) = CatalogSet(
        setNumber = item.setNumber, name = item.name, itemType = item.itemType,
        theme = item.theme, releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
        pieces = item.pieces, minifigs = item.minifigs,
        retailPrice = item.retailPrice, status = item.status,
    )
}
