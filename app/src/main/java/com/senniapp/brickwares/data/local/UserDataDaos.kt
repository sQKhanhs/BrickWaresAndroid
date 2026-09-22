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

    /** One-shot snapshot of the active copies (for the CSV export). */
    @Query("SELECT * FROM collection_copies WHERE deleted = 0")
    suspend fun getActive(): List<CollectionCopyEntity>

    @Query("SELECT COUNT(*) FROM collection_copies WHERE deleted = 0")
    suspend fun activeCount(): Int

    /** Tombstone every active copy (dirty so the deletes sync) — the "overwrite" half of a CSV import. */
    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE deleted = 0")
    suspend fun markAllActiveDeleted(ts: Long)

    @Query("SELECT * FROM collection_copies WHERE dirty = 1")
    suspend fun getDirty(): List<CollectionCopyEntity>

    @Query("SELECT * FROM collection_copies WHERE id = :id")
    suspend fun getById(id: String): CollectionCopyEntity?

    /** Active copies of one item (set number, or fig_num stored in setNumber for minifigs), for merging
     *  a newly-added identical copy into an existing row instead of duplicating it. */
    @Query("SELECT * FROM collection_copies WHERE deleted = 0 AND itemKind = :kind AND setNumber = :setNumber")
    suspend fun activeForItem(setNumber: String, kind: String): List<CollectionCopyEntity>

    /** Active copies of one EXACT set (by catalog set_id) — distinguishes shared-number variants (CMF /
     *  SDCC, where 71050-2 and 71050-4 are different items) that [activeForItem] by number would conflate. */
    @Query("SELECT * FROM collection_copies WHERE deleted = 0 AND setId = :setId")
    suspend fun activeForSetId(setId: Long): List<CollectionCopyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CollectionCopyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CollectionCopyEntity>)

    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE id = :id")
    suspend fun markDeleted(id: String, ts: Long)

    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setNumber = :setNumber AND deleted = 0")
    suspend fun markDeletedBySetNumber(setNumber: String, ts: Long)

    /** Tombstone every active copy of one EXACT set (by set_id) — removes just this shared-number variant. */
    @Query("UPDATE collection_copies SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setId = :setId AND deleted = 0")
    suspend fun markDeletedBySetId(setId: Long, ts: Long)

    @Query("UPDATE collection_copies SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM collection_copies")
    suspend fun clearAll()
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items WHERE deleted = 0")
    fun observeActive(): Flow<List<WishlistEntity>>

    /** One-shot snapshot of the active wishlist items (for the CSV export). */
    @Query("SELECT * FROM wishlist_items WHERE deleted = 0")
    suspend fun getActive(): List<WishlistEntity>

    @Query("SELECT COUNT(*) FROM wishlist_items WHERE deleted = 0")
    suspend fun activeCount(): Int

    /** Tombstone every active item (dirty so the deletes sync) — the "overwrite" half of a CSV import. */
    @Query("UPDATE wishlist_items SET deleted = 1, dirty = 1, updatedAt = :ts WHERE deleted = 0")
    suspend fun markAllActiveDeleted(ts: Long)

    @Query("SELECT * FROM wishlist_items WHERE dirty = 1")
    suspend fun getDirty(): List<WishlistEntity>

    @Query("SELECT * FROM wishlist_items WHERE id = :id")
    suspend fun getById(id: String): WishlistEntity?

    @Query("SELECT * FROM wishlist_items WHERE setNumber = :setNumber AND deleted = 0 LIMIT 1")
    suspend fun findActiveBySetNumber(setNumber: String): WishlistEntity?

    /** Active wishlist row for one EXACT set (by set_id) — for the "already wishlisted?" dedup on a
     *  shared-number variant (so wishlisting 71050-2 doesn't block 71050-4). */
    @Query("SELECT * FROM wishlist_items WHERE setId = :setId AND deleted = 0 LIMIT 1")
    suspend fun findActiveBySetId(setId: Long): WishlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WishlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WishlistEntity>)

    @Query("UPDATE wishlist_items SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setNumber = :setNumber AND deleted = 0")
    suspend fun markDeletedBySetNumber(setNumber: String, ts: Long)

    /** Tombstone the active wishlist row for one EXACT set (by set_id) — removes just this variant. */
    @Query("UPDATE wishlist_items SET deleted = 1, dirty = 1, updatedAt = :ts WHERE setId = :setId AND deleted = 0")
    suspend fun markDeletedBySetId(setId: Long, ts: Long)

    @Query("UPDATE wishlist_items SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM wishlist_items")
    suspend fun clearAll()
}

@Dao
interface SalesDao {
    @Query("SELECT * FROM sales WHERE deleted = 0")
    fun observeActive(): Flow<List<SalesEntity>>

    /** One-shot snapshot of the active sales (for the CSV export). */
    @Query("SELECT * FROM sales WHERE deleted = 0")
    suspend fun getActive(): List<SalesEntity>

    @Query("SELECT COUNT(*) FROM sales WHERE deleted = 0")
    suspend fun activeCount(): Int

    /** Tombstone every active sale (dirty so the deletes sync) — the "overwrite" half of a CSV import. */
    @Query("UPDATE sales SET deleted = 1, dirty = 1, updatedAt = :ts WHERE deleted = 0")
    suspend fun markAllActiveDeleted(ts: Long)

    @Query("SELECT * FROM sales WHERE dirty = 1")
    suspend fun getDirty(): List<SalesEntity>

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getById(id: String): SalesEntity?

    /** Active sales of one item (set number, or fig_num stored in setNumber for minifigs), for merging
     *  a newly-recorded identical sale into an existing row instead of duplicating it. */
    @Query("SELECT * FROM sales WHERE deleted = 0 AND itemKind = :kind AND setNumber = :setNumber")
    suspend fun activeForItem(setNumber: String, kind: String): List<SalesEntity>

    /** Active sales of one EXACT set (by set_id) — distinguishes shared-number variants that
     *  [activeForItem] by number would conflate. */
    @Query("SELECT * FROM sales WHERE deleted = 0 AND setId = :setId")
    suspend fun activeForSetId(setId: Long): List<SalesEntity>

    @Query("UPDATE sales SET deleted = 1, dirty = 1, updatedAt = :ts WHERE id = :id")
    suspend fun markDeleted(id: String, ts: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SalesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SalesEntity>)

    @Query("UPDATE sales SET dirty = 0 WHERE id IN (:ids)")
    suspend fun clearDirty(ids: List<String>)

    @Query("DELETE FROM sales")
    suspend fun clearAll()
}
