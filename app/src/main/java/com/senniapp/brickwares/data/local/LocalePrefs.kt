package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny synchronous store for the user's chosen in-app language ("en" / "vi"). Read in
 * [com.senniapp.brickwares.MainActivity.attachBaseContext] — before any UI or ViewModel exists and
 * before DataStore (which is async) is usable — so a plain [SharedPreferences] is the right tool.
 *
 * `null` means "follow the system language"; setting a tag overrides it. Applying a change is a
 * config swap, so the activity is recreated after writing.
 */
object LocalePrefs {
    private const val PREFS = "brickwares_locale"
    private const val KEY = "language_tag"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences =
        cached ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .also { cached = it }

    /** Warm the cache from [Application.onCreate] so later reads/writes need no context. */
    fun init(context: Context) {
        prefs(context)
    }

    /** The saved language tag, or null to follow the system language. */
    var languageTag: String?
        get() = cached?.getString(KEY, null)
        set(value) { cached?.edit()?.putString(KEY, value)?.apply() }

    /** Synchronous read from an arbitrary [context] (safe before [init]). */
    fun read(context: Context): String? = prefs(context).getString(KEY, null)
}
