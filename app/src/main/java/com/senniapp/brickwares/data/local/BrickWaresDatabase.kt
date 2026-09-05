package com.senniapp.brickwares.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local offline-first store for user data (collection / wishlist / sales).
 *
 * `exportSchema = true` writes this version's schema to `app/schemas/<this class>/<version>.json` at
 * compile time (location = the `room.schemaLocation` KSP arg in `app/build.gradle.kts`). Those files
 * are committed: they're the baseline `AutoMigration` diffs against and `MigrationTestHelper` rebuilds
 * the old-version DB from. Every schema change from here on must bump `version` AND ship a migration
 * path (an `AutoMigration(from, to)` covers added columns) — Room throws at open otherwise, and there
 * is deliberately no `fallbackToDestructiveMigration()`: a wipe would drop dirty (unsynced) rows, and
 * the pull cursor in DataStore would survive it, so the next sync would only re-fetch recent rows.
 */
@Database(
    entities = [CollectionCopyEntity::class, WishlistEntity::class, SalesEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BrickWaresDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun salesDao(): SalesDao
}
