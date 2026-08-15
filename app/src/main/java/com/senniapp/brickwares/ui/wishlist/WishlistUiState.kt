package com.senniapp.brickwares.ui.wishlist

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.WishlistItem

/** Item-type filter chips shown above the wishlist (mirrors the Collection tab). */
enum class WishlistFilter { ALL, SET, MINIFIG }

/**
 * Immutable UI state for the Wishlist tab. [visibleItems] applies the active [filter]; when
 * [moveTarget] is non-null the shared Add-to-Collection sheet is open to move that set into the
 * collection.
 */
data class WishlistUiState(
    val isLoading: Boolean = true,
    val filter: WishlistFilter = WishlistFilter.ALL,
    val items: List<WishlistItem> = emptyList(),
    val showAddSheet: Boolean = false,
    val moveTarget: CatalogSet? = null,
) {
    val visibleItems: List<WishlistItem>
        get() = when (filter) {
            WishlistFilter.ALL -> items
            WishlistFilter.SET -> items.filter { it.itemType == ItemType.SET }
            WishlistFilter.MINIFIG -> items.filter { it.itemType == ItemType.MINIFIG }
        }

    val setCount: Int get() = items.count { it.itemType == ItemType.SET }
    val minifigCount: Int get() = items.count { it.itemType == ItemType.MINIFIG }
    val pieceCount: Int get() = items.sumOf { it.pieces }
}
