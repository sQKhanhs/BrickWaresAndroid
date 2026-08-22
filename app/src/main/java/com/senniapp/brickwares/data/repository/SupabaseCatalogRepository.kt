package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Supabase-backed catalog. Fetches the `sets` table once, maps rows to [CatalogSet], and caches
 * the result in memory; subsequent [all]/[search] serve from the cache with no further network.
 */
class SupabaseCatalogRepository(
    private val client: SupabaseClient,
) : CatalogRepository {

    @Volatile
    private var cache: List<CatalogSet> = emptyList()
    private val loadMutex = Mutex()

    override suspend fun refresh() {
        if (cache.isNotEmpty()) return
        loadMutex.withLock {
            if (cache.isNotEmpty()) return
            try {
                val rows = client.from("sets")
                    .select(Columns.list("set_number,name,item_type,theme,subtheme,year,pieces,minifigs"))
                    .decodeList<SetRow>()
                cache = rows.map { it.toCatalogSet() }
            } catch (e: Exception) {
                // Don't crash the app on a network/permission failure — leave the cache empty so
                // callers show an empty state and a later call can retry. (TODO: surface an error state.)
                Log.e("CatalogRepository", "Failed to load catalog from Supabase", e)
            }
        }
    }

    override fun all(): List<CatalogSet> = cache

    override fun search(query: String): List<CatalogSet> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return cache.filter {
            it.setNumber.lowercase().contains(q) ||
                it.name.lowercase().contains(q) ||
                it.theme.lowercase().contains(q)
        }
    }

    /** Row shape for the `sets` table columns we read (unknown columns are ignored by the decoder). */
    @Serializable
    private data class SetRow(
        @SerialName("set_number") val setNumber: String,
        val name: String? = null,
        @SerialName("item_type") val itemType: String? = null,
        val theme: String? = null,
        val subtheme: String? = null,
        val year: Int? = null,
        val pieces: Int? = null,
        val minifigs: Int? = null,
    ) {
        fun toCatalogSet(): CatalogSet = CatalogSet(
            setNumber = setNumber,
            name = name ?: "",
            itemType = if (itemType == "minifig") ItemType.MINIFIG else ItemType.SET,
            theme = theme ?: "",
            releaseYear = year ?: 0,
            // Our `sets` table stores year only (no month); month awaits a richer ingest.
            releaseMonth = 0,
            pieces = pieces ?: 0,
            minifigs = minifigs ?: 0,
            // TODO(pricing slice): set_prices is per-region (USD/GBP/…), app money is VND — 0 until
            // the currency/region decision is wired. Names/themes/pieces/etc. are real.
            retailPrice = 0L,
            // TODO: derive RETIRED from set_prices.date_last_available once prices are read.
            status = Availability.AVAILABLE,
            subtheme = subtheme ?: "General",
        )
    }
}

/** App-wide singleton so every ViewModel shares one cached catalog (one network load). */
object CatalogRepositoryProvider {
    val instance: CatalogRepository by lazy {
        SupabaseCatalogRepository(SupabaseClientProvider.client)
    }
}
