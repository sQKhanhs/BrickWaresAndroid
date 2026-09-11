package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the Settings → Notifications → "Retirement alerts" toggle, plus the bookkeeping the
 * detector ([com.senniapp.brickwares.util.RetirementAlerts]) needs to notify about a wishlist item
 * exactly once, when its status actually CHANGES to retired:
 *  - [lastWishlist]: the set numbers on the wishlist at the last check.
 *  - [lastRetired]: the subset of those that were already retired then.
 * "Newly retired" = retired now ∩ [lastWishlist] − [lastRetired] — so the first run baselines silently,
 * a set wishlisted after it retired never alerts, and each retirement alerts one time.
 * Same synchronous [SharedPreferences] pattern as [ThemeFavoritesPrefs]; warm via [init] from
 * `Application.onCreate`.
 */
object RetirementAlertPrefs {
    private const val PREFS = "brickwares_retirement_alerts"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_WISHLIST = "last_wishlist"
    private const val KEY_LAST_RETIRED = "last_retired"

    @Volatile
    private var cached: SharedPreferences? = null

    private val _enabled = MutableStateFlow(false)

    /**
     * Whether retirement alerts are on. Defaults to OFF — the user opts in from Settings (which is
     * also what triggers the Android 13+ notification-permission prompt). Observable so Settings
     * reflects it live.
     */
    val enabledFlow: StateFlow<Boolean> = _enabled.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cached = it }

    fun init(context: Context) {
        _enabled.value = prefs(context).getBoolean(KEY_ENABLED, false)
    }

    var enabled: Boolean
        get() = _enabled.value
        set(value) {
            _enabled.value = value
            cached?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    /** Set numbers that were on the wishlist at the last check (empty before the first check). */
    var lastWishlist: Set<String>
        get() = cached?.getStringSet(KEY_LAST_WISHLIST, emptySet())?.toSet() ?: emptySet()
        set(value) {
            cached?.edit()?.putStringSet(KEY_LAST_WISHLIST, value)?.apply()
        }

    /** The retired subset of [lastWishlist] at the last check. */
    var lastRetired: Set<String>
        get() = cached?.getStringSet(KEY_LAST_RETIRED, emptySet())?.toSet() ?: emptySet()
        set(value) {
            cached?.edit()?.putStringSet(KEY_LAST_RETIRED, value)?.apply()
        }
}
