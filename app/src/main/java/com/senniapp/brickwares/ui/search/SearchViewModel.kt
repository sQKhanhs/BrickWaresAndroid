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
import com.senniapp.brickwares.data.repository.ValueRepositoryProvider
import com.senniapp.brickwares.data.local.ThemeFavoritesPrefs
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.util.ImagePrefetcher
import com.senniapp.brickwares.util.NewSets
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
    private val _uiState = MutableStateFlow(
        // Seed favorites from disk so bookmarked themes survive an app restart.
        SearchUiState(
            isLoading = true,
            favoriteThemes = ThemeFavoritesPrefs.setThemes,
            favoriteMinifigThemes = ThemeFavoritesPrefs.minifigThemes,
        ),
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Rebuild the browser whenever the catalog (re)loads (revision bumps on every successful load)
        // — covers the first load, the error-fallback Retry, and a reconnect satisfied by any
        // component's refresh, so the themes always repopulate.
        viewModelScope.launch {
            catalogRepo.revision.collect {
                catalog = catalogRepo.all()
                if (catalog.isNotEmpty()) {
                    val themes = buildThemes()
                    _uiState.update {
                        it.copy(
                            themes = themes,
                            newSetsByTheme = NewSets.groupedByTheme(catalog),
                            isLoading = false,
                        ).withReorderedThemes()
                    }
                    // Warm every theme icon into Coil's disk cache now, so the theme browse (both view
                    // modes) draws fully on first open instead of trickling in one icon per round trip.
                    ImagePrefetcher.warm(themes.mapNotNull { it.logoAsset })
                }
            }
        }
        viewModelScope.launch { catalogRepo.refresh() }
        // Keep the browse marks in sync with the stored favorites. Beyond an in-VM toggle, this fires
        // when an account switch / deletion wipes them (ThemeFavoritesPrefs.clear), so the previous
        // account's stars disappear on a live Search screen without waiting for a restart.
        viewModelScope.launch {
            ThemeFavoritesPrefs.setThemesFlow.collect { favs -> _uiState.update { it.copy(favoriteThemes = favs) } }
        }
        viewModelScope.launch {
            ThemeFavoritesPrefs.minifigThemesFlow.collect { favs -> _uiState.update { it.copy(favoriteMinifigThemes = favs) } }
        }
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
        // Observe sales too, so a set the user has sold also shows "See Detail" (opens the detail page,
        // which surfaces the sale in the merged modal).
        viewModelScope.launch {
            repository.getSoldItems().collect { items ->
                _uiState.update { it.copy(soldNumbers = items.map { s -> s.setNumber }.toSet()) }
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
                // Live suggestions in the dropdown: sets first, then minifigs (matched by name or fig
                // code, e.g. "fig-017485"). The FAB search modal already lists both the same way.
                suggestions = if (query.isBlank()) emptyList() else catalogRepo.search(query, limit = SUGGESTION_LIMIT),
                minifigSuggestions = if (query.isBlank()) emptyList() else catalogRepo.searchMinifigs(query).take(SUGGESTION_LIMIT),
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

    /**
     * Enter the minifig browse home directly (used when switching to minifig search from outside the
     * Search tab, e.g. a Set Detail): reset to the default browse view and force minifig mode.
     */
    fun showMinifigs() {
        resetToDefault()
        _uiState.update { it.copy(mode = SearchMode.MINIFIGS) }
        if (minifigs.isEmpty()) loadMinifigs()
    }

    /** Enter the set browse home directly (used when switching to set search from a Minifig Detail). */
    fun showSets() {
        resetToDefault()
        _uiState.update { it.copy(mode = SearchMode.SETS) }
    }

    /** Flip the Search tab between browsing sets and minifigs. */
    fun onToggleMode() {
        val next = if (_uiState.value.mode == SearchMode.SETS) SearchMode.MINIFIGS else SearchMode.SETS
        // Return to the browse home of the target mode — clear any open theme-detail list / results /
        // query — so toggling from inside a theme's item list lands on the main page, not a stale list.
        resetToDefault()
        _uiState.update { it.copy(mode = next) }
        if (next == SearchMode.MINIFIGS && minifigs.isEmpty()) loadMinifigs()
    }

    private fun loadMinifigs() {
        _uiState.update { it.copy(minifigsLoading = true) }
        viewModelScope.launch {
            catalogRepo.refreshMinifigs()
            minifigs = catalogRepo.allMinifigs()
            _uiState.update { it.copy(minifigThemes = buildMinifigThemes(), minifigsLoading = false).withReorderedThemes() }
        }
    }

    /** Tapping a minifig theme card → that theme's figs (all subthemes). */
    fun onMinifigThemeClick(theme: String) = openMinifigThemeDetail(theme, ALL_SUBTHEMES)

    fun onMinifigThemeBack() {
        _uiState.update {
            it.copy(
                minifigThemeDetail = null,
                minifigItems = emptyList(),
                minifigThemeDetailSub = ALL_SUBTHEMES,
                minifigThemeDetailSort = MinifigSort.NAME,
                minifigThemeDetailSubOptions = emptyList(),
            )
        }
    }

    fun onMinifigPageChange(page: Int) {
        _uiState.update { it.copy(minifigPage = page) }
    }

    private fun openMinifigThemeDetail(theme: String, sub: String) {
        _uiState.update {
            it.copy(
                minifigThemeDetail = theme,
                minifigThemeDetailSub = sub,
                minifigThemeDetailSort = MinifigSort.NAME,
                minifigThemeDetailSubOptions = minifigSubthemesFor(theme),
                minifigItems = minifigThemeResults(theme, sub, MinifigSort.NAME),
                minifigPage = 1,
            )
        }
    }

    fun onMinifigThemeDetailSubChange(sub: String) {
        _uiState.update {
            val theme = it.minifigThemeDetail ?: return@update it
            it.copy(
                minifigThemeDetailSub = sub,
                minifigItems = minifigThemeResults(theme, sub, it.minifigThemeDetailSort),
                minifigPage = 1,
            )
        }
    }

    fun onMinifigThemeDetailSortChange(sort: MinifigSort) {
        _uiState.update {
            val theme = it.minifigThemeDetail ?: return@update it
            it.copy(
                minifigThemeDetailSort = sort,
                minifigItems = minifigThemeResults(theme, it.minifigThemeDetailSub, sort),
                minifigPage = 1,
            )
        }
    }

    private fun minifigThemeResults(theme: String, sub: String, sort: MinifigSort): List<Minifig> {
        val filtered = minifigs.filter { m ->
            if (sub == ALL_SUBTHEMES) theme in m.themes else (theme to sub) in m.themeSubthemes
        }
        return when (sort) {
            MinifigSort.NAME -> filtered.sortedBy { it.name }
            // Minifigs have no retail price, so "value" sorts use the community current value.
            MinifigSort.VALUE_HIGH -> filtered.sortedWith(compareByDescending<Minifig> { figValue(it.figNum) }.thenBy { it.name })
            MinifigSort.VALUE_LOW -> filtered.sortedWith(compareBy<Minifig> { figValue(it.figNum) }.thenBy { it.name })
            MinifigSort.MOST_SETS -> filtered.sortedWith(compareByDescending<Minifig> { it.setCount }.thenBy { it.name })
        }
    }

    /** Community current value for a fig (₫); 0 when none. Snapshot at sort time (the value cache
     *  warms asynchronously — re-selecting the sort re-reads it). */
    private fun figValue(figNum: String): Long =
        ValueRepositoryProvider.instance.valueForFig(figNum)?.amountUsdCents ?: 0L

    private fun minifigSubthemesFor(theme: String): List<SubthemeCount> =
        minifigs.filter { theme in it.themes }
            .flatMap { f -> f.themeSubthemes.filter { it.first == theme }.map { it.second } }
            .groupingBy { it }.eachCount()
            .map { (name, count) -> SubthemeCount(name, count) }
            .sortedBy { it.name }

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
    fun onMinifigSubthemeClick(theme: String, subtheme: String) = openMinifigThemeDetail(theme, subtheme)

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

    /**
     * Open a set theme's detail from outside the browse (a Set Detail's theme/subtheme link). Forces set
     * mode so Back lands on the set browse, not a stale minifig one. [openThemeDetail] clears any typed
     * query, so the filtered list shows instead of the leftover live suggestions.
     */
    fun openSetTheme(theme: String, subtheme: String?) {
        _uiState.update { it.copy(mode = SearchMode.SETS) }
        if (subtheme.isNullOrBlank() || subtheme == theme) onThemeClick(theme)
        else onSubthemeClick(theme, subtheme)
    }

    private fun openThemeDetail(theme: String, sub: String) {
        _uiState.update {
            it.copy(
                // Clear any in-progress search so the theme-filtered list is what shows (and Back from it
                // returns to the browse, not the leftover suggestions).
                query = "", submittedQuery = null, suggestions = emptyList(), minifigSuggestions = emptyList(),
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
                // Re-entering Search lands on page 1 (with favorites pinned on top).
                themePage = 1,
                minifigThemePage = 1,
                // Signal the screen to scroll the browse list back to the top.
                homeScrollTick = it.homeScrollTick + 1,
            ).withReorderedThemes() // re-pin favorites now that we're back on the main browse page
        }
    }

    /**
     * Called when the user (re)enters the Search tab from the nav bar. Beyond [resetToDefault]'s
     * clearing of any search/theme-detail, this also restores the browse DEFAULTS — set mode,
     * alphabetical sort, detail view — so a Favorites filter (or a mode/view switch) from a previous
     * visit doesn't linger. (Mode-swap / cross-nav paths call [resetToDefault] directly and keep those.)
     */
    fun onEnterSearchTab() {
        _uiState.update {
            it.copy(mode = SearchMode.SETS, themeSort = ThemeSort.ALPHABETICAL, themeViewMode = ThemeViewMode.DETAIL)
        }
        resetToDefault()
    }

    /** Freeze the current desired theme order into the paginated browse snapshot (favorites pinned).
     *  Called on browse (re)entry — never on a favorite toggle, so bookmarking doesn't reorder live. */
    private fun SearchUiState.withReorderedThemes(): SearchUiState =
        copy(orderedThemes = sortedThemes, orderedMinifigThemes = sortedMinifigThemes)

    fun onThemeSortChange(sort: ThemeSort) {
        // Reset to page 1 (the Favorites filter changes the list length) and re-freeze the order.
        _uiState.update { it.copy(themeSort = sort, themePage = 1, minifigThemePage = 1).withReorderedThemes() }
    }

    /** Toggle the theme browse between detail cards and the compact 2-column list (favorites shared). */
    fun onThemeViewModeChange(mode: ThemeViewMode) {
        _uiState.update {
            // List mode shows no set count, so its sort menu drops "Amount of sets" — if it was
            // selected, fall back to Alphabetical.
            val sort = if (mode == ThemeViewMode.LIST && it.themeSort == ThemeSort.COUNT) ThemeSort.ALPHABETICAL else it.themeSort
            it.copy(themeViewMode = mode, themeSort = sort, themePage = 1, minifigThemePage = 1).withReorderedThemes()
        }
    }

    fun onThemePageChange(page: Int) = _uiState.update { it.copy(themePage = page) }
    fun onMinifigThemePageChange(page: Int) = _uiState.update { it.copy(minifigThemePage = page) }

    fun onToggleFavorite(theme: String) {
        val cur = _uiState.value.favoriteThemes
        val next = if (theme in cur) cur - theme else cur + theme
        ThemeFavoritesPrefs.setThemes = next // persist across restarts
        _uiState.update { it.copy(favoriteThemes = next) }
    }

    /** Favorite toggle for the MINIFIG theme browse — independent of the set-theme favorites. */
    fun onToggleMinifigFavorite(theme: String) {
        val cur = _uiState.value.favoriteMinifigThemes
        val next = if (theme in cur) cur - theme else cur + theme
        ThemeFavoritesPrefs.minifigThemes = next
        _uiState.update { it.copy(favoriteMinifigThemes = next) }
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

    /** Hand-curated theme icon in R2, by a deterministic slug of the name (see [CatalogImages.themeIconUrl]). */
    private fun themeLogo(theme: String): String = CatalogImages.themeIconUrl(theme)

    private companion object {
        /** Max live suggestions per kind (sets, minifigs) in the typing dropdown. */
        const val SUGGESTION_LIMIT = 6
    }
}
