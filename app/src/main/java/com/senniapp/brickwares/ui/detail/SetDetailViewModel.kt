package com.senniapp.brickwares.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ValueAggregator
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.CollectionRepositoryProvider
import com.senniapp.brickwares.data.repository.ValueContributionRepository
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.util.Observability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the Set Detail page. [load] points it at a set number; it then resolves the set
 * from the catalog and keeps ownership/wishlist state in sync by observing both flows. The same
 * instance is reused as the user navigates between related sets ([load] re-points it).
 */
class SetDetailViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
    private val valueRepo: ValueContributionRepository = ValueRepositoryProvider.instance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetDetailUiState())
    val uiState: StateFlow<SetDetailUiState> = _uiState.asStateFlow()

    // Canonical catalog id ("number-variant"); may also be a bare set number when opened from the
    // mock collection/wishlist (resolved with a fallback in [rebuild]).
    private var catalogKey: String? = null
    private var collectionItems: List<CollectionItem> = emptyList()
    private var wishlist: List<WishlistItem> = emptyList()
    private var soldItems: List<SoldItem> = emptyList()

    // Recommendation snapshot: computed once per page open (keyed by the resolved set id) so cards
    // stay put as the user adds/wishlists here; a fresh batch is drawn on the next open (see [load]).
    private var relatedKey: String? = null
    private var relatedSnapshot: List<CatalogSet> = emptyList()

    // The set id we last fetched a current value for, so rebuild() refetches only on a set change.
    private var valueKey: String? = null

    // The resolved hero set for the current [catalogKey], fetched once per open (Decision 16 — no full
    // catalog in memory); its minifig grid; and whether the fetch failed (drives the offline state).
    private var resolvedSet: CatalogSet? = null
    private var minifigGrid: List<Minifig> = emptyList()
    private var resolveFailed = false
    // Whether the fetchSet attempt for the current [catalogKey] has completed (success or failure). Until
    // it has, [rebuild] — also driven by collection/wishlist/sales flow emissions — must NOT mark the page
    // loaded: otherwise a flow emission mid-fetch (e.g. the catalog overlay loading on first app open)
    // flips loaded=true with a null set, flashing "Set not found" over the spinner before the fetch ends.
    private var resolved = false

    init {
        // Collection/wishlist/sales changes re-run the (network-free) overlay rebuild over the cached set.
        viewModelScope.launch {
            repository.getCollectionItems().collect { collectionItems = it; rebuild() }
        }
        viewModelScope.launch {
            repository.getWishlistItems().collect { wishlist = it; rebuild() }
        }
        viewModelScope.launch {
            repository.getSoldItems().collect { soldItems = it; rebuild() }
        }
    }

    /** Retry after an offline/error state (the error fallback's Retry button): re-fetch + rebuild. */
    fun retry() = resolveAndRebuild()

    /** The set id already reported as viewed for this page open (analytics `view_item`, once per open). */
    private var viewedId: String? = null

    fun load(catalogId: String) {
        this.catalogKey = catalogId
        // Force a fresh set + recommendation + value fetch for this open (even when returning to a set
        // seen before).
        relatedKey = null
        viewedId = null
        valueKey = null
        resolvedSet = null
        resolveFailed = false
        resolved = false
        minifigGrid = emptyList()
        relatedSnapshot = emptyList()
        // Clear the previous page immediately (loaded=false) so navigating detail→detail shows a loading
        // state, not the last set's content, until the new set resolves — fetchSet is a network round-trip.
        _uiState.value = SetDetailUiState()
        resolveAndRebuild()
    }

    /**
     * Fetch the hero set (by id/number), its same-theme recommendations, and its minifig grid ONCE per
     * open (all DB queries), then run the overlay [rebuild]. Ownership/value overlays afterward are
     * recomputed by [rebuild] over the cached set with no further network.
     */
    private fun resolveAndRebuild() {
        val key = catalogKey ?: return
        viewModelScope.launch {
            var failed = false
            val set = try {
                catalogRepo.fetchSet(key)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("SetDetailVM").w(e, "fetchSet failed for %s", key)
                failed = true
                null
            }
            // A newer load() (fast detail→detail nav) changed the target — drop this stale result so it
            // can't overwrite the current page (the shared VM would otherwise flash the previous set).
            if (catalogKey != key) return@launch
            resolveFailed = failed
            resolvedSet = set
            resolved = true
            if (set != null) {
                // Recommend 3 RANDOM same-theme sets the user neither owns nor wishlists — once per open.
                if (relatedKey != set.id) {
                    val ownedNumbers = collectionItems.mapTo(HashSet()) { it.variantKey }
                    val wishlistedNumbers = wishlist.mapTo(HashSet()) { it.variantKey }
                    // Legacy set_id-less rows key on "n<number>"; also exclude a recommended set the user
                    // owns/wants only through such a row (bare number matches) so it isn't offered as "Add".
                    val ownedLegacyNums = collectionItems.filter { it.setId == null }.mapTo(HashSet()) { it.setNumber }
                    val wishlistedLegacyNums = wishlist.filter { it.setId == null }.mapTo(HashSet()) { it.setNumber }
                    relatedSnapshot = runCatching { catalogRepo.setsInTheme(set.theme) }.getOrDefault(emptyList())
                        .filter {
                            it.id != set.id &&
                                it.variantKey !in ownedNumbers && it.variantKey !in wishlistedNumbers &&
                                it.setNumber !in ownedLegacyNums && it.setNumber !in wishlistedLegacyNums
                        }
                        .shuffled()
                        .take(3)
                    relatedKey = set.id
                }
                minifigGrid = set.setId?.let { runCatching { catalogRepo.fetchMinifigsForSet(it) }.getOrDefault(emptyList()) } ?: emptyList()
                if (catalogKey != key) return@launch // superseded during the recommendation / grid fetches
            }
            rebuild()
        }
    }

    private fun rebuild() {
        val key = catalogKey ?: return
        // Don't render (or mark the page loaded) until the fetch has resolved — a collection/wishlist/sales
        // flow emission mid-fetch must not flash the empty "Set not found" page over the loading spinner.
        if (!resolved) return
        val set = resolvedSet
        // The resolved hero's exact-variant identity (CMF/SDCC variants share a set_number, so owned/
        // wishlisted/sold marking must key on this, not the number). Null while unresolved (offline).
        val heroKey = set?.variantKey
        val heroNum = set?.setNumber
        // A user-data row belongs to this hero when its variant matches — or, for a LEGACY row saved
        // before set_id was tracked (variantKey "n<number>"), when the bare number matches. The legacy
        // clause fires only for set_id-less rows, so set_id-backed CMF/SDCC variants stay strictly
        // per-variant; a legacy row stays ambiguous across a shared number until it is re-added.
        fun matchesHero(vk: String, sid: Long?, num: String): Boolean =
            heroKey != null && (vk == heroKey || (sid == null && num == heroNum))
        // Report the view once the set resolves; rebuilds from collection/wishlist changes don't re-report.
        if (set != null && viewedId != set.id) {
            viewedId = set.id
            Observability.logItemViewed(kind = "set", id = set.id, name = set.name, theme = set.theme)
        }
        // Prefer the exact-variant row; only fall back to a legacy set_id-less row when there's no exact
        // one (so a re-added row wins over a stale legacy one for the same number).
        val owned = heroKey?.let { hk -> collectionItems.find { it.variantKey == hk } }
            ?: collectionItems.find { it.setId == null && it.setNumber == heroNum }
        // Keep the open copies dialog live (hero OR a recommended set), so edits/deletes/sells reflect.
        // Resolved by the exact variantKey held in copiesVariantKey, so a shared-number dialog never
        // pulls in a sibling variant's copies/sales. If its item is no longer owned (last copy deleted)
        // and it has no sales, close it — clearing copiesVariantKey so it doesn't silently re-open later.
        val openCs = _uiState.value.copiesVariantKey
        val copiesItem = openCs?.let { cs -> collectionItems.find { it.variantKey == cs } }
        val copiesSales = openCs?.let { cs -> soldItems.filter { s -> s.variantKey == cs } } ?: emptyList()
        // Keep the modal open while the set still has copies OR sales; close it when both are gone.
        val copiesSn = openCs?.takeIf { copiesItem != null || copiesSales.isNotEmpty() }
        val ownedNumbers = collectionItems.mapTo(HashSet()) { it.variantKey }
        val wishlistedNumbers = wishlist.mapTo(HashSet()) { it.variantKey }
        _uiState.update {
            it.copy(
                loaded = true,
                set = set,
                // Couldn't resolve the set from the DB (offline / error) → no-internet placeholder.
                offline = set == null && resolveFailed,
                isOwned = owned != null,
                ownedCount = owned?.totalQty ?: 0,
                ownedItem = owned,
                isWishlisted = wishlist.any { w -> matchesHero(w.variantKey, w.setId, w.setNumber) },
                isSold = soldItems.any { s -> matchesHero(s.variantKey, s.setId, s.setNumber) },
                copiesSales = copiesSales,
                minifigs = if (set == null) emptyList() else minifigGrid,
                related = if (set == null) emptyList() else relatedSnapshot,
                ownedNumbers = ownedNumbers,
                wishlistedNumbers = wishlistedNumbers,
                copiesVariantKey = copiesSn,
                copiesItem = copiesItem,
            )
        }
        // Fetch the community current value once per resolved set (Decision 17).
        if (set?.setId != null && valueKey != set.id) {
            valueKey = set.id
            fetchValue(set)
        }
    }

    /** Load the community current value for [set], clearing any stale value while it's in flight. */
    private fun fetchValue(set: CatalogSet) {
        val id = set.setId ?: return
        _uiState.update { it.copy(currentValue = CurrentValue.NONE, valueLoading = true) }
        viewModelScope.launch {
            val tier = ValueAggregator.tierOf(set.status, set.retiredYear, set.retiredMonth)
            val value = valueRepo.forSet(id, set.retailPrice, tier)
            // Ignore a late result if the user has since navigated to another set.
            if (valueKey == set.id) _uiState.update { it.copy(currentValue = value, valueLoading = false) }
        }
    }

    /**
     * Re-read the current value after the user contributes a paid price. The contribution posts only
     * *after* the collection row syncs (SyncCoordinator), so give that a short head start; if it's
     * not visible yet (offline / slow), the next page open is authoritative.
     */
    private fun refreshValueSoon() {
        val set = _uiState.value.set ?: return
        viewModelScope.launch {
            delay(2_500)
            if (valueKey == set.id) fetchValue(set)
        }
    }

    fun onAddToWishlist() {
        _uiState.value.set?.let(::wishlist)
    }

    fun onAddToCollectionClick() {
        _uiState.update { it.copy(addTarget = it.set, addSalesMode = false) }
    }

    // ---- Recommendation cards (act on a given related set, not the hero set) ----

    /** From a recommendation card's Add button: open the Add sheet targeted at that set. */
    fun onAddRecommendToCollection(set: CatalogSet) {
        _uiState.update { it.copy(addTarget = set, addSalesMode = false) }
    }

    /** From a recommendation card's Wishlist button. */
    fun onAddRecommendToWishlist(set: CatalogSet) = wishlist(set)

    /** From a recommendation card's "Wishlisted" button — removes it from the wishlist. */
    fun onRemoveRecommendFromWishlist(set: CatalogSet) {
        repository.removeFromWishlist(set.setNumber, set.setId)
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_removed_wishlist, listOf(set.name))) }
    }

    /** From a recommendation card's "See Detail" (owned) button — opens its copies dialog in place. */
    fun onRecommendSeeCopies(set: CatalogSet) {
        val vk = set.variantKey
        _uiState.update {
            it.copy(
                copiesVariantKey = vk,
                copiesItem = collectionItems.find { c -> c.variantKey == vk },
                copiesSales = soldItems.filter { s -> s.variantKey == vk },
            )
        }
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

    fun onDismissAdd() {
        _uiState.update { it.copy(addTarget = null, editingCopy = null, addSalesMode = false) }
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
        // A paid price just added/edited becomes a community value point — re-read it (Decision 17).
        refreshValueSoon()
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

    fun onConfirmSell(quantity: Int, salePrice: Long, currency: AppCurrency, soldOn: String) {
        val state = _uiState.value
        val copy = state.sellCopy
        // sellCopy resolves the copy by its id (the set number is only a label), so the exact owned
        // variant is targeted regardless; use the dialog item's own number for a correct toast/label.
        val sn = state.copiesItem?.setNumber ?: state.set?.setNumber
        val name = state.copiesItem?.name ?: state.set?.name
        if (copy != null && sn != null) {
            repository.sellCopy(sn, copy.id, quantity, salePrice, currency, soldOn)
        }
        _uiState.update {
            it.copy(
                sellCopy = null, copiesVariantKey = null, copiesItem = null,
                toastMessage = name?.let { n -> UiText.Res(R.string.toast_sold, listOf(n)) } ?: it.toastMessage,
            )
        }
    }

    // ---- Owned-item copies (the See-Details dialog — hero set or a recommended owned set) ----

    fun onSeeCopies() = _uiState.update {
        val set = it.set
        val owned = it.ownedItem
        // Key the dialog on the actual owned/sold row's OWN variant key — a legacy (set_id-less) row uses
        // "n<number>", not the hero's "s<id>" — so rebuild's exact-key re-resolution keeps it populated.
        val soldMatch = set?.let { s ->
            soldItems.firstOrNull { x -> x.variantKey == s.variantKey }
                ?: soldItems.firstOrNull { x -> x.setId == null && x.setNumber == s.setNumber }
        }
        val vk = owned?.variantKey ?: soldMatch?.variantKey ?: set?.variantKey
        it.copy(
            copiesVariantKey = vk, copiesItem = owned,
            copiesSales = vk?.let { k -> soldItems.filter { x -> x.variantKey == k } } ?: emptyList(),
        )
    }
    fun onDismissCopies() = _uiState.update { it.copy(copiesVariantKey = null, copiesItem = null, copiesSales = emptyList()) }

    /** Delete a sale shown in the merged See Details modal (view-only edit lives on the Collection tab). */
    fun onDeleteSale(saleId: String) = repository.removeSale(saleId)

    fun onDeleteCopy(setNumber: String, copyId: String) = repository.removeCopy(setNumber, copyId)

    /** Add another copy of the copies dialog's set (opens the Add sheet in Collection mode). */
    fun onAddCopyForSet() = _uiState.update {
        it.copy(copiesVariantKey = null, copiesItem = null, copiesSales = emptyList(), editingCopy = null, addSalesMode = false, addTarget = it.copiesTargetSet())
    }

    /** Add a sale of the copies dialog's set (opens the Add sheet in Sales mode). */
    fun onAddSaleForSet() = _uiState.update {
        it.copy(copiesVariantKey = null, copiesItem = null, copiesSales = emptyList(), editingCopy = null, addSalesMode = true, addTarget = it.copiesTargetSet())
    }

    /** Edit an existing copy of the copies dialog's set (opens the Add sheet in edit mode). */
    fun onEditCopy(copy: Copy) = _uiState.update {
        it.copy(copiesVariantKey = null, copiesItem = null, copiesSales = emptyList(), editingCopy = copy, addSalesMode = false, addTarget = it.copiesTargetSet())
    }

    /** The exact [CatalogSet] the copies dialog is for — the hero or a recommended set (right variant). */
    private fun SetDetailUiState.copiesTargetSet(): CatalogSet? {
        val vk = copiesVariantKey ?: return null
        // The copies dialog only ever opens for the hero or a recommendation card, so those cover it.
        return set?.takeIf { it.variantKey == vk }
            ?: related.firstOrNull { it.variantKey == vk }
    }

    // Add-sheet suggestions — a DB query now (Decision 16); the sheet debounces it off the composition.
    suspend fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.searchSets(query)

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
