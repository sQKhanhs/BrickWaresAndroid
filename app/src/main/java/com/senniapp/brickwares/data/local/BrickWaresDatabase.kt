package com.senniapp.brickwares.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/** Local offline-first store for user data (collection / wishlist / sales). */
@Database(
    entities = [CollectionCopyEntity::class, WishlistEntity::class, SalesEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class BrickWaresDatabase : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun wishlistDao(): WishlistDao
    abstract fun salesDao(): SalesDao
}
