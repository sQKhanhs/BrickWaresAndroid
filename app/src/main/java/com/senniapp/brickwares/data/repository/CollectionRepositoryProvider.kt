package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.remote.SupabaseClientProvider

/**
 * App-wide singletons for user data. [instance] is the offline-first [RoomCollectionRepository]
 * (Room = source of truth); [syncCoordinator] runs the two-way Supabase sync driven by auth/writes/
 * reconnect. Every ViewModel shares one repository (one set of Room-backed flows).
 */
object CollectionRepositoryProvider {
    val syncCoordinator: SyncCoordinator by lazy {
        SyncCoordinator(SupabaseClientProvider.client, CatalogRepositoryProvider.instance)
    }
    val instance: CollectionRepository by lazy {
        RoomCollectionRepository(CatalogRepositoryProvider.instance, syncCoordinator)
    }
}
