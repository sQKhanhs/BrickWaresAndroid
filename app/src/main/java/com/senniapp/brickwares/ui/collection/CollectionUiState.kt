package com.senniapp.brickwares.ui.collection

import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ItemType

/** Collection tab has two modes: the collection view and its Sales sub-view. */
enum class CollectionMode { COLLECTION, SALES }

/** Item-type filter chips shown above the list. */
enum class CollectionFilter { ALL, SET, MINIFIG }

/**
 * Immutable UI state for the Collection tab. [visibleItems] applies the active
 * [filter] so the View doesn't compute anything itself.
 */
data class CollectionUiState(
    val isLoading: Boolean = true,
    val mode: CollectionMode = CollectionMode.COLLECTION,
    val filter: CollectionFilter = CollectionFilter.ALL,
    val summary: CollectionSummary? = null,
    val items: List<CollectionItem> = emptyList(),
) {
    val visibleItems: List<CollectionItem>
        get() = when (filter) {
            CollectionFilter.ALL -> items
            CollectionFilter.SET -> items.filter { it.itemType == ItemType.SET }
            CollectionFilter.MINIFIG -> items.filter { it.itemType == ItemType.MINIFIG }
        }
}
