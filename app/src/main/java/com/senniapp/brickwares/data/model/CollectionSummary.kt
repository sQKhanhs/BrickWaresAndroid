package com.senniapp.brickwares.data.model

/**
 * Aggregated stats for the signed-in user's collection, shown on the Home hero + stat row.
 *
 * Money fields are stored as whole VND (Long) at the mock stage. `collectionValue` is the
 * sum of per-set current values; note that once current value becomes crowdsourced, many
 * sets won't have one — the Home cold-start states will need to account for that.
 */
data class CollectionSummary(
    val setCount: Int,
    val minifigCount: Int,
    val pieceCount: Int,
    val collectionValue: Long,
    val paid: Long,
    val growthPercent: Double,
    /** Optional banner image behind the hero card; null → placeholder gradient. */
    val bannerImageUrl: String? = null,
)
