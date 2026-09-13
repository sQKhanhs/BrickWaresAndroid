package com.senniapp.brickwares.data.local

import android.content.Context
import java.util.UUID

/**
 * A random id generated once per app install and kept in [android.content.SharedPreferences] (so it
 * survives restarts, resets on reinstall / clear-data). Not a device identifier and never tied to a
 * person — it exists only so the server can rate-limit **signed-out** feedback per install (see the
 * `submit_feedback` RPC). Warm via [init] from `Application.onCreate`.
 */
object InstallId {
    private const val PREFS = "brickwares_install"
    private const val KEY_ID = "id"

    @Volatile
    private var cached: String? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cached = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_ID, it).apply()
        }
    }

    /** The install id, or null before [init] (the RPC then treats a signed-out caller as invalid). */
    val value: String? get() = cached
}
