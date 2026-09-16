package com.senniapp.brickwares.data.local

import android.content.Context
import android.content.SharedPreferences

/**
 * State of the "Rate BrickWares" prompt (see [com.senniapp.brickwares.util.RatePrompt]):
 *  - [done]: the user went to the store (from the prompt or the Settings row) or chose "Don't ask
 *    again" — the prompt never shows again.
 *  - [lastPromptAt] / [promptCount]: "Not now" snoozes; the prompt may return after a cool-down, a
 *    limited number of times.
 * Same synchronous [SharedPreferences] pattern as the other prefs; warm via [init] from
 * `Application.onCreate`.
 */
object RatePrefs {
    private const val PREFS = "brickwares_rate"
    private const val KEY_DONE = "done"
    private const val KEY_LAST_PROMPT_AT = "last_prompt_at"
    private const val KEY_PROMPT_COUNT = "prompt_count"

    @Volatile
    private var cached: SharedPreferences? = null

    fun init(context: Context) {
        cached = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var done: Boolean
        get() = cached?.getBoolean(KEY_DONE, false) ?: false
        set(value) { cached?.edit()?.putBoolean(KEY_DONE, value)?.apply() }

    /** Epoch millis of the last time the prompt was shown; 0 = never. */
    var lastPromptAt: Long
        get() = cached?.getLong(KEY_LAST_PROMPT_AT, 0L) ?: 0L
        set(value) { cached?.edit()?.putLong(KEY_LAST_PROMPT_AT, value)?.apply() }

    var promptCount: Int
        get() = cached?.getInt(KEY_PROMPT_COUNT, 0) ?: 0
        set(value) { cached?.edit()?.putInt(KEY_PROMPT_COUNT, value)?.apply() }
}
