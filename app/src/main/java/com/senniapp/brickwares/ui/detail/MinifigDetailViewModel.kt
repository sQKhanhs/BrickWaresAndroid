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
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.ValueContributionRepository
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.util.Observability
import timber.log.Timber
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
    private var soldItems: List<SoldItem> = emptyList()
    private var valueKey: String? = null

    // The resolved fig + the sets it appears in, fetched once per open (Decision 16 — no minifig cache);
    // resolveFailed drives the offline placeholder.
    private var resolvedFig: Minifig? = null
    private var appearsInSets: List<CatalogSet> = emptyList()
    private var resolveFailed = false

    init {
        viewModelScope.launch { repository.getCollectionItems().collect { collectionItems = it; rebuild() } }
        viewModelScope.launch { repository.getWishlistItems().collect { wishlist = it; rebuild() } }
        viewModelScope.launch { repository.getSoldItems().collect { soldItems = it; rebuild() } }
    }

    fun retry() = resolveAndRebuild()

    /** The fig already reported as viewed for this page open (analytics `view_item`, once per open). */
    private var viewedFig: String? = null

    fun load(figNumber: String) {
        this.figNum = figNumber
        valueKey = null
        viewedFig = null
        resolvedFig = null
        resolveFailed = false
        appearsInSets = emptyList()
        // Clear the previous page immediately (loaded=false) so navigating detail→detail shows a loading
        // state, not the last fig's content, until the new fig resolves — fetchMinifig is a network query.
        _uiState.value = MinifigDetailUiState()
        resolveAndRebuild()
    }

    /** Fetch the fig + the sets it appears in ONCE per open (DB queries), then run the overlay rebuild. */
    private fun resolveAndRebuild() {
        val fn = figNum ?: return
        viewModelScope.launch {
            var failed = false
            val fig = try {
                catalogRepo.fetchMinifig(fn)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("MinifigDetailVM").w(e, "fetchMinifig failed for %s", fn)
                failed = true
                null
            }
            // A newer load() (fast detail→detail nav) changed the target — drop this stale result so it
            // can't overwrite the current page (the shared VM would otherwise flash the previous fig).
            if (figNum != fn) return@launch
            resolveFailed = failed
            resolvedFig = fig
            appearsInSets = if (fig == null) emptyList() else {
                runCatching { catalogRepo.fetchSetsForMinifig(fn) }.getOrDefault(emptyList())
            }
            if (figNum != fn) return@launch // superseded during the "appears in" fetch
            rebuild()
        }
    }

    private fun rebuild() {
        val fn = figNum ?: return
        val fig = resolvedFig
        // Report the view once the fig resolves; once per page open.
        if (fig != null && viewedFig != fig.figNum) {
            viewedFig = fig.figNum
            Observability.logItemViewed(
                kind = "minifig",
                id = fig.figNum,
                name = fig.name,
                theme = fig.themeSubthemes.firstOrNull()?.first,
            )
        }
        val owned = collectionItems.find { it.itemType == ItemType.MINIFIG && it.setNumber == fn }
        val appearsIn = if (fig == null) emptyList() else appearsInSets
        // Two-state availability from the fig's sets: Retail while any containing set is still
        // obtainable (available / exclusive / GWP / pending); else Retired (all retired, or
        // promo/magazine — never sold at retail). Null = unknown (no sets resolved / catalog not loaded).
        val retired = if (fig == null || appearsIn.isEmpty()) null else appearsIn.none { it.status in OBTAINABLE }
        val figSales = soldItems.filter { it.itemType == ItemType.MINIFIG && it.setNumber == fn }
        _uiState.update {
            it.copy(
                loaded = true,
                fig = fig,
                // Couldn't resolve the fig from the DB (offline / error) → placeholder.
                offline = fig == null && resolveFailed,
                isOwned = owned != null,
                ownedCount = owned?.totalQty ?: 0,
                ownedItem = owned,
                // Keep the See Details modal open while the fig still has copies OR sales; close it
                // when both are gone (so it doesn't re-open when the item is re-added).
                showCopies = (owned != null || figSales.isNotEmpty()) && it.showCopies,
                isWishlisted = wishlist.any { w -> w.itemType == ItemType.MINIFIG && w.setNumber == fn },
                isSold = figSales.isNotEmpty(),
                sales = figSales,
                appearsIn = appearsIn,
                retired = retired,
                ownedNumbers = collectionItems.mapTo(HashSet()) { c -> c.variantKey },
                wishlistedNumbers = wishlist.mapTo(HashSet()) { w -> w.variantKey },
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

    fun onAddClick() = _uiState.update { it.copy(addTarget = it.fig?.let(::figAsCatalogSet), addSalesMode = false) }

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
        repository.removeFromWishlist(fig.figNum, null) // a minifig has no set_id — keyed by fig_num
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(fig.name))) }
    }

    // ---- "Appears in" set cards (act on a set, not the minifig) ----

    fun onSetAddCollection(set: CatalogSet) = _uiState.update { it.copy(addTarget = set, addSalesMode = false) }
    fun onSetAddWishlist(set: CatalogSet) = wishlist(set)
    fun onSetRemoveWishlist(set: CatalogSet) {
        repository.removeFromWishlist(set.setNumber, set.setId)
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(set.name))) }
    }

    private fun wishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs, setId = set.setId,
                retailPrice = set.retailPrice ?: 0L, status = set.status,
                imageUrl = set.imageUrl ?: set.thumbnailUrl,
            ),
        )
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_added_wishlist, listOf(set.name))) }
    }

    // ---- Add sheet ----

    fun onDismissAdd() = _uiState.update { it.copy(addTarget = null, editingCopy = null, addSalesMode = false) }

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

    /** Add sheet in Sales mode: record a standalone minifig sale (does not touch the collection). */
    fun onAddSaleSubmit(item: CollectionItem, salePrice: Long) {
        repository.addSale(item, salePrice)
        _uiState.update {
            it.copy(addTarget = null, editingCopy = null, addSalesMode = false, toastMessage = UiText.Res(R.string.toast_added_sales, listOf(item.name)))
        }
        refreshValueSoon()
    }

    // ---- Owned-copies See Details ----

    fun onSeeCopies() = _uiState.update { it.copy(showCopies = true) }
    fun onDismissCopies() = _uiState.update { it.copy(showCopies = false) }
    fun onDeleteCopy(setNumber: String, copyId: String) = repository.removeCopy(setNumber, copyId)

    /** Delete a sale shown in the merged See Details modal (sale edit lives on the Collection tab). */
    fun onDeleteSale(saleId: String) = repository.removeSale(saleId)

    fun onAddCopy() = _uiState.update { it.copy(showCopies = false, editingCopy = null, addSalesMode = false, addTarget = it.fig?.let(::figAsCatalogSet)) }
    /** Add a sale of this minifig (opens the Add sheet in Sales mode) — from the modal's Sales tab. */
    fun onAddSaleForFig() = _uiState.update { it.copy(showCopies = false, editingCopy = null, addSalesMode = true, addTarget = it.fig?.let(::figAsCatalogSet)) }
    fun onEditCopy(copy: Copy) = _uiState.update { it.copy(showCopies = false, editingCopy = copy, addSalesMode = false, addTarget = it.fig?.let(::figAsCatalogSet)) }

    // Add-sheet / quick-search suggestions — DB queries now (Decision 16); the composables debounce them.
    suspend fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.searchSets(query)

    suspend fun searchMinifigs(query: String): List<Minifig> = catalogRepo.fetchMinifigsMatching(query)

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
