package com.senniapp.brickwares.util

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToLong

/**
 * Converts foreign catalog prices (Brickset gives retail per region: USD/GBP/EUR/CAD — no ₫)
 * into Vietnamese Dong, since the app is VND-first (Decision 12: retail = global price → ₫).
 *
 * Rates are fetched **live** once per session (USD-based table from a free, no-key API) and cached;
 * cross-rates let us convert any region's currency to VND. If the fetch fails (offline / API down)
 * it falls back to a USD→VND constant (non-USD sources then can't be converted → null).
 */
object CurrencyConverter {
    /** Used only when the live rate can't be fetched. Update occasionally. */
    private const val FALLBACK_USD_TO_VND = 26_000.0

    /** Free, no-key FX endpoint that includes VND (ECB-based APIs like Frankfurter don't). */
    private const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"

    /** Maps a set_prices region code to its ISO currency code. */
    private val regionCurrency = mapOf("US" to "USD", "UK" to "GBP", "CA" to "CAD", "DE" to "EUR")

    @Volatile
    private var rates: Map<String, Double>? = null // USD-based (1 USD = rates[X] units of X)
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Fetch + cache the rate table once (idempotent). Call before mapping catalog prices. */
    suspend fun ensureRatesLoaded() {
        if (rates != null) return
        mutex.withLock {
            if (rates != null) return
            rates = try {
                HttpClient(Android).use { client ->
                    json.decodeFromString<ErApiResponse>(client.get(ENDPOINT).bodyAsText()).rates
                }
            } catch (e: Exception) {
                Log.w("CurrencyConverter", "Live FX fetch failed; using fallback rate", e)
                mapOf("USD" to 1.0, "VND" to FALLBACK_USD_TO_VND) // enough to convert USD→VND offline
            }
        }
    }

    /** ISO currency for a set_prices region, or null if unknown. */
    fun currencyForRegion(region: String?): String? = region?.let { regionCurrency[it] }

    /**
     * Convert [amount] in [fromCurrency] to VND using the cached USD-based rates, rounded to the
     * nearest 1,000₫. Returns null if rates aren't loaded or the currency isn't in the table.
     */
    fun toVnd(amount: Double, fromCurrency: String): Long? {
        val r = rates ?: return null
        val vndPerUsd = r["VND"] ?: return null
        val fromPerUsd = if (fromCurrency == "USD") 1.0 else r[fromCurrency] ?: return null
        val vnd = (amount / fromPerUsd) * vndPerUsd // fromCurrency → USD → VND
        return (vnd / 1_000.0).roundToLong() * 1_000L
    }

    @Serializable
    private data class ErApiResponse(
        val result: String? = null,
        val rates: Map<String, Double> = emptyMap(),
    )
}
