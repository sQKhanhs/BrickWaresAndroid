package com.senniapp.brickwares.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.R
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
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
 * ViewModel for the Search tab. Catalog lookups use the repository's LIKE-style
 * [CollectionRepository.searchCatalog]. Typing produces live suggestions; submitting a search
 * fills [SearchUiState.results] (or the too-many-results state). Results can be added to the
 * collection (via the shared Add sheet) or wishlisted directly.
 */
class SearchViewModel(
    private val repository: CollectionRepository = CollectionRepositoryProvider.instance,
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private var catalog: List<CatalogSet> = emptyList()
    private var minifigs: List<Minifig> = emptyList()
    private val _uiState = MutableStateFlow(SearchUiState(isLoading = true))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Rebuild the browser whenever the catalog (re)loads (revision bumps on every successful load)
        // — covers the first load, the error-fallback Retry, and a reconnect satisfied by any
        // component's refresh, so the themes always repopulate.
        viewModelScope.launch {
            catalogRepo.revision.collect {
                catalog = catalogRepo.all()
                if (catalog.isNotEmpty()) {
                    _uiState.update { it.copy(themes = buildThemes(), isLoading = false) }
                }
            }
        }
        viewModelScope.launch { catalogRepo.refresh() }
        // Load minifigs up front too — the search bar is GLOBAL (searches sets + minifigs regardless
        // of the browse mode), so the minifig cache must be ready even before entering minifig mode.
        loadMinifigs()
        // Surface catalog load failures (no connection / error) so the UI can show the error fallback.
        viewModelScope.launch {
            catalogRepo.loadError.collect { failed -> _uiState.update { it.copy(loadError = failed) } }
        }
        // Observe the wishlist so result cards can show a "Wishlisted" state.
        viewModelScope.launch {
            repository.getWishlistItems().collect { items ->
                _uiState.update { it.copy(wishlistedNumbers = items.map { w -> w.setNumber }.toSet()) }
            }
        }
        // Observe the collection so result cards for owned sets show "See Detail" instead of add/wishlist.
        viewModelScope.launch {
            repository.getCollectionItems().collect { items ->
                _uiState.update { it.copy(ownedNumbers = items.map { c -> c.setNumber }.toSet()) }
            }
        }
    }

    /** Retry after a catalog load failure (the error fallback's Retry button). */
    fun retry() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            // On success the catalog `revision` bumps and the observer rebuilds the themes; on failure
            // `loadError` stays set (observed) so the error fallback remains.
            catalogRepo.refresh()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                submittedQuery = null,
                // Live set suggestions (the dropdown); minifigs fold into the submitted global results.
                suggestions = if (query.isBlank()) emptyList() else catalogRepo.search(query).take(6),
            )
        }
    }

    /** Global search — returns matching sets AND minifigs, independent of the browse mode. */
    fun onSubmit() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        _uiState.update {
            it.copy(
                submittedQuery = q, suggestions = emptyList(),
                results = catalogRepo.search(q),
                minifigItems = catalogRepo.searchMinifigs(q), minifigThemeDetail = null, minifigPage = 1,
            )
        }
    }

    // ---- Minifig mode (toggled by the FAB) ----

    /** Flip the Search tab between browsing sets and minifigs. */
    fun onToggleMode() {
        val next = if (_uiState.value.mode == SearchMode.SETS) SearchMode.MINIFIGS else SearchMode.SETS
        _uiState.update {
            it.copy(mode = next, query = "", submittedQuery = null, suggestions = emptyList(), minifigThemeDetail = null)
        }
        if (next == SearchMode.MINIFIGS && minifigs.isEmpty()) loadMinifigs()
    }

    private fun loadMinifigs() {
        _uiState.update { it.copy(minifigsLoading = true) }
        viewModelScope.launch {
            catalogRepo.refreshMinifigs()
            minifigs = catalogRepo.allMinifigs()
            _uiState.update { it.copy(minifigThemes = buildMinifigThemes(), minifigsLoading = false) }
        }
    }

    /** Tapping a minifig theme card → that theme's figs. */
    fun onMinifigThemeClick(theme: String) {
        _uiState.update {
            it.copy(
                minifigThemeDetail = theme,
                minifigItems = minifigs.filter { m -> theme in m.themes }.sortedBy { m -> m.name },
                minifigPage = 1,
            )
        }
    }

    fun onMinifigThemeBack() {
        _uiState.update { it.copy(minifigThemeDetail = null, minifigItems = emptyList()) }
    }

    fun onMinifigPageChange(page: Int) {
        _uiState.update { it.copy(minifigPage = page) }
    }

    // Same ThemeGroup shape as the set browser (so ThemeCard is reused). count = number of minifigs in
    // the theme; subthemes left empty for now (minifigs don't carry subtheme data yet).
    /** Open the shared Add sheet for a minifig (represented as a fig-num "CatalogSet"). */
    fun onAddMinifigClick(fig: Minifig) {
        _uiState.update { it.copy(addTarget = minifigAsCatalogSet(fig)) }
    }

    fun onAddMinifigToWishlist(fig: Minifig) {
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

    /** A minifig as a fig-num-keyed [CatalogSet] so it flows through the shared Add sheet + collection. */
    private fun minifigAsCatalogSet(fig: Minifig) = CatalogSet(
        setNumber = fig.figNum, name = fig.name, itemType = ItemType.MINIFIG,
        theme = fig.themes.firstOrNull() ?: "", releaseYear = 0, releaseMonth = 0,
        pieces = fig.numParts, minifigs = 0, retailPrice = null,
        status = Availability.AVAILABLE, imageUrl = fig.imageUrl,
    )

    /** Tapping a minifig subtheme link → that theme's figs filtered to the subtheme. */
    fun onMinifigSubthemeClick(theme: String, subtheme: String) {
        _uiState.update {
            it.copy(
                minifigThemeDetail = theme,
                minifigItems = minifigs.filter { m -> (theme to subtheme) in m.themeSubthemes }.sortedBy { m -> m.name },
                minifigPage = 1,
            )
        }
    }

    // Same ThemeGroup shape as the set browser (so ThemeCard + its subtheme links are reused).
    // count = minifigs in the theme; subthemes = the sets' subthemes (with per-subtheme fig counts).
    private fun buildMinifigThemes(): List<ThemeGroup> {
        val byTheme = minifigs.flatMap { fig -> fig.themes.map { it to fig } }.groupBy({ it.first }, { it.second })
        return byTheme.map { (theme, figs) ->
            val subs = figs.flatMap { f -> f.themeSubthemes.filter { it.first == theme }.map { it.second } }
                .groupingBy { it }.eachCount()
                .map { (name, count) -> SubthemeCount(name, count) }
                .sortedBy { it.name }
            ThemeGroup(theme, figs.size, themeLogo(theme), subs)
        }
    }

    /** Tapping a theme card opens the theme-detail list (all subthemes). */
    fun onThemeClick(theme: String) = openThemeDetail(theme, ALL_SUBTHEMES)

    /** Tapping a subtheme link opens the theme-detail list filtered to that subtheme. */
    fun onSubthemeClick(theme: String, subtheme: String) = openThemeDetail(theme, subtheme)

    private fun openThemeDetail(theme: String, sub: String) {
        _uiState.update {
            it.copy(
                themeDetail = theme,
                themeDetailSub = sub,
                themeDetailSort = ThemeDetailSort.NEWEST,
                themeDetailSubOptions = subthemesFor(theme),
                themeDetailResults = themeDetailResults(theme, sub, ThemeDetailSort.NEWEST),
                themeDetailPage = 1,
            )
        }
    }

    fun onThemeDetailSubChange(sub: String) {
        _uiState.update {
            val theme = it.themeDetail ?: return@update it
            it.copy(
                themeDetailSub = sub,
                themeDetailResults = themeDetailResults(theme, sub, it.themeDetailSort),
                themeDetailPage = 1,
            )
        }
    }

    fun onThemeDetailSortChange(sort: ThemeDetailSort) {
        _uiState.update {
            val theme = it.themeDetail ?: return@update it
            it.copy(
                themeDetailSort = sort,
                themeDetailResults = themeDetailResults(theme, it.themeDetailSub, sort),
                themeDetailPage = 1,
            )
        }
    }

    fun onThemeDetailPageChange(page: Int) {
        _uiState.update { it.copy(themeDetailPage = page) }
    }

    fun onThemeDetailBack() {
        _uiState.update {
            it.copy(themeDetail = null, themeDetailResults = emptyList(), themeDetailSubOptions = emptyList())
        }
    }

    private fun themeDetailResults(theme: String, sub: String, sort: ThemeDetailSort): List<CatalogSet> {
        val filtered = catalog.filter {
            it.theme.equals(theme, ignoreCase = true) && (sub == ALL_SUBTHEMES || it.subtheme == sub)
        }
        return when (sort) {
            ThemeDetailSort.NEWEST -> filtered.sortedByDescending { it.releaseYear * 100 + it.releaseMonth }
            ThemeDetailSort.OLDEST -> filtered.sortedBy { it.releaseYear * 100 + it.releaseMonth }
            ThemeDetailSort.PRICE_HIGH -> filtered.sortedByDescending { it.retailPrice ?: 0L }
            ThemeDetailSort.PRICE_LOW -> filtered.sortedBy { it.retailPrice ?: 0L }
            ThemeDetailSort.NAME -> filtered.sortedBy { it.name }
        }
    }

    private fun subthemesFor(theme: String): List<SubthemeCount> =
        catalog.filter { it.theme.equals(theme, ignoreCase = true) }
            .groupingBy { it.subtheme }.eachCount()
            .map { (name, count) -> SubthemeCount(name, count) }
            .sortedBy { it.name }

    fun onClearSearch() {
        _uiState.update {
            it.copy(query = "", submittedQuery = null, results = emptyList(), suggestions = emptyList(), minifigItems = emptyList())
        }
    }

    /**
     * Return the tab to its default browse view (search bar + theme list). Called when the user
     * re-enters the Search tab from another tab, so a previous search/theme-detail doesn't linger.
     * Keeps loaded catalog data (themes, favorites, wishlist state).
     */
    fun resetToDefault() {
        _uiState.update {
            it.copy(
                query = "",
                submittedQuery = null,
                results = emptyList(),
                suggestions = emptyList(),
                themeDetail = null,
                themeDetailSub = ALL_SUBTHEMES,
                themeDetailResults = emptyList(),
                themeDetailSubOptions = emptyList(),
                // Minifig browse home too (keep the current mode).
                minifigThemeDetail = null,
                minifigItems = emptyList(),
            )
        }
    }

    fun onThemeSortChange(sort: ThemeSort) {
        _uiState.update { it.copy(themeSort = sort) }
    }

    fun onToggleFavorite(theme: String) {
        _uiState.update {
            val next = if (theme in it.favoriteThemes) it.favoriteThemes - theme else it.favoriteThemes + theme
            it.copy(favoriteThemes = next)
        }
    }

    fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.search(query)

    fun searchMinifigs(query: String): List<Minifig> = catalogRepo.searchMinifigs(query)

    // ---- Add to wishlist ----

    fun onAddToWishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs,
                retailPrice = set.retailPrice ?: 0L, status = set.status,
                imageUrl = set.thumbnailUrl ?: set.imageUrl,
            ),
        )
        _uiState.update { it.copy(toastMessage = UiText.Res(R.string.toast_added_wishlist, listOf(set.name))) }
    }

    // ---- Add to collection (shared sheet) ----

    fun onAddToCollectionClick(set: CatalogSet) {
        _uiState.update { it.copy(addTarget = set) }
    }

    fun onDismissAdd() {
        _uiState.update { it.copy(addTarget = null) }
    }

    fun onAddToCollectionSubmit(item: CollectionItem) {
        repository.addItem(item)
        _uiState.update { it.copy(addTarget = null, toastMessage = UiText.Res(R.string.toast_added_collection, listOf(item.name))) }
    }

    /** Add sheet in Sales mode: records a standalone sale (does not add to the collection). */
    fun onAddToSalesSubmit(item: CollectionItem, salePrice: Long) {
        repository.addSale(item, salePrice)
        _uiState.update { it.copy(addTarget = null, toastMessage = UiText.Res(R.string.toast_added_sales, listOf(item.name))) }
    }

    fun onToastShown() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun buildThemes(): List<ThemeGroup> =
        catalog.groupBy { it.theme }
            .map { (theme, sets) ->
                val subs = sets.groupingBy { it.subtheme }.eachCount()
                    .map { (name, count) -> SubthemeCount(name, count) }
                    .sortedBy { it.name }
                ThemeGroup(theme, sets.size, themeLogo(theme), subs)
            }
            .sortedBy { it.theme }

    private fun themeLogo(theme: String): String? = when (theme.lowercase()) {
        "architecture" -> "file:///android_asset/themelogo-architecture.png"
        "batman" -> "file:///android_asset/themelogo-batman.png"
        else -> null
    }
}
