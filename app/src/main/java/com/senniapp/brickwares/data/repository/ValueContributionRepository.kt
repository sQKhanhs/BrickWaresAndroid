package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.ValueAggregator
import com.senniapp.brickwares.data.model.ValueGuardTier
import com.senniapp.brickwares.data.model.ValuePoint
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.CurrencyConverter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.roundToLong

/**
 * Reads the public `set_value_contributions` table and folds the rows into a displayed
 * [CurrentValue] (Arch Decision 17). Aggregation is **client-side** at the current catalog scale;
 * Decision 16 moves it to a DB view/RPC when the catalog grows. Writes happen elsewhere — a paid
 * price is published as a contribution by [SyncCoordinator] once the collection row has synced.
 */
class ValueContributionRepository(
    private val client: SupabaseClient = SupabaseClientProvider.client,
    private val catalog: CatalogRepository = CatalogRepositoryProvider.instance,
) {
    // In-memory cache of set_id → current value, warmed in bulk so item cards can overlay the value
    // synchronously (same pattern as the catalog status overlay). Decision 16 moves this server-side.
    @Volatile
    private var cache: Map<Long, CurrentValue> = emptyMap()
    @Volatile
    private var figCache: Map<String, CurrentValue> = emptyMap()
    // The raw contribution points behind each cached value, kept so a locally-edited paid price can be
    // folded in and re-aggregated immediately (see [applyLocalPaid]) instead of waiting for a sync.
    @Volatile
    private var setPoints: Map<Long, List<ValuePoint>> = emptyMap()
    @Volatile
    private var figPoints: Map<String, List<ValuePoint>> = emptyMap()
    private val _revision = MutableStateFlow(0)
    /** Bumps whenever [warm] refreshes the cache, so item-card flows can re-emit with values. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /**
     * Bulk-refresh the value cache for the whole (set) catalog in one query — fetch every public
     * contribution and aggregate per set client-side (Decision 17). Cheap while contributions are
     * sparse; ties to Decision 16 for the server-side move at scale. Best-effort — a failure leaves
     * the previous cache intact.
     */
    suspend fun warm() {
        catalog.refresh()
        val rows = runCatching {
            withTimeout(TIMEOUT_MS) { client.from(TABLE).select().decodeList<Row>() }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "value warm failed", it)
            return
        }
        val now = System.currentTimeMillis()
        val bySet = rows.filter { it.setId != null }.groupBy { it.setId!! }
        val byFig = rows.filter { it.figNum != null }.groupBy { it.figNum!! }
        // Keep the raw points so [applyLocalPaid] can re-aggregate one key without another fetch. The
        // DB is now authoritative (it includes any just-synced contribution), replacing optimistic ones.
        setPoints = bySet.mapValues { (_, rs) -> rs.toPoints() }
        figPoints = byFig.mapValues { (_, rs) -> rs.toPoints() }
        cache = bySet.mapValues { (id, rs) ->
            val set = catalog.setById(id)
            val tier = ValueAggregator.tierOf(set?.status ?: Availability.AVAILABLE, set?.retiredYear ?: 0, set?.retiredMonth ?: 0, now)
            ValueAggregator.aggregate(rs.toPoints(), set?.retailPrice, tier, now)
        }
        // Minifig values: no retail anchor → the absolute ₫ sanity band applies (see ValueAggregator).
        figCache = byFig.mapValues { (_, rs) -> ValueAggregator.aggregate(rs.toPoints(), null, ValueGuardTier.NO_ANCHOR, now) }
        _revision.value += 1
    }

    /**
     * Optimistically fold the current user's own paid price into the local value for one set/fig and
     * re-aggregate immediately, so a paid add/edit is reflected in the value (and any collection stat
     * derived from it) at once — without waiting for the sync round-trip that publishes the real
     * community contribution and re-[warm]s. [warm] later replaces this with the DB-accurate value.
     */
    fun applyLocalPaid(
        setId: Long?,
        figNum: String?,
        paidUsdCents: Long,
        nativeMinor: Long,
        nativeCurrency: AppCurrency,
        retail: Long?,
        tier: ValueGuardTier,
        isSale: Boolean,
    ) {
        if (paidUsdCents <= 0L) return
        val now = System.currentTimeMillis()
        val point = ValuePoint(paidUsdCents.toDouble(), now, isSale, nativeMinor, nativeCurrency)
        when {
            setId != null -> {
                val pts = setPoints[setId].orEmpty() + point
                cache = cache + (setId to ValueAggregator.aggregate(pts, retail, tier, now))
            }
            figNum != null -> {
                val pts = figPoints[figNum].orEmpty() + point
                // Same tier the caller derived (NO_ANCHOR for figs) — [warm] uses it too, so the
                // optimistic value and the post-sync value are computed identically.
                figCache = figCache + (figNum to ValueAggregator.aggregate(pts, null, tier, now))
            }
            else -> return
        }
        _revision.value += 1
    }

    /** Cached community value for a set id (synchronous overlay), or null when none is cached. */
    fun valueFor(setId: Long?): CurrentValue? = setId?.let { cache[it] }

    /** Just the cached amount in USD cents for a set id, or null. Convenience for card overlays. */
    fun amountFor(setId: Long?): Long? = valueFor(setId)?.amountUsdCents

    /** Cached community value for a minifig (synchronous overlay), or null when none is cached. */
    fun valueForFig(figNum: String?): CurrentValue? = figNum?.let { figCache[it] }

    /** Just the cached minifig amount in USD cents, or null. Convenience for card overlays. */
    fun amountForFig(figNum: String?): Long? = valueForFig(figNum)?.amountUsdCents

    /** Current value for one set (retail in USD cents + the availability [tier] anchor the guard). */
    suspend fun forSet(setId: Long, retailUsdCents: Long?, tier: ValueGuardTier): CurrentValue =
        aggregate(retailUsdCents, tier) {
            client.from(TABLE).select { filter { eq("set_id", setId) } }.decodeList<Row>()
        }

    /** Current value for one minifig (no retail anchor → the absolute ₫ sanity band applies). */
    suspend fun forFig(figNum: String): CurrentValue =
        aggregate(null, ValueGuardTier.NO_ANCHOR) {
            client.from(TABLE).select { filter { eq("fig_num", figNum) } }.decodeList<Row>()
        }

    /**
     * Bulk current values for many sets (list cards), one round trip → `set_id → CurrentValue`.
     * Sets with no contributions are simply absent from the map (callers treat that as NONE).
     */
    suspend fun forSets(
        setIds: List<Long>,
        retail: Map<Long, Long?>,
        tiers: Map<Long, ValueGuardTier> = emptyMap(),
    ): Map<Long, CurrentValue> {
        if (setIds.isEmpty()) return emptyMap()
        val rows = runCatching {
            withTimeout(TIMEOUT_MS) {
                client.from(TABLE).select { filter { isIn("set_id", setIds) } }.decodeList<Row>()
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "bulk value fetch failed", it)
            return emptyMap()
        }
        val now = System.currentTimeMillis()
        return rows.filter { it.setId != null }
            .groupBy { it.setId!! }
            .mapValues { (id, rs) -> ValueAggregator.aggregate(rs.toPoints(), retail[id], tiers[id] ?: ValueGuardTier.AVAILABLE, now) }
    }

    private suspend fun aggregate(retailUsdCents: Long?, tier: ValueGuardTier, fetch: suspend () -> List<Row>): CurrentValue {
        val rows = runCatching { withTimeout(TIMEOUT_MS) { fetch() } }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "value fetch failed", it)
            return CurrentValue.NONE
        }
        return ValueAggregator.aggregate(rows.toPoints(), retailUsdCents, tier)
    }

    // Each contribution is recorded in its own currency (USD cents or whole ₫); normalize every point
    // to USD cents so the median/guard compare like with like against the USD-cents retail anchor.
    private fun List<Row>.toPoints(): List<ValuePoint> =
        map { r ->
            val ccy = runCatching { AppCurrency.valueOf(r.currency ?: "USD") }.getOrDefault(AppCurrency.USD)
            val nativeMinor = r.value.roundToLong()
            val usdCents = CurrencyConverter.usdCentsOf(nativeMinor, ccy)
            // Carry the recorded amount + currency so a same-currency value displays without USD drift.
            ValuePoint(usdCents.toDouble(), parseIso(r.submittedAt), r.source == "sale", nativeMinor, ccy)
        }

    // PostgREST returns timestamptz as "…+00:00"; Instant.parse accepts that offset form only on newer
    // java.time (JDK 12+ / recent Android) — elsewhere it throws and every point would read as epoch 0
    // (all STALE). OffsetDateTime handles both "Z" and "+00:00".
    private fun parseIso(s: String?): Long = s?.let { stamp ->
        runCatching { OffsetDateTime.parse(stamp).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(stamp).toEpochMilli() }
            .getOrNull()
    } ?: 0L

    @Serializable
    private data class Row(
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        val value: Double,
        /** Currency the [value] was recorded in ("USD" cents / "VND" ₫); normalized to USD cents on read. */
        val currency: String? = null,
        @SerialName("submitted_at") val submittedAt: String? = null,
        /** 'paid' or 'sale' — the available-tier floor is looser for a realized sale. */
        val source: String? = null,
    )

    private companion object {
        const val TABLE = "set_value_contributions"
        const val TIMEOUT_MS = 8_000L
        const val TAG = "ValueContribRepo"
    }
}

/** App-wide singleton (mirrors [CatalogRepositoryProvider]). */
object ValueRepositoryProvider {
    val instance: ValueContributionRepository by lazy { ValueContributionRepository() }
}
