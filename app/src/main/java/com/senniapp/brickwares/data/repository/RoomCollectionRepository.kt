package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.WishlistEntity
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Offline-first user-data repository (Arch Decision 10). **Room is the source of truth** — reads are
 * Room [Flow]s (reactive, work logged-out and offline) and writes go to Room immediately (marking the
 * row `dirty`), then kick a background [SyncCoordinator.requestSync]. The network never blocks a read
 * or write. Catalog display fields are denormalized onto each row at add-time so cards render offline.
 */
class RoomCollectionRepository(
    private val catalog: CatalogRepository,
    private val sync: SyncCoordinator,
    db: BrickWaresDatabase = AppGraph.database,
) : CollectionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collectionDao = db.collectionDao()
    private val wishlistDao = db.wishlistDao()
    private val salesDao = db.salesDao()

    // ---- Reads (Room is the source of truth) ----

    override fun getCollectionItems(): Flow<List<CollectionItem>> =
        collectionDao.observeActive().map { rows ->
            rows.groupBy { it.setNumber }.map { (_, group) -> group.toCollectionItem() }
        }

    override fun getWishlistItems(): Flow<List<WishlistItem>> =
        wishlistDao.observeActive().map { rows -> rows.map { it.toWishlistItem() } }

    override suspend fun getCollectionSummary(): CollectionSummary =
        collectionSummaryOf(getCollectionItems().first())

    override suspend fun getThemeSummaries(): List<ThemeSummary> =
        themeSummariesOf(getCollectionItems().first())

    override suspend fun getSoldItems(): List<SoldItem> =
        salesDao.observeActive().first().map { entity ->
            SoldItem(
                setNumber = entity.setNumber, name = entity.name,
                itemType = entity.itemKind.toItemType(), theme = entity.theme,
                releaseYear = entity.releaseYear, releaseMonth = entity.releaseMonth,
                imageUrl = entity.imageUrl, retailPrice = entity.retailPrice ?: 0L,
                pricePaid = entity.pricePaid, saleValue = entity.salePrice,
            )
        }

    override suspend fun getSalesSummary(): SalesSummary {
        val sold = getSoldItems()
        val totalPaid = sold.sumOf { it.pricePaid }
        val totalProfit = sold.sumOf { it.profit }
        val avg = if (sold.isEmpty()) 0.0 else sold.map { it.profitPercent }.average()
        val overall = if (totalPaid == 0L) 0.0 else totalProfit.toDouble() / totalPaid * 100.0
        return SalesSummary(sold.size, sold.sumOf { it.saleValue }, totalProfit, avg, overall)
    }

    override fun searchCatalog(query: String): List<CatalogSet> = catalog.search(query)

    override fun getCatalog(): List<CatalogSet> = catalog.all()

    // ---- Writes (local Room first, then request a sync) ----

    override fun addItem(item: CollectionItem) = write {
        val set = resolveCatalog(item.setNumber)
        val kind = item.itemType.dbKind()
        val now = System.currentTimeMillis()
        item.copies.forEach { copy ->
            collectionDao.upsert(
                CollectionCopyEntity(
                    id = UUID.randomUUID().toString(),
                    setId = set?.setId, figNum = null, itemKind = kind,
                    setNumber = item.setNumber, name = item.name, theme = item.theme,
                    subtheme = set?.subtheme ?: "General",
                    releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                    pieces = item.pieces, minifigs = item.minifigs,
                    retailPrice = set?.retailPrice ?: item.retailPrice.takeIf { it > 0L },
                    status = item.status.name, imageUrl = item.imageUrl,
                    quantity = copy.qty, condition = copy.condition.dbName(),
                    pricePaid = copy.pricePaid, acquiredOn = copy.dateAdded.ifBlank { null },
                    notes = copy.note, deleted = false, updatedAt = now, dirty = true,
                ),
            )
        }
    }

    override fun removeCopy(setNumber: String, copyId: String) = write {
        collectionDao.markDeleted(copyId, System.currentTimeMillis())
    }

    override fun removeItem(setNumber: String) = write {
        collectionDao.markDeletedBySetNumber(setNumber, System.currentTimeMillis())
    }

    override fun updateCopy(setNumber: String, copy: Copy) = write {
        val existing = collectionDao.getById(copy.id) ?: return@write
        collectionDao.upsert(
            existing.copy(
                quantity = copy.qty, condition = copy.condition.dbName(),
                pricePaid = copy.pricePaid, acquiredOn = copy.dateAdded.ifBlank { null },
                notes = copy.note, updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
    }

    override fun addToWishlist(item: WishlistItem) = write {
        if (wishlistDao.findActiveBySetNumber(item.setNumber) != null) return@write
        val set = resolveCatalog(item.setNumber)
        wishlistDao.upsert(
            WishlistEntity(
                id = UUID.randomUUID().toString(),
                setId = set?.setId, figNum = null, itemKind = item.itemType.dbKind(),
                setNumber = item.setNumber, name = item.name, theme = item.theme,
                subtheme = set?.subtheme ?: "General",
                releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                pieces = item.pieces, minifigs = item.minifigs,
                retailPrice = set?.retailPrice ?: item.retailPrice.takeIf { it > 0L },
                status = item.status.name, imageUrl = item.imageUrl,
                deleted = false, updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
    }

    override fun removeFromWishlist(setNumber: String) = write {
        wishlistDao.markDeletedBySetNumber(setNumber, System.currentTimeMillis())
    }

    // ---- helpers ----

    /** Runs a local write off the main thread, then requests a background sync. */
    private fun write(block: suspend () -> Unit) {
        scope.launch {
            block()
            sync.requestSync()
        }
    }

    private suspend fun resolveCatalog(setNumber: String): CatalogSet? {
        catalog.refresh()
        return catalog.all().firstOrNull { it.setNumber == setNumber }
    }

    private fun ItemType.dbKind() = if (this == ItemType.MINIFIG) "minifig" else "set"
    private fun String.toItemType() = if (this == "minifig") ItemType.MINIFIG else ItemType.SET
    private fun Condition.dbName() = if (this == Condition.USED) "used" else "new"

    private fun List<CollectionCopyEntity>.toCollectionItem(): CollectionItem {
        val head = first()
        return CollectionItem(
            setNumber = head.setNumber, name = head.name, itemType = head.itemKind.toItemType(),
            theme = head.theme, releaseYear = head.releaseYear, releaseMonth = head.releaseMonth,
            pieces = head.pieces, minifigs = head.minifigs, retailPrice = head.retailPrice ?: 0L,
            currentValue = null, growthPercent = null, status = head.status.toAvailability(),
            imageUrl = head.imageUrl,
            copies = map { e ->
                Copy(
                    id = e.id,
                    condition = if (e.condition == "used") Condition.USED else Condition.NEW,
                    qty = e.quantity, pricePaid = e.pricePaid, dateAdded = e.acquiredOn ?: "",
                    note = e.notes,
                )
            },
        )
    }

    private fun WishlistEntity.toWishlistItem() = WishlistItem(
        setNumber = setNumber, name = name, itemType = itemKind.toItemType(), theme = theme,
        releaseYear = releaseYear, releaseMonth = releaseMonth, pieces = pieces, minifigs = minifigs,
        retailPrice = retailPrice ?: 0L, currentValue = null, growthPercent = null,
        status = status.toAvailability(), imageUrl = imageUrl,
    )

    private fun String.toAvailability(): Availability =
        runCatching { Availability.valueOf(this) }.getOrDefault(Availability.AVAILABLE)
}
