package com.senniapp.brickwares.ui.detail

import com.senniapp.brickwares.data.model.CatalogSet

/**
 * Immutable UI state for the Set Detail page. [set] is the catalog record; the ownership fields
 * ([isOwned]/[ownedCount]/[totalPaid]) and [isWishlisted] are derived live from the collection
 * and wishlist so the page reflects adds made from here or elsewhere.
 */
data class SetDetailUiState(
    val loaded: Boolean = false,
    val set: CatalogSet? = null,
    val isOwned: Boolean = false,
    val ownedCount: Int = 0,
    val totalPaid: Long = 0L,
    val isWishlisted: Boolean = false,
    val related: List<CatalogSet> = emptyList(),
    /** When non-null, the shared Add-to-Collection sheet is open for this set. */
    val addTarget: CatalogSet? = null,
    val toastMessage: String? = null,
)
