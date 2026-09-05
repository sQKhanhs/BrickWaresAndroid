package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences
import com.senniapp.brickwares.util.AppCurrency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's chosen **display currency**, persisted + reactive. Money is always STORED in ₫ (VND) —
 * sync, the community-value engine and all aggregation depend on that — so this only changes how
 * amounts are shown and how price inputs are read (USD is converted back to ₫ on save via
 * [com.senniapp.brickwares.util.CurrencyConverter]).
 *
 * Backed by a synchronous [SharedPreferences] (like [LocalePrefs]) so [init] can seed the value at
 * app start, and exposed as a [StateFlow] so the theme's `LocalAppCurrency` and any ViewModel react to
 * a change with no restart. Default is [AppCurrency.USD] — the canonical/base currency (LEGO retail is
 * USD), so prices render exactly out of the box; ₫ is opt-in in Settings.
 */
object CurrencyPrefs {
    private const val PREFS = "brickwares_currency"
    private const val KEY = "app_currency"

    @Volatile
    private var cached: SharedPreferences? = null

    private val _currency = MutableStateFlow(AppCurrency.USD)

    /** The live display currency; collect in Compose (`LocalAppCurrency`) or a ViewModel. */
    val currency: StateFlow<AppCurrency> = _currency.asStateFlow()

    /** The current value without collecting — for one-shot reads (e.g. an input-field prefill). */
    val current: AppCurrency get() = _currency.value

    /** Warm the store and seed the persisted choice. Call from [Application.onCreate]. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cached = p
        _currency.value = p.getString(KEY, null)
            ?.let { runCatching { AppCurrency.valueOf(it) }.getOrNull() }
            ?: AppCurrency.USD
    }

    /** Persist and broadcast a new display currency (no-op if unchanged). */
    fun set(currency: AppCurrency) {
        if (_currency.value == currency) return
        _currency.value = currency
        cached?.edit()?.putString(KEY, currency.name)?.apply()
    }
}
