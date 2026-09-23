package com.senniapp.brickwares.data.model

import kotlinx.serialization.Serializable

/** Condition of an owned copy, chosen in the Add-to-Collection sheet. */
enum class Condition { NEW, USED }

/**
 * Prefix marking a Set-Detail navigation key that carries an exact catalog `set_id` (e.g. "sid:51452").
 * A bare set_number resolves a shared-number CMF/SDCC series to its LOWEST variant, so Collection /
 * Wishlist / Sales cards — which store the picked variant only as a set_id, not a number_variant — must
 * navigate with this form to open the exact variant the user owns/wants/sold. Resolved in
 * [com.senniapp.brickwares.data.repository.CatalogRepository.fetchSet]. Colon-delimited so it can never
 * collide with a real set_number.
 */
const val SID_PREFIX = "sid:"

/**
 * Catalog (reference) data for a set, returned by set-number search in the Add sheet.
 * In production this comes from Brickset; here it's mock data. It carries everything
 * needed to build a [CollectionItem] except the user-supplied fields (paid, condition…).
 *
 * [Serializable] so the Add sheet can persist the in-progress selection across a rotation via a
 * `rememberSaveable` Saver. Only the constructor properties serialize; [searchKey] (a body val) is
 * recomputed from its initializer on decode, and [id]/[variantKey] are getters with no backing field.
 */
@Serializable
data class CatalogSet(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    /** Retail in **USD cents** (canonical base — LEGO retail is USD), or null when no source price. */
    val retailPrice: Long?,
    val status: Availability,
    /**
     * Retirement date (from the latest past `date_last_available`), split into year/month; both 0
     * when the set isn't retired or the date is unknown. Shown on the detail page for RETIRED sets.
     */
    val retiredYear: Int = 0,
    val retiredMonth: Int = 0,
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
    /** Brickset availability/sourcing note (e.g. "[NA] Available from Target and Kohl's"); usually null. */
    val notes: String? = null,
    /** Vietnamese translation of [notes] (translate-at-ingest, `sets.notes_vi`); null if untranslated. */
    val notesVi: String? = null,
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

    /**
     * Ownership identity — matches [CollectionItem.variantKey]/[WishlistItem.variantKey]: the catalog
     * `set_id` for a cataloged set (so shared-number CMF/SDCC variants mark owned/wishlisted per-variant,
     * not all-or-nothing by number), else the number. Used by the card owned/wishlisted/sold marking.
     */
    val variantKey: String get() = setId?.let { "s$it" } ?: "n$setNumber"

    /**
     * Pre-lowercased "number name theme" key for substring search, computed once at construction.
     * A per-keystroke search is then a single [String.contains] per set with no allocation — vs
     * lowercasing three fields per set per key press (~66k String allocations at the full ~22k
     * catalog, on the main thread). Matching across the field boundary ("75192 mill") is a bonus.
     */
    val searchKey: String = "$setNumber $name $theme".lowercase()
}
