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
 * retail, a recently-retired one (< 2 years) has softened, and a long-retired one (>= 2 years) can sit
 * well below retail. NO_ANCHOR (promo / magazine / GWP, which have no real retail price) skips the
 * retail-relative band and uses the absolute ₫ sanity band instead. See [ValueAggregator.tierOf].
 */
enum class ValueGuardTier { AVAILABLE, RETIRED_RECENT, RETIRED_OLD, NO_ANCHOR }

/**
 * One contributed value point for [ValueAggregator]: the amount, its submission time, and whether it
 * came from a realized **sale** (vs a paid price) — the available-tier floor is looser for a sale.
 */
data class ValuePoint(val value: Double, val submittedAtMs: Long, val isSale: Boolean = false)

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
 *    before aggregating (skipped entirely for NO_ANCHOR — promo/magazine/GWP — and for minifigs /
 *    no-price sets). Lower bound: **60% paid / 70% sale** (available), **50%** (retired < 2yr),
 *    **40%** (retired >= 2yr). Upper bound: **5×** (available), **8×** (retired < 2yr), **20×**
 *    (retired >= 2yr) — a fat-finger high barely moves a median but wrecks a small sample's tails.
 *  - **Median, not average** — resists the remaining spread.
 *  - **Tiered recency** — if any point falls in the last ~24 months, the value is the median of
 *    *only* those (recent wins → FRESH); otherwise fall back to the median of all remaining points
 *    (STALE); nothing at all → NONE.
 */
object ValueAggregator {
    /** ~24 months. Decision 17's primary window. */
    private const val RECENT_WINDOW_DAYS = 730L
    // Lower outlier bound (reject below this fraction of retail). The available tier's floor is looser
    // for a realized sale than for a paid price; retired tiers don't distinguish source.
    private const val OUTLIER_MIN_AVAILABLE_PAID = 0.60
    private const val OUTLIER_MIN_AVAILABLE_SALE = 0.70
    private const val OUTLIER_MIN_RETIRED_RECENT = 0.50  // retired < 2 years
    private const val OUTLIER_MIN_RETIRED_OLD = 0.40     // retired >= 2 years
    // Upper outlier bound (reject above this multiple of retail), tiered.
    private const val OUTLIER_MAX_AVAILABLE = 5.0
    private const val OUTLIER_MAX_RETIRED_RECENT = 8.0
    private const val OUTLIER_MAX_RETIRED_OLD = 20.0
    // Split between the two retired tiers: retired < / >= this many days.
    private const val RETIRED_RECENT_DAYS = 730L         // 2 years
    // Absolute ₫ sanity band for items with NO retail anchor (promo/magazine/GWP, minifigs, no-price
    // sets): with nothing to compare against, bound the raw value instead of accepting anything. The
    // cap (~1B₫ ≈ $38k) clears the priciest sealed sets and all normal minifigs while rejecting
    // fat-fingers and the gold-minifig one-offs (~$200k).
    private const val ABS_MIN_VND = 10_000.0
    private const val ABS_MAX_VND = 1_000_000_000.0
    private const val DAY_MS = 86_400_000L

    /**
     * The [ValueGuardTier] for a set from its availability [status]:
     *  - RETIRED → RETIRED_RECENT (< 2 years since retirement) or RETIRED_OLD (>= 2 years), from the
     *    catalog's [retiredYear]/[retiredMonth]; an unknown date falls back to RETIRED_OLD.
     *  - **PROMO / MAGAZINE / GWP** → NO_ANCHOR: they have no real retail price to anchor against
     *    (never sold at retail), so the guard is skipped for them.
     *  - anything else → AVAILABLE.
     */
    fun tierOf(
        status: Availability,
        retiredYear: Int,
        retiredMonth: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): ValueGuardTier {
        if (status == Availability.PROMO || status == Availability.MAGAZINE || status == Availability.GWP) {
            return ValueGuardTier.NO_ANCHOR
        }
        if (status != Availability.RETIRED) return ValueGuardTier.AVAILABLE
        if (retiredYear <= 0) return ValueGuardTier.RETIRED_OLD
        val retiredDate = LocalDate.of(retiredYear, retiredMonth.coerceIn(1, 12), 1)
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalDate()
        val ageDays = ChronoUnit.DAYS.between(retiredDate, today)
        return if (ageDays < RETIRED_RECENT_DAYS) ValueGuardTier.RETIRED_RECENT else ValueGuardTier.RETIRED_OLD
    }

    /**
     * @param points contributed value points (value, submittedAt, isSale).
     * @param retailVnd retail anchor for the guard; null (or a NO_ANCHOR [tier]) uses the absolute band.
     * @param tier availability tier setting the guard's bounds (see [tierOf]).
     */
    fun aggregate(
        points: List<ValuePoint>,
        retailVnd: Long?,
        tier: ValueGuardTier = ValueGuardTier.AVAILABLE,
        nowMs: Long = System.currentTimeMillis(),
    ): CurrentValue {
        val anchored = retailVnd != null && retailVnd > 0 && tier != ValueGuardTier.NO_ANCHOR
        val guarded = if (anchored) {
            val maxF = when (tier) {
                ValueGuardTier.AVAILABLE -> OUTLIER_MAX_AVAILABLE
                ValueGuardTier.RETIRED_RECENT -> OUTLIER_MAX_RETIRED_RECENT
                ValueGuardTier.RETIRED_OLD -> OUTLIER_MAX_RETIRED_OLD
                ValueGuardTier.NO_ANCHOR -> 0.0 // unreachable (anchored is false)
            }
            val hi = retailVnd!! * maxF
            points.filter { p ->
                val minF = when (tier) {
                    // Available floor is looser for a realized sale than a paid price.
                    ValueGuardTier.AVAILABLE -> if (p.isSale) OUTLIER_MIN_AVAILABLE_SALE else OUTLIER_MIN_AVAILABLE_PAID
                    ValueGuardTier.RETIRED_RECENT -> OUTLIER_MIN_RETIRED_RECENT
                    ValueGuardTier.RETIRED_OLD -> OUTLIER_MIN_RETIRED_OLD
                    ValueGuardTier.NO_ANCHOR -> 0.0
                }
                p.value in (retailVnd * minF)..hi
            }
        } else {
            // No retail to compare against → bound the raw value with the absolute sanity band.
            points.filter { it.value in ABS_MIN_VND..ABS_MAX_VND }
        }
        if (guarded.isEmpty()) return CurrentValue.NONE

        val cutoff = nowMs - RECENT_WINDOW_DAYS * DAY_MS
        val recent = guarded.filter { it.submittedAtMs >= cutoff }
        val fresh = recent.isNotEmpty()
        val used = if (fresh) recent else guarded

        val newestAgeDays = ((nowMs - guarded.maxOf { it.submittedAtMs }) / DAY_MS).toInt().coerceAtLeast(0)
        return CurrentValue(
            amountVnd = roundToVnd(median(used.map { it.value })),
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
