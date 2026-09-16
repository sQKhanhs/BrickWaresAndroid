package com.senniapp.brickwares.ui.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.ui.components.ItemSort
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
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
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WishlistUiState())
    val uiState: StateFlow<WishlistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getWishlistItems().collect { items ->
                _uiState.update { it.copy(itemsLoaded = true, items = items) }
            }
        }
    }

    fun onFilterSelected(filter: WishlistFilter) {
        _uiState.update { it.copy(filter = filter, page = 1) }
    }

    fun onPageChange(page: Int) {
        _uiState.update { it.copy(page = page) }
    }

    fun onSortChange(sort: ItemSort) {
        _uiState.update { it.copy(sort = sort, page = 1) }
    }

    // Add-sheet suggestions — a DB query now (Decision 16); the sheet debounces it off the composition.
    suspend fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.searchSets(query)

    fun onRemove(setNumber: String) {
        val item = _uiState.value.items.find { it.setNumber == setNumber }
        repository.removeFromWishlist(setNumber)
        if (item != null) {
            _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(item.name))) }
        }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
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
