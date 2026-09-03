package com.senniapp.brickwares.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Freshness of a computed [CurrentValue] (Arch Decision 17). */
enum class ValueFreshness { FRESH, STALE, NONE }

/**
 * Availability tier for the outlier guard's retail-relative bounds: an available set trades near
 * retail, a recently-retired one (< 1 year) has softened, and a long-retired one (>= 1 year) can sit
 * well below retail on the used market. See [ValueAggregator.tierOf].
 */
enum class ValueGuardTier { AVAILABLE, RETIRED_RECENT, RETIRED_OLD }

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
 *  - **Outlier guard** — when a retail anchor is known, drop points outside a retail-relative band
 *    before aggregating. The lower bound is tiered by [ValueGuardTier]: reject below **60%** of
 *    retail for an available set, **40%** for a set retired < 1 year, **20%** for one retired >= 1
 *    year. The upper bound is 10× (available) or 50× (retired) — a fat-finger high barely moves a
 *    median but wrecks a small sample's tails.
 *  - **Median, not average** — resists the remaining spread.
 *  - **Tiered recency** — if any point falls in the last ~24 months, the value is the median of
 *    *only* those (recent wins → FRESH); otherwise fall back to the median of all remaining points
 *    (STALE); nothing at all → NONE.
 */
object ValueAggregator {
    /** ~24 months. Decision 17's primary window. */
    private const val RECENT_WINDOW_DAYS = 730L
    // Lower outlier bound (reject a value below this fraction of retail), tiered by availability.
    private const val OUTLIER_MIN_AVAILABLE = 0.60       // available: trades near retail
    private const val OUTLIER_MIN_RETIRED_RECENT = 0.40  // retired < 1 year
    private const val OUTLIER_MIN_RETIRED_OLD = 0.20     // retired >= 1 year
    // Upper outlier bound (reject a value above this multiple of retail).
    private const val OUTLIER_MAX_AVAILABLE = 10.0       // available sets stay near retail
    private const val OUTLIER_MAX_RETIRED = 50.0         // retired sets can genuinely appreciate
    private const val DAY_MS = 86_400_000L
    private const val YEAR_DAYS = 365L

    /**
     * The [ValueGuardTier] for a set from its availability [status]:
     *  - RETIRED → RETIRED_RECENT (< 1 year since retirement) or RETIRED_OLD (>= 1 year), from the
     *    catalog's [retiredYear]/[retiredMonth]; an unknown date falls back to RETIRED_OLD.
     *  - **PROMO / MAGAZINE** → treated as retired even though they never carry a RETIRED status
     *    (they're never sold at retail and Brickset gives them no exit date). With no retirement date
     *    to age, they use RETIRED_OLD.
     *  - anything else → AVAILABLE.
     */
    fun tierOf(
        status: Availability,
        retiredYear: Int,
        retiredMonth: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): ValueGuardTier {
        if (status == Availability.PROMO || status == Availability.MAGAZINE) return ValueGuardTier.RETIRED_OLD
        if (status != Availability.RETIRED) return ValueGuardTier.AVAILABLE
        if (retiredYear <= 0) return ValueGuardTier.RETIRED_OLD
        val retiredDate = LocalDate.of(retiredYear, retiredMonth.coerceIn(1, 12), 1)
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(retiredDate, today)
        return if (ageDays < YEAR_DAYS) ValueGuardTier.RETIRED_RECENT else ValueGuardTier.RETIRED_OLD
    }

    /**
     * @param points contributions as (valueVnd, submittedAtEpochMs).
     * @param retailVnd retail anchor for the outlier guard; null skips the guard.
     * @param tier availability tier setting the guard's retail-relative bounds (see [tierOf]).
     */
    fun aggregate(
        points: List<Pair<Double, Long>>,
        retailVnd: Long?,
        tier: ValueGuardTier = ValueGuardTier.AVAILABLE,
        nowMs: Long = System.currentTimeMillis(),
    ): CurrentValue {
        val guarded = if (retailVnd != null && retailVnd > 0) {
            val minF = when (tier) {
                ValueGuardTier.AVAILABLE -> OUTLIER_MIN_AVAILABLE
                ValueGuardTier.RETIRED_RECENT -> OUTLIER_MIN_RETIRED_RECENT
                ValueGuardTier.RETIRED_OLD -> OUTLIER_MIN_RETIRED_OLD
            }
            val maxF = if (tier == ValueGuardTier.AVAILABLE) OUTLIER_MAX_AVAILABLE else OUTLIER_MAX_RETIRED
            val lo = retailVnd * minF
            val hi = retailVnd * maxF
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
