package com.senniapp.brickwares.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for the offline-first user-data tables. Reads expose live [Flow]s (Room emits on every write,
 * so the UI is reactive). [getDirty]/[clearDirty] and [upsertAll] serve the sync engine's push/pull;
 * [clearAll] is the "discard local" branch of the account-switch guard.
 */
@Dao
interface CollectionDao {
    @Query("SELECT * FROM collection_copies WHERE deleted = 0")
    fun observeActive(): Flow<List<CollectionCopyEntity>>

    @Query("SELECT COUNT(*) FROM collection_copies WHERE deleted = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM collection_copies WHERE dirty = 1")
    suspend fun getDirty(): List<CollectionCopyEntity>

    @Query("SELECT * FROM collection_copies WHERE id = :id")
    suspend fun getById(id: String): CollectionCopyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionCopyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CollectionCopyEntity>)

    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE id = :id")
    suspend fun markDeleted(id: String, ts: Long)

    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setNumber = :setNumber AND deleted = 0")
    suspend fun markDeletedBySetNumber(setNumber: String, ts: Long)

    @Query("UPDATE collection_copies SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM collection_copies")
    suspend fun clearAll()
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items WHERE deleted = 0")
    fun observeActive(): Flow<List<WishlistEntity>>

    @Query("SELECT COUNT(*) FROM wishlist_items WHERE deleted = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM wishlist_items WHERE dirty = 1")
    suspend fun getDirty(): List<WishlistEntity>

    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    suspend fun getById(id: String): WishlistEntity?

    @Query("SELECT * FROM wishlist_items WHERE setNumber = :setNumber AND deleted = 0 LIMIT 1")
    suspend fun findActiveBySetNumber(setNumber: String): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WishlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WishlistEntity>)

    @Query("UPDATE wishlist_items SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setNumber = :setNumber AND deleted = 0")
    suspend fun markDeletedBySetNumber(setNumber: String, ts: Long)

    @Query("UPDATE wishlist_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM wishlist_items")
    suspend fun clearAll()
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM sales WHERE deleted = 0")
    fun observeActive(): Flow<List<SalesEntity>>

    @Query("SELECT COUNT(*) FROM sales WHERE deleted = 0")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM sales WHERE dirty = 1")
    suspend fun getDirty(): List<SalesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SalesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SalesEntity>)

    @Query("UPDATE sales SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM sales")
    suspend fun clearAll()
}
