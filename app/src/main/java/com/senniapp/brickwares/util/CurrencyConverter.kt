package com.senniapp.brickwares.util

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * FX between the app's canonical **USD** base (LEGO retail is USD; money is stored in USD cents) and
 * the **₫** display option, plus foreign catalog regions (Brickset gives retail per region:
 * USD/GBP/EUR/CAD) → USD cents at ingest.
 *
 * Rates are fetched **live** once per session (USD-based table from a free, no-key API). The last
 * successful table is **persisted** and re-seeded on the next launch, so the fallback is the most
 * recent live rate rather than a stale constant — the hardcoded [FALLBACK_USD_TO_VND] is only used
 * on the very first run before any live fetch has ever succeeded. Cross-rates let us convert any
 * region's currency to USD (non-USD sources need the live/persisted table, else → null).
 */
object CurrencyConverter {
    /** Ultimate fallback — only used the first time, before any live fetch has ever succeeded. */
    private const val FALLBACK_USD_TO_VND = 26_000.0

    /**
     * Approximate USD-based fallback table (1 USD = X units), used ONLY before any live/persisted table
     * has loaded. It includes the non-US catalog regions (GBP/EUR/CAD) so a set with **no US retail**
     * still converts its native price back to USD on a cold first run — instead of showing "No data" —
     * and is replaced by the live table the moment it arrives (returning users already have the live
     * table seeded synchronously at launch, so they get exact rates immediately).
     */
    private val FALLBACK_RATES = mapOf(
        "USD" to 1.0, "VND" to FALLBACK_USD_TO_VND, "GBP" to 0.79, "EUR" to 0.92, "CAD" to 1.36,
    )

    /** The FX call is best-effort — fail fast to the fallback rather than blocking price display. */
    private const val FX_TIMEOUT_MS = 6_000L

    /** Free, no-key FX endpoint that includes VND (ECB-based APIs like Frankfurter don't). */
    private const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"
    private const val PREFS = "fx_rates"
    private const val KEY_RATES = "rates_json"

    /** Maps a set_prices region code to its ISO currency code. */
    private val regionCurrency = mapOf("US" to "USD", "UK" to "GBP", "CA" to "CAD", "DE" to "EUR")

    @Volatile
    private var rates: Map<String, Double>? = null // USD-based (1 USD = rates[X] units of X)
    @Volatile
    private var liveFetched = false
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    /** Wire up persistence + seed the last-known live rate synchronously (call at app start). */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (rates == null) rates = loadPersisted()
    }

    /**
     * Try to refresh the live rate once per session (idempotent); persists it on success so it
     * becomes the fallback for later launches. Best-effort — a failure keeps the last-known /
     * constant rate. Runs off the catalog's critical path (price mapping uses whatever is loaded).
     */
    suspend fun ensureRatesLoaded() {
        if (liveFetched) return
        mutex.withLock {
            if (liveFetched) return
            if (rates == null) rates = loadPersisted()
            runCatching {
                withTimeout(FX_TIMEOUT_MS) {
                    HttpClient(Android).use { client ->
                        json.decodeFromString<ErApiResponse>(client.get(ENDPOINT).bodyAsText()).rates
                    }
                }
            }.onSuccess { fresh ->
                if (fresh.isNotEmpty() && fresh.containsKey("VND")) {
                    rates = fresh
                    persist(fresh)
                    liveFetched = true
                }
            }.onFailure { e ->
                Timber.tag("CurrencyConverter").w(e, "Live FX fetch failed; using last-known / fallback rate")
            }
            // Nothing live and nothing persisted → the approximate fallback table (first-ever run only).
            if (rates == null) rates = FALLBACK_RATES
        }
    }

    private fun loadPersisted(): Map<String, Double>? =
        prefs?.getString(KEY_RATES, null)
            ?.let { runCatching { json.decodeFromString<Map<String, Double>>(it) }.getOrNull() }
            ?.takeIf { it.isNotEmpty() }

    private fun persist(map: Map<String, Double>) {
        runCatching { prefs?.edit()?.putString(KEY_RATES, json.encodeToString(map))?.apply() }
    }

    /** ISO currency for a set_prices region, or null if unknown. */
    fun currencyForRegion(region: String?): String? = region?.let { regionCurrency[it] }

    /** VND per 1 USD currently in effect — the live/persisted rate, else the first-run fallback. */
    private fun vndPerUsd(): Double = rates?.get("VND") ?: FALLBACK_RATES.getValue("VND")

    /**
     * Convert [amount] (in [currency]'s own unit — **USD cents**, or whole **₫**) to the canonical
     * **USD cents** used for retail comparison and value aggregation. USD is already cents (identity);
     * ₫ divides by the live rate. Falls back to the USD constant before any live/persisted rate loads.
     */
    fun usdCentsOf(amount: Long, currency: AppCurrency): Long = when (currency) {
        AppCurrency.USD -> amount
        AppCurrency.VND -> ((amount.toDouble() / vndPerUsd()) * 100.0).roundToLong()
    }

    /**
     * Convert [usdCents] to [currency]'s own display unit — **USD cents** unchanged, or whole **₫** at
     * the live rate (rounded to the nearest dong). The inverse of [usdCentsOf].
     */
    fun fromUsdCents(usdCents: Long, currency: AppCurrency): Long = when (currency) {
        AppCurrency.USD -> usdCents
        AppCurrency.VND -> ((usdCents / 100.0) * vndPerUsd()).roundToLong()
    }

    /**
     * Convert [amount] from currency [from] to [to], both in their own unit. **Identity when equal** —
     * so a same-currency value stays exact (no lossy USD round-trip). Used for showing a single item's
     * paid/avg in the display currency without drift when all its copies share that currency.
     */
    fun convert(amount: Long, from: AppCurrency, to: AppCurrency): Long =
        if (from == to) amount else fromUsdCents(usdCentsOf(amount, from), to)

    /**
     * A catalog region price (major unit — USD 299.99, GBP 249.99…) → **USD cents**. US is exact
     * (× 100, no FX); other regions cross-convert via the live table. Null if that region's rate
     * isn't loaded (the caller then falls back to another region or leaves retail null).
     */
    fun usdCentsFromRegion(amount: Double, regionCurrency: String): Long? {
        val perUsd = when (regionCurrency) {
            "USD" -> 1.0
            // Live rate if loaded, else the approximate fallback so a non-US retail still converts.
            else -> rates?.get(regionCurrency) ?: FALLBACK_RATES[regionCurrency] ?: return null
        }
        return ((amount / perUsd) * 100.0).roundToLong()
    }

    @Serializable
    private data class ErApiResponse(
        val result: String? = null,
        val rates: Map<String, Double> = emptyMap(),
    )
}
