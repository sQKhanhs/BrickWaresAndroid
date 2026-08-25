package com.senniapp.brickwares.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * ViewModel for the Set Detail page. [load] points it at a set number; it then resolves the set
 * from the catalog and keeps ownership/wishlist state in sync by observing both flows. The same
 * instance is reused as the user navigates between related sets ([load] re-points it).
 */
class SetDetailViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetDetailUiState())
    val uiState: StateFlow<SetDetailUiState> = _uiState.asStateFlow()

    // Canonical catalog id ("number-variant"); may also be a bare set number when opened from the
    // mock collection/wishlist (resolved with a fallback in [rebuild]).
    private var catalogKey: String? = null
    private var collectionItems: List<CollectionItem> = emptyList()
    private var wishlist: List<WishlistItem> = emptyList()

    init {
        // Warm the catalog cache, then rebuild so the set resolves from real data.
        viewModelScope.launch {
            catalogRepo.refresh()
            rebuild()
        }
        viewModelScope.launch {
            repository.getCollectionItems().collect { collectionItems = it; rebuild() }
        }
        viewModelScope.launch {
            repository.getWishlistItems().collect { wishlist = it; rebuild() }
        }
    }

    fun load(catalogId: String) {
        this.catalogKey = catalogId
        _uiState.update { it.copy(addTarget = null, toastMessage = null) }
        rebuild()
    }

    private fun rebuild() {
        val key = catalogKey ?: return
        val all = catalogRepo.all()
        // Resolve by canonical id ("number-variant"); fall back to a bare set number.
        val set = all.find { it.id == key } ?: all.find { it.setNumber == key }
        val sn = set?.setNumber ?: key
        val owned = collectionItems.find { it.setNumber == sn }
        val related = all.filter { it.theme == set?.theme && it.id != set?.id }.take(4)
        _uiState.update {
            it.copy(
                loaded = true,
                set = set,
                // No catalog to resolve against (offline / not loaded) → show the no-internet placeholder.
                offline = set == null && all.isEmpty(),
                isOwned = owned != null,
                ownedCount = owned?.totalQty ?: 0,
                totalPaid = owned?.totalPaid ?: 0L,
                isWishlisted = wishlist.any { w -> w.setNumber == sn },
                related = related,
            )
        }
    }

    fun onAddToWishlist() {
        val set = _uiState.value.set ?: return
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs,
                retailPrice = set.retailPrice ?: 0L, status = set.status,
                imageUrl = set.imageUrl ?: set.thumbnailUrl,
            ),
        )
        _uiState.update { it.copy(toastMessage = "${set.name} added to Wishlist") }
    }

    fun onAddToCollectionClick() {
        _uiState.update { it.copy(addTarget = it.set) }
    }

    fun onDismissAdd() {
        _uiState.update { it.copy(addTarget = null) }
    }

    fun onAddToCollectionSubmit(item: CollectionItem) {
        repository.addItem(item)
        _uiState.update { it.copy(addTarget = null, toastMessage = "${item.name} added to Collection") }
    }

    fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.search(query)

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
