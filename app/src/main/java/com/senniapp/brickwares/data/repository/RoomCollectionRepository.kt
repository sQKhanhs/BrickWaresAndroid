package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.CurrencyPrefs
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.WishlistEntity
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.Condition
import com.senniapp.brickwares.data.model.Copy
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary
import com.senniapp.brickwares.data.model.ValueAggregator
import com.senniapp.brickwares.data.model.ValueGuardTier
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.CurrencyConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
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
    private val values: ValueContributionRepository = ValueRepositoryProvider.instance,
) : CollectionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collectionDao = db.collectionDao()
    private val wishlistDao = db.wishlistDao()
    private val salesDao = db.salesDao()

    // User-scoped catalog cache (Decision 16): only the sets/figs the user references, so the overlays
    // below get fresh reference data (status, box image, release-month backfill, minifig image/set-count)
    // without the client holding the whole ~23k-set catalog in memory. Reads resolve against these maps;
    // a background observer rebuilds them (one batch query) whenever the referenced keys change, bumping
    // [catalogCacheRevision] so the read flows re-run. Empty until the first fetch (and after an offline
    // failure) — the reads then fall back to each row's denormalized fields, exactly as before.
    @Volatile private var setsById: Map<Long, CatalogSet> = emptyMap()
    @Volatile private var setsByNumber: Map<String, CatalogSet> = emptyMap()
    @Volatile private var figsByNum: Map<String, Minifig> = emptyMap()
    private val catalogCacheRevision = MutableStateFlow(0)
    private val _catalogOverlayReady = MutableStateFlow(false)
    override val catalogOverlayReady: StateFlow<Boolean> = _catalogOverlayReady.asStateFlow()

    init {
        // Warm the community value cache so cards can show the "Value" line (Decision 17).
        scope.launch { values.warm() }
        // Keep the user-scoped catalog cache in sync with the user's rows (see above).
        scope.launch { observeReferencedCatalog() }
    }

    /**
     * Watches the user's collection/wishlist/sales rows and, whenever the set of referenced set numbers
     * / fig numbers changes, rebuilds [setsByNumber]/[setsById]/[figsByNum] from one batch catalog query.
     */
    private suspend fun observeReferencedCatalog() {
        combine(
            collectionDao.observeActive(),
            wishlistDao.observeActive(),
            salesDao.observeActive(),
        ) { copies, wishes, sales ->
            referencedKeys(copies, wishes, sales)
        }.distinctUntilChanged().collect { keys ->
            refreshCatalogCache(keys)
        }
    }

    private data class ReferencedKeys(val setNumbers: Set<String>, val figNums: Set<String>, val setIds: Set<Long>)

    /**
     * The distinct catalog keys the user's rows reference: set numbers + fig numbers (for the byNumber /
     * fig caches) AND the exact `set_id`s — so a shared-number variant (CMF/SDCC) resolves to the precise
     * set the row stores, not the number's lowest variant.
     */
    private fun referencedKeys(
        copies: List<CollectionCopyEntity>,
        wishes: List<WishlistEntity>,
        sales: List<SalesEntity>,
    ): ReferencedKeys {
        val setNumbers = HashSet<String>()
        val figNums = HashSet<String>()
        val setIds = HashSet<Long>()
        fun add(kind: String, setNumber: String, figNum: String?, setId: Long?) {
            if (kind == "minifig") {
                // A minifig row's fig key is its fig_num, or (legacy rows) the set_number field.
                figNum?.let(figNums::add)
                figNums.add(setNumber)
            } else {
                setNumbers.add(setNumber)
                setId?.let(setIds::add)
            }
        }
        copies.forEach { add(it.itemKind, it.setNumber, it.figNum, it.setId) }
        wishes.forEach { add(it.itemKind, it.setNumber, it.figNum, it.setId) }
        sales.forEach { add(it.itemKind, it.setNumber, it.figNum, it.setId) }
        return ReferencedKeys(setNumbers, figNums, setIds)
    }

    /** One batch refresh of the user-scoped catalog maps; keeps the last-known cache on network failure. */
    private suspend fun refreshCatalogCache(keys: ReferencedKeys) {
        try {
            fetchAndStoreCatalog(keys)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep the last-known cache; a later row change or reconnect retries. The reads still render
            // from the rows' denormalized fields in the meantime.
            Timber.tag("RoomCollectionRepository").w(e, "Referenced-catalog cache refresh failed")
            catalogCacheRevision.value += 1 // nudge the reads to fall back rather than wait forever
        }
    }

    /** Fetch + store the user-scoped catalog maps and bump the revision; throws on a network failure. */
    private suspend fun fetchAndStoreCatalog(keys: ReferencedKeys) {
        val byNumber = catalog.fetchSetsByNumbers(keys.setNumbers)
        val byId = if (keys.setIds.isEmpty()) emptyList() else catalog.fetchSetsByIds(keys.setIds)
        setsByNumber = byNumber.associateBy { it.setNumber }
        // Exact-variant rows (byId) win in setsById; the lowest-variant byNumber sets fill any others, so
        // catalogFor(setId) resolves the precise CMF/SDCC variant the row stores, not the number's lowest.
        setsById = (byNumber + byId).mapNotNull { s -> s.setId?.let { it to s } }.toMap()
        val figs = catalog.fetchMinifigsByNums(keys.figNums)
        figsByNum = figs.associateBy { it.figNum }
        _catalogOverlayReady.value = true // authoritative overlay is loaded (set before the revision bump)
        catalogCacheRevision.value += 1
    }

    override suspend fun refreshReferencedCatalog() {
        // throws on failure so the caller (retirement worker) retries
        fetchAndStoreCatalog(referencedKeys(collectionDao.getActive(), wishlistDao.getActive(), salesDao.getActive()))
    }

    // ---- Reads (Room is the source of truth) ----

    // Reads are combined with the catalog [revision] so that when the catalog (re)loads, the
    // catalog-derived status (e.g. RETIRED) is re-overlaid onto rows whose stored status is stale
    // (it was denormalized at add-time). Room stays the source of truth for user data; status is
    // reference data, so the live catalog value wins when available, falling back to the stored one.
    // The mapping runs on Dispatchers.Default (flowOn): combine's transform otherwise executes in the
    // collector's coroutine — every ViewModel's viewModelScope, i.e. the main thread.

    override fun getCollectionItems(): Flow<List<CollectionItem>> =
        combine(collectionDao.observeActive(), catalogCacheRevision, values.revision) { rows, _, _ ->
            // Group by the EXACT item, not the bare number — a shared-number set (CMF/SDCC) resolves by
            // set_id so its variants are separate cards, while minifigs / legacy rows fall back to number.
            rows.groupBy { it.variantKey() }.map { (_, group) -> group.toCollectionItem() }
        }.flowOn(Dispatchers.Default)

    override fun getWishlistItems(): Flow<List<WishlistItem>> =
        combine(wishlistDao.observeActive(), catalogCacheRevision, values.revision) { rows, _, _ ->
            rows.map { it.toWishlistItem() }
        }.flowOn(Dispatchers.Default)

    override suspend fun getCollectionSummary(): CollectionSummary =
        collectionSummaryOf(getCollectionItems().first(), CurrencyPrefs.current)

    override suspend fun getThemeSummaries(): List<ThemeSummary> =
        themeSummariesOf(getCollectionItems().first(), CurrencyPrefs.current)

    // ---- CSV export / import (Settings → Data) ----

    override suspend fun exportCollectionCsv(): String =
        CollectionCsv.encode(
            copies = collectionDao.getActive(),
            sales = salesDao.getActive(),
            wishlist = wishlistDao.getActive(),
        )

    override suspend fun importCollectionCsv(csv: String): Int {
        val parsed = CollectionCsv.parse(csv)
        // Reject a file that isn't a BrickWares export BEFORE touching anything, so picking the wrong
        // file can't silently wipe user data (a valid but empty export is allowed — it clears everything).
        require("set_number" in parsed.header && "item_kind" in parsed.header) {
            "Not a BrickWares export"
        }
        // Refuse a file from a newer app version rather than importing it with silently-dropped fields
        // (or, pre-v2, mis-reading sale/wishlist rows as collection copies).
        val version = CollectionCsv.versionOf(parsed)
        if (version > CollectionCsv.FORMAT_VERSION) throw CsvTooNewException(version)
        val now = System.currentTimeMillis()
        // A hand-edited file may omit set_id — resolve those in ONE batch catalog query (Decision 16),
        // rather than a per-row lookup against a full in-memory catalog. Minifig rows carry no set_id.
        val setIdByNumber: Map<String, Long> = run {
            val numbers = parsed.rows.mapNotNull { row ->
                fun s(k: String) = row[k]?.trim()?.ifBlank { null }
                val isFig = s("item_kind")?.lowercase() == "minifig"
                if (isFig || s("set_id") != null) null else s("set_number")
            }.toSet()
            runCatching { catalog.fetchSetsByNumbers(numbers) }.getOrDefault(emptyList())
                .mapNotNull { st -> st.setId?.let { st.setNumber to it } }.toMap()
        }
        // Split the tagged rows into the three lists (a v1 file has no record_type → all collection).
        val copies = mutableListOf<CollectionCopyEntity>()
        val sales = mutableListOf<SalesEntity>()
        val wishlist = mutableListOf<WishlistEntity>()
        for (row in parsed.rows) {
            when (CollectionCsv.recordType(row)) {
                "sale" -> row.toImportedSale(now, setIdByNumber)?.let(sales::add)
                "wishlist" -> row.toImportedWishlist(now, setIdByNumber)?.let(wishlist::add)
                else -> row.toImportedCopy(now, setIdByNumber)?.let(copies::add)
            }
        }
        // Overwrite all three tables: tombstone the current rows (so the removals push), then insert the
        // imported rows. Doing every table under one import keeps the file a full snapshot restore.
        collectionDao.markAllActiveDeleted(now)
        salesDao.markAllActiveDeleted(now)
        wishlistDao.markAllActiveDeleted(now)
        if (copies.isNotEmpty()) collectionDao.upsertAll(copies)
        if (sales.isNotEmpty()) salesDao.upsertAll(sales)
        if (wishlist.isNotEmpty()) wishlistDao.upsertAll(wishlist)
        // Sync now and wait, so the caller can hold a loading screen until the overwrite has pushed +
        // pulled (and the value cache re-warmed). Best-effort: if it fails (offline) the data is already
        // local and a reconnect sync will push it later.
        sync.syncNow()
        return copies.size + sales.size + wishlist.size
    }

    /** One CSV row → a fresh dirty [CollectionCopyEntity]; null when it has no usable item identity. */
    private fun Map<String, String>.toImportedCopy(now: Long, setIdByNumber: Map<String, Long>): CollectionCopyEntity? {
        fun s(key: String) = this[key]?.trim()?.ifBlank { null }
        val kind = s("item_kind")?.lowercase()?.takeIf { it == "minifig" } ?: "set"
        val isFig = kind == "minifig"
        val figNum = s("fig_num") ?: if (isFig) s("set_number") else null
        val setNumber = s("set_number") ?: figNum ?: return null
        // The sync key: from the file, else resolved from the catalog (a hand-edited file may omit it).
        val setId = s("set_id")?.toLongOrNull() ?: if (!isFig) setIdByNumber[setNumber] else null
        val currency = s("currency")?.let { runCatching { AppCurrency.valueOf(it.uppercase()) }.getOrNull() }?.name ?: "USD"
        return CollectionCopyEntity(
            id = UUID.randomUUID().toString(),
            setId = setId, figNum = if (isFig) (figNum ?: setNumber) else figNum, itemKind = kind,
            setNumber = setNumber, name = s("name").orEmpty(),
            theme = s("theme") ?: "Unknown", subtheme = s("subtheme") ?: "General",
            releaseYear = s("release_year")?.toIntOrNull() ?: 0,
            releaseMonth = s("release_month")?.toIntOrNull() ?: 0,
            pieces = s("pieces")?.toIntOrNull() ?: 0,
            minifigs = s("minifigs")?.toIntOrNull() ?: 0,
            retailPrice = s("retail_price")?.toLongOrNull(),
            status = s("status") ?: Availability.AVAILABLE.name,
            imageUrl = s("image_url"),
            quantity = (s("quantity")?.toIntOrNull() ?: 1).coerceAtLeast(1),
            condition = s("condition")?.lowercase()?.takeIf { it == "used" } ?: "new",
            pricePaid = s("price_paid")?.toLongOrNull() ?: 0L,
            currency = currency,
            acquiredOn = s("acquired_on"),
            notes = s("notes"),
            deleted = false, updatedAt = now, dirty = true,
        )
    }

    /** One CSV row → a fresh dirty [SalesEntity]; null when it has no usable item identity. */
    private fun Map<String, String>.toImportedSale(now: Long, setIdByNumber: Map<String, Long>): SalesEntity? {
        fun s(key: String) = this[key]?.trim()?.ifBlank { null }
        val kind = s("item_kind")?.lowercase()?.takeIf { it == "minifig" } ?: "set"
        val isFig = kind == "minifig"
        val figNum = s("fig_num") ?: if (isFig) s("set_number") else null
        val setNumber = s("set_number") ?: figNum ?: return null
        val setId = s("set_id")?.toLongOrNull() ?: if (!isFig) setIdByNumber[setNumber] else null
        val currency = s("currency")?.let { runCatching { AppCurrency.valueOf(it.uppercase()) }.getOrNull() }?.name ?: "USD"
        return SalesEntity(
            id = UUID.randomUUID().toString(),
            setId = setId, figNum = if (isFig) (figNum ?: setNumber) else figNum, itemKind = kind,
            setNumber = setNumber, name = s("name").orEmpty(),
            theme = s("theme") ?: "Unknown",
            releaseYear = s("release_year")?.toIntOrNull() ?: 0,
            releaseMonth = s("release_month")?.toIntOrNull() ?: 0,
            imageUrl = s("image_url"),
            retailPrice = s("retail_price")?.toLongOrNull(),
            quantity = (s("quantity")?.toIntOrNull() ?: 1).coerceAtLeast(1),
            condition = s("condition")?.lowercase()?.takeIf { it == "used" } ?: "new",
            pricePaid = s("price_paid")?.toLongOrNull() ?: 0L,
            salePrice = s("sale_price")?.toLongOrNull() ?: 0L,
            currency = currency,
            soldOn = s("sold_on"),
            notes = s("notes"),
            deleted = false, updatedAt = now, dirty = true,
        )
    }

    /** One CSV row → a fresh dirty [WishlistEntity]; null when it has no usable item identity. */
    private fun Map<String, String>.toImportedWishlist(now: Long, setIdByNumber: Map<String, Long>): WishlistEntity? {
        fun s(key: String) = this[key]?.trim()?.ifBlank { null }
        val kind = s("item_kind")?.lowercase()?.takeIf { it == "minifig" } ?: "set"
        val isFig = kind == "minifig"
        val figNum = s("fig_num") ?: if (isFig) s("set_number") else null
        val setNumber = s("set_number") ?: figNum ?: return null
        val setId = s("set_id")?.toLongOrNull() ?: if (!isFig) setIdByNumber[setNumber] else null
        return WishlistEntity(
            id = UUID.randomUUID().toString(),
            setId = setId, figNum = if (isFig) (figNum ?: setNumber) else figNum, itemKind = kind,
            setNumber = setNumber, name = s("name").orEmpty(),
            theme = s("theme") ?: "Unknown", subtheme = s("subtheme") ?: "General",
            releaseYear = s("release_year")?.toIntOrNull() ?: 0,
            releaseMonth = s("release_month")?.toIntOrNull() ?: 0,
            pieces = s("pieces")?.toIntOrNull() ?: 0,
            minifigs = s("minifigs")?.toIntOrNull() ?: 0,
            retailPrice = s("retail_price")?.toLongOrNull(),
            status = s("status") ?: Availability.AVAILABLE.name,
            imageUrl = s("image_url"),
            deleted = false, updatedAt = now, dirty = true,
        )
    }

    override fun getSoldItems(): Flow<List<SoldItem>> =
        combine(salesDao.observeActive(), catalogCacheRevision, values.revision) { rows, _, _ ->
            rows.map { entity ->
                val cat = catalogFor(entity.setId, entity.setNumber)
                val value = if (entity.figNum != null) values.valueForFig(entity.figNum) else values.valueFor(entity.setId)
                SoldItem(
                    id = entity.id,
                    setNumber = entity.setNumber, setId = entity.setId, name = entity.name,
                    itemType = entity.itemKind.toItemType(), theme = entity.theme,
                    releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: entity.releaseYear,
                    releaseMonth = cat?.releaseMonth ?: entity.releaseMonth,
                    pieces = cat?.pieces ?: 0, minifigs = cat?.minifigs ?: 0,
                    // Recover the minifig image from the catalog: prefer fig_num, else the setNumber
                    // field (holds the fig_num for minifigs) so pre-fix sales without a fig_num recover.
                    imageUrl = minifigFor(entity.figNum ?: entity.setNumber.takeIf { entity.itemKind == "minifig" })?.imageUrl
                        ?: entity.imageUrl,
                    boxImageUrl = cat?.boxImageUrl,
                    retailPrice = entity.retailPrice ?: 0L,
                    pricePaid = entity.pricePaid, saleValue = entity.salePrice,
                    currency = entity.currency.toCurrency(),
                    quantity = entity.quantity,
                    condition = if (entity.condition == "used") Condition.USED else Condition.NEW,
                    soldOn = entity.soldOn, note = entity.notes,
                    status = cat?.status ?: Availability.AVAILABLE,
                    currentValueInfo = value,
                )
            }
        }.flowOn(Dispatchers.Default)

    // ---- Writes (local Room first, then request a sync) ----

    override fun addItem(item: CollectionItem) = write {
        val kind = item.itemType.dbKind()
        // A minifig item is keyed by its fig_num (stored in setNumber) — no catalog set to resolve.
        val isFig = kind == "minifig"
        val set = if (isFig) null else resolveCatalog(item.setNumber)
        val now = System.currentTimeMillis()
        // Existing copies of this item — a newly-added copy identical in condition, paid price, date and
        // note merges into one (bumps its quantity) instead of adding a duplicate row.
        val existingCopies = activeCopiesOf(item.setId, item.setNumber, kind)
        item.copies.forEach { copy ->
            val cond = copy.condition.dbName()
            val date = copy.dateAdded.ifBlank { null }
            // Match on per-unit paid (cross-multiply avoids integer-division rounding, and keeps
            // matching as the merged row's total grows) so a repeated identical add bumps quantity.
            // Same currency required — amounts in different units aren't comparable/mergeable.
            val match = existingCopies.firstOrNull {
                it.condition == cond && it.acquiredOn == date &&
                    it.notes.orEmpty() == copy.note.orEmpty() && it.currency == copy.currency.name &&
                    it.pricePaid * copy.qty == copy.pricePaid * it.quantity
            }
            if (match != null) {
                // pricePaid is the total for a copy's qty, so sum it alongside the quantity.
                collectionDao.upsert(
                    match.copy(
                        quantity = match.quantity + copy.qty,
                        pricePaid = match.pricePaid + copy.pricePaid,
                        updatedAt = now, dirty = true,
                    ),
                )
            } else {
                collectionDao.upsert(
                    CollectionCopyEntity(
                        id = UUID.randomUUID().toString(),
                        // Prefer the item's own (SELECTED variant) fields; `set` is the number-resolved
                        // LOWEST variant, kept only as a fallback for legacy items with no setId/image/retail
                        // — for CMF/SDCC shared numbers the two differ, and the item's are the right ones.
                        setId = item.setId ?: set?.setId, figNum = if (isFig) item.setNumber else null, itemKind = kind,
                        setNumber = item.setNumber, name = item.name, theme = item.theme,
                        subtheme = set?.subtheme ?: "General",
                        releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                        pieces = item.pieces, minifigs = item.minifigs,
                        retailPrice = item.retailPrice.takeIf { it > 0L } ?: set?.retailPrice,
                        status = item.status.name, imageUrl = item.imageUrl ?: set?.imageUrl,
                        quantity = copy.qty, condition = cond,
                        pricePaid = copy.pricePaid, currency = copy.currency.name, acquiredOn = date,
                        notes = copy.note, deleted = false, updatedAt = now, dirty = true,
                    ),
                )
            }
        }
        // Reflect the paid price in the community value cache now (Decision 17) — one point per user,
        // so the last copy's paid represents this set/fig (mirrors the sync's upsert-per-item).
        item.copies.lastOrNull()?.let { copy ->
            contributeLocalValue(if (isFig) null else (item.setId ?: set?.setId), if (isFig) item.setNumber else null, item.setNumber, copy.pricePaid, copy.currency, isSale = false)
        }
        // Owning an item removes it from the wishlist (want → have) — applies to EVERY add path (search,
        // detail, collection, or the wishlist "Move"). No-op when it wasn't wishlisted; marks the row
        // deleted+dirty so the removal syncs. Keyed by set_id for a cataloged set (so owning 71050-2
        // only clears 71050-2 from the wishlist), else by set number (a minifig's fig_num is stored there).
        if (item.setId != null) wishlistDao.markDeletedBySetId(item.setId, now)
        else wishlistDao.markDeletedBySetNumber(item.setNumber, now)
    }

    override fun removeCopy(setNumber: String, copyId: String) = write {
        collectionDao.markDeleted(copyId, System.currentTimeMillis())
    }

    override fun removeItem(setNumber: String, setId: Long?) = write {
        val now = System.currentTimeMillis()
        // By set_id for a cataloged set (removes just this shared-number variant), else by set number.
        if (setId != null) collectionDao.markDeletedBySetId(setId, now)
        else collectionDao.markDeletedBySetNumber(setNumber, now)
    }

    override fun updateCopy(setNumber: String, copy: Copy) = write {
        val existing = collectionDao.getById(copy.id) ?: return@write
        collectionDao.upsert(
            existing.copy(
                quantity = copy.qty, condition = copy.condition.dbName(),
                pricePaid = copy.pricePaid, currency = copy.currency.name,
                acquiredOn = copy.dateAdded.ifBlank { null },
                notes = copy.note, updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
        // Reflect the edited paid price in the community value cache immediately (Decision 17).
        contributeLocalValue(existing.setId, existing.figNum, existing.setNumber, copy.pricePaid, copy.currency, isSale = false)
    }

    override fun addSale(item: CollectionItem, salePrice: Long) = write {
        val kind = item.itemType.dbKind()
        // A minifig sale is keyed by its fig_num (stored in setNumber) — no catalog set to resolve.
        val isFig = kind == "minifig"
        val set = if (isFig) null else resolveCatalog(item.setNumber)
        val copy = item.copies.firstOrNull()
        val now = System.currentTimeMillis()
        val cond = copy?.condition?.dbName() ?: "new"
        val qty = copy?.qty ?: 1
        val paid = copy?.pricePaid ?: 0L
        // Paid + sale were entered together in one currency (recorded, not converted).
        val currency = copy?.currency ?: AppCurrency.USD
        val soldOn = copy?.dateAdded?.ifBlank { null }
        val note = copy?.note
        // An identical sale (condition, currency, per-unit paid, per-unit sale price, date, note) merges
        // into one row — bumps quantity and sums the paid/sale totals — instead of adding a duplicate.
        val match = activeSalesOf(item.setId, item.setNumber, kind).firstOrNull {
            it.condition == cond && it.soldOn == soldOn && it.notes.orEmpty() == note.orEmpty() &&
                it.currency == currency.name &&
                it.pricePaid * qty == paid * it.quantity &&
                it.salePrice * qty == salePrice * it.quantity
        }
        if (match != null) {
            salesDao.upsert(
                match.copy(
                    quantity = match.quantity + qty,
                    pricePaid = match.pricePaid + paid,
                    salePrice = match.salePrice + salePrice,
                    updatedAt = now, dirty = true,
                ),
            )
        } else {
            salesDao.upsert(
                SalesEntity(
                    id = UUID.randomUUID().toString(),
                    // Prefer the item's own (SELECTED variant) fields over the number-resolved lowest
                    // variant `set` (they differ for CMF/SDCC shared numbers) — see addItem.
                    setId = item.setId ?: set?.setId, figNum = if (isFig) item.setNumber else null, itemKind = kind,
                    setNumber = item.setNumber, name = item.name, theme = item.theme,
                    releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                    imageUrl = item.imageUrl ?: set?.imageUrl,
                    retailPrice = item.retailPrice.takeIf { it > 0L } ?: set?.retailPrice,
                    quantity = qty, condition = cond,
                    pricePaid = paid, salePrice = salePrice, currency = currency.name,
                    soldOn = soldOn, notes = note, deleted = false,
                    updatedAt = now, dirty = true,
                ),
            )
        }
        // A sale price is a community value point too (Decision 17) — reflect it locally at once.
        contributeLocalValue(if (isFig) null else (item.setId ?: set?.setId), if (isFig) item.setNumber else null, item.setNumber, salePrice, currency, isSale = true)
    }

    override fun sellCopy(setNumber: String, copyId: String, quantity: Int, salePrice: Long, currency: AppCurrency, soldOn: String?) = write {
        val copy = collectionDao.getById(copyId) ?: return@write
        val available = copy.quantity
        // Nothing to sell (a legacy 0-quantity copy) — bail before coerceIn(1, 0) throws on the empty range.
        if (available <= 0) return@write
        val sellQty = quantity.coerceIn(1, available)
        // Prorate the copy's paid cost so profit is a fair basis and paid stays conserved between
        // the remaining copy and the sale (whole-copy sale → full cost basis). The sale price is typed
        // in the current display [currency]; convert the copy's cost basis (in the copy's own currency)
        // into it so the sale row is single-currency.
        val soldPaidCopyCcy = if (available <= 0) 0L else copy.pricePaid * sellQty / available
        val copyCurrency = runCatching { AppCurrency.valueOf(copy.currency) }.getOrDefault(AppCurrency.USD)
        val soldPaid = convertAmount(soldPaidCopyCcy, copyCurrency, currency)
        val now = System.currentTimeMillis()
        val soldOnNorm = soldOn?.ifBlank { null }
        // Selling identical copies one at a time (same condition, currency, per-unit paid, per-unit sale,
        // date, note) merges into one sales row instead of piling up duplicate rows — mirrors addSale.
        val match = activeSalesOf(copy.setId, copy.setNumber, copy.itemKind).firstOrNull {
            it.condition == copy.condition && it.soldOn == soldOnNorm &&
                it.notes.orEmpty() == copy.notes.orEmpty() && it.currency == currency.name &&
                it.pricePaid * sellQty == soldPaid * it.quantity &&
                it.salePrice * sellQty == salePrice * it.quantity
        }
        if (match != null) {
            salesDao.upsert(
                match.copy(
                    quantity = match.quantity + sellQty,
                    pricePaid = match.pricePaid + soldPaid,
                    salePrice = match.salePrice + salePrice,
                    updatedAt = now, dirty = true,
                ),
            )
        } else {
            salesDao.upsert(
                SalesEntity(
                    id = UUID.randomUUID().toString(),
                    setId = copy.setId, figNum = copy.figNum, itemKind = copy.itemKind,
                    setNumber = copy.setNumber, name = copy.name, theme = copy.theme,
                    releaseYear = copy.releaseYear, releaseMonth = copy.releaseMonth,
                    imageUrl = copy.imageUrl, retailPrice = copy.retailPrice,
                    quantity = sellQty, condition = copy.condition,
                    pricePaid = soldPaid, salePrice = salePrice, currency = currency.name,
                    soldOn = soldOnNorm, notes = copy.notes,
                    deleted = false, updatedAt = now, dirty = true,
                ),
            )
        }
        if (sellQty >= available) {
            collectionDao.markDeleted(copyId, now)
        } else {
            collectionDao.upsert(
                copy.copy(
                    quantity = available - sellQty,
                    pricePaid = copy.pricePaid - soldPaidCopyCcy,
                    updatedAt = now, dirty = true,
                ),
            )
        }
        // The sale price seeds the community value too (Decision 17).
        contributeLocalValue(copy.setId, copy.figNum, copy.setNumber, salePrice, currency, isSale = true)
    }

    override fun updateSale(
        saleId: String,
        quantity: Int,
        condition: Condition,
        pricePaid: Long,
        salePrice: Long,
        currency: AppCurrency,
        soldOn: String?,
        note: String?,
    ) = write {
        val existing = salesDao.getById(saleId) ?: return@write
        salesDao.upsert(
            existing.copy(
                quantity = quantity, condition = condition.dbName(),
                pricePaid = pricePaid, salePrice = salePrice, currency = currency.name,
                soldOn = soldOn?.ifBlank { null }, notes = note,
                updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
        // Reflect the edited sale price in the community value cache immediately (Decision 17).
        contributeLocalValue(existing.setId, existing.figNum, existing.setNumber, salePrice, currency, isSale = true)
    }

    override fun removeSale(saleId: String) = write {
        salesDao.markDeleted(saleId, System.currentTimeMillis())
    }

    override fun addToWishlist(item: WishlistItem) = write {
        // Already wishlisted? Dedup by the exact set (set_id) for a cataloged set, so a shared-number
        // variant doesn't block another; else by set number (minifig / legacy).
        val already = if (item.setId != null) wishlistDao.findActiveBySetId(item.setId)
        else wishlistDao.findActiveBySetNumber(item.setNumber)
        if (already != null) return@write
        val kind = item.itemType.dbKind()
        val isFig = kind == "minifig"
        val set = if (isFig) null else resolveCatalog(item.setNumber)
        wishlistDao.upsert(
            WishlistEntity(
                id = UUID.randomUUID().toString(),
                // Prefer the item's own (SELECTED variant) fields over the number-resolved lowest variant
                // `set` (they differ for CMF/SDCC shared numbers) — see addItem.
                setId = item.setId ?: set?.setId, figNum = if (isFig) item.setNumber else null, itemKind = kind,
                setNumber = item.setNumber, name = item.name, theme = item.theme,
                subtheme = set?.subtheme ?: "General",
                releaseYear = item.releaseYear, releaseMonth = item.releaseMonth,
                pieces = item.pieces, minifigs = item.minifigs,
                retailPrice = item.retailPrice.takeIf { it > 0L } ?: set?.retailPrice,
                status = item.status.name, imageUrl = item.imageUrl ?: set?.imageUrl,
                deleted = false, updatedAt = System.currentTimeMillis(), dirty = true,
            ),
        )
    }

    override fun removeFromWishlist(setNumber: String, setId: Long?) = write {
        val now = System.currentTimeMillis()
        // By set_id for a cataloged set (removes just this shared-number variant), else by set number.
        if (setId != null) wishlistDao.markDeletedBySetId(setId, now)
        else wishlistDao.markDeletedBySetNumber(setNumber, now)
    }

    // ---- helpers ----

    /** Runs a local write off the main thread, then requests a background sync. */
    private fun write(block: suspend () -> Unit) {
        scope.launch {
            block()
            sync.requestSync()
        }
    }

    /** The catalog set for an add/wishlist write — the user-scoped cache first, else a one-set query. */
    private suspend fun resolveCatalog(setNumber: String): CatalogSet? =
        setsByNumber[setNumber] ?: try {
            catalog.fetchSet(setNumber)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun ItemType.dbKind() = if (this == ItemType.MINIFIG) "minifig" else "set"
    private fun String.toItemType() = if (this == "minifig") ItemType.MINIFIG else ItemType.SET

    /**
     * The identity used to GROUP copies into one card and to match/remove an item. A cataloged set keys
     * on its `set_id` so shared-number variants (CMF/SDCC: 71050-2 vs 71050-4) stay distinct; a minifig
     * or a legacy set with no set_id falls back to its fig_num / set number.
     */
    private fun CollectionCopyEntity.variantKey(): String =
        setId?.let { "s$it" } ?: "n${figNum ?: setNumber}"

    /** Active copies of the EXACT item — by set_id for a cataloged set, else by set number (minifig/legacy). */
    private suspend fun activeCopiesOf(setId: Long?, setNumber: String, kind: String): List<CollectionCopyEntity> =
        if (setId != null) collectionDao.activeForSetId(setId) else collectionDao.activeForItem(setNumber, kind)

    /** Active sales of the EXACT item — by set_id for a cataloged set, else by set number (minifig/legacy). */
    private suspend fun activeSalesOf(setId: Long?, setNumber: String, kind: String): List<SalesEntity> =
        if (setId != null) salesDao.activeForSetId(setId) else salesDao.activeForItem(setNumber, kind)
    private fun Condition.dbName() = if (this == Condition.USED) "used" else "new"
    private fun String.toCurrency() = runCatching { AppCurrency.valueOf(this) }.getOrDefault(AppCurrency.USD)

    /** Convert [amount] from currency [from] to [to] (both in their own unit); identity when equal. */
    private fun convertAmount(amount: Long, from: AppCurrency, to: AppCurrency): Long =
        if (from == to) amount else CurrencyConverter.fromUsdCents(CurrencyConverter.usdCentsOf(amount, from), to)

    /**
     * The live catalog record for a user row (matched by set_id, else set number), or null when the
     * catalog isn't loaded (offline / not yet fetched) — callers then keep the stored denormalized
     * values. Lets reference fields (status, and the release month/year — which older rows saved as
     * month 0 before ingestion derived it) refresh to the current catalog value.
     */
    private fun catalogFor(setId: Long?, setNumber: String): CatalogSet? =
        setId?.let { setsById[it] } ?: setsByNumber[setNumber]

    /** The catalog minifig for a fig_num (for the image + set-count overlay), or null. */
    private fun minifigFor(figNum: String?) = figNum?.let { figsByNum[it] }

    /**
     * Reflect a just-written paid price in the community value cache immediately (Decision 17), so the
     * collection value + growth update at once instead of only after the sync round-trip publishes the
     * real contribution and re-warms. Uses the live catalog retail/status so the outlier guard matches
     * what [ValueContributionRepository.warm] will later compute.
     */
    private fun contributeLocalValue(setId: Long?, figNum: String?, setNumber: String, amount: Long, currency: AppCurrency, isSale: Boolean) {
        if (amount <= 0L) return
        // The value engine works in USD cents (retail anchor is USD cents), so normalize the entry.
        val usdCents = CurrencyConverter.usdCentsOf(amount, currency)
        if (usdCents <= 0L) return
        if (figNum != null) {
            values.applyLocalPaid(null, figNum, usdCents, amount, currency, null, ValueGuardTier.NO_ANCHOR, isSale)
        } else if (setId != null) {
            val cat = catalogFor(setId, setNumber)
            val tier = ValueAggregator.tierOf(cat?.status ?: Availability.AVAILABLE, cat?.retiredYear ?: 0, cat?.retiredMonth ?: 0)
            values.applyLocalPaid(setId, null, usdCents, amount, currency, cat?.retailPrice, tier, isSale)
        }
    }

    private fun List<CollectionCopyEntity>.toCollectionItem(): CollectionItem {
        val head = first()
        val cat = catalogFor(head.setId, head.setNumber)
        val fig = minifigFor(head.figNum)
        val value = if (head.figNum != null) values.valueForFig(head.figNum) else values.valueFor(head.setId)
        val status = cat?.status ?: head.status.toAvailability()
        val isFig = head.figNum != null
        // The current value is shown for minifigs + retired/promo/magazine sets.
        val valueShown = isFig || status == Availability.RETIRED || status == Availability.PROMO || status == Availability.MAGAZINE
        val retail = head.retailPrice ?: 0L
        // Growth vs what was paid, per unit — everything in USD cents (retail + value are USD cents;
        // each copy's paid is converted from its own currency). The reference is the current value when
        // shown, else retail. Every card gets a growth as long as something was paid.
        val totalQty = sumOf { it.quantity }
        val paidUsdCents = sumOf { CurrencyConverter.usdCentsOf(it.pricePaid, it.currency.toCurrency()) }
        val unitPaid = if (totalQty > 0) paidUsdCents.toDouble() / totalQty else 0.0
        val growthRef = (value?.amountUsdCents?.takeIf { valueShown }) ?: retail.takeIf { it > 0 }
        val growth = growthRef?.takeIf { unitPaid > 0 }?.let { ((it - unitPaid) / unitPaid) * 100.0 }
        return CollectionItem(
            setNumber = head.setNumber, name = head.name, itemType = head.itemKind.toItemType(),
            theme = head.theme, setId = head.setId,
            releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: head.releaseYear,
            releaseMonth = cat?.releaseMonth ?: head.releaseMonth,
            pieces = head.pieces, minifigs = head.minifigs, minifigSetCount = fig?.setCount ?: 0,
            retailPrice = retail,
            currentValue = value?.amountUsdCents, currentValueInfo = value, growthPercent = growth,
            status = status,
            // Minifigs: overlay the catalog image (older rows were stored without it); sets keep theirs.
            imageUrl = fig?.imageUrl ?: head.imageUrl,
            // Box shot from the live catalog (sets only; null for minifigs) — the card's fallback + gallery.
            boxImageUrl = cat?.boxImageUrl,
            copies = map { e ->
                Copy(
                    id = e.id,
                    condition = if (e.condition == "used") Condition.USED else Condition.NEW,
                    qty = e.quantity, pricePaid = e.pricePaid, currency = e.currency.toCurrency(),
                    dateAdded = e.acquiredOn ?: "", note = e.notes,
                )
            },
        )
    }

    private fun WishlistEntity.toWishlistItem(): WishlistItem {
        val cat = catalogFor(setId, setNumber)
        val value = if (figNum != null) values.valueForFig(figNum) else values.valueFor(setId)
        return WishlistItem(
            setNumber = setNumber, name = name, itemType = itemKind.toItemType(), theme = theme, setId = setId,
            releaseYear = cat?.releaseYear?.takeIf { it > 0 } ?: releaseYear,
            releaseMonth = cat?.releaseMonth ?: releaseMonth,
            pieces = pieces, minifigs = minifigs,
            retailPrice = retailPrice ?: 0L, currentValue = value?.amountUsdCents, currentValueInfo = value, growthPercent = null,
            status = cat?.status ?: status.toAvailability(), imageUrl = minifigFor(figNum)?.imageUrl ?: imageUrl,
            boxImageUrl = cat?.boxImageUrl,
            addedAt = updatedAt,
        )
    }

    private fun String.toAvailability(): Availability =
        runCatching { Availability.valueOf(this) }.getOrDefault(Availability.AVAILABLE)
}
