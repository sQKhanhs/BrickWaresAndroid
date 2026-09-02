package com.senniapp.brickwares.data.repository

import android.util.Log
import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.Minifig
import com.senniapp.brickwares.data.remote.SupabaseClientProvider
import com.senniapp.brickwares.util.CatalogImages
import com.senniapp.brickwares.util.CurrencyConverter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Supabase-backed catalog. Fetches the `sets` table once, maps rows to [CatalogSet], and caches
 * the result in memory; subsequent [all]/[search] serve from the cache with no further network.
 */
class SupabaseCatalogRepository(
    private val client: SupabaseClient,
) : CatalogRepository {

    @Volatile
    private var cache: List<CatalogSet> = emptyList()

    @Volatile
    private var minifigCache: List<Minifig> = emptyList()
    private val minifigMutex = Mutex()
    private val loadMutex = Mutex()
    private val _revision = MutableStateFlow(0)
    override val revision: StateFlow<Int> = _revision.asStateFlow()
    private val _loadError = MutableStateFlow(false)
    override val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    init {
        // Warm the FX rates in the background (best-effort) so they don't block the catalog load —
        // catalog price mapping falls back to the USD constant until these arrive.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { CurrencyConverter.ensureRatesLoaded() }
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 15_000L
    }

    override suspend fun refresh() {
        if (cache.isNotEmpty()) return
        loadMutex.withLock {
            if (cache.isNotEmpty()) return
            try {
                // Bound the fetch so a dropped connection fails fast (instead of the UI hanging on
                // "loading") and releases the mutex promptly so a retry isn't blocked.
                withTimeout(LOAD_TIMEOUT_MS) {
                    val rows = client.from("sets")
                        .select(
                            Columns.raw(
                                "set_id,set_number,number_variant,name,item_type,theme,subtheme,year,pieces," +
                                    "minifigs,availability,notes,launch_date,exit_date,set_prices(region,retail_price,date_first_available,date_last_available)",
                            ),
                        )
                        .decodeList<SetRow>()
                    cache = rows.map { it.toCatalogSet() }
                }
                _loadError.value = false
                // Signal consumers (e.g. the collection/wishlist status overlay) that the cache is ready.
                _revision.value += 1
            } catch (e: TimeoutCancellationException) {
                _loadError.value = true
                Log.e("CatalogRepository", "Catalog load timed out", e)
            } catch (e: CancellationException) {
                throw e // genuine coroutine cancellation — never swallow it
            } catch (e: Exception) {
                // Don't crash the app on a network/permission failure — flag the error so catalog-backed
                // screens show the error/offline fallback, leave the cache empty, and allow a retry.
                _loadError.value = true
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

    override suspend fun refreshMinifigs() {
        if (minifigCache.isNotEmpty()) return
        minifigMutex.withLock {
            if (minifigCache.isNotEmpty()) return
            try {
                withTimeout(LOAD_TIMEOUT_MS) {
                    // Each fig + the sets it's in (for the set-count, theme browse, and the detail's
                    // "appears in" list) via the join — set_id from the link, theme/subtheme from sets.
                    val rows = client.from("minifigs")
                        .select(Columns.raw("fig_num,name,num_parts,image_url,set_minifigs(set_id,sets(theme,subtheme))"))
                        .decodeList<MinifigRow>()
                    minifigCache = rows.map { it.toMinifig() }
                }
                _loadError.value = false
                _revision.value += 1
            } catch (e: TimeoutCancellationException) {
                _loadError.value = true
                Log.e("CatalogRepository", "Minifig load timed out", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loadError.value = true
                Log.e("CatalogRepository", "Failed to load minifigs from Supabase", e)
            }
        }
    }

    override fun allMinifigs(): List<Minifig> = minifigCache

    override fun searchMinifigs(query: String): List<Minifig> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return minifigCache.filter {
            it.figNum.lowercase().contains(q) || it.name.lowercase().contains(q)
        }
    }

    override fun setsForMinifig(figNum: String): List<CatalogSet> {
        val fig = minifigCache.firstOrNull { it.figNum == figNum } ?: return emptyList()
        val byId = cache.mapNotNull { s -> s.setId?.let { it to s } }.toMap()
        return fig.setIds.mapNotNull { byId[it] }.sortedByDescending { it.releaseYear }
    }

    override fun minifigsForSet(setId: Long?): List<Minifig> {
        if (setId == null) return emptyList()
        return minifigCache.filter { setId in it.setIds }.sortedBy { it.figNum }
    }

    /** Row shape for the `sets` table columns we read (unknown columns are ignored by the decoder). */
    @Serializable
    private data class SetRow(
        @SerialName("set_id") val setId: Long? = null,
        @SerialName("set_number") val setNumber: String,
        @SerialName("number_variant") val numberVariant: Int? = null,
        val name: String? = null,
        @SerialName("item_type") val itemType: String? = null,
        val theme: String? = null,
        val subtheme: String? = null,
        val year: Int? = null,
        val pieces: Int? = null,
        val minifigs: Int? = null,
        /** Brickset sales channel: "Retail", "LEGO exclusive", "Retail - limited", GWP, etc. */
        val availability: String? = null,
        val notes: String? = null,
        /** Brickset set-level "Launch"/"Exit" dates — the canonical release + retirement dates. */
        @SerialName("launch_date") val launchDate: String? = null,
        @SerialName("exit_date") val exitDate: String? = null,
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

        private fun parseDate(s: String?): LocalDate? =
            s?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        /**
         * The set's release date: prefer the Brickset set-level `launch_date` (the official launch),
         * falling back to the earliest LEGO.com `date_first_available`. These differ when LEGO.com
         * availability starts before the official launch (VIP early access) — the launch is correct.
         */
        private fun releaseDate(): LocalDate? =
            parseDate(launchDate) ?: prices
                .mapNotNull { parseDate(it.dateFirstAvailable) }
                .minOrNull()

        /**
         * The set's retirement date: prefer the Brickset set-level `exit_date`, falling back to the
         * latest LEGO.com `date_last_available`. Null = still available. (Arch Decision: only mark
         * RETIRED when the exit date is actually in the past — no forward estimate.)
         */
        private fun retirementDate(): LocalDate? =
            parseDate(exitDate) ?: prices
                .mapNotNull { parseDate(it.dateLastAvailable) }
                .maxOrNull()

        private fun deriveStatus(): Availability {
            // Not yet released (launch date in the future) → Pending Release, ahead of everything else
            // (a set that isn't out yet is neither available nor retired).
            releaseDate()?.let { if (it.isAfter(LocalDate.now())) return Availability.PENDING }
            // Promotional items + magazine gifts are never sold at retail and Brickset gives them no
            // exit date, so they keep their own badge and are NEVER marked RETIRED (checked first).
            when {
                availability.equals("Promotional", ignoreCase = true) -> return Availability.PROMO
                availability.equals("Magazine gift", ignoreCase = true) -> return Availability.MAGAZINE
            }
            val retire = retirementDate()
            if (retire != null && retire.isBefore(LocalDate.now())) return Availability.RETIRED
            return when {
                availability.equals("LEGO exclusive", ignoreCase = true) -> Availability.EXCLUSIVE
                availability.equals("LEGO Gift with Purchase", ignoreCase = true) -> Availability.GWP
                else -> Availability.AVAILABLE
            }
        }

        fun toCatalogSet(): CatalogSet = CatalogSet(
            setNumber = setNumber,
            name = name ?: "",
            itemType = if (itemType == "minifig") ItemType.MINIFIG else ItemType.SET,
            theme = theme ?: "",
            releaseYear = releaseDate()?.year ?: year ?: 0,
            // Month from the launch date (0 = unknown, e.g. a set with only a year on Brickset).
            releaseMonth = releaseDate()?.monthValue ?: 0,
            pieces = pieces ?: 0,
            minifigs = minifigs ?: 0,
            // Brickset has no VN retail — convert US (or fallback region) price to ₫; null if none.
            retailPrice = retailVnd(),
            status = deriveStatus(),
            // Retirement date shown on the detail page — only when the exit date is actually in the past.
            retiredYear = retirementDate()?.takeIf { it.isBefore(LocalDate.now()) }?.year ?: 0,
            retiredMonth = retirementDate()?.takeIf { it.isBefore(LocalDate.now()) }?.monthValue ?: 0,
            subtheme = subtheme ?: "General",
            // Brickset's image host is Cloudflare-blocked for non-browser clients, so images come from
            // hosts that load over plain HTTP: the built-set render from Rebrickable's CDN, and the
            // preferred box shot from BrickLink — both addressed by set number + variant.
            imageUrl = CatalogImages.renderUrl(setNumber, numberVariant ?: 1),
            boxImageUrl = CatalogImages.boxUrl(setNumber, numberVariant ?: 1),
            thumbnailUrl = null,
            numberVariant = numberVariant ?: 1,
            notes = notes?.takeIf { it.isNotBlank() },
            setId = setId,
        )
    }

    /** Embedded row from `set_prices` (one per region) for the parent set. */
    @Serializable
    private data class PriceRow(
        val region: String? = null,
        @SerialName("retail_price") val retailPrice: Double? = null,
        /** LEGO.com first-available date (YYYY-MM-DD) for this region; source of the release month. */
        @SerialName("date_first_available") val dateFirstAvailable: String? = null,
        /** LEGO.com exit date (YYYY-MM-DD) for this region; past = retired. Null while still sold. */
        @SerialName("date_last_available") val dateLastAvailable: String? = null,
    )

    /** Row shape for `minifigs` + the embedded `set_minifigs → sets` join (for set-count + themes). */
    @Serializable
    private data class MinifigRow(
        @SerialName("fig_num") val figNum: String,
        val name: String? = null,
        @SerialName("num_parts") val numParts: Int? = null,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("set_minifigs") val setMinifigs: List<SetMinifigRow> = emptyList(),
    ) {
        fun toMinifig(): Minifig {
            val pairs = setMinifigs.mapNotNull { sm ->
                sm.sets?.theme?.takeIf(String::isNotBlank)?.let { it to (sm.sets.subtheme?.takeIf(String::isNotBlank) ?: "General") }
            }.distinct()
            return Minifig(
                figNum = figNum, name = name ?: figNum, imageUrl = imageUrl,
                numParts = numParts ?: 0, setCount = setMinifigs.size, themeSubthemes = pairs,
                setIds = setMinifigs.mapNotNull { it.setId }.distinct(),
            )
        }

        @Serializable
        data class SetMinifigRow(
            @SerialName("set_id") val setId: Long? = null,
            val sets: SetThemeRow? = null,
        )

        @Serializable
        data class SetThemeRow(val theme: String? = null, val subtheme: String? = null)
    }
}

/** App-wide singleton so every ViewModel shares one cached catalog (one network load). */
object CatalogRepositoryProvider {
    val instance: CatalogRepository by lazy {
        SupabaseCatalogRepository(SupabaseClientProvider.client)
    }
}
