package com.senniapp.brickwares.data.model

/** Condition of an owned copy, chosen in the Add-to-Collection sheet. */
enum class Condition { NEW, USED }

/**
 * Catalog (reference) data for a set, returned by set-number search in the Add sheet.
 * In production this comes from Brickset; here it's mock data. It carries everything
 * needed to build a [CollectionItem] except the user-supplied fields (paid, condition…).
 */
data class CatalogSet(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    /** Retail in the display currency (VND), or null when no source price is available. */
    val retailPrice: Long?,
    val status: Availability,
    /** Sub-grouping within a theme (e.g. "Landmarks"). Defaults for catalog rows built from owned items. */
    val subtheme: String = "General",
    /** Rebrickable render of the built set. Null for rows built from owned items without one. */
    val imageUrl: String? = null,
    /**
     * BrickLink "original box" packaging photo — the preferred display image on cards and the detail
     * hero. Falls back to [imageUrl] (the render) when a set has no box item (404).
     */
    val boxImageUrl: String? = null,
    /** Smaller image for list cards; falls back to [imageUrl] when absent. */
    val thumbnailUrl: String? = null,
    /** Brickset number variant (e.g. 1 for "10282-1"); disambiguates same-number sets in lists. */
    val numberVariant: Int = 1,
    /**
     * Catalog primary key (`sets.set_id`, the Brickset setID). Present for rows read from Supabase;
     * null for mock/owned-item-built rows. Used to write user-data rows (which FK to `sets`).
     */
    val setId: Long? = null,
) {
    /**
     * Canonical unique identity ("10282-1"). Set NUMBER alone is not unique — CMF (Collectible
     * Minifigure) series share one number across many variants (71050-1 … 71050-12) — so use this
     * for list keys and detail navigation, never [setNumber] on its own.
     */
    val id: String get() = "$setNumber-$numberVariant"
}
