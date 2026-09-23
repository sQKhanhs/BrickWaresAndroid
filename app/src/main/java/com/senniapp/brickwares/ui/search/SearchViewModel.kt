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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

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

    // The open theme's sets / minifigs, fetched on demand (Decision 16). Subtheme filter / sort /
    // pagination run over these bounded lists.
    private var themeSets: List<CatalogSet> = emptyList()
    private var minifigThemeItems: List<Minifig> = emptyList()
    private var suggestJob: Job? = null
    private var searchJob: Job? = null
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
        // Theme browse + New Sets come from server-side count queries (Decision 16), not a full
        // in-memory catalog. Loaded once here and re-run by [retry].
        loadBrowse()
        // Keep the browse marks in sync with the stored favorites. Beyond an in-VM toggle, this fires
        // when an account switch / deletion wipes them (ThemeFavoritesPrefs.clear), so the previous
        // account's stars disappear on a live Search screen without waiting for a restart.
        viewModelScope.launch {
            ThemeFavoritesPrefs.setThemesFlow.collect { favs -> _uiState.update { it.copy(favoriteThemes = favs) } }
        }
        viewModelScope.launch {
            ThemeFavoritesPrefs.minifigThemesFlow.collect { favs -> _uiState.update { it.copy(favoriteMinifigThemes = favs) } }
        }
        // Load the minifig theme browse up front too — the search bar is GLOBAL (searches sets AND
        // minifigs regardless of the browse mode), so its counts must be ready before minifig mode.
        loadMinifigBrowse()
        // Observe the wishlist so result cards can show a "Wishlisted" state.
        viewModelScope.launch {
            repository.getWishlistItems().collect { items ->
                _uiState.update { it.copy(wishlistedNumbers = items.map { w -> w.variantKey }.toSet()) }
            }
        }
        // Observe the collection so result cards for owned sets show "See Detail" instead of add/wishlist.
        viewModelScope.launch {
            repository.getCollectionItems().collect { items ->
                _uiState.update { it.copy(ownedNumbers = items.map { c -> c.variantKey }.toSet()) }
            }
        }
        // Observe sales too, so a set the user has sold also shows "See Detail" (opens the detail page,
        // which surfaces the sale in the merged modal).
        viewModelScope.launch {
            repository.getSoldItems().collect { items ->
                _uiState.update { it.copy(soldNumbers = items.map { s -> s.variantKey }.toSet()) }
            }
        }
    }

    /** Retry after a browse-load failure (the error fallback's Retry button). */
    fun retry() {
        loadBrowse()
        loadMinifigBrowse()
    }

    /**
     * Load the theme browse (theme + subtheme counts) and the New Sets grouping via server-side
     * queries — no full catalog in memory (Decision 16). Sets [SearchUiState.loadError] on failure so
     * the error/retry fallback shows.
     */
    private fun loadBrowse() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val counts = catalogRepo.themeCounts()
                val subsByTheme = catalogRepo.subthemeCounts().groupBy { it.theme }
                val themes = counts.map { c ->
                    ThemeGroup(
                        theme = c.theme,
                        setCount = c.setCount,
                        logoAsset = themeLogo(c.theme),
                        subthemes = subsByTheme[c.theme].orEmpty()
                            .map { SubthemeCount(it.subtheme, it.setCount) }
                            .sortedBy { it.name },
                    )
                }.sortedBy { it.theme }
                val candidates = catalogRepo.newSetCandidates()
                _uiState.update {
                    it.copy(
                        themes = themes,
                        newSetsByTheme = NewSets.groupedByTheme(candidates),
                        isLoading = false,
                        loadError = false,
                    ).withReorderedThemes()
                }
                // Warm the theme icons into Coil's disk cache so the browse draws fully on first open.
                ImagePrefetcher.warm(themes.mapNotNull { it.logoAsset })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("SearchVM").w(e, "theme browse load failed")
                _uiState.update { it.copy(isLoading = false, loadError = true) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, submittedQuery = null) }
        // Editing the query retires the submitted search — cancel its fetch so a late response can't
        // land on whatever view is open by then (a theme page, the browse).
        searchJob?.cancel()
        // Set suggestions are a DB query now (Decision 16), so debounce per keystroke; minifig
        // suggestions still come from the in-memory minifig cache.
        suggestJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList(), minifigSuggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val sets = runCatching { catalogRepo.searchSets(query, limit = SUGGESTION_LIMIT) }.getOrDefault(emptyList())
            val figs = runCatching { catalogRepo.fetchMinifigsMatching(query, limit = SUGGESTION_LIMIT) }.getOrDefault(emptyList())
            // runCatching above also swallows the CancellationException from the next keystroke's
            // suggestJob.cancel(), so bail before overwriting the newer query's suggestions with this
            // (now stale / empty) result — otherwise "No matches" flashes for a debounce + round-trip each key.
            ensureActive()
            _uiState.update { it.copy(suggestions = sets, minifigSuggestions = figs) }
        }
    }

    /** Global search — returns matching sets AND minifigs, independent of the browse mode. */
    fun onSubmit() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        // Cancel any in-flight search so a slow earlier one can't land after — and overwrite — this one;
        // a pending suggestion fetch is moot once a search is submitted.
        searchJob?.cancel()
        suggestJob?.cancel()
        // Clear the previous results and show a spinner up front, so the stale "too many" / "no results"
        // text can't linger during the round-trip; searchError is reset for this fresh attempt.
        _uiState.update {
            it.copy(
                submittedQuery = q, suggestions = emptyList(), minifigSuggestions = emptyList(),
                results = emptyList(), minifigItems = emptyList(),
                minifigThemeDetail = null, minifigPage = 1,
                searchLoading = true, searchError = false,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val sets = catalogRepo.searchSets(q, limit = SEARCH_LIMIT)
                val figs = catalogRepo.fetchMinifigsMatching(q, limit = SEARCH_LIMIT)
                _uiState.update {
                    // Belt and braces with the cancels above: never adopt a response for a query that is
                    // no longer the submitted one (cleared, reset, or replaced meanwhile) — it would
                    // overwrite an open theme's list with the search's minifigs.
                    if (it.submittedQuery != q) it
                    else it.copy(results = sets, minifigItems = figs, searchLoading = false, searchError = false)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // superseded by a newer submit — unwind without touching state
            } catch (e: Exception) {
                // Offline / server error: surface it as an error state (with retry) instead of letting the
                // empty result read as a genuine "no sets found".
                Timber.tag("SearchVM").w(e, "search failed for %s", q)
                _uiState.update { it.copy(searchLoading = false, searchError = true) }
            }
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
    }

    /** Minifig theme browse (theme + subtheme counts) via DB views — no full minifig list in memory. */
    private fun loadMinifigBrowse() {
        _uiState.update { it.copy(minifigsLoading = true, minifigLoadError = false) }
        viewModelScope.launch {
            try {
                val counts = catalogRepo.minifigThemeCounts()
                val subsByTheme = catalogRepo.minifigSubthemeCounts().groupBy { it.theme }
                val themes = counts.map { c ->
                    ThemeGroup(
                        theme = c.theme,
                        setCount = c.setCount,
                        logoAsset = themeLogo(c.theme),
                        subthemes = subsByTheme[c.theme].orEmpty()
                            .map { SubthemeCount(it.subtheme, it.setCount) }
                            .sortedBy { it.name },
                    )
                }.sortedBy { it.theme }
                _uiState.update { it.copy(minifigThemes = themes, minifigsLoading = false, minifigLoadError = false).withReorderedThemes() }
                ImagePrefetcher.warm(themes.mapNotNull { it.logoAsset })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Surface the error+retry instead of leaving the empty grid read as "No minifigs in the
                // catalog yet" for the rest of the session (the retry re-runs both browses).
                Timber.tag("SearchVM").w(e, "minifig browse load failed")
                _uiState.update { it.copy(minifigsLoading = false, minifigLoadError = true) }
            }
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
        // Show the shell, then fetch this theme's figs (bounded); subtheme/sort/pagination run in memory.
        _uiState.update {
            it.copy(
                minifigThemeDetail = theme,
                minifigThemeDetailSub = sub,
                minifigThemeDetailSort = MinifigSort.NAME,
                minifigThemeDetailSubOptions = emptyList(),
                minifigThemeDetailLoading = true,
                minifigThemeDetailError = false,
                minifigItems = emptyList(),
                minifigPage = 1,
            )
        }
        loadMinifigThemeDetail(theme)
    }

    /** Retry the open minifig theme's fig fetch after an error — from the theme-detail ErrorScreen. */
    fun onMinifigThemeDetailRetry() {
        val theme = _uiState.value.minifigThemeDetail ?: return
        _uiState.update { it.copy(minifigThemeDetailLoading = true, minifigThemeDetailError = false) }
        loadMinifigThemeDetail(theme)
    }

    private fun loadMinifigThemeDetail(theme: String) {
        viewModelScope.launch {
            try {
                val fetched = catalogRepo.minifigsInTheme(theme)
                _uiState.update {
                    // Adopt the backing list only AFTER the stale-result guard (see loadThemeDetail): a slow
                    // fetch for a since-abandoned theme must not overwrite [minifigThemeItems], or the open
                    // theme's sort/subtheme filtering would show the old theme's figs.
                    if (it.minifigThemeDetail != theme) return@update it
                    minifigThemeItems = fetched
                    it.copy(
                        minifigThemeDetailLoading = false,
                        minifigThemeDetailError = false,
                        minifigThemeDetailSubOptions = minifigSubthemesFromItems(fetched, theme),
                        minifigItems = minifigThemeResults(theme, it.minifigThemeDetailSub, it.minifigThemeDetailSort),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // superseded by another theme open — unwind without touching state
            } catch (e: Exception) {
                Timber.tag("SearchVM").w(e, "minifigsInTheme failed for %s", theme)
                _uiState.update {
                    if (it.minifigThemeDetail != theme) return@update it
                    it.copy(minifigThemeDetailLoading = false, minifigThemeDetailError = true)
                }
            }
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
        // Filter the OPEN theme's fetched figs (already scoped to this theme by the query).
        val filtered = minifigThemeItems.filter { m -> sub == ALL_SUBTHEMES || (theme to sub) in m.themeSubthemes }
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

    private fun minifigSubthemesFromItems(items: List<Minifig>, theme: String): List<SubthemeCount> =
        items.flatMap { f -> f.themeSubthemes.filter { it.first == theme }.map { it.second } }
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
        // Show the theme-detail shell immediately (loading), then fetch just this theme's sets
        // (bounded) — subtheme filter / sort / pagination run over that list in memory (Decision 16).
        _uiState.update {
            it.copy(
                // Clear any in-progress search so the theme-filtered list is what shows (and Back from it
                // returns to the browse, not the leftover suggestions).
                query = "", submittedQuery = null, suggestions = emptyList(), minifigSuggestions = emptyList(),
                themeDetail = theme,
                themeDetailSub = sub,
                themeDetailSort = ThemeDetailSort.NEWEST,
                themeDetailLoading = true,
                themeDetailError = false,
                themeDetailSubOptions = emptyList(),
                themeDetailResults = emptyList(),
                themeDetailPage = 1,
            )
        }
        loadThemeDetail(theme)
    }

    /** Retry the open theme's set fetch after an error (e.g. offline) — from the theme-detail ErrorScreen. */
    fun onThemeDetailRetry() {
        val theme = _uiState.value.themeDetail ?: return
        _uiState.update { it.copy(themeDetailLoading = true, themeDetailError = false) }
        loadThemeDetail(theme)
    }

    private fun loadThemeDetail(theme: String) {
        viewModelScope.launch {
            try {
                val fetched = catalogRepo.setsInTheme(theme)
                _uiState.update {
                    // Ignore a stale result if the user has since navigated to another theme / closed it.
                    // Adopt the backing list only AFTER this guard: a slow fetch for a since-abandoned theme
                    // must not overwrite [themeSets], or the open theme's in-memory sort/subtheme filtering
                    // (which reads [themeSets]) would show the old theme's sets.
                    if (it.themeDetail != theme) return@update it
                    themeSets = fetched
                    it.copy(
                        themeDetailLoading = false,
                        themeDetailError = false,
                        themeDetailSubOptions = subthemesFrom(fetched),
                        themeDetailResults = themeDetailResults(it.themeDetailSub, it.themeDetailSort),
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // superseded by another theme open — unwind without touching state
            } catch (e: Exception) {
                // Offline / server error — surface the error+retry instead of an empty "0 sets" list.
                Timber.tag("SearchVM").w(e, "setsInTheme failed for %s", theme)
                _uiState.update {
                    if (it.themeDetail != theme) return@update it
                    it.copy(themeDetailLoading = false, themeDetailError = true)
                }
            }
        }
    }

    fun onThemeDetailSubChange(sub: String) {
        _uiState.update {
            if (it.themeDetail == null) return@update it
            it.copy(
                themeDetailSub = sub,
                themeDetailResults = themeDetailResults(sub, it.themeDetailSort),
                themeDetailPage = 1,
            )
        }
    }

    fun onThemeDetailSortChange(sort: ThemeDetailSort) {
        _uiState.update {
            if (it.themeDetail == null) return@update it
            it.copy(
                themeDetailSort = sort,
                themeDetailResults = themeDetailResults(it.themeDetailSub, sort),
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

    /** Filter + sort the OPEN theme's fetched sets ([themeSets]) — no full-catalog scan (Decision 16). */
    private fun themeDetailResults(sub: String, sort: ThemeDetailSort): List<CatalogSet> {
        val filtered = themeSets.filter { sub == ALL_SUBTHEMES || it.subtheme == sub }
        return when (sort) {
            ThemeDetailSort.NEWEST -> filtered.sortedByDescending { it.releaseYear * 100 + it.releaseMonth }
            ThemeDetailSort.OLDEST -> filtered.sortedBy { it.releaseYear * 100 + it.releaseMonth }
            ThemeDetailSort.PRICE_HIGH -> filtered.sortedByDescending { it.retailPrice ?: 0L }
            ThemeDetailSort.PRICE_LOW -> filtered.sortedBy { it.retailPrice ?: 0L }
            ThemeDetailSort.NAME -> filtered.sortedBy { it.name }
        }
    }

    private fun subthemesFrom(sets: List<CatalogSet>): List<SubthemeCount> =
        sets.groupingBy { it.subtheme }.eachCount()
            .map { (name, count) -> SubthemeCount(name, count) }
            .sortedBy { it.name }

    fun onClearSearch() {
        // Retire both in-flight fetches: a late search response must not land on the next view, and a
        // late suggestion fetch would show stale rows under the next query.
        searchJob?.cancel()
        suggestJob?.cancel()
        _uiState.update {
            it.copy(
                query = "", submittedQuery = null, results = emptyList(),
                suggestions = emptyList(), minifigSuggestions = emptyList(), minifigItems = emptyList(),
                searchLoading = false, searchError = false,
            )
        }
    }

    /**
     * Return the tab to its default browse view (search bar + theme list). Called when the user
     * re-enters the Search tab from another tab, so a previous search/theme-detail doesn't linger.
     * Keeps loaded catalog data (themes, favorites, wishlist state).
     */
    fun resetToDefault() {
        searchJob?.cancel()
        suggestJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                submittedQuery = null,
                results = emptyList(),
                searchLoading = false,
                searchError = false,
                suggestions = emptyList(),
                minifigSuggestions = emptyList(),
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

    // Add-sheet / quick-search suggestions — DB queries now (Decision 16); the composables debounce them.
    suspend fun searchCatalog(query: String): List<CatalogSet> = catalogRepo.searchSets(query)

    suspend fun searchMinifigs(query: String): List<Minifig> = catalogRepo.fetchMinifigsMatching(query)

    // ---- Add to wishlist ----

    fun onAddToWishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs, setId = set.setId,
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

    /** Hand-curated theme icon in R2, by a deterministic slug of the name (see [CatalogImages.themeIconUrl]). */
    private fun themeLogo(theme: String): String = CatalogImages.themeIconUrl(theme)

    private companion object {
        /** Max live suggestions per kind (sets, minifigs) in the typing dropdown. */
        const val SUGGESTION_LIMIT = 6

        /** Debounce (ms) before firing the set-suggestion DB query on each keystroke. */
        const val SUGGEST_DEBOUNCE_MS = 250L

        /** Cap on submitted-search results (the old in-memory search was uncapped; a cap keeps the DB
         *  query + result render bounded for broad terms). */
        const val SEARCH_LIMIT = 100
    }
}
