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
    /** The theme's icon URL (R2 `themes/<slug>.png`, see CatalogImages.themeIconUrl); null = no icon (placeholder). */
    val logoAsset: String?,
    val subthemes: List<SubthemeCount> = emptyList(),
)

/** Ordering for the theme browser. */
enum class ThemeSort(val label: String) {
    ALPHABETICAL("Alphabetical"),
    COUNT("Amount of sets"),
    FAVORITE("Favorites"),
}

/**
 * How the theme browse renders: [DETAIL] is the full card (logo + name + count + subthemes + favorite
 * star, one per row); [LIST] is a compact 2-column grid of just name + set count (no logo/subthemes/
 * favorites). Shared by the set + minifig browse.
 */
enum class ThemeViewMode { DETAIL, LIST }

/** Ordering for the sets listed inside a theme-detail view. */
enum class ThemeDetailSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    PRICE_HIGH("Price: high to low"),
    PRICE_LOW("Price: low to high"),
    NAME("Name"),
}

/**
 * Ordering for the minifigs listed inside a minifig theme-detail view. Minifigs have no release year
 * or retail price, so instead of newest/price the sorts are by name, community current value, and how
 * many sets the fig appears in.
 */
enum class MinifigSort(val label: String) {
    NAME("Name"),
    VALUE_HIGH("Value: high to low"),
    VALUE_LOW("Value: low to high"),
    MOST_SETS("In most sets"),
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
    /** Live set suggestions (the typing dropdown), shown above [minifigSuggestions]. */
    val suggestions: List<CatalogSet> = emptyList(),
    /** Live minifig suggestions (matched by name or fig code, e.g. "fig-017485"), shown below the sets. */
    val minifigSuggestions: List<Minifig> = emptyList(),
    val themes: List<ThemeGroup> = emptyList(),
    val themeSort: ThemeSort = ThemeSort.ALPHABETICAL,
    /** Detail cards vs a compact 2-column list. Shared by the set + minifig theme browse. */
    val themeViewMode: ThemeViewMode = ThemeViewMode.DETAIL,
    /** 1-based current page for the theme browse grid (10/page). */
    val themePage: Int = 1,
    /** Frozen display order for the set theme browse — favorites pinned. Recomputed only when the
     *  browse is (re)entered, NOT on a favorite toggle, so bookmarking doesn't reorder the list live. */
    val orderedThemes: List<ThemeGroup> = emptyList(),
    /** Favorited SET themes (separate from minifig favorites — the two browses are independent). */
    val favoriteThemes: Set<String> = emptySet(),
    /** Favorited MINIFIG themes (kept apart from [favoriteThemes]). */
    val favoriteMinifigThemes: Set<String> = emptySet(),
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
    /** 1-based page for the minifig theme browse grid (10/page). */
    val minifigThemePage: Int = 1,
    /** Frozen display order for the minifig theme browse (see [orderedThemes]). */
    val orderedMinifigThemes: List<ThemeGroup> = emptyList(),
    /** Open minifig theme (its figs are in [minifigItems]); null = the theme browse. */
    val minifigThemeDetail: String? = null,
    /** Selected subtheme filter inside the minifig theme-detail (ALL_SUBTHEMES = no filter). */
    val minifigThemeDetailSub: String = ALL_SUBTHEMES,
    /** Sort applied inside the minifig theme-detail. */
    val minifigThemeDetailSort: MinifigSort = MinifigSort.NAME,
    /** Subtheme options (with counts) for the open minifig theme's filter dropdown. */
    val minifigThemeDetailSubOptions: List<SubthemeCount> = emptyList(),
    /** The minifigs currently shown — a theme's figs, or keyword-search results. */
    val minifigItems: List<Minifig> = emptyList(),
    /** 1-based page for the minifig list. */
    val minifigPage: Int = 1,
    /**
     * Bumped on every reset-to-browse-home (mode toggle, mode switch from a detail, Search-nav tap) so
     * the screen can scroll the browse list back to the top — without disturbing back-navigation, which
     * doesn't reset and so leaves this unchanged (the list restores its previous scroll).
     */
    val homeScrollTick: Int = 0,
    /**
     * New catalog sets (pending release + released this/last month) grouped by theme, themes A→Z —
     * the full list behind the Home "New LEGO Sets" card, rendered on the dedicated New Sets page.
     * Rebuilt whenever the catalog (re)loads.
     */
    val newSetsByTheme: List<Pair<String, List<CatalogSet>>> = emptyList(),
) {
    val isMinifigMode: Boolean get() = mode == SearchMode.MINIFIGS
    /** Minifig browse (theme cards) shows when no theme is open and no search is submitted. */
    val showMinifigBrowse: Boolean get() = isMinifigMode && minifigThemeDetail == null && submittedQuery == null
    val showMinifigList: Boolean get() = isMinifigMode && (minifigThemeDetail != null || submittedQuery != null)

    /**
     * The minifig theme-detail is its own full view (like the set [showThemeDetail]) — a theme was
     * opened and no keyword search is active — so it replaces the banner/search browse chrome.
     */
    val showMinifigThemeDetail: Boolean
        get() = isMinifigMode && minifigThemeDetail != null && submittedQuery == null && !minifigsLoading

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

    /** The desired theme order right now (favorites pinned first; the Favorites option filters to only
     *  favorites). The ViewModel SNAPSHOTS this into [orderedThemes] on browse entry — the browse grid
     *  paginates the frozen snapshot, so favoriting doesn't reorder the list under the user's finger. */
    val sortedThemes: List<ThemeGroup> get() = themes.sortedByThemeSort(themeSort, favoriteThemes)
    val themePageCount: Int get() = ((orderedThemes.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val themeCurrentPage: Int get() = themePage.coerceIn(1, themePageCount)
    val themePageItems: List<ThemeGroup> get() = orderedThemes.drop((themeCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    /** Minifig-browse desired order (snapshotted into [orderedMinifigThemes], using minifig favorites). */
    val sortedMinifigThemes: List<ThemeGroup> get() = minifigThemes.sortedByThemeSort(themeSort, favoriteMinifigThemes)
    val minifigThemePageCount: Int get() = ((orderedMinifigThemes.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val minifigThemeCurrentPage: Int get() = minifigThemePage.coerceIn(1, minifigThemePageCount)
    val minifigThemePageItems: List<ThemeGroup> get() = orderedMinifigThemes.drop((minifigThemeCurrentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    companion object {
        const val MAX_RESULTS = 20
    }
}

/**
 * Shared ordering for the set + minifig theme browsers. Alphabetical / Count pin favorites to the top
 * (so bookmarks lead the browse); Favorites is a filter — only favorited themes, alphabetically.
 */
private fun List<ThemeGroup>.sortedByThemeSort(sort: ThemeSort, favorites: Set<String>): List<ThemeGroup> =
    when (sort) {
        ThemeSort.ALPHABETICAL ->
            sortedWith(compareByDescending<ThemeGroup> { it.theme in favorites }.thenBy { it.theme })
        ThemeSort.COUNT ->
            sortedWith(compareByDescending<ThemeGroup> { it.theme in favorites }.thenByDescending { it.setCount }.thenBy { it.theme })
        ThemeSort.FAVORITE -> filter { it.theme in favorites }.sortedBy { it.theme }
    }
