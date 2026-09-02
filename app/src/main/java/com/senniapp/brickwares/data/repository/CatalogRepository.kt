package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Minifig
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

    /**
     * True when the last [refresh] attempt failed (network/permission) and the cache is still empty —
     * so catalog-backed screens (Search, Set Detail) can show an error/offline fallback + a retry.
     * Flips back to false once a refresh succeeds.
     */
    val loadError: StateFlow<Boolean>

    /** Snapshot of the cached catalog (empty until [refresh] has completed at least once). */
    fun all(): List<CatalogSet>

    /** Substring (LIKE-style) match over the cache on set number, name, or theme. */
    fun search(query: String): List<CatalogSet>

    /** Loads the minifig catalog (with each fig's set-count + themes) into memory, if not already. */
    suspend fun refreshMinifigs()

    /** Snapshot of the cached minifig catalog (empty until [refreshMinifigs] has completed once). */
    fun allMinifigs(): List<Minifig>

    /** Substring (LIKE-style) match over the minifig cache on fig number or name. */
    fun searchMinifigs(query: String): List<Minifig>

    /** The catalog sets a minifig appears in (resolved from the in-memory caches), newest first. */
    fun setsForMinifig(figNum: String): List<CatalogSet>

    /** The catalog minifigs that appear in a set (resolved from the in-memory minifig cache). */
    fun minifigsForSet(setId: Long?): List<Minifig>
}
