package com.senniapp.brickwares.data.model

/** A sold item, shown in Collection tab's Sales sub-mode. */
data class SoldItem(
    val setNumber: String,
    val name: String,
    val itemType: ItemType,
    val theme: String,
    val releaseYear: Int,
    val releaseMonth: Int,
    val imageUrl: String? = null,
    val retailPrice: Long,
    val pricePaid: Long,
    val saleValue: Long,
) {
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
