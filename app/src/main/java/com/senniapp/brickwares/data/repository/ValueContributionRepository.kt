package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.model.CurrentValue
import com.senniapp.brickwares.data.model.ValueAggregator
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
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
        val retail = catalog.all().mapNotNull { s -> s.setId?.let { it to s.retailPrice } }.toMap()
        val rows = runCatching {
            withTimeout(TIMEOUT_MS) { client.from(TABLE).select().decodeList<Row>() }
        }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "value warm failed", it)
            return
        }
        val now = System.currentTimeMillis()
        cache = rows.filter { it.setId != null }
            .groupBy { it.setId!! }
            .mapValues { (id, rs) -> ValueAggregator.aggregate(rs.toPoints(), retail[id], now) }
        _revision.value += 1
    }

    /** Cached community value for a set id (synchronous overlay), or null when none is cached. */
    fun valueFor(setId: Long?): CurrentValue? = setId?.let { cache[it] }

    /** Just the cached amount in ₫ for a set id, or null. Convenience for card overlays. */
    fun amountFor(setId: Long?): Long? = valueFor(setId)?.amountVnd

    /** Current value for one set (retail anchors the outlier guard). */
    suspend fun forSet(setId: Long, retailVnd: Long?): CurrentValue =
        aggregate(retailVnd) {
            client.from(TABLE).select { filter { eq("set_id", setId) } }.decodeList<Row>()
        }

    /** Current value for one minifig (no retail anchor — outlier guard is skipped). */
    suspend fun forFig(figNum: String): CurrentValue =
        aggregate(null) {
            client.from(TABLE).select { filter { eq("fig_num", figNum) } }.decodeList<Row>()
        }

    /**
     * Bulk current values for many sets (list cards), one round trip → `set_id → CurrentValue`.
     * Sets with no contributions are simply absent from the map (callers treat that as NONE).
     */
    suspend fun forSets(setIds: List<Long>, retail: Map<Long, Long?>): Map<Long, CurrentValue> {
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
            .mapValues { (id, rs) -> ValueAggregator.aggregate(rs.toPoints(), retail[id], now) }
    }

    private suspend fun aggregate(retailVnd: Long?, fetch: suspend () -> List<Row>): CurrentValue {
        val rows = runCatching { withTimeout(TIMEOUT_MS) { fetch() } }.getOrElse {
            if (it is CancellationException) throw it
            Log.w(TAG, "value fetch failed", it)
            return CurrentValue.NONE
        }
        return ValueAggregator.aggregate(rows.toPoints(), retailVnd)
    }

    private fun List<Row>.toPoints(): List<Pair<Double, Long>> = map { it.value to parseIso(it.submittedAt) }

    private fun parseIso(s: String?): Long =
        s?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    @Serializable
    private data class Row(
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("fig_num") val figNum: String? = null,
        val value: Double,
        @SerialName("submitted_at") val submittedAt: String? = null,
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
