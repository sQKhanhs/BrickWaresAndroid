package com.senniapp.brickwares.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The client-side mirror of the server CHECK constraints (migration 20260922120000_hardening.sql).
 * A row past any of these fails the whole push batch for its table, so the caps must hold on every
 * write path — these pin the numbers and the edge behaviour.
 */
class UserDataLimitsTest {

    @Test
    fun `caps match the server CHECK constraints`() {
        assertEquals(2000, UserDataLimits.MAX_NOTE_CHARS)
        assertEquals(9999, UserDataLimits.MAX_QUANTITY)
        assertEquals(1_000_000_000_000L, UserDataLimits.MAX_PRICE_MINOR)
    }

    @Test
    fun `note is truncated to the cap and null passes through`() {
        assertNull(UserDataLimits.capNote(null))
        assertEquals("", UserDataLimits.capNote(""))
        val exact = "x".repeat(2000)
        assertEquals(exact, UserDataLimits.capNote(exact))
        assertEquals(2000, UserDataLimits.capNote("y".repeat(2500))!!.length)
    }

    @Test
    fun `note cap counts characters not bytes so Vietnamese text keeps every character`() {
        val vi = "Bộ sưu tập LEGO của tôi — rất đẹp. ".repeat(100) // multi-byte diacritics
        val capped = UserDataLimits.capNote(vi)!!
        assertEquals(2000, capped.length)
        assertEquals(vi.substring(0, 2000), capped)
    }

    @Test
    fun `quantity is clamped into 1 to 9999`() {
        assertEquals(1, UserDataLimits.capQty(0))
        assertEquals(1, UserDataLimits.capQty(-5))
        assertEquals(1, UserDataLimits.capQty(1))
        assertEquals(9999, UserDataLimits.capQty(9999))
        assertEquals(9999, UserDataLimits.capQty(10_000))
        assertEquals(9999, UserDataLimits.capQty(Int.MAX_VALUE))
    }

    @Test
    fun `price is clamped into 0 to 1e12 minor units`() {
        assertEquals(0L, UserDataLimits.capPrice(-1L))
        assertEquals(0L, UserDataLimits.capPrice(0L))
        assertEquals(12_345L, UserDataLimits.capPrice(12_345L))
        assertEquals(1_000_000_000_000L, UserDataLimits.capPrice(1_000_000_000_000L))
        assertEquals(1_000_000_000_000L, UserDataLimits.capPrice(1_000_000_000_001L))
        assertEquals(1_000_000_000_000L, UserDataLimits.capPrice(Long.MAX_VALUE))
    }

    @Test
    fun `a merge is allowed only while the summed quantity stays within the cap`() {
        assertTrue(UserDataLimits.canMergeQty(1, 1))
        assertTrue(UserDataLimits.canMergeQty(9998, 1))
        assertTrue(UserDataLimits.canMergeQty(5000, 4999))
        assertFalse(UserDataLimits.canMergeQty(9999, 1))
        assertFalse(UserDataLimits.canMergeQty(9000, 2000))
        // Nothing to merge: a 0/negative add never merges (the caller clamps it to a fresh row of 1).
        assertFalse(UserDataLimits.canMergeQty(10, 0))
        assertFalse(UserDataLimits.canMergeQty(10, -1))
    }
}
