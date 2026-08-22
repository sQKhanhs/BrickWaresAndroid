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
 * Converts foreign catalog prices (Brickset gives retail in USD/GBP/… — no ₫) into Vietnamese
 * Dong, since the app is VND-first.
 *
 * The USD→VND rate is fetched **live** once per session from a free, no-key FX API and cached;
 * if that fails (offline / API down) it falls back to [FALLBACK_USD_TO_VND].
 *
 * NOTE: this is US MSRP × FX rate — a ballpark, not the true Vietnam shelf price (which includes
 * duties/VAT/markup). Real VN retail is better sourced as a crowdsourced field later.
 */
object CurrencyConverter {
    /** Used only when the live rate can't be fetched. Update occasionally. */
    private const val FALLBACK_USD_TO_VND = 26_000.0

    /** Free, no-key FX endpoint that includes VND (ECB-based APIs like Frankfurter don't). */
    private const val ENDPOINT = "https://open.er-api.com/v6/latest/USD"

    @Volatile
    private var cachedRate: Double? = null
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Current USD→VND rate: fetched live once and cached for the session; fallback on failure. */
    suspend fun usdToVndRate(): Double {
        cachedRate?.let { return it }
        return mutex.withLock {
            cachedRate ?: run {
                val fetched = try {
                    HttpClient(Android).use { client ->
                        val body = client.get(ENDPOINT).bodyAsText()
                        json.decodeFromString<ErApiResponse>(body).rates["VND"]
                    }
                } catch (e: Exception) {
                    Log.w("CurrencyConverter", "Live FX fetch failed; using fallback rate", e)
                    null
                }
                (fetched ?: FALLBACK_USD_TO_VND).also { cachedRate = it }
            }
        }
    }

    /** Convert USD→VND at [rate], rounded to the nearest 1,000₫ so prices read like real tags. */
    fun usdToVnd(usd: Double, rate: Double): Long = ((usd * rate) / 1_000.0).roundToLong() * 1_000L

    @Serializable
    private data class ErApiResponse(
        val result: String? = null,
        val rates: Map<String, Double> = emptyMap(),
    )
}
