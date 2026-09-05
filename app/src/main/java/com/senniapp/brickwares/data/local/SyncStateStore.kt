package com.senniapp.brickwares.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

/**
 * Persisted sync state (DataStore): [lastAccountId] backs the account-switch guard (Decision 10), and
 * the per-table **pull cursors** — the newest SERVER `server_updated_at` stamp received for each
 * user-data table; the next pull asks for rows stamped after it. One cursor per table, so a capped
 * page in one table can't be skipped past by a later stamp from another.
 *
 * [pullCursorVersion] records what the stored cursors *mean*: 1 = the legacy single client-clock
 * cursor on `updated_at` (which missed other devices' late-landing offline edits), 2 = per-table server
 * stamps. A stored value below the current one triggers a one-time full re-pull.
 */
class SyncStateStore(private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val CURSOR_VERSION = intPreferencesKey("cursor_version")
        /** The version-1 single cursor; only ever removed now. */
        val LEGACY_LAST_SYNCED_AT = stringPreferencesKey("last_synced_at")
        const val PULL_CURSOR_PREFIX = "pull_cursor_"
        fun pullCursor(table: String) = stringPreferencesKey(PULL_CURSOR_PREFIX + table)
    }

    suspend fun lastAccountId(): String? = context.syncDataStore.data.first()[Keys.LAST_ACCOUNT_ID]

    suspend fun setLastAccountId(id: String) {
        context.syncDataStore.edit { it[Keys.LAST_ACCOUNT_ID] = id }
    }

    /** Newest `server_updated_at` received for [table], or null = never pulled (fetch everything). */
    suspend fun pullCursor(table: String): String? =
        context.syncDataStore.data.first()[Keys.pullCursor(table)]

    suspend fun setPullCursor(table: String, serverStamp: String) {
        context.syncDataStore.edit { it[Keys.pullCursor(table)] = serverStamp }
    }

    /** Drops every table's cursor so the next sync re-pulls everything (account switch, cursor upgrade). */
    suspend fun clearPullCursors() {
        context.syncDataStore.edit { prefs ->
            // filter{} snapshots the keys into a new list first, so removing while iterating is safe.
            prefs.asMap().keys.filter { it.name.startsWith(Keys.PULL_CURSOR_PREFIX) }.forEach { prefs -= it }
            prefs -= Keys.LEGACY_LAST_SYNCED_AT
        }
    }

    suspend fun pullCursorVersion(): Int = context.syncDataStore.data.first()[Keys.CURSOR_VERSION] ?: 1

    suspend fun setPullCursorVersion(version: Int) {
        context.syncDataStore.edit { it[Keys.CURSOR_VERSION] = version }
    }
}
