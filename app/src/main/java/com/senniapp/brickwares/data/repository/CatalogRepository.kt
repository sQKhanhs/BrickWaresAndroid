package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CatalogSet
import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only access to the reference catalog (sets/minifigs), sourced from Supabase.
 *
 * Kept separate from [CollectionRepository] (user data) because the catalog is public,
 * network-backed, and shared app-wide. The current implementation loads the catalog once
 * into an in-memory cache and serves the synchronous [all]/[search] from it — fine at the
 * present catalog size; when it grows toward the full ~22k set catalog this moves to
 * DB-side queries (WHERE/GROUP BY + pagination) per the architecture's caching strategy.
 */
interface CatalogRepository {
    /** Loads the catalog into the in-memory cache if not already loaded (idempotent, safe to call often). */
    suspend fun refresh()

    /**
     * Bumps each time the in-memory cache is (re)loaded. Reactive consumers can [kotlinx.coroutines.flow.combine]
     * this with their own flow to re-read [all]/[search] once the catalog becomes available — e.g. to
     * overlay fresh catalog-derived status onto denormalized user rows.
     */
    val revision: StateFlow<Int>

    /** Snapshot of the cached catalog (empty until [refresh] has completed at least once). */
    fun all(): List<CatalogSet>

    /** Substring (LIKE-style) match over the cache on set number, name, or theme. */
    fun search(query: String): List<CatalogSet>
}
