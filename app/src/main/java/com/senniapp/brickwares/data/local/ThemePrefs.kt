package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences
import com.senniapp.brickwares.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's chosen app theme (System / Light / Dark), persisted + reactive. Backed by a synchronous
 * [SharedPreferences] (like [CurrencyPrefs]) so [init] seeds the saved choice at app start — the first
 * frame already draws in the right theme — and exposed as a [StateFlow] so MainActivity re-themes the
 * whole app the moment Settings changes it. Default is [ThemeMode.SYSTEM]: follow the device.
 */
object ThemePrefs {
    private const val PREFS = "brickwares_theme"
    private const val KEY = "theme_mode"

    @Volatile
    private var cached: SharedPreferences? = null

    private val _mode = MutableStateFlow(ThemeMode.SYSTEM)

    /** The live theme choice; collected by MainActivity and the Settings theme row. */
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    /** Warm the store and seed the persisted choice. Call from [Application.onCreate]. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cached = p
        _mode.value = p.getString(KEY, null)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
    }

    /** Persist and broadcast a new theme (no-op if unchanged). */
    fun set(mode: ThemeMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        cached?.edit()?.putString(KEY, mode.name)?.apply()
    }
}
