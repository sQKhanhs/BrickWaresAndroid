package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user's favorited theme names across app restarts (the Search-tab theme browse). Kept
 * apart into set-themes vs minifig-themes, mirroring the two independent browses. Synchronous
 * [SharedPreferences] (like [LocalePrefs]) so the Search ViewModel can seed its initial state without
 * waiting on DataStore. Warm the cache from [Application.onCreate] via [init].
 *
 * The favorites are also mirrored into in-memory [StateFlow]s so a live screen (the Search VM collects
 * them) reflects an external reset immediately — [clear] wipes them on account switch / deletion so the
 * next account never inherits the previous one's bookmarks, and the stars update without a restart.
 */
object ThemeFavoritesPrefs {
    private const val PREFS = "brickwares_theme_favorites"
    private const val KEY_SETS = "set_themes"
    private const val KEY_MINIFIGS = "minifig_themes"

    @Volatile
    private var cached: SharedPreferences? = null

    private val _setThemes = MutableStateFlow<Set<String>>(emptySet())
    private val _minifigThemes = MutableStateFlow<Set<String>>(emptySet())

    /** Favorited SET / MINIFIG themes as observable streams (seeded from disk in [init]). */
    val setThemesFlow: StateFlow<Set<String>> = _setThemes.asStateFlow()
    val minifigThemesFlow: StateFlow<Set<String>> = _minifigThemes.asStateFlow()

    private fun prefs(context: Context): SharedPreferences =
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cached = it }

    fun init(context: Context) {
        val p = prefs(context)
        _setThemes.value = p.getStringSet(KEY_SETS, emptySet())?.toSet() ?: emptySet()
        _minifigThemes.value = p.getStringSet(KEY_MINIFIGS, emptySet())?.toSet() ?: emptySet()
    }

    /** Favorited SET themes. Reads the in-memory mirror; writes persist and notify collectors. */
    var setThemes: Set<String>
        get() = _setThemes.value
        set(value) {
            _setThemes.value = value
            cached?.edit()?.putStringSet(KEY_SETS, value)?.apply()
        }

    /** Favorited MINIFIG themes (independent of [setThemes]). */
    var minifigThemes: Set<String>
        get() = _minifigThemes.value
        set(value) {
            _minifigThemes.value = value
            cached?.edit()?.putStringSet(KEY_MINIFIGS, value)?.apply()
        }

    /** Wipes both browses' favorites from memory and disk (account switch / deletion). */
    fun clear() {
        _setThemes.value = emptySet()
        _minifigThemes.value = emptySet()
        cached?.edit()?.remove(KEY_SETS)?.remove(KEY_MINIFIGS)?.apply()
    }
}
