package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CollectionSummary

/**
 * Source of collection data for the UI layer. The mock implementation returns
 * canned data; a Supabase-backed implementation will replace it later without
 * touching the ViewModels that depend on this interface.
 */
interface CollectionRepository {
    suspend fun getCollectionSummary(): CollectionSummary
}
