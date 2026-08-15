package com.senniapp.brickwares.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.MockCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Collection tab. Loads the summary once and observes the item
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
                _uiState.update { it.copy(isLoading = false, items = items) }
            }
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

    fun onAddClick() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun onDismissAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    /** Catalog search backing the Add sheet's set-number field. */
    fun searchCatalog(query: String): List<CatalogSet> = repository.searchCatalog(query)

    /** Adds the item and closes the sheet; it appears live in the list via the items Flow. */
    fun addToCollection(item: CollectionItem) {
        repository.addItem(item)
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun onItemDetail(item: CollectionItem) {
        // TODO: open the See Details modal for this set.
    }
}
