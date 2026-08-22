package com.senniapp.brickwares.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.data.repository.CatalogRepository
import com.senniapp.brickwares.data.repository.CatalogRepositoryProvider
import com.senniapp.brickwares.data.repository.CollectionRepository
import com.senniapp.brickwares.data.repository.MockCollectionRepository
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
    private val repository: CollectionRepository = MockCollectionRepository(),
    private val catalogRepo: CatalogRepository = CatalogRepositoryProvider.instance,
) : ViewModel() {

    private var catalog: List<CatalogSet> = emptyList()
    private val _uiState = MutableStateFlow(SearchUiState(isLoading = true))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // Load the real catalog from Supabase, then build the theme browser.
        viewModelScope.launch {
            catalogRepo.refresh()
            catalog = catalogRepo.all()
            _uiState.update { it.copy(themes = buildThemes(), isLoading = false) }
        }
        // Observe the wishlist so result cards can show a "Wishlisted" state.
        viewModelScope.launch {
            repository.getWishlistItems().collect { items ->
                _uiState.update { it.copy(wishlistedNumbers = items.map { w -> w.setNumber }.toSet()) }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                submittedQuery = null,
                suggestions = if (query.isBlank()) emptyList() else catalogRepo.search(query).take(6),
            )
        }
    }

    fun onSubmit() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        _uiState.update {
            it.copy(submittedQuery = q, results = catalogRepo.search(q), suggestions = emptyList())
        }
    }

    /** Tapping a suggestion shows that single set as the result. */
    fun onSuggestionClick(set: CatalogSet) {
        _uiState.update {
            it.copy(
                query = "${set.setNumber} ${set.name}",
                submittedQuery = set.name,
                results = listOf(set),
                suggestions = emptyList(),
            )
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
            )
        }
    }

    fun onThemeDetailSubChange(sub: String) {
        _uiState.update {
            val theme = it.themeDetail ?: return@update it
            it.copy(themeDetailSub = sub, themeDetailResults = themeDetailResults(theme, sub, it.themeDetailSort))
        }
    }

    fun onThemeDetailSortChange(sort: ThemeDetailSort) {
        _uiState.update {
            val theme = it.themeDetail ?: return@update it
            it.copy(themeDetailSort = sort, themeDetailResults = themeDetailResults(theme, it.themeDetailSub, sort))
        }
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
            ThemeDetailSort.PRICE_HIGH -> filtered.sortedByDescending { it.retailPrice }
            ThemeDetailSort.PRICE_LOW -> filtered.sortedBy { it.retailPrice }
            ThemeDetailSort.NAME -> filtered.sortedBy { it.name }
        }
    }

    private fun subthemesFor(theme: String): List<SubthemeCount> =
        catalog.filter { it.theme.equals(theme, ignoreCase = true) }
            .groupingBy { it.subtheme }.eachCount()
            .map { (name, count) -> SubthemeCount(name, count) }
            .sortedBy { it.name }

    fun onClearSearch() {
        _uiState.update { it.copy(query = "", submittedQuery = null, results = emptyList(), suggestions = emptyList()) }
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

    // ---- Add to wishlist ----

    fun onAddToWishlist(set: CatalogSet) {
        repository.addToWishlist(
            WishlistItem(
                setNumber = set.setNumber, name = set.name, itemType = set.itemType,
                theme = set.theme, releaseYear = set.releaseYear, releaseMonth = set.releaseMonth,
                pieces = set.pieces, minifigs = set.minifigs,
                retailPrice = set.retailPrice, status = set.status,
            ),
        )
        _uiState.update { it.copy(toastMessage = "${set.name} added to Wishlist") }
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
        _uiState.update { it.copy(addTarget = null, toastMessage = "${item.name} added to Collection") }
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
