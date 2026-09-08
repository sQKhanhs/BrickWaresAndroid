package com.senniapp.brickwares.ui.wishlist

import com.senniapp.brickwares.data.model.CatalogSet
import com.senniapp.brickwares.ui.components.UiText
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.WishlistItem
import com.senniapp.brickwares.ui.components.ItemSort
import com.senniapp.brickwares.ui.components.PAGE_SIZE

/** Item-type filter chips shown above the wishlist (mirrors the Collection tab). */
enum class WishlistFilter { ALL, SET, MINIFIG }

/**
 * Immutable UI state for the Wishlist tab. [visibleItems] applies the active [filter]; when
 * [moveTarget] is non-null the shared Add-to-Collection sheet is open to move that set into the
 * collection.
 */
data class WishlistUiState(
    /** Whether the wishlist Flow has emitted at least once (empty list is a valid loaded state). */
    val itemsLoaded: Boolean = false,
    val filter: WishlistFilter = WishlistFilter.ALL,
    /** Sort order for the wishlist ("Date added" = when the item was added to the wishlist). */
    val sort: ItemSort = ItemSort.DATE_ADDED,
    val items: List<WishlistItem> = emptyList(),
    val moveTarget: CatalogSet? = null,
    /** 1-based current page for the numbered pagination. */
    val page: Int = 1,
    /** Transient toast message (e.g. after a remove); cleared once shown. */
    val toastMessage: UiText? = null,
) {
    val isLoading: Boolean get() = !itemsLoaded

    val visibleItems: List<WishlistItem>
        get() = when (filter) {
            WishlistFilter.ALL -> items
            WishlistFilter.SET -> items.filter { it.itemType == ItemType.SET }
            WishlistFilter.MINIFIG -> items.filter { it.itemType == ItemType.MINIFIG }
        }

    /** Total pages (>=1) and the clamped current page for [pageItems]. */
    val pageCount: Int get() = ((visibleItems.size + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    val currentPage: Int get() = page.coerceIn(1, pageCount)

    /** The current page's slice of the sorted [visibleItems] (what the list renders). */
    val pageItems: List<WishlistItem>
        get() = visibleItems.applyItemSort(sort).drop((currentPage - 1) * PAGE_SIZE).take(PAGE_SIZE)

    val setCount: Int get() = items.count { it.itemType == ItemType.SET }
    val minifigCount: Int get() = items.count { it.itemType == ItemType.MINIFIG }
    val pieceCount: Int get() = items.sumOf { it.pieces }
}

/** Apply an [ItemSort] to wishlist items: price = retail (USD cents); date = when it was wishlisted. */
private fun List<WishlistItem>.applyItemSort(sort: ItemSort): List<WishlistItem> = when (sort) {
    ItemSort.NAME -> sortedBy { it.name.lowercase() }
    ItemSort.PRICE_HIGH -> sortedByDescending { it.retailPrice }
    ItemSort.PRICE_LOW -> sortedBy { it.retailPrice }
    ItemSort.DATE_ADDED -> sortedByDescending { it.addedAt }
    ItemSort.RELEASE_NEWEST -> sortedByDescending { it.releaseYear * 100 + it.releaseMonth }
    ItemSort.RELEASE_OLDEST -> sortedBy { it.releaseYear * 100 + it.releaseMonth }
}
