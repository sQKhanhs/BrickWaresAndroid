package com.senniapp.brickwares.data.model

/** Whether a collection entry is a set or a minifig (drives the All/Set/Minifig filter). */
enum class ItemType { SET, MINIFIG }

/**
 * Availability status — every item always has one. RETIRED is derived from
 * `date_last_available` in the real data; AVAILABLE/EXCLUSIVE describe currently-sold sets.
 */
enum class Availability { AVAILABLE, EXCLUSIVE, RETIRED }

/**
 * One entry in the user's collection, as shown on a Collection-tab card.
 *
 * Money is whole VND (Long) at the mock stage. [currentValue]/[growthPercent] are nullable
 * because "current value" is crowdsourced and often absent — cards must render without them.
 */
data class CollectionItem(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int,
    val minifigs: Int,
    val retailPrice: Long,
    val pricePaid: Long,
    val currentValue: Long? = null,
    val growthPercent: Double? = null,
    val status: Availability = Availability.AVAILABLE,
    val imageUrl: String? = null,
)
