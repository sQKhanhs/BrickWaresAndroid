package com.senniapp.brickwares.data.repository

import timber.log.Timber
import com.senniapp.brickwares.data.local.AppGraph
import com.senniapp.brickwares.data.local.BrickWaresDatabase
import com.senniapp.brickwares.data.local.CollectionCopyEntity
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.local.SalesEntity
import com.senniapp.brickwares.data.local.SyncStateStore
import com.senniapp.brickwares.data.local.ThemeFavoritesPrefs
import com.senniapp.brickwares.data.local.WishlistEntity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

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

    // Bounded retry after a FAILED sync while online — see [scheduleRetry]. A fresh request (a local
    // write, a reconnect, the app coming to the foreground) restarts the schedule from the first delay.
    private var retryJob: Job? = null
    @Volatile private var retryAttempt = 0
    private var sawFirstStart = false

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
        // Sync when the app returns to the foreground, so edits made on another device since this one
        // was last open show up without waiting for a local write or a connectivity flip. The FIRST
        // ON_START (cold start) is skipped: sign-in already runs a full sync then. Lifecycle observers
        // must be registered on the main thread.
        scope.launch(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        if (sawFirstStart) requestSync() else sawFirstStart = true
                    }
                },
            )
        }
    }

    /** Requests a sync after a local write (no-op if signed out). Debounced — see [syncRequests]. */
    fun requestSync() {
        retryJob?.cancel()
        retryAttempt = 0
        syncRequests.trySend(Unit)
    }

    /**
     * A sync failed while the device believes it is online. The online edge can fire a beat before the
     * network is actually routable (DNS not ready, captive portal), so a single attempt would strand the
     * dirty rows until the next write. Retry on a short bounded backoff ([SyncRules.RETRY_DELAYS_MS]),
     * skipping the attempt if the device went offline meanwhile (the reconnect edge re-requests then).
     */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        val attempt = retryAttempt
        if (attempt >= SyncRules.RETRY_DELAYS_MS.size) return
        retryAttempt = attempt + 1
        retryJob = scope.launch {
            delay(SyncRules.RETRY_DELAYS_MS[attempt])
            // Release the handle BEFORE syncing: a failed retry calls scheduleRetry() from inside this
            // very job, and the isActive guard above would otherwise see itself and skip attempts 2..n.
            retryJob = null
            if (!AppGraph.connectivity.isOnline.value) return@launch
            client.auth.currentUserOrNull()?.id?.let { sync(it) }
        }
    }

    /**
     * Runs a full push+pull **now** (bypassing the debounce) and suspends until it finishes. Returns
     * true on success, false when signed out or the round-trip failed. Used by the CSV import so the UI
     * can lock behind a loading screen until the overwrite has actually synced.
     */
    suspend fun syncNow(): Boolean {
        val uid = client.auth.currentUserOrNull()?.id ?: return false
        return sync(uid)
    }

    private suspend fun onSignedIn(user: AuthUser) {
        val last = syncState.lastAccountId()
        if (last != null && last != user.id) {
            // Different account → wipe the previous account's local data and load this account's
            // (Room holds one account at a time; logged-out users can't create data, so nothing to
            // merge). Drop the pull cursors so the new account's full set is fetched, and the previous
            // account's theme favorites so the new one doesn't inherit its bookmarks.
            clearLocal()
            syncState.clearPullCursors()
            ThemeFavoritesPrefs.clear()
        }
        // Record local ownership NOW, before syncing: past this point local Room holds this account's data
        // (freshly cleared for a switch, or this account's already), so a later sign-in compares against the
        // right id even if this first sync fails — closing the window where a failed sync left the previous
        // account's id recorded while local already belonged to the new one.
        syncState.setLastAccountId(user.id)
        sync(user.id)
    }

    private suspend fun sync(uid: String): Boolean {
        val ok = syncLocked(uid)
        if (ok) {
            retryJob?.cancel()
            retryAttempt = 0
        } else {
            scheduleRetry()
        }
        return ok
    }

    private suspend fun syncLocked(uid: String): Boolean = mutex.withLock {
        // PULL before push. A newer remote row — e.g. another device's delete — must be applied locally
        // FIRST so it can supersede a stale dirty local edit (applyCopy/Wish/Sale now let a newer remote
        // replace even a dirty row). If we pushed first, a device reconnecting with an OLD offline edit
        // would push it and resurrect a newer delete on the server; pulling first means that delete has
        // already landed locally and the now-clean row isn't re-pushed. The BEFORE UPDATE trigger
        // (migration 20260923120000) is the server-side backstop for the same race.
        // Push and pull are isolated (each its own catch) so a failure in one half doesn't skip the
        // other — a bad push batch still lets the pull through, and vice versa. syncNow() reports
        // success only when both halves ran.
        val pullOk = runCatching { pull() } // rebuilds each pulled row's display fields via a batch catalog query (Decision 16)
            .onFailure { Timber.tag(TAG).e(it, "pull failed") }
            .isSuccess
        val pushOk = runCatching { push(uid) }
            .onFailure { Timber.tag(TAG).e(it, "push failed") }
            .isSuccess
        // Refresh the community value cache AFTER push (which publishes this round's contributions) so a
        // just-synced paid/sale price shows on the cards. Best-effort — a warm failure doesn't fail sync.
        runCatching { ValueRepositoryProvider.instance.warm() }
            .onFailure { Timber.tag(TAG).w(it, "value cache warm failed") }
        pullOk && pushOk
    }

    // ---- push (dirty local → Supabase upsert) ----

    private suspend fun push(uid: String) {
        // Clear dirty CONDITIONALLY, per row, on the snapshot's updatedAt: a repository write can re-dirty
        // a row (a new updatedAt) DURING the network upsert (writes run on their own scope, no lock), and
        // an unconditional clear-by-id would wipe that flag → the new edit never reaches the server.
        // clearDirtyIfUnchanged clears only when updatedAt still matches the pushed snapshot, so an edit
        // made mid-push keeps its dirty flag and syncs on the next round.
        // Never send an empty batch (a row with neither set_id nor fig_num can't be mapped to a remote row).
        // The three tables are pushed INDEPENDENTLY: a rejected batch in one (a CHECK / unique violation on
        // a single row) must not skip the others. A rejected row stays dirty (see [upsertRows]) so it is
        // never claimed as synced; the first failure is rethrown at the end so sync() still reports it.
        val failures = mutableListOf<Throwable>()
        runCatching {
            collectionDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
                val rejected = upsertRows("collection_copies", dirty.mapNotNull { it.toRemote(uid) }) { it.id }
                dirty.forEach { if (it.id !in rejected) collectionDao.clearDirtyIfUnchanged(it.id, it.updatedAt) }
                contributeValues(dirty.filter { it.id !in rejected }) // publish paid prices as community value points (Decision 17)
                if (rejected.isNotEmpty()) failures += rejectedError("collection_copies", rejected)
            }
        }.onFailure { if (it is CancellationException) throw it; failures += it }
        runCatching {
            wishlistDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
                val rejected = upsertRows("wishlist_items", dirty.mapNotNull { it.toRemote(uid) }) { it.id }
                dirty.forEach { if (it.id !in rejected) wishlistDao.clearDirtyIfUnchanged(it.id, it.updatedAt) }
                if (rejected.isNotEmpty()) failures += rejectedError("wishlist_items", rejected)
            }
        }.onFailure { if (it is CancellationException) throw it; failures += it }
        runCatching {
            salesDao.getDirty().takeIf { it.isNotEmpty() }?.let { dirty ->
                // Includes minifig sales (fig_num, no set_id) — they used to be dropped here yet marked clean.
                val rejected = upsertRows("sales", dirty.mapNotNull { it.toRemote(uid) }) { it.id }
                dirty.forEach { if (it.id !in rejected) salesDao.clearDirtyIfUnchanged(it.id, it.updatedAt) }
                contributeSaleValues(dirty.filter { it.id !in rejected }) // a realized sale price is a community value point too (Decision 17)
                if (rejected.isNotEmpty()) failures += rejectedError("sales", rejected)
            }
        }.onFailure { if (it is CancellationException) throw it; failures += it }
        failures.firstOrNull()?.let { throw it }
    }

    private fun rejectedError(table: String, rejected: Set<String>) =
        IllegalStateException("$table: ${rejected.size} row(s) rejected by the server (${rejected.joinToString()})")

    /**
     * Upsert [rows] as one batch; if the SERVER rejects the batch because of its CONTENT (a 400 CHECK
     * violation or a 409 unique violation — one bad row fails the whole statement), fall back to one
     * upsert per row so the good rows still land, and return the ids the server rejected. The caller
     * clears dirty on the rows that landed (so they are not re-sent on every retry) and keeps the
     * rejected ones dirty; each is logged once per push. Anything else — a transport failure (offline,
     * timeout), an expired session (401), a gateway/server error (5xx) — is NOT retried row by row (it
     * would fail N times for the same reason, N non-fatals each) and propagates so the sync counts as
     * failed and is retried on the backoff.
     */
    private suspend inline fun <reified R : Any> upsertRows(table: String, rows: List<R>, idOf: (R) -> String): Set<String> {
        if (rows.isEmpty()) return emptySet()
        try {
            client.from(table).upsert(rows)
            return emptySet()
        } catch (e: RestException) {
            if (!e.isRowRejection()) throw e
            Timber.tag(TAG).w(e, "$table batch rejected (${e.statusCode}) — pushing row by row")
        }
        val rejected = mutableSetOf<String>()
        for (row in rows) {
            try {
                client.from(table).upsert(row)
            } catch (e: RestException) {
                if (!e.isRowRejection()) throw e
                rejected += idOf(row)
                Timber.tag(TAG).w("$table push rejected row ${idOf(row)}: ${e.statusCode} ${e.error}")
            }
        }
        return rejected
    }

    /** A rejection caused by the row itself: 400 (CHECK / not-null) or 409 (unique / FK) — not 401/404/5xx. */
    private fun RestException.isRowRejection(): Boolean = statusCode == 400 || statusCode == 409

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
            }.onFailure { Timber.tag(TAG).w(it, "contribute_value failed for ${row.id}") }
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
            }.onFailure { Timber.tag(TAG).w(it, "contribute_value (sale) failed for ${row.id}") }
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
        val copies = fetchTable<RemoteCopy>("collection_copies")
        val wishes = fetchTable<RemoteWish>("wishlist_items")
        val sales = fetchTable<RemoteSale>("sales")
        // Rebuild each pulled row's denormalized display fields from the catalog. Resolve every referenced
        // set/fig for all three tables in ONE batch per kind (Decision 16 — the client no longer holds the
        // whole catalog). Let a fetch failure THROW so the pull ABORTS here, before any apply/cursor-advance:
        // otherwise the rows whose name/theme couldn't be reconstructed get skipped by apply* AND the cursor
        // advances past them, permanently losing that batch. Aborting means the next sync retries it.
        val setIds = (copies.mapNotNull { it.setId } + wishes.mapNotNull { it.setId } + sales.mapNotNull { it.setId }).toSet()
        val figNums = (copies.mapNotNull { it.figNum } + wishes.mapNotNull { it.figNum } + sales.mapNotNull { it.figNum }).toSet()
        val sets = catalog.fetchSetsByIds(setIds).mapNotNull { s -> s.setId?.let { it to s } }.toMap()
        val figs = catalog.fetchMinifigsByNums(figNums).associateBy { it.figNum }
        // Cursors advance only AFTER a table's rows apply, so an apply failure still re-pulls the batch.
        copies.forEach { applyCopy(it, sets, figs) }; advanceCursor("collection_copies", copies)
        wishes.forEach { applyWish(it, sets, figs) }; advanceCursor("wishlist_items", wishes)
        sales.forEach { applySale(it, sets, figs) }; advanceCursor("sales", sales)
    }

    /**
     * Fetch EVERY row of one table past that table's own cursor, paging through PostgREST's row cap.
     * Keyset on (server_updated_at, id) — see [SyncRules.Cursor]: a bulk upsert stamps all its rows with
     * ONE server time, so a stamp-only `>` cursor advanced past a capped page lost every sibling row
     * (a 1,200-copy import left 200 of them missing on the other device). Each page continues from the
     * LAST ROW of the previous one (not from an offset: a row re-stamped by another device mid-pull
     * sorts to the end and would shift every later row up one slot, silently skipping one), until a
     * short page; rows are then de-duplicated by id. The stored cursor advances separately
     * ([advanceCursor]) only after the rows apply.
     */
    private suspend inline fun <reified T : RemoteRow> fetchTable(table: String): List<T> {
        var cursor = SyncRules.Cursor.decode(syncState.pullCursor(table))
        val pages = mutableListOf<List<T>>()
        while (true) {
            val after = cursor
            val page = client.from(table).select {
                filter {
                    if (after != null) {
                        val lastId = after.lastId
                        if (lastId == null) {
                            gt(SERVER_UPDATED_AT, after.stamp)
                        } else {
                            or {
                                gt(SERVER_UPDATED_AT, after.stamp)
                                and { eq(SERVER_UPDATED_AT, after.stamp); gt("id", lastId) }
                            }
                        }
                    }
                }
                order(SERVER_UPDATED_AT, Order.ASCENDING)
                order("id", Order.ASCENDING)
                limit(SyncRules.PULL_PAGE_SIZE.toLong())
            }.decodeList<T>()
            pages += page
            if (SyncRules.isLastPage(page.size)) break
            val last = page.last()
            cursor = last.serverUpdatedAt?.let { SyncRules.Cursor(it, last.id) } ?: break
        }
        return SyncRules.mergePages(pages) { it.id }
    }

    /** Advance a table's pull cursor to the newest SERVER stamp (+ greatest id at it) actually received. */
    private suspend fun advanceCursor(table: String, rows: List<RemoteRow>) {
        SyncRules.nextCursor(rows, { it.serverUpdatedAt }, { it.id })?.let { syncState.setPullCursor(table, it.encode()) }
    }

    private suspend fun applyCopy(r: RemoteCopy, sets: Map<Long, CatalogSet>, figs: Map<String, Minifig>) {
        val remoteAt = parseIso(r.updatedAt)
        val local = collectionDao.getById(r.id)
        // Newer-wins on the client updated_at — a NEWER remote row replaces the local one even when it's
        // dirty (an unpushed edit), so a newer remote delete/edit supersedes a stale offline edit instead
        // of being blocked forever by the dirty flag. A dirty local that is newer-or-equal still wins and
        // is pushed. Pairs with pull-before-push and the server reject_stale_update trigger.
        if (!SyncRules.remoteWins(local?.updatedAt, remoteAt)) return
        // Polymorphic: reconstruct denormalized display fields from the set OR the minifig catalog.
        val set = r.setId?.let { sets[it] }
        val fig = if (set == null) r.figNum?.let { figs[it] } else null
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

    private suspend fun applyWish(r: RemoteWish, sets: Map<Long, CatalogSet>, figs: Map<String, Minifig>) {
        val remoteAt = parseIso(r.updatedAt)
        val local = wishlistDao.getById(r.id)
        // Newer-wins even over a dirty local row (see applyCopy).
        if (!SyncRules.remoteWins(local?.updatedAt, remoteAt)) return
        val set = r.setId?.let { sets[it] }
        val fig = if (set == null) r.figNum?.let { figs[it] } else null
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
        // The server allows ONE live wishlist row per item. If this device minted its own row for the
        // same item (wishlisted on two devices before either synced), that local row would violate the
        // unique index on every push, forever — and block the sales push behind it. The remote row is the
        // one the server already holds, so it survives; the local duplicate is tombstoned (dirty, so the
        // tombstone pushes — deleted rows are outside the partial index).
        if (!r.deleted) {
            val now = System.currentTimeMillis()
            SyncRules.wishlistDuplicates(wishlistDao.activeMatching(r.setId, r.figNum), r.id, r.setId, r.figNum)
                .forEach { wishlistDao.markDeleted(it.id, now) }
        }
    }

    private suspend fun applySale(r: RemoteSale, sets: Map<Long, CatalogSet>, figs: Map<String, Minifig>) {
        val remoteAt = parseIso(r.updatedAt)
        val local = salesDao.getById(r.id)
        // Newer-wins even over a dirty local row (see applyCopy).
        if (!SyncRules.remoteWins(local?.updatedAt, remoteAt)) return
        // Polymorphic like copies: a minifig sale has fig_num only — reconstruct from the fig catalog.
        val set = r.setId?.let { sets[it] }
        val fig = if (set == null) r.figNum?.let { figs[it] } else null
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

    private fun parseIso(s: String): Long = SyncRules.parseIso(s)

    /**
     * Common shape of a pulled row: its id (the keyset tiebreaker) and the DB-trigger stamp that drives
     * that table's pull cursor. The stamp is sent as absent/null on push (its default) — the BEFORE
     * trigger stamps it server-side regardless.
     */
    private interface RemoteRow {
        val id: String
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
        override val id: String,
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
        override val id: String,
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
        override val id: String,
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
