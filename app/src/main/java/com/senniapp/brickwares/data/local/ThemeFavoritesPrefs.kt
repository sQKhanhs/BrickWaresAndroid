package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the user's favorited theme names across app restarts (the Search-tab theme browse). Kept
 * apart into set-themes vs minifig-themes, mirroring the two independent browses. Synchronous
 * [SharedPreferences] (like [LocalePrefs]) so the Search ViewModel can seed its initial state without
 * waiting on DataStore. Warm the cache from [Application.onCreate] via [init].
 */
object ThemeFavoritesPrefs {
    private const val PREFS = "brickwares_theme_favorites"
    private const val KEY_SETS = "set_themes"
    private const val KEY_MINIFIGS = "minifig_themes"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences =
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cached = it }

    fun init(context: Context) {
        prefs(context)
    }

    /** Favorited SET themes. getStringSet returns a shared instance, so hand back a copy. */
    var setThemes: Set<String>
        get() = cached?.getStringSet(KEY_SETS, emptySet())?.toSet() ?: emptySet()
        set(value) { cached?.edit()?.putStringSet(KEY_SETS, value)?.apply() }

    /** Favorited MINIFIG themes (independent of [setThemes]). */
    var minifigThemes: Set<String>
        get() = cached?.getStringSet(KEY_MINIFIGS, emptySet())?.toSet() ?: emptySet()
        set(value) { cached?.edit()?.putStringSet(KEY_MINIFIGS, value)?.apply() }
}
