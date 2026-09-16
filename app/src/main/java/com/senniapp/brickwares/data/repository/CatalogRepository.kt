package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Minifig

/** A theme with how many catalog sets it has — the theme-browse list row, from the DB (Decision 16). */
data class ThemeCount(val theme: String, val setCount: Int)

/** A theme's subtheme + set count — feeds the browse cards' subtheme chips (empty subtheme = "no subtheme"). */
data class ThemeSubthemeCount(val theme: String, val subtheme: String, val setCount: Int)

/**
 * Read-only access to the reference catalog (sets/minifigs), sourced from Supabase.
 *
 * Kept separate from [CollectionRepository] (user data) because the catalog is public, network-backed,
 * and shared app-wide. Every method is a DB query (Decision 16 — the ~23k-set catalog is far too large
 * to hold in memory): browse/search hit indexed columns and count views, the Set/Minifig detail pages
 * fetch a single row, and the collection/wishlist/sales overlays resolve just the user's referenced
 * sets/figs via the batch [fetchSetsByNumbers]/[fetchSetsByIds]/[fetchMinifigsByNums]. All suspend and
 * throw on failure so the caller can show an error/retry.
 */
interface CatalogRepository {
    /** Substring search over set number / name / theme, capped at [limit], newest first. DB query. */
    suspend fun searchSets(query: String, limit: Int = 25): List<CatalogSet>

    /** The theme-browse list (theme + set count) from the `catalog_theme_counts` view. */
    suspend fun themeCounts(): List<ThemeCount>

    /** Every theme's subtheme counts (for the browse cards' subtheme chips), from the DB view. */
    suspend fun subthemeCounts(): List<ThemeSubthemeCount>

    /**
     * Every set in one theme — bounded (a theme is at most ~1–2k sets), so the caller groups subthemes,
     * sorts and paginates in memory over this list instead of holding the whole catalog.
     */
    suspend fun setsInTheme(theme: String): List<CatalogSet>

    /**
     * Candidate "new" sets for the Home New Sets section: launch date on/after the start of the
     * previous month (covers both future/pending launches and current/previous-month releases —
     * [com.senniapp.brickwares.util.NewSets] applies the exact rule over these).
     */
    suspend fun newSetCandidates(): List<CatalogSet>

    /**
     * Resolve ONE set by its canonical id (`"<number>-<variant>"`, i.e. [CatalogSet.id]) or a bare set
     * number (lowest variant wins); null if unknown. A single-row DB query for the Set Detail page.
     */
    suspend fun fetchSet(catalogKey: String): CatalogSet?

    /**
     * Batch-resolve catalog sets by set number (lowest variant per number wins). Feeds the user-scoped
     * catalog cache that overlays the Collection/Wishlist/Sales rows (Decision 16 — the client no longer
     * holds the whole catalog). Bounded to the user's referenced items.
     */
    suspend fun fetchSetsByNumbers(numbers: Collection<String>): List<CatalogSet>

    /** Batch-resolve catalog sets by their primary key (`sets.set_id`) — for the sync pull + value warm. */
    suspend fun fetchSetsByIds(ids: Collection<Long>): List<CatalogSet>

    /** Batch-resolve catalog minifigs by fig number — for the user-scoped cache + the sync pull. */
    suspend fun fetchMinifigsByNums(figNums: Collection<String>): List<Minifig>

    // ---- Minifig server-side queries (Decision 16) — the minifig catalog is too large for memory too. ----

    /** Substring search over fig number / name, capped at [limit]. DB query. */
    suspend fun fetchMinifigsMatching(query: String, limit: Int = 25): List<Minifig>

    /** The minifig theme-browse list (theme + distinct-minifig count), from the DB view. */
    suspend fun minifigThemeCounts(): List<ThemeCount>

    /** Every minifig theme's subtheme counts (browse cards' subtheme chips), from the DB view. */
    suspend fun minifigSubthemeCounts(): List<ThemeSubthemeCount>

    /** Every minifig in one theme (bounded — the caller groups subthemes / sorts / paginates in memory). */
    suspend fun minifigsInTheme(theme: String): List<Minifig>

    /** One minifig by fig number, with its themes + the sets it appears in; null if unknown. DB query. */
    suspend fun fetchMinifig(figNum: String): Minifig?

    /** The catalog sets a minifig appears in (newest first). DB query. */
    suspend fun fetchSetsForMinifig(figNum: String): List<CatalogSet>

    /** The catalog minifigs that appear in a set (for the Set Detail grid). DB query. */
    suspend fun fetchMinifigsForSet(setId: Long): List<Minifig>
}
