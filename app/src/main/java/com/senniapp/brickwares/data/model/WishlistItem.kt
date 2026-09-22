package com.senniapp.brickwares.data.model

/**
 * A set or minifig the user wants but doesn't own yet (the Wishlist tab). It mirrors the
 * display fields of a [CollectionItem] minus the ownership data (no copies / paid), since a
 * wishlisted item hasn't been bought. [currentValue]/[growthPercent] are crowdsourced and
 * often absent — cards must render without them.
 */
data class WishlistItem(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    /** Catalog primary key (`sets.set_id`) of the SELECTED variant — see [CollectionItem.setId]. Null for
     *  minifig items and rows added before this was carried. */
    val setId: Long? = null,
    val retailPrice: Long,
    val currentValue: Long? = null,
    /** Full community-value detail (freshness/count) for the card's "!" info bubble (Decision 17). */
    val currentValueInfo: CurrentValue? = null,
    val growthPercent: Double? = null,
    val status: Availability = Availability.AVAILABLE,
    /** Default card image: the Rebrickable render (thumb). See [boxImageUrl] for the box-shot fallback. */
    val imageUrl: String? = null,
    /** Re-hosted box shot (R2) — the card image fallback and the tap gallery's second image, after the render. */
    val boxImageUrl: String? = null,
    /** When the item was added to the wishlist (epoch millis; the row's updatedAt — wishlist rows aren't
     *  edited after adding, so this is effectively the "date added"). Drives the "Date added" sort. */
    val addedAt: Long = 0L,
)
