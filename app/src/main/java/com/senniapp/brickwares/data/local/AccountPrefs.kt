package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * Small per-account facts the server doesn't expose. Today: **which user ids have set a password**
 * from Settings ("Set password" on a Google-only account). Supabase's user object has no
 * "has password" field and we don't rely on it adding an `email` identity after `updateUser`, so
 * the app remembers it here to hide the button afterwards. Device-local: on another device the
 * button can reappear for the same account, where it simply acts as "change password" — harmless.
 */
object AccountPrefs {
    private const val PREFS = "brickwares_account"
    private const val KEY_PASSWORD_SET_IDS = "password_set_ids"

    @Volatile
    private var cached: SharedPreferences? = null

    fun init(context: Context) {
        cached = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun hasPassword(userId: String): Boolean =
        cached?.getStringSet(KEY_PASSWORD_SET_IDS, emptySet())?.contains(userId) == true

    fun markPasswordSet(userId: String) {
        val prefs = cached ?: return
        val ids = prefs.getStringSet(KEY_PASSWORD_SET_IDS, emptySet()).orEmpty() + userId
        prefs.edit().putStringSet(KEY_PASSWORD_SET_IDS, ids).apply()
    }
}
