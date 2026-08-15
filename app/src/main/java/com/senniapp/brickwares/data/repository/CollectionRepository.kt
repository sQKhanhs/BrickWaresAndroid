package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
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

    /** Catalog search for the Add-to-Collection sheet (by set number or name). */
    fun searchCatalog(query: String): List<CatalogSet>

    /**
     * Adds an item's copies to the collection. If a set with the same number already exists,
     * its copies are merged in; otherwise the item is added. Updates [getCollectionItems].
     */
    fun addItem(item: CollectionItem)

    /** Removes a single copy; if it was the set's last copy, the set is removed too. */
    fun removeCopy(setNumber: String, copyId: String)

    /** Replaces an existing copy (matched by id) with an edited version. */
    fun updateCopy(setNumber: String, copy: Copy)

    suspend fun getSoldItems(): List<SoldItem>

    suspend fun getSalesSummary(): SalesSummary
}
