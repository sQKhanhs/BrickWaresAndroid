package com.senniapp.brickwares.data.repository

/**
 * The server-side CHECK constraints on the remote user-data tables (migration
 * `20260922120000_hardening.sql`), mirrored on the client so no dirty row can ever violate them. A row
 * past any cap fails the whole push batch for its table, so every write path — add, edit, merge, sell,
 * CSV import — clamps through here BEFORE the row is marked dirty. Pure and JVM-testable.
 */
object UserDataLimits {
    /** `notes` length cap (characters). */
    const val MAX_NOTE_CHARS = 2000

    /** `quantity between 0 and 9999`; the client never stores 0 (an empty copy is tombstoned instead). */
    const val MAX_QUANTITY = 9999

    /** `price >= 0 and price <= 1e12` — in the row's own minor unit (USD cents / whole ₫). */
    const val MAX_PRICE_MINOR = 1_000_000_000_000L

    fun capNote(note: String?): String? = note?.take(MAX_NOTE_CHARS)

    /** Clamp a stored quantity to the valid server range [1, [MAX_QUANTITY]]. */
    fun capQty(quantity: Int): Int = quantity.coerceIn(1, MAX_QUANTITY)

    /** Clamp a price (paid or sale, total for the row) to [0, [MAX_PRICE_MINOR]]. */
    fun capPrice(amount: Long): Long = amount.coerceIn(0L, MAX_PRICE_MINOR)

    /**
     * Whether an identical copy/sale of [added] units may MERGE into an existing row of [existing] units.
     * Past the cap the merge is refused (the caller inserts a fresh row for the new units) rather than
     * clamped — clamping would keep the summed money but drop the units, silently losing data.
     */
    fun canMergeQty(existing: Int, added: Int): Boolean = added >= 1 && existing + added <= MAX_QUANTITY
}
