package com.senniapp.brickwares.data.model

import kotlin.math.roundToLong

/** Freshness of a computed [CurrentValue] (Arch Decision 17). */
enum class ValueFreshness { FRESH, STALE, NONE }

/**
 * The community "current value" for a set or minifig — a recency-tiered median over the public
 * `set_value_contributions` (each = one user's paid price, upserted so one row per user).
 *
 * @param amountVnd  the displayed median in ₫, or null when [freshness] is NONE.
 * @param contributionCount  distinct users backing the shown value (the honesty signal on the card).
 * @param freshness  FRESH (recent window), STALE (only older data), or NONE (nothing yet).
 * @param newestAgeDays  age of the most-recent contribution, for the STALE "last updated …" note.
 */
data class CurrentValue(
    val amountVnd: Long?,
    val contributionCount: Int,
    val freshness: ValueFreshness,
    val newestAgeDays: Int?,
) {
    companion object {
        val NONE = CurrentValue(null, 0, ValueFreshness.NONE, null)
    }
}

/**
 * Turns raw contributions into a displayed [CurrentValue] (Arch Decision 17). Pure + deterministic
 * (no clock/network) so it's unit-testable; the repository supplies the points, retail anchor, and
 * `nowMs`.
 *
 * Rules:
 *  - **Outlier guard** — when a retail anchor is known, drop points outside `[10% … 50×] retail`
 *    before aggregating (a fat-finger 10× barely moves a median but wrecks a small sample's tails).
 *  - **Median, not average** — resists the remaining spread.
 *  - **Tiered recency** — if any point falls in the last ~24 months, the value is the median of
 *    *only* those (recent wins → FRESH); otherwise fall back to the median of all remaining points
 *    (STALE); nothing at all → NONE.
 */
object ValueAggregator {
    /** ~24 months. Decision 17's primary window. */
    private const val RECENT_WINDOW_DAYS = 730L
    private const val OUTLIER_MIN_FACTOR = 0.10   // reject < 10% of retail
    private const val OUTLIER_MAX_FACTOR = 50.0   // reject > 50× retail
    private const val DAY_MS = 86_400_000L

    /**
     * @param points contributions as (valueVnd, submittedAtEpochMs).
     * @param retailVnd retail anchor for the outlier guard; null skips the guard.
     */
    fun aggregate(
        points: List<Pair<Double, Long>>,
        retailVnd: Long?,
        nowMs: Long = System.currentTimeMillis(),
    ): CurrentValue {
        val guarded = if (retailVnd != null && retailVnd > 0) {
            val lo = retailVnd * OUTLIER_MIN_FACTOR
            val hi = retailVnd * OUTLIER_MAX_FACTOR
            points.filter { it.first in lo..hi }
        } else {
            points.filter { it.first > 0 }
        }
        if (guarded.isEmpty()) return CurrentValue.NONE

        val cutoff = nowMs - RECENT_WINDOW_DAYS * DAY_MS
        val recent = guarded.filter { it.second >= cutoff }
        val fresh = recent.isNotEmpty()
        val used = if (fresh) recent else guarded

        val newestAgeDays = ((nowMs - guarded.maxOf { it.second }) / DAY_MS).toInt().coerceAtLeast(0)
        return CurrentValue(
            amountVnd = roundToVnd(median(used.map { it.first })),
            contributionCount = used.size,
            freshness = if (fresh) ValueFreshness.FRESH else ValueFreshness.STALE,
            newestAgeDays = newestAgeDays,
        )
    }

    private fun median(xs: List<Double>): Double {
        val s = xs.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /** Round to the nearest 1,000₫, matching the retail display (CurrencyConverter). */
    private fun roundToVnd(v: Double): Long = (v / 1_000.0).roundToLong() * 1_000L
}
