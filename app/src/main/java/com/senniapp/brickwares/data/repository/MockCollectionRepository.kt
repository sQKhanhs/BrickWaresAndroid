package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CollectionSummary
import kotlinx.coroutines.delay

/**
 * In-memory mock repository. Values mirror the design handoff screenshots so the
 * UI looks like the reference. The small [delay] simulates async loading so the
 * ViewModel's loading state is exercised.
 */
class MockCollectionRepository : CollectionRepository {

    override suspend fun getCollectionSummary(): CollectionSummary {
        delay(300)
        return CollectionSummary(
            setCount = 8,
            minifigCount = 38,
            pieceCount = 28_553,
            collectionValue = 90_608_440,
            paid = 82_939_480,
            growthPercent = 9.0,
            bannerImageUrl = null,
        )
    }
}
