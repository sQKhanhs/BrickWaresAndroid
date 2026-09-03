package com.senniapp.brickwares.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.ValueContributionRepository
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.ui.components.UiText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Minifig Detail page. [load] points it at a fig number; it resolves the minifig
 * from the catalog, keeps ownership/wishlist in sync, fetches the community value, and lists the sets
 * the fig appears in. Reused across navigations ([load] re-points it).
 */
class MinifigDetailViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
    private val valueRepo: ValueContributionRepository = ValueRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MinifigDetailUiState())
    val uiState: StateFlow<MinifigDetailUiState> = _uiState.asStateFlow()

    private var figNum: String? = null
    private var collectionItems: List<CollectionItem> = emptyList()
    private var wishlist: List<WishlistItem> = emptyList()
    private var valueKey: String? = null

    init {
        viewModelScope.launch {
            catalogRepo.refresh()
            catalogRepo.refreshMinifigs()
            rebuild()
        }
        viewModelScope.launch { repository.getCollectionItems().collect { collectionItems = it; rebuild() } }
        viewModelScope.launch { repository.getWishlistItems().collect { wishlist = it; rebuild() } }
    }

    fun retry() {
        viewModelScope.launch {
            catalogRepo.refresh()
            catalogRepo.refreshMinifigs()
            rebuild()
        }
    }

    fun load(figNumber: String) {
        this.figNum = figNumber
        valueKey = null
        _uiState.update { it.copy(addTarget = null, toastMessage = null, showCopies = false) }
        rebuild()
    }

    private fun rebuild() {
        val fn = figNum ?: return
        val fig = catalogRepo.allMinifigs().firstOrNull { it.figNum == fn }
        val owned = collectionItems.find { it.itemType == ItemType.MINIFIG && it.setNumber == fn }
        val appearsIn = if (fig == null) emptyList() else catalogRepo.setsForMinifig(fn)
        // Two-state availability from the fig's sets: Retail while any containing set is still
        // obtainable (available / exclusive / GWP / pending); else Retired (all retired, or
        // promo/magazine — never sold at retail). Null = unknown (no sets resolved / catalog not loaded).
        val retired = if (fig == null || appearsIn.isEmpty()) null else appearsIn.none { it.status in OBTAINABLE }
        _uiState.update {
            it.copy(
                loaded = true,
                fig = fig,
                // No minifig catalog to resolve against (offline / not loaded) → placeholder.
                offline = fig == null && catalogRepo.allMinifigs().isEmpty(),
                isOwned = owned != null,
                ownedCount = owned?.totalQty ?: 0,
                ownedItem = owned,
                // Close the copies dialog if the item is no longer owned (e.g. its last copy was
                // deleted) so it doesn't re-open when the item is re-added.
                showCopies = owned != null && it.showCopies,
                isWishlisted = wishlist.any { w -> w.itemType == ItemType.MINIFIG && w.setNumber == fn },
                appearsIn = appearsIn,
                retired = retired,
                ownedNumbers = collectionItems.mapTo(HashSet()) { c -> c.setNumber },
                wishlistedNumbers = wishlist.mapTo(HashSet()) { w -> w.setNumber },
            )
        }
        if (fig != null && valueKey != fn) {
            valueKey = fn
            fetchValue(fn)
        }
    }

    private fun fetchValue(fn: String) {
        _uiState.update { it.copy(currentValue = com.senniapp.brickwares.data.model.CurrentValue.NONE, valueLoading = true) }
        viewModelScope.launch {
            val value = valueRepo.forFig(fn)
            if (valueKey == fn) _uiState.update { it.copy(currentValue = value, valueLoading = false) }
        }
    }

    private fun refreshValueSoon() {
        val fn = figNum ?: return
        viewModelScope.launch {
            delay(2_500)
            if (valueKey == fn) fetchValue(fn)
        }
    }

    // ---- Hero add / wishlist ----

    fun onAddClick() = _uiState.update { it.copy(addTarget = it.fig?.let(::figAsCatalogSet)) }

    fun onAddToWishlist() {
        val fig = _uiState.value.fig ?: return
        repository.addToWishlist(
            WishlistItem(
                setNumber = fig.figNum, name = fig.name, itemType = ItemType.MINIFIG,
                theme = fig.themes.firstOrNull() ?: "", releaseYear = 0, releaseMonth = 0,
                pieces = fig.numParts, minifigs = 0, retailPrice = 0L,
                status = Availability.AVAILABLE, imageUrl = fig.imageUrl,
            ),
        )
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_added_wishlist, listOf(fig.name))) }
    }

    fun onRemoveFromWishlist() {
        val fig = _uiState.value.fig ?: return
        repository.removeFromWishlist(fig.figNum)
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(fig.name))) }
    }

    // ---- "Appears in" set cards (act on a set, not the minifig) ----

    fun onSetAddCollection(set: CatalogSet) = _uiState.update { it.copy(addTarget = set) }
    fun onSetAddWishlist(set: CatalogSet) = wishlist(set)
    fun onSetRemoveWishlist(set: CatalogSet) {
        repository.removeFromWishlist(set.setNumber)
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(set.name))) }
    }

    private fun wishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs,
                retailPrice = set.retailPrice ?: 0L, status = set.status,
                imageUrl = set.imageUrl ?: set.thumbnailUrl,
            ),
        )
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_added_wishlist, listOf(set.name))) }
    }

    // ---- Add sheet ----

    fun onDismissAdd() = _uiState.update { it.copy(addTarget = null, editingCopy = null) }

    fun onAddSubmit(item: CollectionItem) {
        if (_uiState.value.editingCopy != null) {
            repository.updateCopy(item.setNumber, item.copies.first())
            _uiState.update { it.copy(addTarget = null, editingCopy = null) }
        } else {
            repository.addItem(item)
            _uiState.update {
                it.copy(addTarget = null, editingCopy = null, toastMessage = UiText.Res(R.string.toast_added_collection, listOf(item.name)))
            }
        }
        refreshValueSoon()
    }

    // ---- Owned-copies See Details ----

    fun onSeeCopies() = _uiState.update { it.copy(showCopies = true) }
    fun onDismissCopies() = _uiState.update { it.copy(showCopies = false) }
    fun onDeleteCopy(setNumber: String, copyId: String) = repository.removeCopy(setNumber, copyId)

    fun onAddCopy() = _uiState.update { it.copy(showCopies = false, editingCopy = null, addTarget = it.fig?.let(::figAsCatalogSet)) }
    fun onEditCopy(copy: Copy) = _uiState.update { it.copy(showCopies = false, editingCopy = copy, addTarget = it.fig?.let(::figAsCatalogSet)) }

    fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.search(query)

    fun onToastShown() = _uiState.update { it.copy(toastMessage = null) }

    /** A minifig as a fig-num-keyed [CatalogSet] so it flows through the shared Add sheet + collection. */
    private fun figAsCatalogSet(fig: Minifig) = CatalogSet(
        setNumber = fig.figNum, name = fig.name, itemType = ItemType.MINIFIG,
        theme = fig.themes.firstOrNull() ?: "", releaseYear = 0, releaseMonth = 0,
        pieces = fig.numParts, minifigs = 0, retailPrice = null,
        status = Availability.AVAILABLE, imageUrl = fig.imageUrl,
    )

    private companion object {
        /** Set statuses that count as "still obtainable" → the minifig reads as Retail (not Retired). */
        val OBTAINABLE = setOf(
            Availability.AVAILABLE, Availability.EXCLUSIVE, Availability.GWP, Availability.PENDING,
        )
    }
}
