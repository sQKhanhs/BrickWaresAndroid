package com.senniapp.brickwares.data.repository

import com.senniapp.brickwares.data.model.CollectionItem
import com.senniapp.brickwares.data.model.CollectionSummary
import com.senniapp.brickwares.data.model.ItemType
import com.senniapp.brickwares.data.model.SalesSummary
import com.senniapp.brickwares.data.model.SoldItem
import com.senniapp.brickwares.data.model.ThemeSummary

/**
 * Derives the collection [CollectionSummary] from the live item list, so the Home hero and the
 * Collection stat row read 0 when empty and grow as items are added (replacing the old static mock
 * summary). A set contributes its own minifig count; a standalone minifig contributes 1.
 */
fun collectionSummaryOf(
    items: List<CollectionItem>,
    bannerImageUrl: String? = null,
): CollectionSummary {
    var setCount = 0
    var minifigCount = 0
    var pieceCount = 0
    var value = 0L
    var paid = 0L
    for (item in items) {
        val qty = item.totalQty
        if (item.itemType == ItemType.SET) {
            setCount += qty
            minifigCount += item.minifigs * qty
        } else {
            // A standalone minifig counts as one minifig.
            minifigCount += qty
        }
        pieceCount += item.pieces * qty
        value += (item.currentValue ?: item.retailPrice) * qty
        paid += item.totalPaid
    }
    val growth = if (paid > 0L) (value - paid).toDouble() / paid * 100.0 else 0.0
    return CollectionSummary(
        setCount = setCount,
        minifigCount = minifigCount,
        pieceCount = pieceCount,
        collectionValue = value,
        paid = paid,
        growthPercent = growth,
        bannerImageUrl = bannerImageUrl,
    )
}

/**
 * Aggregates the Sales stat tiles + profit bar from the live sold-item list, so the Sales sub-view
 * reads 0 when empty and updates as sales are recorded (mirrors [collectionSummaryOf]).
 */
fun salesSummaryOf(sold: List<SoldItem>): SalesSummary {
    val totalPaid = sold.sumOf { it.pricePaid }
    val totalProfit = sold.sumOf { it.profit }
    val avg = if (sold.isEmpty()) 0.0 else sold.map { it.profitPercent }.average()
    val overall = if (totalPaid == 0L) 0.0 else totalProfit.toDouble() / totalPaid * 100.0
    return SalesSummary(sold.size, sold.sumOf { it.saleValue }, totalProfit, avg, overall)
}

/** Per-theme counts + value for the Home "Collection by Theme" card, highest value first. */
fun themeSummariesOf(items: List<CollectionItem>): List<ThemeSummary> =
    items.groupBy { it.theme }
        .map { (theme, list) ->
            ThemeSummary(
                theme = theme,
                setCount = list.sumOf { it.totalQty },
                totalValue = list.sumOf { (it.currentValue ?: it.retailPrice) * it.totalQty },
            )
        }
        .sortedByDescending { it.totalValue }
