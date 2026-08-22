package com.senniapp.brickwares.ui.search

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.ui.components.PAGE_SIZE

/** A subtheme within a theme, with how many catalog sets it has. */
data class SubthemeCount(val name: String, val count: Int)

/** A theme grouping shown in the Search tab's default browse view (logo + name + set count). */
data class ThemeGroup(
    val theme: String,
    val setCount: Int,
    /** `file:///android_asset/...` for the themes that have a logo, else null (placeholder). */
    val logoAsset: String?,
    val subthemes: List<SubthemeCount> = emptyList(),
)

/** Ordering for the theme browser. */
enum class ThemeSort(val label: String) {
    ALPHABETICAL("Alphabetical"),
    COUNT("Amount of sets"),
    FAVORITE("Favorites"),
}

/** Ordering for the sets listed inside a theme-detail view. */
enum class ThemeDetailSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    PRICE_HIGH("Price: high to low"),
    PRICE_LOW("Price: low to high"),
    NAME("Name"),
}

/** Sentinel meaning "all subthemes" in the theme-detail subtheme filter. */
const val ALL_SUBTHEMES = "__all"

/**
 * Immutable UI state for the Search tab. Three display modes derive from [query]/[submittedQuery]:
 * browse (empty query), live suggestions (typing), and results (after a search is submitted).
 * A submitted search returning more than [MAX_RESULTS] shows the "too many results" tips page.
 */
data class SearchUiState(
    /** True until the catalog has loaded from Supabase for the first time. */
    val isLoading: Boolean = false,
    val query: String = "",
    /** The query that produced [results]; null while the user hasn't submitted a search yet. */
    val submittedQuery: String? = null,
    val results: List<CatalogSet> = emptyList(),
    val suggestions: List<CatalogSet> = emptyList(),
    val themes: List<ThemeGroup> = emptyList(),
    val themeSort: ThemeSort = ThemeSort.ALPHABETICAL,
    val favoriteThemes: Set<String> = emptySet(),
    val wishlistedNumbers: Set<String> = emptySet(),
    // Theme-detail view (non-null theme = open, overrides the search/browse views).
    val themeDetail: String? = null,
    val themeDetailSub: String = ALL_SUBTHEMES,
    val themeDetailSort: ThemeDetailSort = ThemeDetailSort.NEWEST,
    val themeDetailResults: List<CatalogSet> = emptyList(),
    val themeDetailSubOptions: List<SubthemeCount> = emptyList(),
    /** 1-based current page for the theme-detail results (numbered pagination). */
    val themeDetailPage: Int = 1,
    /** When non-null, the shared Add-to-Collection sheet is open for this set. */
    val addTarget: CatalogSet? = null,
    val toastMessage: String? = null,
) {
    val showThemeDetail: Boolean get() = themeDetail != null
    val showBrowse: Boolean get() = submittedQuery == null && query.isBlank()
    val showSuggestions: Boolean get() = submittedQuery == null && query.isNotBlank()
    val showResults: Boolean get() = submittedQuery != null
    val tooMany: Boolean get() = submittedQuery != null && results.size > MAX_RESULTS

    /** Numbered pagination for the theme-detail results (a theme can have many sets). */
    val themeDetailPageCount: Int get() = ((themeDetailResults.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val themeDetailCurrentPage: Int get() = themeDetailPage.coerceIn(1, themeDetailPageCount)
    val themeDetailPageItems: List<CatalogSet>
        get() = themeDetailResults.drop((themeDetailCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** Themes ordered by the active [themeSort] (favorites-first for the favorite sort). */
    val sortedThemes: List<ThemeGroup>
        get() = when (themeSort) {
            ThemeSort.ALPHABETICAL -> themes.sortedBy { it.theme }
            ThemeSort.COUNT -> themes.sortedWith(compareByDescending<ThemeGroup> { it.setCount }.thenBy { it.theme })
            ThemeSort.FAVORITE -> themes.sortedWith(compareByDescending<ThemeGroup> { it.theme in favoriteThemes }.thenBy { it.theme })
        }

    companion object {
        const val MAX_RESULTS = 20
    }
}
