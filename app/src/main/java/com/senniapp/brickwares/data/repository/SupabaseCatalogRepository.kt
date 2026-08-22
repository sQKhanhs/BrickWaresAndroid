package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import com.senniapp.brickwares.util.CurrencyConverter
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
                CurrencyConverter.ensureRatesLoaded()
                val rows = client.from("sets")
                    .select(
                        Columns.raw(
                            "set_number,number_variant,name,item_type,theme,subtheme,year,pieces," +
                                "minifigs,set_prices(region,retail_price)",
                        ),
                    )
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
        @SerialName("number_variant") val numberVariant: Int? = null,
        val name: String? = null,
        @SerialName("item_type") val itemType: String? = null,
        val theme: String? = null,
        val subtheme: String? = null,
        val year: Int? = null,
        val pieces: Int? = null,
        val minifigs: Int? = null,
        @SerialName("set_prices") val prices: List<PriceRow> = emptyList(),
    ) {
        /**
         * Retail in ₫. Prefer the US price (Decision 12: global USD MSRP → ₫); if a set has no US
         * price, fall back to any other region's price and cross-convert. Null if no price exists.
         */
        private fun retailVnd(): Long? {
            val chosen = prices.firstOrNull { it.region == "US" && it.retailPrice != null }
                ?: prices.firstOrNull { it.retailPrice != null }
                ?: return null
            val currency = CurrencyConverter.currencyForRegion(chosen.region) ?: return null
            return CurrencyConverter.toVnd(chosen.retailPrice!!, currency)
        }

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
            // Brickset has no VN retail — convert US (or fallback region) price to ₫; null if none.
            retailPrice = retailVnd(),
            // TODO: derive RETIRED from set_prices.date_last_available once that's read.
            status = Availability.AVAILABLE,
            subtheme = subtheme ?: "General",
            // Brickset's image host is behind Cloudflare (blocks non-browser clients), so use
            // Rebrickable's open CDN, addressed by set number + variant. Falls back to a type icon
            // in the UI when a set isn't on Rebrickable.
            imageUrl = "https://cdn.rebrickable.com/media/sets/$setNumber-${numberVariant ?: 1}.jpg",
            thumbnailUrl = null,
            numberVariant = numberVariant ?: 1,
        )
    }

    /** Embedded row from `set_prices` (one per region) for the parent set. */
    @Serializable
    private data class PriceRow(
        val region: String? = null,
        @SerialName("retail_price") val retailPrice: Double? = null,
    )
}

/** App-wide singleton so every ViewModel shares one cached catalog (one network load). */
object CatalogRepositoryProvider {
    val instance: CatalogRepository by lazy {
        SupabaseCatalogRepository(SupabaseClientProvider.client)
    }
}
