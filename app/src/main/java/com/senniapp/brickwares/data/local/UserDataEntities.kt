package com.senniapp.brickwares.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * Room = the offline-first source of truth for user data (Arch Decision 10). Every row carries the
 * sync columns — client-generated UUID [id], client [updatedAt] (LWW basis), a [deleted] tombstone,
 * and a [dirty] flag for not-yet-pushed local changes — PLUS the catalog display fields the card
 * needs, denormalized in at add-time so owned items render fully OFFLINE (the catalog itself is
 * network-only; only the Set Detail page degrades to a "no internet" placeholder offline).
 *
 * No user_id column: Room holds the device's data for one account at a time; the account-switch
 * guard (last_account_id in DataStore) decides whose it is on login.
 */

@Entity(tableName = "collection_copies")
data class CollectionCopyEntity(
    @PrimaryKey val id: String,
    // Catalog reference (one is set for now; fig_num reserved for in-set minifigs).
    val setId: Long?,
    val figNum: String?,
    val itemKind: String,               // "set" | "minifig"
    // Denormalized catalog display (for offline cards).
    val setNumber: String,
    val name: String,
    val theme: String,
    val subtheme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    val retailPrice: Long?,             // VND; null = no retail price
    val status: String,                 // Availability name
    val imageUrl: String?,
    // Copy data.
    val quantity: Int,
    val condition: String,              // "new" | "used"
    val pricePaid: Long,
    val acquiredOn: String?,            // yyyy-MM-dd
    val notes: String?,
    // Sync.
    val deleted: Boolean,
    val updatedAt: Long,                // epoch millis (client)
    val dirty: Boolean,
)

@Entity(tableName = "wishlist_items")
data class WishlistEntity(
    @PrimaryKey val id: String,
    val setId: Long?,
    val figNum: String?,
    val itemKind: String,
    val setNumber: String,
    val name: String,
    val theme: String,
    val subtheme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    val retailPrice: Long?,
    val status: String,
    val imageUrl: String?,
    val deleted: Boolean,
    val updatedAt: Long,
    val dirty: Boolean,
)

@Entity(tableName = "sales")
data class SalesEntity(
    @PrimaryKey val id: String,
    val setId: Long?,
    val figNum: String?,
    val itemKind: String,
    val setNumber: String,
    val name: String,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val imageUrl: String?,
    val retailPrice: Long?,
    val quantity: Int,
    val condition: String,
    val pricePaid: Long,
    val salePrice: Long,
    val soldOn: String?,
    val notes: String?,
    val deleted: Boolean,
    val updatedAt: Long,
    val dirty: Boolean,
)
