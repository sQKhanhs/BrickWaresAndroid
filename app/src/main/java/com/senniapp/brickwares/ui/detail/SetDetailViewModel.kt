package com.senniapp.brickwares.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.ui.components.UiText
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

    // Recommendation snapshot: computed once per page open (keyed by the resolved set id) so cards
    // stay put as the user adds/wishlists here; a fresh batch is drawn on the next open (see [load]).
    private var relatedKey: String? = null
    private var relatedSnapshot: List<CatalogSet> = emptyList()

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

    /** Retry after an offline/error state (the error fallback's Retry button): re-fetch + rebuild. */
    fun retry() {
        viewModelScope.launch {
            catalogRepo.refresh()
            rebuild()
        }
    }

    fun load(catalogId: String) {
        this.catalogKey = catalogId
        // Force a fresh recommendation batch for this open (even when returning to a set seen before).
        relatedKey = null
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
        // Keep the open copies dialog live (hero OR a recommended set), so edits/deletes/sells reflect.
        val copiesSn = _uiState.value.copiesSetNumber
        val copiesItem = copiesSn?.let { cs -> collectionItems.find { it.setNumber == cs } }
        val ownedNumbers = collectionItems.mapTo(HashSet()) { it.setNumber }
        val wishlistedNumbers = wishlist.mapTo(HashSet()) { it.setNumber }
        // Recommend 3 RANDOM same-theme sets the user neither owns nor wishlists — captured ONCE per
        // page open. The snapshot does NOT refilter as the user adds/wishlists here, so a card stays
        // put (its buttons just flip); a fresh batch (excluding the newly-added) is drawn next open.
        if (set != null && relatedKey != set.id) {
            relatedSnapshot = all
                .filter {
                    it.theme == set.theme && it.id != set.id &&
                        it.setNumber !in ownedNumbers && it.setNumber !in wishlistedNumbers
                }
                .shuffled()
                .take(3)
            relatedKey = set.id
        }
        _uiState.update {
            it.copy(
                loaded = true,
                set = set,
                // No catalog to resolve against (offline / not loaded) → show the no-internet placeholder.
                offline = set == null && all.isEmpty(),
                isOwned = owned != null,
                ownedCount = owned?.totalQty ?: 0,
                totalPaid = owned?.totalPaid ?: 0L,
                ownedItem = owned,
                isWishlisted = wishlist.any { w -> w.setNumber == sn },
                related = if (set == null) emptyList() else relatedSnapshot,
                ownedNumbers = ownedNumbers,
                wishlistedNumbers = wishlistedNumbers,
                copiesItem = copiesItem,
            )
        }
    }

    fun onAddToWishlist() {
        _uiState.value.set?.let(::wishlist)
    }

    fun onAddToCollectionClick() {
        _uiState.update { it.copy(addTarget = it.set) }
    }

    // ---- Recommendation cards (act on a given related set, not the hero set) ----

    /** From a recommendation card's Add button: open the Add sheet targeted at that set. */
    fun onAddRecommendToCollection(set: CatalogSet) {
        _uiState.update { it.copy(addTarget = set) }
    }

    /** From a recommendation card's Wishlist button. */
    fun onAddRecommendToWishlist(set: CatalogSet) = wishlist(set)

    /** From a recommendation card's "Wishlisted" button — removes it from the wishlist. */
    fun onRemoveRecommendFromWishlist(set: CatalogSet) {
        repository.removeFromWishlist(set.setNumber)
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(set.name))) }
    }

    /** From a recommendation card's "See Detail" (owned) button — opens its copies dialog in place. */
    fun onRecommendSeeCopies(set: CatalogSet) {
        _uiState.update {
            it.copy(copiesSetNumber = set.setNumber, copiesItem = collectionItems.find { c -> c.setNumber == set.setNumber })
        }
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

    fun onDismissAdd() {
        _uiState.update { it.copy(addTarget = null, editingCopy = null) }
    }

    fun onAddToCollectionSubmit(item: CollectionItem) {
        if (_uiState.value.editingCopy != null) {
            // Edit mode: replace the copy rather than adding a new one.
            repository.updateCopy(item.setNumber, item.copies.first())
            _uiState.update { it.copy(addTarget = null, editingCopy = null) }
        } else {
            repository.addItem(item)
            _uiState.update {
                it.copy(addTarget = null, editingCopy = null, toastMessage = UiText.Res(R.string.toast_added_collection, listOf(item.name)))
            }
        }
    }

    /** Add sheet in Sales mode: records a standalone sale (does not add to the collection). */
    fun onAddToSalesSubmit(item: CollectionItem, salePrice: Long) {
        repository.addSale(item, salePrice)
        _uiState.update {
            it.copy(addTarget = null, editingCopy = null, toastMessage = UiText.Res(R.string.toast_added_sales, listOf(item.name)))
        }
    }

    // ---- Sell an owned copy → Sales ----

    // Keep the copies dialog's target set open behind the sell dialog so cancelling returns to it.
    fun onSellCopyRequest(copy: Copy) = _uiState.update { it.copy(sellCopy = copy) }

    fun onDismissSell() = _uiState.update { it.copy(sellCopy = null) }

    fun onConfirmSell(quantity: Int, salePrice: Long, soldOn: String) {
        val state = _uiState.value
        val copy = state.sellCopy
        val sn = state.copiesSetNumber ?: state.set?.setNumber
        val name = state.copiesItem?.name ?: state.set?.name
        if (copy != null && sn != null) {
            repository.sellCopy(sn, copy.id, quantity, salePrice, soldOn)
        }
        _uiState.update {
            it.copy(
                sellCopy = null, copiesSetNumber = null, copiesItem = null,
                toastMessage = name?.let { n -> UiText.Res(R.string.toast_sold, listOf(n)) } ?: it.toastMessage,
            )
        }
    }

    // ---- Owned-item copies (the See-Details dialog — hero set or a recommended owned set) ----

    fun onSeeCopies() = _uiState.update { it.copy(copiesSetNumber = it.set?.setNumber, copiesItem = it.ownedItem) }
    fun onDismissCopies() = _uiState.update { it.copy(copiesSetNumber = null, copiesItem = null) }

    fun onDeleteCopy(setNumber: String, copyId: String) = repository.removeCopy(setNumber, copyId)

    /** Add another copy of the copies dialog's set (opens the Add sheet, fresh copy). */
    fun onAddCopyForSet() = _uiState.update {
        it.copy(copiesSetNumber = null, copiesItem = null, editingCopy = null, addTarget = it.copiesTargetSet())
    }

    /** Edit an existing copy of the copies dialog's set (opens the Add sheet in edit mode). */
    fun onEditCopy(copy: Copy) = _uiState.update {
        it.copy(copiesSetNumber = null, copiesItem = null, editingCopy = copy, addTarget = it.copiesTargetSet())
    }

    /** The exact [CatalogSet] the copies dialog is for — the hero or a recommended set (right variant). */
    private fun SetDetailUiState.copiesTargetSet(): CatalogSet? {
        val sn = copiesSetNumber ?: return null
        return set?.takeIf { it.setNumber == sn }
            ?: related.firstOrNull { it.setNumber == sn }
            ?: catalogRepo.all().firstOrNull { it.setNumber == sn }
    }

    fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.search(query)

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
