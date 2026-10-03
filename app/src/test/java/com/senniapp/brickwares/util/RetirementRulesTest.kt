package com.senniapp.brickwares.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The once-per-retirement diff, including the signed-out case that used to consume alerts silently. */
class RetirementRulesTest {

    @Test
    fun `a wishlisted set that retires since the last baseline is alerted once`() {
        val out = RetirementRules.diff(
            retiredNow = setOf("s1"), lastWishlist = setOf("s1", "s2"), lastRetired = emptySet(), signedOut = false,
        )
        assertEquals(setOf("s1"), out.newlyRetired)
        assertTrue(out.saveBaseline)
        // After the baseline is saved (lastRetired now holds s1) the same data alerts nothing.
        val again = RetirementRules.diff(
            retiredNow = setOf("s1"), lastWishlist = setOf("s1", "s2"), lastRetired = setOf("s1"), signedOut = false,
        )
        assertTrue(again.newlyRetired.isEmpty())
    }

    @Test
    fun `an item added to the wishlist already retired is not an alert`() {
        // Not in lastWishlist → the user wishlisted it knowing (or not caring) that it is retired.
        val out = RetirementRules.diff(
            retiredNow = setOf("s9"), lastWishlist = setOf("s1"), lastRetired = emptySet(), signedOut = false,
        )
        assertTrue(out.newlyRetired.isEmpty())
        assertTrue(out.saveBaseline)
    }

    @Test
    fun `while signed out nothing is alerted and the baseline is left alone`() {
        val out = RetirementRules.diff(
            retiredNow = setOf("s1"), lastWishlist = setOf("s1"), lastRetired = emptySet(), signedOut = true,
        )
        assertTrue(out.newlyRetired.isEmpty())
        assertFalse(out.saveBaseline)
    }

    @Test
    fun `a set that retired during a signed-out spell is alerted after signing back in`() {
        // Signed out: the retirement is seen but must NOT be recorded…
        val lastWishlist = setOf("s1")
        val lastRetired = emptySet<String>()
        val whileOut = RetirementRules.diff(setOf("s1"), lastWishlist, lastRetired, signedOut = true)
        assertFalse(whileOut.saveBaseline)
        // …so the stored baseline is unchanged, and the first evaluation after sign-in still finds it new.
        val afterSignIn = RetirementRules.diff(setOf("s1"), lastWishlist, lastRetired, signedOut = false)
        assertEquals(setOf("s1"), afterSignIn.newlyRetired)
    }

    @Test
    fun `shared-number variants are separate keys`() {
        // Variant keys, not set numbers: 71050-3 retiring must not alert for (or be hidden by) 71050-7.
        val out = RetirementRules.diff(
            retiredNow = setOf("s103", "s107"), lastWishlist = setOf("s103", "s107"), lastRetired = setOf("s103"), signedOut = false,
        )
        assertEquals(setOf("s107"), out.newlyRetired)
    }
}
