package com.senniapp.brickwares.util

/**
 * The pure decision behind [RetirementAlerts.evaluate] — no Android, no prefs — so it is unit-testable.
 * Inputs are variant keys ("s<set_id>" / "n<number>").
 */
internal object RetirementRules {

    /**
     * @property newlyRetired wishlist items that are retired NOW, were already on the wishlist at the
     *   last baseline, and were not retired then — the ones to alert about.
     * @property saveBaseline whether the caller may overwrite the stored baseline with the current state.
     */
    data class Outcome(val newlyRetired: Set<String>, val saveBaseline: Boolean)

    /**
     * While explicitly signed out NOTHING happens — no alert and, crucially, no baseline update. Room
     * keeps the last session's wishlist after sign-out, so the evaluation still runs; recording the new
     * state then would consume a retirement silently, and on the next sign-in there would be nothing
     * "new" left to alert about. Leaving the baseline untouched defers the diff until an account is back.
     */
    fun diff(
        retiredNow: Set<String>,
        lastWishlist: Set<String>,
        lastRetired: Set<String>,
        signedOut: Boolean,
    ): Outcome {
        if (signedOut) return Outcome(emptySet(), saveBaseline = false)
        val newly = retiredNow.filterTo(LinkedHashSet()) { it in lastWishlist && it !in lastRetired }
        return Outcome(newly, saveBaseline = true)
    }
}
