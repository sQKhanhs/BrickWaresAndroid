package com.senniapp.brickwares.data.model

import com.senniapp.brickwares.util.AppCurrency

/**
 * A sold item, shown in Collection tab's Sales sub-mode. [id] is the sales row's client UUID.
 * [pricePaid]/[saleValue] are in [currency]'s own unit (the currency the sale was entered in);
 * [retailPrice] is **USD cents** (catalog canonical).
 */
data class SoldItem(
    val id: String,
    val setNumber: String,
    /** Catalog `set_id` of the exact variant (null for minifigs / legacy) — see [CollectionItem.setId]. */
    val setId: Long? = null,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val pieces: Int = 0,
    val minifigs: Int = 0,
    /** Default card image: the Rebrickable render (thumb). See [boxImageUrl] for the box-shot fallback. */
    val imageUrl: String? = null,
    /** Re-hosted box shot (R2) — the card image fallback and the tap gallery's second image, after the render. */
    val boxImageUrl: String? = null,
    val retailPrice: Long,
    val pricePaid: Long,
    val saleValue: Long,
    /** The currency [pricePaid] and [saleValue] were entered in (recorded, not converted). */
    val currency: AppCurrency = AppCurrency.USD,
    val quantity: Int = 1,
    val condition: Condition = Condition.NEW,
    val soldOn: String? = null,
    val note: String? = null,
    /** Live catalog status (drives whether the sold card shows a current value). */
    val status: Availability = Availability.AVAILABLE,
    /** Community current value for the item, when shown (retired / promo / magazine sets). */
    val currentValueInfo: CurrentValue? = null,
) {
    /** Grouping/targeting identity — see [CollectionItem.variantKey]. */
    val variantKey: String get() = setId?.let { "s$it" } ?: "n$setNumber"

    /** Set-Detail navigation key — see [CollectionItem.detailNavKey]. */
    val detailNavKey: String get() = setId?.let { "$SID_PREFIX$it" } ?: setNumber

    /** Profit in [currency]'s unit (sale and paid were entered together, so same currency). */
    val profit: Long get() = saleValue - pricePaid
    val profitPercent: Double
        get() = if (pricePaid == 0L) 0.0 else (profit.toDouble() / pricePaid) * 100.0
}

/** Aggregated Sales stats: Total Sold + Sale Value tiles and the profit summary bar. */
data class SalesSummary(
    val totalSold: Int,
    val totalSaleValue: Long,
    val totalProfit: Long,
    val avgProfitPercent: Double,
    val profitPercent: Double,
)
