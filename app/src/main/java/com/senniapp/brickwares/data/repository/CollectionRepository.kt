package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.util.AppCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * Force-refresh the user-scoped catalog cache (the referenced sets/figs) from the network and await
     * it, so a subsequent [getWishlistItems]/[getCollectionItems] read carries today's status. Throws on
     * a network failure so the daily retirement worker can retry instead of diffing stale data.
     */
    suspend fun refreshReferencedCatalog()

    /**
     * True once the user-scoped catalog overlay has successfully loaded at least once. The retirement
     * diff waits for this so it never baselines or fires on stale add-time status (before Decision 16
     * this was "the in-memory catalog is non-empty").
     */
    val catalogOverlayReady: StateFlow<Boolean>

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

    // ---- CSV export / import (Settings → Data) ----

    /** Serializes the current collection to CSV text the user can save and later re-import. */
    suspend fun exportCollectionCsv(): String

    /**
     * Replaces the collection with the rows in [csv] (overwrite): tombstones the current copies so the
     * removals sync, then inserts the parsed rows as fresh dirty entities and kicks a sync. Returns how
     * many copies were imported; throws if the text can't be read as the export format.
     */
    suspend fun importCollectionCsv(csv: String): Int

    /** Sold items (Sales sub-view), exposed as a [Flow] so the list updates as sales are recorded. */
    fun getSoldItems(): Flow<List<SoldItem>>

    /**
     * Records a standalone sale (from the Add sheet's Sales mode). The item's single [Copy] carries
     * the quantity/condition/cost-basis/date/note; [salePrice] is what it sold for. Does not touch
     * the collection — this is for logging a sale of something not necessarily tracked as owned.
     */
    fun addSale(item: CollectionItem, salePrice: Long)

    /**
     * Sells [quantity] units of an owned [copyId], moving them from the collection into Sales. The
     * copy's quantity (and prorated cost basis) is reduced by [quantity]; when nothing remains the
     * copy is removed. [salePrice] is the total the units sold for, in [currency] (the display currency
     * at sell time; the copy's cost basis is converted into it so the sale row is single-currency).
     */
    fun sellCopy(setNumber: String, copyId: String, quantity: Int, salePrice: Long, currency: AppCurrency, soldOn: String?)

    /** Edits an existing sale row (matched by [saleId]). [pricePaid]/[salePrice] are in [currency]. */
    fun updateSale(
        saleId: String,
        quantity: Int,
        condition: Condition,
        pricePaid: Long,
        salePrice: Long,
        currency: AppCurrency,
        soldOn: String?,
        note: String?,
    )

    /** Removes a sale (soft-delete tombstone). */
    fun removeSale(saleId: String)

    // ---- Wishlist ----

    /** Sets/minifigs the user wants but doesn't own yet, exposed as a [Flow] like the collection. */
    fun getWishlistItems(): Flow<List<WishlistItem>>

    /** Adds a set to the wishlist. No-op if the set is already wishlisted. */
    fun addToWishlist(item: WishlistItem)

    /** Removes a set from the wishlist (e.g. after moving it into the collection). */
    fun removeFromWishlist(setNumber: String)
}
