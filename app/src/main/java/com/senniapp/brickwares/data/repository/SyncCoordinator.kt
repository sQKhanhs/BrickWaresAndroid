package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.SyncStateStore
import com.senniapp.brickwares.data.local.WishlistEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    data class PendingSwitch(val accountId: String, val accountName: String)

    private val _pendingSwitch = MutableStateFlow<PendingSwitch?>(null)
    val pendingSwitch: StateFlow<PendingSwitch?> = _pendingSwitch.asStateFlow()

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

    /** Debounced-ish sync request after a local write (no-op if signed out or mid-switch-decision). */
    fun requestSync() {
        scope.launch {
            val uid = client.auth.currentUserOrNull()?.id ?: return@launch
            if (_pendingSwitch.value != null) return@launch
            sync(uid)
        }
    }

    fun keepAndMerge() = _pendingSwitch.value?.let { p ->
        scope.launch { _pendingSwitch.value = null; sync(p.accountId) }
    }

    fun discardAndLoad() = _pendingSwitch.value?.let { p ->
        scope.launch {
            _pendingSwitch.value = null
            clearLocal()
            syncState.setLastSyncedAt("") // reset cursor so the full remote set is pulled
            syncState.setLastAccountId(p.accountId)
            sync(p.accountId)
        }
    }

    private suspend fun onSignedIn(user: AuthUser) {
        val last = syncState.lastAccountId()
        when {
            // No prior account, or same account → claim/merge (covers anonymous → first login).
            last == null || last == user.id -> sync(user.id)
            // Different account but nothing local to protect → just adopt + pull.
            !localHasData() -> { syncState.setLastAccountId(user.id); sync(user.id) }
            // Different account over existing local data → must ask (never silently mix).
            else -> _pendingSwitch.value = PendingSwitch(user.id, user.displayName)
        }
    }

    private suspend fun sync(uid: String) = mutex.withLock {
        runCatching {
            catalog.refresh()
            push(uid)
            pull()
            syncState.setLastAccountId(uid)
        }.onFailure { Log.e(TAG, "sync failed", it) }
    }

    // ---- push (dirty local → Supabase upsert) ----

    private suspend fun push(uid: String) {
        collectionDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("collection_copies").upsert(dirty.mapNotNull { it.toRemote(uid) })
            collectionDao.clearDirty(dirty.map { it.id })
        }
        wishlistDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("wishlist_items").upsert(dirty.mapNotNull { it.toRemote(uid) })
            wishlistDao.clearDirty(dirty.map { it.id })
        }
        salesDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            client.from("sales").upsert(dirty.mapNotNull { it.toRemote(uid) })
            salesDao.clearDirty(dirty.map { it.id })
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
        val set = r.setId?.let { catalogById()[it] } ?: return
        collectionDao.upsert(
            CollectionCopyEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = set.setNumber, name = set.name, theme = set.theme, subtheme = set.subtheme,
                releaseYear = set.releaseYear, releaseMonth = set.releaseMonth, pieces = set.pieces,
                minifigs = set.minifigs, retailPrice = set.retailPrice, status = set.status.name,
                imageUrl = set.imageUrl, quantity = r.quantity,
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
        val set = r.setId?.let { catalogById()[it] } ?: return
        wishlistDao.upsert(
            WishlistEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = set.setNumber, name = set.name, theme = set.theme, subtheme = set.subtheme,
                releaseYear = set.releaseYear, releaseMonth = set.releaseMonth, pieces = set.pieces,
                minifigs = set.minifigs, retailPrice = set.retailPrice, status = set.status.name,
                imageUrl = set.imageUrl, deleted = r.deleted, updatedAt = remoteAt, dirty = false,
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

    private suspend fun localHasData(): Boolean =
        collectionDao.activeCount() > 0 || wishlistDao.activeCount() > 0 || salesDao.activeCount() > 0

    private suspend fun clearLocal() {
        collectionDao.clearAll(); wishlistDao.clearAll(); salesDao.clearAll()
    }

    private fun CollectionCopyEntity.toRemote(uid: String): RemoteCopy? = setId?.let {
        RemoteCopy(
            id = id, userId = uid, setId = it, itemKind = itemKind, quantity = quantity,
            condition = condition, pricePaid = pricePaid.toDouble(), acquiredOn = acquiredOn,
            notes = notes, deleted = deleted, updatedAt = toIso(updatedAt),
        )
    }

    private fun WishlistEntity.toRemote(uid: String): RemoteWish? = setId?.let {
        RemoteWish(
            id = id, userId = uid, setId = it, itemKind = itemKind,
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
