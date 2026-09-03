package com.senniapp.brickwares.ui.search

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.ui.components.PAGE_SIZE

/** The Search tab browses either sets or minifigs (toggled by the minifig-head FAB). */
enum class SearchMode { SETS, MINIFIGS }

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
    /** The catalog failed to load (no connection / error) — show the error fallback with a retry. */
    val loadError: Boolean = false,
    val query: String = "",
    /** The query that produced [results]; null while the user hasn't submitted a search yet. */
    val submittedQuery: String? = null,
    val results: List<CatalogSet> = emptyList(),
    val suggestions: List<CatalogSet> = emptyList(),
    val themes: List<ThemeGroup> = emptyList(),
    val themeSort: ThemeSort = ThemeSort.ALPHABETICAL,
    val favoriteThemes: Set<String> = emptySet(),
    val wishlistedNumbers: Set<String> = emptySet(),
    /** Set numbers already in the collection — result cards show "See Detail" instead of Add/Wishlist. */
    val ownedNumbers: Set<String> = emptySet(),
    /** Set/fig numbers the user has sold — also show "See Detail" (the detail page surfaces the sale). */
    val soldNumbers: Set<String> = emptySet(),
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
    val toastMessage: UiText? = null,
    // ---- Minifig mode (toggled by the FAB) ----
    val mode: SearchMode = SearchMode.SETS,
    val minifigsLoading: Boolean = false,
    /** Theme cards for the minifig browse (same shape as the set themes, so ThemeCard is reused). */
    val minifigThemes: List<ThemeGroup> = emptyList(),
    /** Open minifig theme (its figs are in [minifigItems]); null = the theme browse. */
    val minifigThemeDetail: String? = null,
    /** The minifigs currently shown — a theme's figs, or keyword-search results. */
    val minifigItems: List<Minifig> = emptyList(),
    /** 1-based page for the minifig list. */
    val minifigPage: Int = 1,
) {
    val isMinifigMode: Boolean get() = mode == SearchMode.MINIFIGS
    /** Minifig browse (theme cards) shows when no theme is open and no search is submitted. */
    val showMinifigBrowse: Boolean get() = isMinifigMode && minifigThemeDetail == null && submittedQuery == null
    val showMinifigList: Boolean get() = isMinifigMode && (minifigThemeDetail != null || submittedQuery != null)

    val minifigPageCount: Int get() = ((minifigItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val minifigCurrentPage: Int get() = minifigPage.coerceIn(1, minifigPageCount)
    val minifigPageItems: List<Minifig>
        get() = minifigItems.drop((minifigCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

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
    val sortedThemes: List<ThemeGroup> get() = themes.sortedByThemeSort(themeSort, favoriteThemes)

    /** Minifig-browse themes ordered by the same [themeSort]. */
    val sortedMinifigThemes: List<ThemeGroup> get() = minifigThemes.sortedByThemeSort(themeSort, favoriteThemes)

    companion object {
        const val MAX_RESULTS = 20
    }
}

/** Shared ordering for the set + minifig theme browsers. */
private fun List<ThemeGroup>.sortedByThemeSort(sort: ThemeSort, favorites: Set<String>): List<ThemeGroup> =
    when (sort) {
        ThemeSort.ALPHABETICAL -> sortedBy { it.theme }
        ThemeSort.COUNT -> sortedWith(compareByDescending<ThemeGroup> { it.setCount }.thenBy { it.theme })
        ThemeSort.FAVORITE -> sortedWith(compareByDescending<ThemeGroup> { it.theme in favorites }.thenBy { it.theme })
    }
