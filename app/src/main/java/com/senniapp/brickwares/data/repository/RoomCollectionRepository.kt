package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.WishlistEntity
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.data.model.WishlistItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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

    init {
        // Warm the catalog so the status overlay below has data to reconcile against (idempotent).
        scope.launch { catalog.refresh() }
    }

    // ---- Reads (Room is the source of truth) ----

    // Reads are combined with the catalog [revision] so that when the catalog (re)loads, the
    // catalog-derived status (e.g. RETIRED) is re-overlaid onto rows whose stored status is stale
    // (it was denormalized at add-time). Room stays the source of truth for user data; status is
    // reference data, so the live catalog value wins when available, falling back to the stored one.

    override fun getCollectionItems(): Flow<List<CollectionItem>> =
        combine(collectionDao.observeActive(), catalog.revision) { rows, _ ->
            rows.groupBy { it.setNumber }.map { (_, group) -> group.toCollectionItem() }
        }

    override fun getWishlistItems(): Flow<List<WishlistItem>> =
        combine(wishlistDao.observeActive(), catalog.revision) { rows, _ ->
            rows.map { it.toWishlistItem() }
        }

    override suspend fun getCollectionSummary(): CollectionSummary =
        collectionSummaryOf(getCollectionItems().first())

    override suspend fun getThemeSummaries(): List<ThemeSummary> =
        themeSummariesOf(getCollectionItems().first())

    override fun getSoldItems(): Flow<List<SoldItem>> =
        combine(salesDao.observeActive(), catalog.revision) { rows, _ ->
            rows.map { entity ->
                val cat = catalogFor(entity.setId, entity.setNumber)
                SoldItem(
                    id = entity.id,
                    setNumber = entity.setNumber, name = entity.name,
                    itemType = entity.itemKind.toItemType(), theme = entity.theme,
                    releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: entity.releaseYear,
                    releaseMonth = cat?.releaseMonth ?: entity.releaseMonth,
                    imageUrl = entity.imageUrl, retailPrice = entity.retailPrice ?: 0L,
                    pricePaid = entity.pricePaid, saleValue = entity.salePrice,
                    quantity = entity.quantity,
                    condition = if (entity.condition == "used") Condition.USED else Condition.NEW,
                    soldOn = entity.soldOn, note = entity.notes,
                )
            }
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
                    status = item.status.name, imageUrl = set?.imageUrl ?: item.imageUrl,
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

    override fun addSale(item: CollectionItem, salePrice: Long) = write {
        val set = resolveCatalog(item.setNumber)
        val copy = item.copies.firstOrNull()
        salesDao.upsert(
            SalesEntity(
                id = UUID.randomUUID().toString(),
                setId = set?.setId, figNum = null, itemKind = item.itemType.dbKind(),
                setNumber = item.setNumber, name = item.name, theme = item.theme,
                releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                imageUrl = set?.imageUrl ?: item.imageUrl,
                retailPrice = set?.retailPrice ?: item.retailPrice.takeIf { it > 0L },
                quantity = copy?.qty ?: 1,
                condition = copy?.condition?.dbName() ?: "new",
                pricePaid = copy?.pricePaid ?: 0L, salePrice = salePrice,
                soldOn = copy?.dateAdded?.ifBlank { null },
                notes = copy?.note, deleted = false,
                updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
    }

    override fun sellCopy(setNumber: String, copyId: String, quantity: Int, salePrice: Long, soldOn: String?) = write {
        val copy = collectionDao.getById(copyId) ?: return@write
        val available = copy.quantity
        val sellQty = quantity.coerceIn(1, available)
        // Prorate the copy's paid cost so profit is a fair basis and paid stays conserved between
        // the remaining copy and the sale (whole-copy sale → full cost basis).
        val soldPaid = if (available <= 0) 0L else copy.pricePaid * sellQty / available
        val now = System.currentTimeMillis()
        salesDao.upsert(
            SalesEntity(
                id = UUID.randomUUID().toString(),
                setId = copy.setId, figNum = copy.figNum, itemKind = copy.itemKind,
                setNumber = copy.setNumber, name = copy.name, theme = copy.theme,
                releaseYear = copy.releaseYear, releaseMonth = copy.releaseMonth,
                imageUrl = copy.imageUrl, retailPrice = copy.retailPrice,
                quantity = sellQty, condition = copy.condition,
                pricePaid = soldPaid, salePrice = salePrice,
                soldOn = soldOn?.ifBlank { null }, notes = copy.notes,
                deleted = false, updatedAt = now, dirty = true,
            ),
        )
        if (sellQty >= available) {
            collectionDao.markDeleted(copyId, now)
        } else {
            collectionDao.upsert(
                copy.copy(
                    quantity = available - sellQty,
                    pricePaid = copy.pricePaid - soldPaid,
                    updatedAt = now, dirty = true,
                ),
            )
        }
    }

    override fun updateSale(
        saleId: String,
        quantity: Int,
        condition: Condition,
        pricePaid: Long,
        salePrice: Long,
        soldOn: String?,
        note: String?,
    ) = write {
        val existing = salesDao.getById(saleId) ?: return@write
        salesDao.upsert(
            existing.copy(
                quantity = quantity, condition = condition.dbName(),
                pricePaid = pricePaid, salePrice = salePrice,
                soldOn = soldOn?.ifBlank { null }, notes = note,
                updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
    }

    override fun removeSale(saleId: String) = write {
        salesDao.markDeleted(saleId, System.currentTimeMillis())
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
                status = item.status.name, imageUrl = set?.imageUrl ?: item.imageUrl,
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

    /**
     * The live catalog record for a user row (matched by set_id, else set number), or null when the
     * catalog isn't loaded (offline / not yet fetched) — callers then keep the stored denormalized
     * values. Lets reference fields (status, and the release month/year — which older rows saved as
     * month 0 before ingestion derived it) refresh to the current catalog value.
     */
    private fun catalogFor(setId: Long?, setNumber: String): CatalogSet? =
        catalog.all().firstOrNull { c ->
            (setId != null && c.setId == setId) || c.setNumber == setNumber
        }

    private fun List<CollectionCopyEntity>.toCollectionItem(): CollectionItem {
        val head = first()
        val cat = catalogFor(head.setId, head.setNumber)
        return CollectionItem(
            setNumber = head.setNumber, name = head.name, itemType = head.itemKind.toItemType(),
            theme = head.theme,
            releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: head.releaseYear,
            releaseMonth = cat?.releaseMonth ?: head.releaseMonth,
            pieces = head.pieces, minifigs = head.minifigs, retailPrice = head.retailPrice ?: 0L,
            currentValue = null, growthPercent = null,
            status = cat?.status ?: head.status.toAvailability(),
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

    private fun WishlistEntity.toWishlistItem(): WishlistItem {
        val cat = catalogFor(setId, setNumber)
        return WishlistItem(
            setNumber = setNumber, name = name, itemType = itemKind.toItemType(), theme = theme,
            releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: releaseYear,
            releaseMonth = cat?.releaseMonth ?: releaseMonth,
            pieces = pieces, minifigs = minifigs,
            retailPrice = retailPrice ?: 0L, currentValue = null, growthPercent = null,
            status = cat?.status ?: status.toAvailability(), imageUrl = imageUrl,
        )
    }

    private fun String.toAvailability(): Availability =
        runCatching { Availability.valueOf(this) }.getOrDefault(Availability.AVAILABLE)
}
