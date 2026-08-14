package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
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

    fun getCollectionItems(): Flow<List<CollectionItem>>
}
