package com.senniapp.brickwares.util

import com.senniapp.brickwares.data.model.Availability
import com.senniapp.brickwares.data.model.CatalogSet
import java.time.YearMonth

/**
 * The "New LEGO Sets" selection rule, kept in one place so the Home preview and the full
 * (grouped-by-theme) detail page agree on what counts as new.
 *
 * A catalog row is "new" when either:
 *  1. it's a **Pending Release** set (launch date still in the future), or
 *  2. it was **released in the current month or the previous month**.
 *
 * Ordered pending-first (upcoming sets lead), then newest release first, then by set number.
 */
object NewSets {

    fun select(catalog: List<CatalogSet>, ref: YearMonth = YearMonth.now()): List<CatalogSet> {
        val prev = ref.minusMonths(1)
        fun isNew(s: CatalogSet): Boolean {
            if (s.status == Availability.PENDING) return true
            // Month-precise rule; year-only catalog rows (month 0) can't be placed in a month, so
            // they only qualify via the Pending check above.
            if (s.releaseMonth !in 1..12) return false
            val ym = YearMonth.of(s.releaseYear, s.releaseMonth)
            return ym == ref || ym == prev
        }
        return catalog.asSequence()
            .filter(::isNew)
            .sortedWith(
                compareByDescending<CatalogSet> { it.status == Availability.PENDING }
                    .thenByDescending { it.releaseYear * 100 + it.releaseMonth }
                    .thenBy { it.setNumber },
            )
            .toList()
    }

    /**
     * [select]'ed new sets grouped by theme (theme name → its new sets), themes A→Z to mirror the
     * BrickEconomy "new sets" layout. Each theme's sets keep [select]'s newest-first order.
     */
    fun groupedByTheme(catalog: List<CatalogSet>, ref: YearMonth = YearMonth.now()): List<Pair<String, List<CatalogSet>>> =
        select(catalog, ref)
            .groupBy { it.theme }
            .toList()
            .sortedBy { it.first.lowercase() }
}
