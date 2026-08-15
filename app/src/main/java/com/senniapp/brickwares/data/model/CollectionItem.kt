package com.senniapp.brickwares.data.model

/** Whether a collection entry is a set or a minifig (drives the All/Set/Minifig filter). */
enum class ItemType { SET, MINIFIG }

/**
 * Availability status — every item always has one. RETIRED is derived from
 * `date_last_available` in the real data; AVAILABLE/EXCLUSIVE describe currently-sold sets.
 */
enum class Availability { AVAILABLE, EXCLUSIVE, RETIRED }

/**
 * One owned copy of a set. A set can hold several copies bought at different times, conditions
 * and prices — this is what the See Details modal lists and what the Add sheet creates.
 */
data class Copy(
    val id: String,
    val condition: Condition,
    val qty: Int,
    val pricePaid: Long,
    val dateAdded: String, // ISO yyyy-MM-dd
    val note: String? = null,
)

/**
 * An owned item (a set or minifig) plus its copies. Money is whole VND (Long) at the mock stage.
 * [currentValue]/[growthPercent] are nullable because "current value" is crowdsourced and often
 * absent — cards must render without them.
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
    val currentValue: Long? = null,
    val growthPercent: Double? = null,
    val status: Availability = Availability.AVAILABLE,
    val imageUrl: String? = null,
    val copies: List<Copy> = emptyList(),
) {
    /** Total paid across all copies (the card's "Paid"). */
    val totalPaid: Long get() = copies.sumOf { it.pricePaid }

    /** Total number of pieces/units owned across copies. */
    val totalQty: Int get() = copies.sumOf { it.qty }

    /** Average paid per copy (shown in the See Details "Avg" row). */
    val avgPaid: Long get() = if (copies.isEmpty()) 0L else copies.sumOf { it.pricePaid } / copies.size
}
