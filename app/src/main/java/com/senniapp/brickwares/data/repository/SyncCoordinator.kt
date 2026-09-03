package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.SyncStateStore
import com.senniapp.brickwares.data.local.WishlistEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Two-way sync between Room (local source of truth) and Supabase, per Arch Decision 10. Runs on
 * sign-in and on demand ([requestSync] after local writes). Push = dirty rows upserted by client
 * UUID (idempotent); pull = remote rows changed since the [SyncStateStore.lastSyncedAt] cursor,
 * merged last-write-wins by `updated_at`; deletes ride the `deleted` tombstone. Guards against an
 * account switch: a *different* account signing in over existing local data raises [pendingSwitch]
 * instead of silently mixing data.
 */
class SyncCoordinator(
    private val client: SupabaseClient,
    private val catalog: CatalogRepository,
    private val db: BrickWaresDatabase = AppGraph.database,
    private val syncState: SyncStateStore = AppGraph.syncState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val collectionDao = db.collectionDao()
    private val wishlistDao = db.wishlistDao()
    private val salesDao = db.salesDao()
    private val mutex = Mutex()

    init {
        AuthRepository.authState
            .onEach { state -> if (state is AuthState.SignedIn) onSignedIn(state.user) }
            .launchIn(scope)
        // Sync-on-reconnect: when connectivity returns (offline → online), push any edits made
        // offline + pull. drop(1) skips the initial value (cold-start sign-in already syncs).
        AppGraph.connectivity.isOnline
            .drop(1)
            .filter { it }
            .onEach { requestSync() }
            .launchIn(scope)
    }

    /** Debounced-ish sync request after a local write (no-op if signed out). */
    fun requestSync() {
        scope.launch {
            val uid = client.auth.currentUserOrNull()?.id ?: return@launch
            sync(uid)
        }
    }

    private suspend fun onSignedIn(user: AuthUser) {
        val last = syncState.lastAccountId()
        if (last != null && last != user.id) {
            // Different account → wipe the previous account's local data and load this account's
            // (Room holds one account at a time; logged-out users can't create data, so nothing to
            // merge). Reset the pull cursor so the new account's full set is fetched.
            clearLocal()
            syncState.setLastSyncedAt("")
        }
        sync(user.id) // sets last_account_id
    }

    private suspend fun sync(uid: String) = mutex.withLock {
        runCatching {
            catalog.refresh()
            push(uid)
            pull()
            syncState.setLastAccountId(uid)
            // Refresh the community value cache so a just-contributed paid price shows on the cards.
            ValueRepositoryProvider.instance.warm()
        }.onFailure { Log.e(TAG, "sync failed", it) }
    }

    // ---- push (dirty local → Supabase upsert) ----

    private suspend fun push(uid: String) {
        collectionDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("collection_copies").upsert(dirty.mapNotNull { it.toRemote(uid) })
            collectionDao.clearDirty(dirty.map { it.id })
            contributeValues(dirty) // publish paid prices as community value points (Decision 17)
        }
        wishlistDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("wishlist_items").upsert(dirty.mapNotNull { it.toRemote(uid) })
            wishlistDao.clearDirty(dirty.map { it.id })
        }
        salesDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("sales").upsert(dirty.mapNotNull { it.toRemote(uid) })
            salesDao.clearDirty(dirty.map { it.id })
            contributeSaleValues(dirty) // a realized sale price is a community value point too (Decision 17)
        }
    }

    /**
     * Decision 17: publish each newly-synced paid price as a public community value point via the
     * `contribute_value` RPC (one row per user per item — the RPC upserts). Runs *after* the
     * collection rows are upserted so the server-side owner-gate can see them. Best-effort and
     * per-row guarded — a contribution failure must never abort the sync (which is why it's not in
     * the sync's outer runCatching alone).
     */
    private suspend fun contributeValues(rows: List<CollectionCopyEntity>) {
        rows.forEach { row ->
            if (row.deleted || row.pricePaid <= 0L) return@forEach
            if ((row.setId == null) == (row.figNum == null)) return@forEach // need exactly one ref
            runCatching {
                client.postgrest.rpc(
                    "contribute_value",
                    buildJsonObject {
                        row.setId?.let { put("p_set_id", it) }
                        row.figNum?.let { put("p_fig_num", it) }
                        put("p_value", row.pricePaid)
                        put("p_source", "paid")
                    },
                )
            }.onFailure { Log.w(TAG, "contribute_value failed for ${row.id}", it) }
        }
    }

    /**
     * Decision 17: publish each newly-synced SALE price as a community value point (same one-row-per-
     * user-per-item upsert as paid prices). Runs after the sale rows are upserted so the owner-gate
     * (now sales-aware) passes; when a user has both a paid copy and a sale of the same item, the sale
     * is contributed last in a push and so wins the single point — a realized sale is the better signal.
     */
    private suspend fun contributeSaleValues(rows: List<SalesEntity>) {
        rows.forEach { row ->
            if (row.deleted || row.salePrice <= 0L) return@forEach
            if ((row.setId == null) == (row.figNum == null)) return@forEach // need exactly one ref
            runCatching {
                client.postgrest.rpc(
                    "contribute_value",
                    buildJsonObject {
                        row.setId?.let { put("p_set_id", it) }
                        row.figNum?.let { put("p_fig_num", it) }
                        put("p_value", row.salePrice)
                        put("p_source", "sale")
                    },
                )
            }.onFailure { Log.w(TAG, "contribute_value (sale) failed for ${row.id}", it) }
        }
    }

    // ---- pull (Supabase changed-since-cursor → Room, LWW) ----

    private suspend fun pull() {
        val cursor = syncState.lastSyncedAt()?.takeIf { it.isNotBlank() }
        val next = Instant.now().toString()

        client.from("collection_copies").select {
            filter { if (cursor != null) gt("updated_at", cursor) }
        }.decodeList<RemoteCopy>().forEach { applyCopy(it) }

        client.from("wishlist_items").select {
            filter { if (cursor != null) gt("updated_at", cursor) }
        }.decodeList<RemoteWish>().forEach { applyWish(it) }

        client.from("sales").select {
            filter { if (cursor != null) gt("updated_at", cursor) }
        }.decodeList<RemoteSale>().forEach { applySale(it) }

        syncState.setLastSyncedAt(next)
    }

    private suspend fun applyCopy(r: RemoteCopy) {
        val remoteAt = parseIso(r.updatedAt)
        val local = collectionDao.getById(r.id)
        if (local != null && (local.dirty || local.updatedAt >= remoteAt)) return // local wins
        // Polymorphic: reconstruct denormalized display fields from the set OR the minifig catalog.
        val set = r.setId?.let { catalogById()[it] }
        val fig = if (set == null) r.figNum?.let { minifigByNum()[it] } else null
        val setNumber = set?.setNumber ?: fig?.figNum ?: return
        collectionDao.upsert(
            CollectionCopyEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = setNumber, name = set?.name ?: fig?.name ?: setNumber,
                theme = set?.theme ?: fig?.themes?.firstOrNull() ?: "",
                subtheme = set?.subtheme ?: "General",
                releaseYear = set?.releaseYear ?: 0, releaseMonth = set?.releaseMonth ?: 0,
                pieces = set?.pieces ?: fig?.numParts ?: 0, minifigs = set?.minifigs ?: 0,
                retailPrice = set?.retailPrice, status = (set?.status ?: Availability.AVAILABLE).name,
                imageUrl = set?.imageUrl ?: fig?.imageUrl, quantity = r.quantity,
                condition = r.condition ?: "new", pricePaid = (r.pricePaid ?: 0.0).toLong(),
                acquiredOn = r.acquiredOn, notes = r.notes,
                deleted = r.deleted, updatedAt = remoteAt, dirty = false,
            ),
        )
    }

    private suspend fun applyWish(r: RemoteWish) {
        val remoteAt = parseIso(r.updatedAt)
        val local = wishlistDao.getById(r.id)
        if (local != null && (local.dirty || local.updatedAt >= remoteAt)) return
        val set = r.setId?.let { catalogById()[it] }
        val fig = if (set == null) r.figNum?.let { minifigByNum()[it] } else null
        val setNumber = set?.setNumber ?: fig?.figNum ?: return
        wishlistDao.upsert(
            WishlistEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = setNumber, name = set?.name ?: fig?.name ?: setNumber,
                theme = set?.theme ?: fig?.themes?.firstOrNull() ?: "",
                subtheme = set?.subtheme ?: "General",
                releaseYear = set?.releaseYear ?: 0, releaseMonth = set?.releaseMonth ?: 0,
                pieces = set?.pieces ?: fig?.numParts ?: 0, minifigs = set?.minifigs ?: 0,
                retailPrice = set?.retailPrice, status = (set?.status ?: Availability.AVAILABLE).name,
                imageUrl = set?.imageUrl ?: fig?.imageUrl, deleted = r.deleted, updatedAt = remoteAt, dirty = false,
            ),
        )
    }

    private suspend fun applySale(r: RemoteSale) {
        val remoteAt = parseIso(r.updatedAt)
        val set = r.setId?.let { catalogById()[it] } ?: return
        salesDao.upsert(
            SalesEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = set.setNumber, name = set.name, theme = set.theme,
                releaseYear = set.releaseYear, releaseMonth = set.releaseMonth, imageUrl = set.imageUrl,
                retailPrice = set.retailPrice, quantity = r.quantity, condition = r.condition ?: "new",
                pricePaid = (r.pricePaid ?: 0.0).toLong(), salePrice = r.salePrice.toLong(),
                soldOn = r.soldOn, notes = r.notes, deleted = r.deleted, updatedAt = remoteAt, dirty = false,
            ),
        )
    }

    // ---- helpers ----

    private suspend fun catalogById(): Map<Long, com.senniapp.brickwares.data.model.CatalogSet> {
        catalog.refresh()
        return catalog.all().mapNotNull { s -> s.setId?.let { it to s } }.toMap()
    }

    private suspend fun minifigByNum(): Map<String, com.senniapp.brickwares.data.model.Minifig> {
        catalog.refreshMinifigs()
        return catalog.allMinifigs().associateBy { it.figNum }
    }

    private suspend fun clearLocal() {
        collectionDao.clearAll(); wishlistDao.clearAll(); salesDao.clearAll()
    }

    // set_id XOR fig_num (polymorphic) — push whichever this row carries; skip rows with neither.
    private fun CollectionCopyEntity.toRemote(uid: String): RemoteCopy? {
        if (setId == null && figNum == null) return null
        return RemoteCopy(
            id = id, userId = uid, setId = setId, figNum = figNum, itemKind = itemKind, quantity = quantity,
            condition = condition, pricePaid = pricePaid.toDouble(), acquiredOn = acquiredOn,
            notes = notes, deleted = deleted, updatedAt = toIso(updatedAt),
        )
    }

    private fun WishlistEntity.toRemote(uid: String): RemoteWish? {
        if (setId == null && figNum == null) return null
        return RemoteWish(
            id = id, userId = uid, setId = setId, figNum = figNum, itemKind = itemKind,
            deleted = deleted, updatedAt = toIso(updatedAt),
        )
    }

    private fun SalesEntity.toRemote(uid: String): RemoteSale? = setId?.let {
        RemoteSale(
            id = id, userId = uid, setId = it, itemKind = itemKind, quantity = quantity,
            condition = condition, pricePaid = pricePaid.toDouble(), salePrice = salePrice.toDouble(),
            soldOn = soldOn, notes = notes, deleted = deleted, updatedAt = toIso(updatedAt),
        )
    }

    private fun toIso(millis: Long) = Instant.ofEpochMilli(millis).toString()
    private fun parseIso(s: String) = runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)

    @Serializable
    private data class RemoteCopy(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @SerialName("item_kind") val itemKind: String = "set",
        val quantity: Int = 1,
        val condition: String? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        @SerialName("acquired_on") val acquiredOn: String? = null,
        val notes: String? = null,
        val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class RemoteWish(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @SerialName("item_kind") val itemKind: String = "set",
        val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
    )

    @Serializable
    private data class RemoteSale(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @SerialName("item_kind") val itemKind: String = "set",
        val quantity: Int = 1,
        val condition: String? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        @SerialName("sale_price") val salePrice: Double,
        @SerialName("sold_on") val soldOn: String? = null,
        val notes: String? = null,
        val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
    )

    private companion object {
        const val TAG = "SyncCoordinator"
    }
}
