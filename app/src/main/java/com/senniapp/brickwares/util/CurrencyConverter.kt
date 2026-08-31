package com.senniapp.brickwares.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
 * Converts foreign catalog prices (Brickset gives retail per region: USD/GBP/EUR/CAD — no ₫)
 * into Vietnamese Dong, since the app is VND-first (Decision 12: retail = global price → ₫).
 *
 * Rates are fetched **live** once per session (USD-based table from a free, no-key API). The last
 * successful table is **persisted** and re-seeded on the next launch, so the fallback is the most
 * recent live rate rather than a stale constant — the hardcoded [FALLBACK_USD_TO_VND] is only used
 * on the very first run before any live fetch has ever succeeded. Cross-rates let us convert any
 * region's currency to VND (non-USD sources need the live/persisted table, else → null).
 */
object CurrencyConverter {
    /** Ultimate fallback — only used the first time, before any live fetch has ever succeeded. */
    private const val FALLBACK_USD_TO_VND = 26_000.0

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
                Log.w("CurrencyConverter", "Live FX fetch failed; using last-known / fallback rate", e)
            }
            // Nothing live and nothing persisted → the hardcoded constant (first-ever run only).
            if (rates == null) rates = mapOf("USD" to 1.0, "VND" to FALLBACK_USD_TO_VND)
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

    /**
     * Convert [amount] in [fromCurrency] to VND using the cached USD-based rates, rounded to the
     * nearest 1,000₫. Returns null if rates aren't loaded or the currency isn't in the table.
     */
    fun toVnd(amount: Double, fromCurrency: String): Long? {
        val r = rates
        // Fall back to the USD constant when live rates aren't loaded yet, so USD prices still convert
        // without blocking on the FX fetch. Non-USD needs the live table (else null).
        val vndPerUsd = r?.get("VND") ?: FALLBACK_USD_TO_VND
        val fromPerUsd = when {
            fromCurrency == "USD" -> 1.0
            r != null -> r[fromCurrency] ?: return null
            else -> return null
        }
        val vnd = (amount / fromPerUsd) * vndPerUsd // fromCurrency → USD → VND
        return (vnd / 1_000.0).roundToLong() * 1_000L
    }

    @Serializable
    private data class ErApiResponse(
        val result: String? = null,
        val rates: Map<String, Double> = emptyMap(),
    )
}
