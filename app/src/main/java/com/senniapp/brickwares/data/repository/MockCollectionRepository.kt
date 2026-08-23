package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.data.model.WishlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory mock repository. Values mirror the design handoff screenshots. Items are held in a
 * [MutableStateFlow] so additions/removals appear live in the Collection list.
 */
class MockCollectionRepository : CollectionRepository {

    // Backed by process-wide flows (declared in the companion) so every constructed instance
    // shares the same in-memory data — e.g. moving a set from the Wishlist tab into the
    // collection is visible on the Collection tab even though each ViewModel news up its own repo.
    private val _items get() = itemsFlow
    private val _wishlist get() = wishlistFlow

    // Summary + themes are now DERIVED from the live items (see CollectionStats) so they read 0
    // when empty and grow as the user adds — the Home/Collection VMs observe the item Flow directly;
    // these one-shot methods compute from the current snapshot for any remaining caller.
    override suspend fun getCollectionSummary(): CollectionSummary = collectionSummaryOf(itemsFlow.value)

    override suspend fun getThemeSummaries(): List<ThemeSummary> = themeSummariesOf(itemsFlow.value)

    override fun getCollectionItems(): Flow<List<CollectionItem>> = _items.asStateFlow()

    override fun searchCatalog(query: String): List<CatalogSet> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        return MOCK_CATALOG.filter {
            it.setNumber.contains(q, ignoreCase = true) ||
                it.name.contains(q, ignoreCase = true) ||
                it.theme.contains(q, ignoreCase = true)
        }
    }

    override fun getCatalog(): List<CatalogSet> = MOCK_CATALOG

    override fun addItem(item: CollectionItem) {
        _items.update { current ->
            if (current.any { it.setNumber == item.setNumber }) {
                current.map { existing ->
                    if (existing.setNumber == item.setNumber) {
                        existing.copy(copies = existing.copies + item.copies)
                    } else {
                        existing
                    }
                }
            } else {
                current + item
            }
        }
    }

    override fun removeCopy(setNumber: String, copyId: String) {
        _items.update { current ->
            current.mapNotNull { item ->
                if (item.setNumber != setNumber) {
                    item
                } else {
                    val remaining = item.copies.filterNot { it.id == copyId }
                    if (remaining.isEmpty()) null else item.copy(copies = remaining)
                }
            }
        }
    }

    override fun removeItem(setNumber: String) {
        _items.update { current -> current.filterNot { it.setNumber == setNumber } }
    }

    override fun updateCopy(setNumber: String, copy: Copy) {
        _items.update { current ->
            current.map { item ->
                if (item.setNumber != setNumber) {
                    item
                } else {
                    item.copy(copies = item.copies.map { if (it.id == copy.id) copy else it })
                }
            }
        }
    }

    override suspend fun getSoldItems(): List<SoldItem> = soldFlow.value

    override suspend fun getSalesSummary(): SalesSummary {
        val sold = soldFlow.value
        val totalPaid = sold.sumOf { it.pricePaid }
        val totalProfit = sold.sumOf { it.profit }
        val avg = if (sold.isEmpty()) 0.0 else sold.map { it.profitPercent }.average()
        val overall = if (totalPaid == 0L) 0.0 else totalProfit.toDouble() / totalPaid * 100.0
        return SalesSummary(
            totalSold = sold.size,
            totalSaleValue = sold.sumOf { it.saleValue },
            totalProfit = totalProfit,
            avgProfitPercent = avg,
            profitPercent = overall,
        )
    }

    // ---- Wishlist ----

    override fun getWishlistItems(): Flow<List<WishlistItem>> = _wishlist.asStateFlow()

    override fun addToWishlist(item: WishlistItem) {
        _wishlist.update { current ->
            if (current.any { it.setNumber == item.setNumber }) current else current + item
        }
    }

    override fun removeFromWishlist(setNumber: String) {
        _wishlist.update { current -> current.filterNot { it.setNumber == setNumber } }
    }

    private companion object {
        // Mock card images (a real app would use each item's own photo). Set cards share one
        // set photo, minifig cards share one minifig photo.
        const val SET_IMG = "file:///android_asset/mock_set.jpg"
        const val MINIFIG_IMG = "file:///android_asset/mock_minifig.jpg"

        val MOCK_ITEMS = listOf(
            CollectionItem(
                setNumber = "60380", name = "City Center", itemType = ItemType.SET,
                theme = "City", releaseYear = 2023, releaseMonth = 1,
                pieces = 790, minifigs = 8,
                retailPrice = 2_599_740, currentValue = null, growthPercent = null,
                status = Availability.AVAILABLE, imageUrl = SET_IMG,
                copies = listOf(
                    Copy("60380-a", Condition.NEW, 1, 2_599_740, "2023-02-14"),
                    Copy("60380-b", Condition.USED, 1, 2_100_000, "2024-01-05", "Open box, complete"),
                ),
            ),
            CollectionItem(
                setNumber = "10297", name = "Boutique Hotel", itemType = ItemType.SET,
                theme = "Icons", releaseYear = 2022, releaseMonth = 1,
                pieces = 3066, minifigs = 5,
                retailPrice = 5_899_740, currentValue = 7_250_000, growthPercent = 34.3,
                status = Availability.RETIRED, imageUrl = SET_IMG,
                copies = listOf(
                    Copy("10297-a", Condition.NEW, 1, 5_400_000, "2022-03-01", "Sealed, kept in box"),
                ),
            ),
            CollectionItem(
                setNumber = "21058", name = "Great Pyramid of Giza", itemType = ItemType.SET,
                theme = "Architecture", releaseYear = 2022, releaseMonth = 6,
                pieces = 1476, minifigs = 0,
                retailPrice = 3_499_740, currentValue = 3_900_000, growthPercent = 25.8,
                status = Availability.EXCLUSIVE, imageUrl = SET_IMG,
                copies = listOf(
                    Copy("21058-a", Condition.NEW, 1, 3_100_000, "2022-08-20"),
                ),
            ),
            CollectionItem(
                setNumber = "71043", name = "Hogwarts Castle", itemType = ItemType.SET,
                theme = "Harry Potter", releaseYear = 2018, releaseMonth = 9,
                pieces = 6020, minifigs = 4,
                retailPrice = 10_499_740, currentValue = 14_500_000, growthPercent = 47.9,
                status = Availability.RETIRED, imageUrl = SET_IMG,
                copies = listOf(
                    Copy("71043-a", Condition.NEW, 1, 9_800_000, "2019-11-02"),
                ),
            ),
            CollectionItem(
                setNumber = "42115", name = "Lamborghini Sián FKP 37", itemType = ItemType.SET,
                theme = "Technic", releaseYear = 2020, releaseMonth = 6,
                pieces = 3696, minifigs = 0,
                retailPrice = 9_999_740, currentValue = 8_200_000, growthPercent = -12.4,
                status = Availability.RETIRED, imageUrl = SET_IMG,
                copies = listOf(
                    Copy("42115-a", Condition.NEW, 1, 9_400_000, "2020-08-15"),
                ),
            ),
            CollectionItem(
                setNumber = "fig-025", name = "Ninja Gold Minifig", itemType = ItemType.MINIFIG,
                theme = "Ninjago", releaseYear = 2021, releaseMonth = 3,
                pieces = 4, minifigs = 1,
                retailPrice = 320_000, currentValue = 450_000, growthPercent = 60.7,
                status = Availability.EXCLUSIVE, imageUrl = MINIFIG_IMG,
                copies = listOf(
                    Copy("fig-025-a", Condition.NEW, 1, 280_000, "2021-05-18"),
                ),
            ),
        )

        val MOCK_THEMES = listOf(
            ThemeSummary("Icons", setCount = 3, totalValue = 34_200_000),
            ThemeSummary("City", setCount = 2, totalValue = 12_400_000),
            ThemeSummary("Star Wars", setCount = 1, totalValue = 22_500_000),
            ThemeSummary("Harry Potter", setCount = 1, totalValue = 14_500_000),
            ThemeSummary("Architecture", setCount = 1, totalValue = 3_900_000),
        )

        val MOCK_CATALOG = listOf(
            // Architecture
            CatalogSet("21058", "Great Pyramid of Giza", ItemType.SET, "Architecture", 2022, 6, 1476, 0, 3_499_740, Availability.EXCLUSIVE, "Landmarks"),
            CatalogSet("21060", "Himeji Castle", ItemType.SET, "Architecture", 2023, 8, 2125, 0, 4_999_740, Availability.AVAILABLE, "Landmarks"),
            CatalogSet("21028", "New York City", ItemType.SET, "Architecture", 2016, 6, 598, 0, 1_299_740, Availability.RETIRED, "Skylines"),
            CatalogSet("21044", "Paris", ItemType.SET, "Architecture", 2019, 1, 649, 0, 1_499_740, Availability.AVAILABLE, "Skylines"),
            // Batman
            CatalogSet("76240", "Batmobile Tumbler", ItemType.SET, "Batman", 2021, 11, 2049, 2, 6_299_740, Availability.RETIRED, "Vehicles"),
            CatalogSet("76252", "Batcave Shadow Box", ItemType.SET, "Batman", 2023, 8, 3981, 6, 9_999_740, Availability.AVAILABLE, "Buildings"),
            CatalogSet("76271", "Batman Gotham City", ItemType.SET, "Batman", 2024, 6, 4210, 5, 8_499_740, Availability.AVAILABLE, "Buildings"),
            CatalogSet("76139", "1989 Batmobile", ItemType.SET, "Batman", 2019, 11, 3306, 3, 6_999_740, Availability.RETIRED, "Vehicles"),
            CatalogSet("col-bat-01", "Batman Classic Suit", ItemType.MINIFIG, "Batman", 2020, 3, 5, 1, 350_000, Availability.AVAILABLE, "Minifigures"),
            // Star Wars
            CatalogSet("75313", "AT-AT", ItemType.SET, "Star Wars", 2021, 11, 6785, 9, 19_999_740, Availability.RETIRED, "Ultimate Collector Series"),
            CatalogSet("75257", "Millennium Falcon", ItemType.SET, "Star Wars", 2019, 10, 1351, 7, 4_499_740, Availability.AVAILABLE, "The Rise of Skywalker"),
            CatalogSet("75355", "X-Wing Starfighter", ItemType.SET, "Star Wars", 2023, 5, 1949, 4, 6_299_740, Availability.AVAILABLE, "Ultimate Collector Series"),
            CatalogSet("col-sw-01", "Boba Fett", ItemType.MINIFIG, "Star Wars", 2019, 5, 6, 1, 650_000, Availability.RETIRED, "Minifigures"),
            // Technic
            CatalogSet("42115", "Lamborghini Sian FKP 37", ItemType.SET, "Technic", 2020, 6, 3696, 0, 9_999_740, Availability.RETIRED, "Vehicles"),
            CatalogSet("42143", "Ferrari Daytona SP3", ItemType.SET, "Technic", 2022, 6, 3778, 0, 11_499_740, Availability.AVAILABLE, "Vehicles"),
            CatalogSet("42154", "2022 Ford GT", ItemType.SET, "Technic", 2023, 3, 1466, 0, 3_299_740, Availability.AVAILABLE, "Vehicles"),
            // Icons
            CatalogSet("10297", "Boutique Hotel", ItemType.SET, "Icons", 2022, 1, 3066, 5, 5_899_740, Availability.RETIRED, "Modular Buildings"),
            CatalogSet("10307", "Eiffel Tower", ItemType.SET, "Icons", 2022, 11, 10001, 0, 16_999_740, Availability.AVAILABLE, "Landmarks"),
            CatalogSet("10327", "Dune Atreides Royal Ornithopter", ItemType.SET, "Icons", 2023, 8, 1369, 8, 4_499_740, Availability.AVAILABLE, "Movie"),
            // City
            CatalogSet("60380", "Downtown City Center", ItemType.SET, "City", 2023, 1, 2010, 14, 4_299_740, Availability.AVAILABLE, "General"),
            CatalogSet("60367", "Passenger Airplane", ItemType.SET, "City", 2023, 3, 913, 9, 2_499_740, Availability.AVAILABLE, "Airport"),
            // Harry Potter
            CatalogSet("71043", "Hogwarts Castle", ItemType.SET, "Harry Potter", 2018, 9, 6020, 4, 10_499_740, Availability.RETIRED, "Hogwarts"),
            CatalogSet("76419", "Hogwarts Castle and Grounds", ItemType.SET, "Harry Potter", 2023, 9, 2660, 4, 4_999_740, Availability.AVAILABLE, "Hogwarts"),
            CatalogSet("col-hp-01", "Harry Potter in Robes", ItemType.MINIFIG, "Harry Potter", 2018, 8, 6, 1, 280_000, Availability.AVAILABLE, "Minifigures"),
            // Ninjago
            CatalogSet("71741", "NINJAGO City Gardens", ItemType.SET, "Ninjago", 2021, 2, 5685, 19, 8_999_740, Availability.RETIRED, "NINJAGO City"),
            CatalogSet("col-nin-01", "Golden Ninja", ItemType.MINIFIG, "Ninjago", 2021, 3, 5, 1, 320_000, Availability.EXCLUSIVE, "Minifigures"),
            // Botanical
            CatalogSet("10281", "Bonsai Tree", ItemType.SET, "Botanical", 2021, 1, 878, 0, 1_499_740, Availability.AVAILABLE, "Plants"),
            CatalogSet("10309", "Succulents", ItemType.SET, "Botanical", 2022, 1, 771, 0, 1_299_740, Availability.AVAILABLE, "Plants"),
            // Ideas
            CatalogSet("21344", "The Orient Express Train", ItemType.SET, "Ideas", 2024, 3, 2540, 6, 8_999_740, Availability.AVAILABLE, "Originals"),
            // Marvel
            CatalogSet("76218", "Sanctum Sanctorum", ItemType.SET, "Marvel", 2022, 8, 2708, 9, 6_499_740, Availability.AVAILABLE, "Avengers"),
            // Art
            CatalogSet("31203", "World Map", ItemType.SET, "Art", 2021, 6, 11695, 0, 6_999_740, Availability.RETIRED, "Art"),
        )

        val MOCK_SOLD = listOf(
            SoldItem(
                setNumber = "21322", name = "Pirates of Barracuda Bay", itemType = ItemType.SET,
                theme = "Ideas", releaseYear = 2020, releaseMonth = 4, imageUrl = SET_IMG,
                retailPrice = 4_299_740, pricePaid = 4_000_000, saleValue = 6_500_000,
            ),
            SoldItem(
                setNumber = "10281", name = "Bonsai Tree", itemType = ItemType.SET,
                theme = "Botanical", releaseYear = 2021, releaseMonth = 1, imageUrl = SET_IMG,
                retailPrice = 1_499_740, pricePaid = 1_300_000, saleValue = 1_100_000,
            ),
        )

        val MOCK_WISHLIST = listOf(
            WishlistItem(
                setNumber = "75313", name = "AT-AT", itemType = ItemType.SET,
                theme = "Star Wars", releaseYear = 2021, releaseMonth = 11,
                pieces = 6785, minifigs = 9, retailPrice = 19_999_740,
                currentValue = 26_500_000, growthPercent = 32.5,
                status = Availability.RETIRED, imageUrl = SET_IMG,
            ),
            WishlistItem(
                setNumber = "10307", name = "Eiffel Tower", itemType = ItemType.SET,
                theme = "Icons", releaseYear = 2022, releaseMonth = 11,
                pieces = 10001, minifigs = 0, retailPrice = 16_999_740,
                currentValue = null, growthPercent = null,
                status = Availability.AVAILABLE, imageUrl = SET_IMG,
            ),
            WishlistItem(
                setNumber = "42143", name = "Ferrari Daytona SP3", itemType = ItemType.SET,
                theme = "Technic", releaseYear = 2022, releaseMonth = 6,
                pieces = 3778, minifigs = 0, retailPrice = 11_499_740,
                currentValue = 13_200_000, growthPercent = 14.8,
                status = Availability.AVAILABLE, imageUrl = SET_IMG,
            ),
            WishlistItem(
                setNumber = "fig-042", name = "Boba Fett", itemType = ItemType.MINIFIG,
                theme = "Star Wars", releaseYear = 2019, releaseMonth = 5,
                pieces = 4, minifigs = 1, retailPrice = 650_000,
                currentValue = 980_000, growthPercent = 50.8,
                status = Availability.RETIRED, imageUrl = MINIFIG_IMG,
            ),
        )

        // Process-wide stores shared by all repository instances (see [_items] / [_wishlist]).
        // Seeded EMPTY so first open shows the cold-start empty states / NoValue hero; the user
        // fills them via the add flows. The MOCK_* constants above are kept for a quick demo restore
        // (swap emptyList() for MOCK_ITEMS / MOCK_WISHLIST / MOCK_SOLD) and are superseded once the
        // real Supabase/Room user-data slice lands.
        val itemsFlow = MutableStateFlow<List<CollectionItem>>(emptyList())
        val wishlistFlow = MutableStateFlow<List<WishlistItem>>(emptyList())
        val soldFlow = MutableStateFlow<List<SoldItem>>(emptyList())
    }
}
