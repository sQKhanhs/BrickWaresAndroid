package com.senniapp.brickwares.data.local

import android.content.Context
import androidx.room.Room
import com.senniapp.brickwares.util.CurrencyConverter

/**
 * Minimal service locator for the process-lifetime local components that need an application
 * [Context] (Room + DataStore). Initialized once from [com.senniapp.brickwares.BrickWaresApplication].
 */
object AppGraph {
    lateinit var database: BrickWaresDatabase
        private set
    lateinit var syncState: SyncStateStore
        private set
    lateinit var connectivity: ConnectivityObserver
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        database = Room.databaseBuilder(app, BrickWaresDatabase::class.java, "brickwares.db").build()
        syncState = SyncStateStore(app)
        connectivity = ConnectivityObserver(app)
        // Seed FX rates from the last-known persisted live rate (used as the fallback until refreshed).
        CurrencyConverter.init(app)
    }
}
