package com.senniapp.brickwares.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.syncDataStore: DataStore<Preferences> by preferencesDataStore(name = "sync_state")

/**
 * Persisted sync state (DataStore): [lastAccountId] backs the account-switch guard (Decision 10),
 * and [lastSyncedAt] is the pull cursor (a Supabase `updated_at` ISO timestamp — rows changed since
 * are pulled on the next sync).
 */
class SyncStateStore(private val context: Context) {

    private object Keys {
        val LAST_ACCOUNT_ID = stringPreferencesKey("last_account_id")
        val LAST_SYNCED_AT = stringPreferencesKey("last_synced_at")
    }

    suspend fun lastAccountId(): String? = context.syncDataStore.data.first()[Keys.LAST_ACCOUNT_ID]

    suspend fun setLastAccountId(id: String) {
        context.syncDataStore.edit { it[Keys.LAST_ACCOUNT_ID] = id }
    }

    suspend fun lastSyncedAt(): String? = context.syncDataStore.data.first()[Keys.LAST_SYNCED_AT]

    suspend fun setLastSyncedAt(timestamp: String) {
        context.syncDataStore.edit { it[Keys.LAST_SYNCED_AT] = timestamp }
    }
}
