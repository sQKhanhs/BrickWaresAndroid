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

    /**
     * Substring (LIKE-style) match over the cache on set number, name, or theme. [limit] stops the
     * scan after that many hits (the live-suggestion dropdown wants 6) instead of walking the whole
     * catalog and truncating afterwards.
     */
    fun search(query: String, limit: Int = Int.MAX_VALUE): List<CatalogSet>

    /** O(1) lookup by catalog primary key (`sets.set_id`); null when unknown or not yet loaded. */
    fun setById(setId: Long): CatalogSet?

    /**
     * O(1) lookup by set number; null when unknown or not yet loaded. When several sets share a
     * number (CMF series variants) the lowest variant wins, so the pick is deterministic.
     */
    fun setByNumber(setNumber: String): CatalogSet?

    /** Loads the minifig catalog (with each fig's set-count + themes) into memory, if not already. */
    suspend fun refreshMinifigs()

    /** Snapshot of the cached minifig catalog (empty until [refreshMinifigs] has completed once). */
    fun allMinifigs(): List<Minifig>

    /** Substring (LIKE-style) match over the minifig cache on fig number or name. */
    fun searchMinifigs(query: String): List<Minifig>

    /** O(1) lookup of a minifig by fig_num; null when unknown or not yet loaded. */
    fun minifigByNum(figNum: String): Minifig?

    /** The catalog sets a minifig appears in (resolved from the in-memory caches), newest first. */
    fun setsForMinifig(figNum: String): List<CatalogSet>

    /** The catalog minifigs that appear in a set (resolved from the in-memory minifig cache). */
    fun minifigsForSet(setId: Long?): List<Minifig>
}
