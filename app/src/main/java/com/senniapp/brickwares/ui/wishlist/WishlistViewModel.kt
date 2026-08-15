package com.senniapp.brickwares.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.MockCollectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Wishlist tab. Observes the wishlist as a Flow (offline-first shape, same as
 * the Collection tab). "Move to Collection" reuses the shared Add-to-Collection sheet: on submit
 * it adds the item to the collection and removes it from the wishlist.
 */
class WishlistViewModel(
    private val repository: CollectionRepository = MockCollectionRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getWishlistItems().collect { items ->
                _uiState.update { it.copy(isLoading = false, items = items) }
            }
        }
    }

    fun onFilterSelected(filter: WishlistFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun searchCatalog(query: String): List<CatalogSet> = repository.searchCatalog(query)

    fun onRemove(setNumber: String) = repository.removeFromWishlist(setNumber)

    // ---- Add to wishlist ----

    fun onAddClick() {
        _uiState.update { it.copy(showAddSheet = true) }
    }

    fun onDismissAddSheet() {
        _uiState.update { it.copy(showAddSheet = false) }
    }

    fun onAddToWishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs,
                retailPrice = set.retailPrice, status = set.status,
            ),
        )
        _uiState.update { it.copy(showAddSheet = false) }
    }

    // ---- Move to collection ----

    /** Opens the Add-to-Collection sheet prefilled with this wishlisted set. */
    fun onMoveClick(item: WishlistItem) {
        _uiState.update { it.copy(moveTarget = catalogFrom(item)) }
    }

    fun onDismissMove() {
        _uiState.update { it.copy(moveTarget = null) }
    }

    /** Add-sheet submit: add to the collection, then drop it from the wishlist. */
    fun onMoveSubmit(item: CollectionItem) {
        repository.addItem(item)
        repository.removeFromWishlist(item.setNumber)
        _uiState.update { it.copy(moveTarget = null) }
    }

    private fun catalogFrom(item: WishlistItem) = CatalogSet(
        setNumber = item.setNumber, name = item.name, itemType = item.itemType,
        theme = item.theme, releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
        pieces = item.pieces, minifigs = item.minifigs,
        retailPrice = item.retailPrice, status = item.status,
    )
}
