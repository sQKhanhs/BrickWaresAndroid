package com.senniapp.brickwares.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.WishlistItem
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(themes = buildThemes()))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
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
                suggestions = if (query.isBlank()) emptyList() else repository.searchCatalog(query).take(6),
            )
        }
    }

    fun onSubmit() {
        val q = _uiState.value.query.trim()
        if (q.isBlank()) return
        _uiState.update {
            it.copy(submittedQuery = q, results = repository.searchCatalog(q), suggestions = emptyList())
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

    /** Tapping a theme card shows all of that theme's sets. */
    fun onThemeClick(theme: String) {
        val results = repository.getCatalog().filter { it.theme.equals(theme, ignoreCase = true) }
        _uiState.update { it.copy(query = theme, submittedQuery = theme, results = results, suggestions = emptyList()) }
    }

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

    fun searchCatalog(query: String): List<CatalogSet> = repository.searchCatalog(query)

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
        repository.getCatalog()
            .groupBy { it.theme }
            .map { (theme, sets) -> ThemeGroup(theme, sets.size, themeLogo(theme)) }
            .sortedWith(compareByDescending<ThemeGroup> { it.setCount }.thenBy { it.theme })

    private fun themeLogo(theme: String): String? = when (theme.lowercase()) {
        "architecture" -> "file:///android_asset/themelogo-architecture.png"
        "batman" -> "file:///android_asset/themelogo-batman.png"
        else -> null
    }
}
