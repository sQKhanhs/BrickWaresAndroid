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
    val retailPrice: Long,
    val currentValue: Long? = null,
    /** Full community-value detail (freshness/count) for the card's "!" info bubble (Decision 17). */
    val currentValueInfo: CurrentValue? = null,
    val growthPercent: Double? = null,
    val status: Availability = Availability.AVAILABLE,
    val imageUrl: String? = null,
)
