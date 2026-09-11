package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Settings → Privacy → "Usage analytics" consent — the gate for Firebase ANALYTICS (usage
 * data): OFF by default, nothing is collected until the user opts in, and
 * [com.senniapp.brickwares.util.Observability] applies every change immediately. (Crashlytics is always
 * on — crash diagnostics are NOT behind this toggle.) Same synchronous [SharedPreferences] pattern as
 * [ThemeFavoritesPrefs]; warm via [init] from `Application.onCreate` (before Observability reads it).
 */
object AnalyticsPrefs {
    private const val PREFS = "brickwares_analytics"
    private const val KEY_CONSENT = "consent"

    @Volatile
    private var cached: SharedPreferences? = null

    private val _consent = MutableStateFlow(false)

    /** Whether the user has opted in to usage analytics (defaults to NO). */
    val consentFlow: StateFlow<Boolean> = _consent.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cached = it }

    fun init(context: Context) {
        _consent.value = prefs(context).getBoolean(KEY_CONSENT, false)
    }

    var consent: Boolean
        get() = _consent.value
        set(value) {
            _consent.value = value
            cached?.edit()?.putBoolean(KEY_CONSENT, value)?.apply()
        }
}
