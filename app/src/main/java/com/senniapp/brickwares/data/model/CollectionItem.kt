package com.senniapp.brickwares.data.model

import com.senniapp.brickwares.util.AppCurrency
import com.senniapp.brickwares.util.CurrencyConverter

/** Whether a collection entry is a set or a minifig (drives the All/Set/Minifig filter). */
enum class ItemType { SET, MINIFIG }

/**
 * Availability status — every item always has one. RETIRED is derived from
 * `date_last_available` in the real data; AVAILABLE/EXCLUSIVE describe currently-sold sets.
 */
enum class Availability { AVAILABLE, PENDING, EXCLUSIVE, GWP, PROMO, MAGAZINE, RETIRED }

/**
 * One owned copy of a set. A set can hold several copies bought at different times, conditions
 * and prices — this is what the See Details modal lists and what the Add sheet creates.
 */
data class Copy(
    val id: String,
    val condition: Condition,
    val qty: Int,
    /** Total paid for this copy's qty, in [currency]'s own unit (USD cents / whole ₫). */
    val pricePaid: Long,
    /** The currency this copy's [pricePaid] was entered in (recorded, not converted). */
    val currency: AppCurrency = AppCurrency.USD,
    val dateAdded: String, // ISO yyyy-MM-dd
    val note: String? = null,
)

/**
 * An owned item (a set or minifig) plus its copies. [retailPrice]/[currentValue] are **USD cents**
 * (the canonical base); each copy's paid price carries its own currency. [currentValue]/[growthPercent]
 * are nullable because "current value" is crowdsourced and often absent — cards must render without them.
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
    /** For a minifig item: how many catalog sets it appears in (the card's "in N sets"). */
    val minifigSetCount: Int = 0,
    val retailPrice: Long,
    val currentValue: Long? = null,
    /** Full community-value detail (freshness/count) for the card's "!" info bubble (Decision 17). */
    val currentValueInfo: CurrentValue? = null,
    val growthPercent: Double? = null,
    val status: Availability = Availability.AVAILABLE,
    val imageUrl: String? = null,
    val copies: List<Copy> = emptyList(),
) {
    /** Total paid across all copies, in **USD cents** (copies may carry different currencies, so each
     *  is normalized before summing). For cross-item math (collection/sales stats); for a single item's
     *  on-screen "Paid" use [totalPaidIn] so a same-currency item shows exactly, without a USD round-trip. */
    val totalPaid: Long get() = copies.sumOf { CurrencyConverter.usdCentsOf(it.pricePaid, it.currency) }

    /** Total number of pieces/units owned across copies. */
    val totalQty: Int get() = copies.sumOf { it.qty }

    /**
     * Total paid across all copies expressed in [display]'s unit, converting each copy **from its own
     * currency** — so a single-currency item shown in that currency is exact (no ₫→cents→₫ drift), while
     * a mixed-currency item still sums consistently with the per-copy rows. Use for the card "Paid" and
     * See-Details totals; format the result with `formatIn(_, display)`.
     */
    fun totalPaidIn(display: AppCurrency): Long =
        copies.sumOf { CurrencyConverter.convert(it.pricePaid, it.currency, display) }

    /** Average paid per **unit** in [display]'s unit (See-Details "Avg"): [totalPaidIn] / total qty. */
    fun avgPaidIn(display: AppCurrency): Long =
        if (totalQty == 0) 0L else totalPaidIn(display) / totalQty

    /**
     * Per-unit "current worth" for the collection value + growth, in **USD cents**. Uses the crowdsourced
     * community value only when it's actually shown for this item — minifigs and retired / promo / magazine
     * sets — otherwise **retail** (an available set is still buyable at retail). Mirrors the card's
     * value-vs-retail rule, so the hero "Collection Value" doesn't just echo the user's own paid price
     * (for a single contributor the community value equals what they paid → Value == Paid, 0% growth).
     */
    val worthPerUnit: Long
        get() {
            val valueShown = itemType == ItemType.MINIFIG ||
                status == Availability.RETIRED || status == Availability.PROMO || status == Availability.MAGAZINE
            return (currentValue?.takeIf { valueShown }) ?: retailPrice
        }
}
