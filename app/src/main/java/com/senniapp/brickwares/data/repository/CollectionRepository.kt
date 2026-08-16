package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.data.model.WishlistItem
import kotlinx.coroutines.flow.Flow

/**
 * Source of collection data for the UI layer. The mock implementation returns
 * canned data; a Supabase-backed implementation will replace it later without
 * touching the ViewModels that depend on this interface.
 *
 * List reads are exposed as [Flow] so that once Room becomes the offline-first
 * source of truth, the UI reactively reflects local changes with no rework.
 */
interface CollectionRepository {
    suspend fun getCollectionSummary(): CollectionSummary

    suspend fun getThemeSummaries(): List<ThemeSummary>

    fun getCollectionItems(): Flow<List<CollectionItem>>

    /** Catalog search (LIKE-style substring match on set number, name, or theme). */
    fun searchCatalog(query: String): List<CatalogSet>

    /** The full reference catalog (used by the Search tab's theme browser). */
    fun getCatalog(): List<CatalogSet>

    /**
     * Adds an item's copies to the collection. If a set with the same number already exists,
     * its copies are merged in; otherwise the item is added. Updates [getCollectionItems].
     */
    fun addItem(item: CollectionItem)

    /** Removes a single copy; if it was the set's last copy, the set is removed too. */
    fun removeCopy(setNumber: String, copyId: String)

    /** Removes an entire item (all copies of the set) from the collection. */
    fun removeItem(setNumber: String)

    /** Replaces an existing copy (matched by id) with an edited version. */
    fun updateCopy(setNumber: String, copy: Copy)

    suspend fun getSoldItems(): List<SoldItem>

    suspend fun getSalesSummary(): SalesSummary

    // ---- Wishlist ----

    /** Sets/minifigs the user wants but doesn't own yet, exposed as a [Flow] like the collection. */
    fun getWishlistItems(): Flow<List<WishlistItem>>

    /** Adds a set to the wishlist. No-op if the set is already wishlisted. */
    fun addToWishlist(item: WishlistItem)

    /** Removes a set from the wishlist (e.g. after moving it into the collection). */
    fun removeFromWishlist(setNumber: String)
}
