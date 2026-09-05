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
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Two-way sync between Room (local source of truth) and Supabase, per Arch Decision 10. Runs on
 * sign-in and on demand ([requestSync] after local writes). Push = dirty rows upserted by client
 * UUID (idempotent); pull = remote rows whose SERVER-stamped `server_updated_at` is past that table's
 * [SyncStateStore.pullCursor], merged last-write-wins by the CLIENT `updated_at`; deletes ride the
 * `deleted` tombstone. The two timestamps are deliberately separate columns: `updated_at` says when the
 * edit happened (even offline) and decides conflicts; `server_updated_at` (DB trigger) says when the
 * row actually landed and decides what a device still has to fetch — a cursor on the client time misses
 * another device's offline edit that lands late carrying an older `updated_at`. Guards against an
 * account switch: a *different* account signing in over existing local data wipes it and re-pulls
 * instead of silently mixing data. Write-triggered syncs are debounced (see [syncRequests]).
 */
@OptIn(FlowPreview::class) // Flow.debounce
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

    // Write-triggered sync requests are coalesced: a burst of edits (sell, edit, delete, …) yields ONE
    // sync once the burst settles, instead of one full push + pull + value-cache warm per write (the
    // warm re-fetches the whole contributions table). Room keeps each row's dirty flag until the sync
    // actually runs, so nothing is lost by waiting. A Channel (not a SharedFlow) buffers a request
    // made before the collector below has attached.
    private val syncRequests = Channel<Unit>(Channel.CONFLATED)

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
        syncRequests.receiveAsFlow()
            .debounce(SYNC_DEBOUNCE_MS)
            .onEach { client.auth.currentUserOrNull()?.id?.let { sync(it) } }
            .launchIn(scope)
    }

    /** Requests a sync after a local write (no-op if signed out). Debounced — see [syncRequests]. */
    fun requestSync() {
        syncRequests.trySend(Unit)
    }

    private suspend fun onSignedIn(user: AuthUser) {
        val last = syncState.lastAccountId()
        if (last != null && last != user.id) {
            // Different account → wipe the previous account's local data and load this account's
            // (Room holds one account at a time; logged-out users can't create data, so nothing to
            // merge). Drop the pull cursors so the new account's full set is fetched.
            clearLocal()
            syncState.clearPullCursors()
        }
        sync(user.id) // sets last_account_id
    }

    private suspend fun sync(uid: String) = mutex.withLock {
        runCatching {
            // Both catalogs must be loaded before pull() reconstructs rows from set_id / fig_num.
            catalog.refresh()
            catalog.refreshMinifigs()
            push(uid)
            pull()
            syncState.setLastAccountId(uid)
            // Refresh the community value cache so a just-contributed paid price shows on the cards.
            ValueRepositoryProvider.instance.warm()
        }.onFailure { Log.e(TAG, "sync failed", it) }
    }

    // ---- push (dirty local → Supabase upsert) ----

    private suspend fun push(uid: String) {
        // Never send an empty batch (a row with neither set_id nor fig_num can't be mapped to a remote row).
        collectionDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            dirty.mapNotNull { it.toRemote(uid) }.takeIf { it.isNotEmpty() }?.let { client.from("collection_copies").upsert(it) }
            collectionDao.clearDirty(dirty.map { it.id })
            contributeValues(dirty) // publish paid prices as community value points (Decision 17)
        }
        wishlistDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            dirty.mapNotNull { it.toRemote(uid) }.takeIf { it.isNotEmpty() }?.let { client.from("wishlist_items").upsert(it) }
            wishlistDao.clearDirty(dirty.map { it.id })
        }
        salesDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
            // Includes minifig sales (fig_num, no set_id) — they used to be dropped here yet marked clean.
            dirty.mapNotNull { it.toRemote(uid) }.takeIf { it.isNotEmpty() }?.let { client.from("sales").upsert(it) }
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
                        put("p_currency", row.currency)
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
                        put("p_currency", row.currency)
                        put("p_source", "sale")
                    },
                )
            }.onFailure { Log.w(TAG, "contribute_value (sale) failed for ${row.id}", it) }
        }
    }

    // ---- pull (Supabase rows stamped after each table's cursor → Room, LWW on the client updated_at) ----

    private suspend fun pull() {
        // One-time reset when upgrading from the legacy client-clock cursor (see the class doc): a full
        // re-pull heals any rows that cursor missed. Idempotent — LWW keeps local/dirty edits.
        if (syncState.pullCursorVersion() < PULL_CURSOR_VERSION) {
            syncState.clearPullCursors()
            syncState.setPullCursorVersion(PULL_CURSOR_VERSION)
        }
        pullTable<RemoteCopy>("collection_copies") { applyCopy(it) }
        pullTable<RemoteWish>("wishlist_items") { applyWish(it) }
        pullTable<RemoteSale>("sales") { applySale(it) }
    }

    /**
     * Pull one table's rows stamped after that table's own cursor, apply them, then advance the cursor
     * to the newest SERVER stamp actually received — never the client clock. Ascending order means that
     * if PostgREST caps the page (max-rows), the next sync resumes from the last row we did get instead
     * of skipping the rest.
     */
    private suspend inline fun <reified T : RemoteRow> pullTable(table: String, apply: (T) -> Unit) {
        val cursor = syncState.pullCursor(table)
        val rows = client.from(table).select {
            filter { if (cursor != null) gt(SERVER_UPDATED_AT, cursor) }
            order(SERVER_UPDATED_AT, Order.ASCENDING)
        }.decodeList<T>()
        rows.forEach { apply(it) }
        rows.mapNotNull { it.serverUpdatedAt }.maxByOrNull { parseIso(it) }?.let { syncState.setPullCursor(table, it) }
    }

    private suspend fun applyCopy(r: RemoteCopy) {
        val remoteAt = parseIso(r.updatedAt)
        val local = collectionDao.getById(r.id)
        if (local != null && (local.dirty || local.updatedAt >= remoteAt)) return // local wins
        // Polymorphic: reconstruct denormalized display fields from the set OR the minifig catalog.
        val set = r.setId?.let { catalog.setById(it) }
        val fig = if (set == null) r.figNum?.let { catalog.minifigByNum(it) } else null
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
                currency = r.currency ?: "USD",
                acquiredOn = r.acquiredOn, notes = r.notes,
                deleted = r.deleted, updatedAt = remoteAt, dirty = false,
            ),
        )
    }

    private suspend fun applyWish(r: RemoteWish) {
        val remoteAt = parseIso(r.updatedAt)
        val local = wishlistDao.getById(r.id)
        if (local != null && (local.dirty || local.updatedAt >= remoteAt)) return
        val set = r.setId?.let { catalog.setById(it) }
        val fig = if (set == null) r.figNum?.let { catalog.minifigByNum(it) } else null
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
        val local = salesDao.getById(r.id)
        if (local != null && (local.dirty || local.updatedAt >= remoteAt)) return // local wins (LWW)
        // Polymorphic like copies: a minifig sale has fig_num only — reconstruct from the fig catalog.
        val set = r.setId?.let { catalog.setById(it) }
        val fig = if (set == null) r.figNum?.let { catalog.minifigByNum(it) } else null
        val setNumber = set?.setNumber ?: fig?.figNum ?: return
        salesDao.upsert(
            SalesEntity(
                id = r.id, setId = r.setId, figNum = r.figNum, itemKind = r.itemKind,
                setNumber = setNumber, name = set?.name ?: fig?.name ?: setNumber,
                theme = set?.theme ?: fig?.themes?.firstOrNull() ?: "",
                releaseYear = set?.releaseYear ?: 0, releaseMonth = set?.releaseMonth ?: 0,
                imageUrl = set?.imageUrl ?: fig?.imageUrl, retailPrice = set?.retailPrice,
                quantity = r.quantity, condition = r.condition ?: "new",
                pricePaid = (r.pricePaid ?: 0.0).toLong(), salePrice = r.salePrice.toLong(),
                currency = r.currency ?: "USD",
                soldOn = r.soldOn, notes = r.notes, deleted = r.deleted, updatedAt = remoteAt, dirty = false,
            ),
        )
    }

    // ---- helpers ----

    private suspend fun clearLocal() {
        collectionDao.clearAll(); wishlistDao.clearAll(); salesDao.clearAll()
    }

    // set_id XOR fig_num (polymorphic) — push whichever this row carries; skip rows with neither.
    private fun CollectionCopyEntity.toRemote(uid: String): RemoteCopy? {
        if (setId == null && figNum == null) return null
        return RemoteCopy(
            id = id, userId = uid, setId = setId, figNum = figNum, itemKind = itemKind, quantity = quantity,
            condition = condition, pricePaid = pricePaid.toDouble(), currency = currency, acquiredOn = acquiredOn,
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

    // Sales are polymorphic too — a minifig sale carries fig_num only. The old `setId?.let` dropped those,
    // so they were never pushed yet marked clean: minifig sales silently never synced.
    private fun SalesEntity.toRemote(uid: String): RemoteSale? {
        if (setId == null && figNum == null) return null
        return RemoteSale(
            id = id, userId = uid, setId = setId, figNum = figNum, itemKind = itemKind, quantity = quantity,
            condition = condition, pricePaid = pricePaid.toDouble(), salePrice = salePrice.toDouble(),
            currency = currency, soldOn = soldOn, notes = notes, deleted = deleted, updatedAt = toIso(updatedAt),
        )
    }

    private fun toIso(millis: Long) = Instant.ofEpochMilli(millis).toString()

    // PostgREST returns timestamptz as "…+00:00"; Instant.parse accepts that offset form only on newer
    // java.time (JDK 12+ / recent Android) — elsewhere it throws and every remote row would read as epoch
    // 0, so LWW would always keep local. OffsetDateTime handles both "Z" and "+00:00".
    private fun parseIso(s: String): Long =
        runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(s).toEpochMilli() }
            .getOrDefault(0L)

    /**
     * Common shape of a pulled row: the DB-trigger stamp that drives that table's pull cursor. Sent as
     * absent/null on push (its default) — the BEFORE trigger stamps it server-side regardless.
     */
    private interface RemoteRow {
        val serverUpdatedAt: String?
    }

    // @EncodeDefault(ALWAYS) on the NOT-NULL-backed columns is required, not cosmetic: kotlinx omits a
    // property that equals its default (encodeDefaults is off), and PostgREST builds the bulk ?columns=
    // list from the UNION of keys across the batch. So in a mixed batch (e.g. one copy qty 2, another
    // qty 1) the column lands in the INSERT while the defaulted row omits its value → PostgREST writes
    // NULL for it → "null value in column violates not-null constraint" (hit on quantity 2026-09-06).
    // Forcing these to always serialize keeps every row's value present. Same trap for item_kind
    // (set vs minifig batch) and deleted (edit vs delete batch).
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class RemoteCopy(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("item_kind") val itemKind: String = "set",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val quantity: Int = 1,
        val condition: String? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        val currency: String? = null,
        @SerialName("acquired_on") val acquiredOn: String? = null,
        val notes: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
    ) : RemoteRow

    // See RemoteCopy: force-encode NOT-NULL-backed defaulted columns for mixed-batch upserts.
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class RemoteWish(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("item_kind") val itemKind: String = "set",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
    ) : RemoteRow

    // See RemoteCopy: force-encode NOT-NULL-backed defaulted columns for mixed-batch upserts.
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class RemoteSale(
        val id: String,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) @SerialName("item_kind") val itemKind: String = "set",
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val quantity: Int = 1,
        val condition: String? = null,
        @SerialName("price_paid") val pricePaid: Double? = null,
        @SerialName("sale_price") val salePrice: Double,
        val currency: String? = null,
        @SerialName("sold_on") val soldOn: String? = null,
        val notes: String? = null,
        @EncodeDefault(EncodeDefault.Mode.ALWAYS) val deleted: Boolean = false,
        @SerialName("updated_at") val updatedAt: String,
        @SerialName("server_updated_at") override val serverUpdatedAt: String? = null,
    ) : RemoteRow

    private companion object {
        const val TAG = "SyncCoordinator"
        /** Quiet period after the last local write before the coalesced sync runs. */
        const val SYNC_DEBOUNCE_MS = 750L
        /** DB-trigger-stamped column the pull filters and orders on (migration 20260905120000). */
        const val SERVER_UPDATED_AT = "server_updated_at"
        /** What the stored cursors mean — see [SyncStateStore.pullCursorVersion]. */
        const val PULL_CURSOR_VERSION = 2
    }
}
