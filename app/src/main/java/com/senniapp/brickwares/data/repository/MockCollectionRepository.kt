package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ItemType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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

    override fun getCollectionItems(): Flow<List<CollectionItem>> = flowOf(MOCK_ITEMS)

    private companion object {
        val MOCK_ITEMS = listOf(
            CollectionItem(
                setNumber = "60380", name = "City Center", itemType = ItemType.SET,
                theme = "City", releaseYear = 2023, releaseMonth = 1,
                pieces = 790, minifigs = 8,
                retailPrice = 2_599_740, pricePaid = 2_599_740,
                currentValue = null, growthPercent = null, status = Availability.AVAILABLE,
            ),
            CollectionItem(
                setNumber = "10297", name = "Boutique Hotel", itemType = ItemType.SET,
                theme = "Icons", releaseYear = 2022, releaseMonth = 1,
                pieces = 3066, minifigs = 5,
                retailPrice = 5_899_740, pricePaid = 5_400_000,
                currentValue = 7_250_000, growthPercent = 34.3, status = Availability.RETIRED,
            ),
            CollectionItem(
                setNumber = "21058", name = "Great Pyramid of Giza", itemType = ItemType.SET,
                theme = "Architecture", releaseYear = 2022, releaseMonth = 6,
                pieces = 1476, minifigs = 0,
                retailPrice = 3_499_740, pricePaid = 3_100_000,
                currentValue = 3_900_000, growthPercent = 25.8, status = Availability.EXCLUSIVE,
            ),
            CollectionItem(
                setNumber = "71043", name = "Hogwarts Castle", itemType = ItemType.SET,
                theme = "Harry Potter", releaseYear = 2018, releaseMonth = 9,
                pieces = 6020, minifigs = 4,
                retailPrice = 10_499_740, pricePaid = 9_800_000,
                currentValue = 14_500_000, growthPercent = 47.9, status = Availability.RETIRED,
            ),
            CollectionItem(
                setNumber = "fig-025", name = "Ninja Gold Minifig", itemType = ItemType.MINIFIG,
                theme = "Ninjago", releaseYear = 2021, releaseMonth = 3,
                pieces = 4, minifigs = 1,
                retailPrice = 320_000, pricePaid = 280_000,
                currentValue = 450_000, growthPercent = 60.7, status = Availability.EXCLUSIVE,
            ),
        )
    }
}
