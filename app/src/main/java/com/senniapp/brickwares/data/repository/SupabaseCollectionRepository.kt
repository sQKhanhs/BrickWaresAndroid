package com.senniapp.brickwares.data.repository

import android.util.Log
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
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Real, Supabase-backed user data (collection / wishlist / sales) — replaces the in-memory mock for
 * the signed-in path. Online-only for now (direct Postgrest, RLS `auth.uid() = user_id`); Room +
 * offline sync (Decision 10) come later. On sign-in the user's rows are pulled into the exposed
 * flows; on sign-out the flows clear (no local store yet). Writes fire against Postgrest, then re-pull.
 *
 * Catalog details (name/theme/pieces/retail/image) are joined in from the [CatalogRepository] cache,
 * which also maps set number ↔ `set_id` — the FK the user tables reference. **Sets-only** for now:
 * items resolve to a `sets` row (normal sets and CMF minifigs alike); in-set minifigs (`fig_num`)
 * await the Rebrickable ingest, so they don't map here yet.
 */
class SupabaseCollectionRepository(
    private val client: SupabaseClient,
    private val catalog: CatalogRepository,
) : CollectionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val itemsFlow = MutableStateFlow<List<CollectionItem>>(emptyList())
    private val wishlistFlow = MutableStateFlow<List<WishlistItem>>(emptyList())
    private val soldFlow = MutableStateFlow<List<SoldItem>>(emptyList())

    init {
        // Pull the signed-in user's rows on login; clear on logout (no offline store yet).
        AuthRepository.authState
            .onEach { state ->
                if (state is AuthState.SignedIn) refreshAll() else clearLocal()
            }
            .launchIn(scope)
    }

    // ---- Catalog lookup (set number ↔ set_id ↔ display details) ----

    private suspend fun catalogById(): Map<Long, CatalogSet> {
        catalog.refresh()
        return catalog.all().mapNotNull { set -> set.setId?.let { it to set } }.toMap()
    }

    private suspend fun setIdFor(setNumber: String): Long? {
        catalog.refresh()
        return catalog.all().firstOrNull { it.setNumber == setNumber }?.setId
    }

    // ---- Reads ----

    override fun getCollectionItems(): Flow<List<CollectionItem>> = itemsFlow.asStateFlow()

    override fun getWishlistItems(): Flow<List<WishlistItem>> = wishlistFlow.asStateFlow()

    override suspend fun getCollectionSummary(): CollectionSummary = collectionSummaryOf(itemsFlow.value)

    override suspend fun getThemeSummaries(): List<ThemeSummary> = themeSummariesOf(itemsFlow.value)

    override suspend fun getSoldItems(): List<SoldItem> {
        refreshSales()
        return soldFlow.value
    }

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

    override fun searchCatalog(query: String): List<CatalogSet> = catalog.search(query)

    override fun getCatalog(): List<CatalogSet> = catalog.all()

    private suspend fun refreshAll() {
        refreshCollection()
        refreshWishlist()
        refreshSales()
    }

    private fun clearLocal() {
        itemsFlow.value = emptyList()
        wishlistFlow.value = emptyList()
        soldFlow.value = emptyList()
    }

    private suspend fun refreshCollection() = runCatching {
        val byId = catalogById()
        val rows = client.from("collection_copies")
            .select { filter { eq("deleted", false) } }
            .decodeList<CopyRow>()
        // Group all copy rows by their set, then build one CollectionItem per set from the catalog.
        itemsFlow.value = rows
            .filter { it.setId != null && byId.containsKey(it.setId) }
            .groupBy { it.setId!! }
            .mapNotNull { (setId, copyRows) ->
                val set = byId[setId] ?: return@mapNotNull null
                set.toCollectionItem(copyRows.map { it.toCopy() })
            }
    }.onFailure { Log.e(TAG, "refreshCollection failed", it) }.getOrDefault(Unit)

    private suspend fun refreshWishlist() = runCatching {
        val byId = catalogById()
        val rows = client.from("wishlist_items")
            .select { filter { eq("deleted", false) } }
            .decodeList<WishRow>()
        wishlistFlow.value = rows
            .mapNotNull { row -> row.setId?.let { byId[it] }?.toWishlistItem() }
    }.onFailure { Log.e(TAG, "refreshWishlist failed", it) }.getOrDefault(Unit)

    private suspend fun refreshSales() = runCatching {
        val byId = catalogById()
        val rows = client.from("sales")
            .select { filter { eq("deleted", false) } }
            .decodeList<SaleRow>()
        soldFlow.value = rows.mapNotNull { row ->
            val set = row.setId?.let { byId[it] } ?: return@mapNotNull null
            set.toSoldItem(pricePaid = row.pricePaid?.toLong() ?: 0L, saleValue = row.salePrice.toLong())
        }
    }.onFailure { Log.e(TAG, "refreshSales failed", it) }.getOrDefault(Unit)

    // ---- Writes (fire against Postgrest, then re-pull). All require a signed-in user. ----

    override fun addItem(item: CollectionItem) = write {
        val uid = userId() ?: return@write
        val setId = setIdFor(item.setNumber) ?: return@write
        val kind = item.itemType.kind()
        val inserts = item.copies.map { c ->
            CopyInsert(
                id = UUID.randomUUID().toString(),
                userId = uid,
                setId = setId,
                itemKind = kind,
                quantity = c.qty,
                condition = c.condition.db(),
                pricePaid = c.pricePaid,
                acquiredOn = c.dateAdded.ifBlank { null },
                notes = c.note,
            )
        }
        client.from("collection_copies").insert(inserts)
        refreshCollection()
    }

    override fun removeCopy(setNumber: String, copyId: String) = write {
        client.from("collection_copies")
            .update({ set("deleted", true); set("updated_at", nowIso()) }) { filter { eq("id", copyId) } }
        refreshCollection()
    }

    override fun removeItem(setNumber: String) = write {
        val setId = setIdFor(setNumber) ?: return@write
        client.from("collection_copies")
            .update({ set("deleted", true); set("updated_at", nowIso()) }) {
                filter { eq("set_id", setId); eq("deleted", false) }
            }
        refreshCollection()
    }

    override fun updateCopy(setNumber: String, copy: Copy) = write {
        client.from("collection_copies").update({
            set("quantity", copy.qty)
            set("condition", copy.condition.db())
            set("price_paid", copy.pricePaid)
            if (copy.dateAdded.isNotBlank()) set("acquired_on", copy.dateAdded)
            if (copy.note != null) set("notes", copy.note)
            set("updated_at", nowIso())
        }) { filter { eq("id", copy.id) } }
        refreshCollection()
    }

    override fun addToWishlist(item: WishlistItem) = write {
        if (wishlistFlow.value.any { it.setNumber == item.setNumber }) return@write
        val uid = userId() ?: return@write
        val setId = setIdFor(item.setNumber) ?: return@write
        client.from("wishlist_items").insert(
            WishInsert(id = UUID.randomUUID().toString(), userId = uid, setId = setId, itemKind = item.itemType.kind()),
        )
        refreshWishlist()
    }

    override fun removeFromWishlist(setNumber: String) = write {
        val setId = setIdFor(setNumber) ?: return@write
        client.from("wishlist_items")
            .update({ set("deleted", true); set("updated_at", nowIso()) }) {
                filter { eq("set_id", setId); eq("deleted", false) }
            }
        refreshWishlist()
    }

    // ---- helpers ----

    private fun write(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { Log.e(TAG, "write failed", it) } }
    }

    private fun userId(): String? = client.auth.currentUserOrNull()?.id

    private fun ItemType.kind() = if (this == ItemType.MINIFIG) "minifig" else "set"

    private fun Condition.db() = if (this == Condition.USED) "used" else "new"

    private fun nowIso() = Instant.now().toString()

    private fun CatalogSet.toCollectionItem(copies: List<Copy>) = CollectionItem(
        setNumber = setNumber, name = name, itemType = itemType, theme = theme,
        releaseYear = releaseYear, releaseMonth = releaseMonth, pieces = pieces, minifigs = minifigs,
        retailPrice = retailPrice ?: 0L, currentValue = null, growthPercent = null,
        status = status, imageUrl = imageUrl, copies = copies,
    )

    private fun CatalogSet.toWishlistItem() = WishlistItem(
        setNumber = setNumber, name = name, itemType = itemType, theme = theme,
        releaseYear = releaseYear, releaseMonth = releaseMonth, pieces = pieces, minifigs = minifigs,
        retailPrice = retailPrice ?: 0L, currentValue = null, growthPercent = null,
        status = status, imageUrl = imageUrl,
    )

    private fun CatalogSet.toSoldItem(pricePaid: Long, saleValue: Long) = SoldItem(
        setNumber = setNumber, name = name, itemType = itemType, theme = theme,
        releaseYear = releaseYear, releaseMonth = releaseMonth, imageUrl = imageUrl,
        retailPrice = retailPrice ?: 0L, pricePaid = pricePaid, saleValue = saleValue,
    )

    private fun CopyRow.toCopy() = Copy(
        id = id,
        condition = if (condition == "used") Condition.USED else Condition.NEW,
        qty = quantity,
        pricePaid = pricePaid?.toLong() ?: 0L,
        dateAdded = acquiredOn ?: "",
        note = notes,
    )

    // ---- row DTOs (Postgrest ignores unknown columns on decode) ----

    @Serializable
    private data class CopyRow(
        val id: String,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("item_kind") val itemKind: String = "set",
        val quantity: Int = 1,
        val condition: String? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        @SerialName("acquired_on") val acquiredOn: String? = null,
        val notes: String? = null,
    )

    @Serializable
    private data class CopyInsert(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("set_id") val setId: Long,
        @SerialName("item_kind") val itemKind: String,
        val quantity: Int,
        val condition: String,
        @SerialName("price_paid") val pricePaid: Long,
        @SerialName("acquired_on") val acquiredOn: String? = null,
        val notes: String? = null,
    )

    @Serializable
    private data class WishRow(
        @SerialName("set_id") val setId: Long? = null,
    )

    @Serializable
    private data class WishInsert(
        val id: String,
        @SerialName("user_id") val userId: String,
        @SerialName("set_id") val setId: Long,
        @SerialName("item_kind") val itemKind: String,
    )

    @Serializable
    private data class SaleRow(
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        @SerialName("sale_price") val salePrice: Double,
    )

    private companion object {
        const val TAG = "CollectionRepo"
    }
}

/**
 * App-wide singletons. [instance] is the offline-first [RoomCollectionRepository] (Room source of
 * truth); [syncCoordinator] runs the two-way Supabase sync + exposes the account-switch prompt for
 * the app shell. (The older direct-Postgrest [SupabaseCollectionRepository] is superseded.)
 */
object CollectionRepositoryProvider {
    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(SupabaseClientProvider.client, CatalogRepositoryProvider.instance)
    }
    val instance: CollectionRepository by lazy {
        RoomCollectionRepository(CatalogRepositoryProvider.instance, syncCoordinator)
    }
}
